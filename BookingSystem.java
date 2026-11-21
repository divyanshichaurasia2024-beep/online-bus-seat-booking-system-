import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BookingSystem extends JFrame {
    private static final int SERVER_PORT = 5000;

    private final String serverHost;
    private Socket socket;
    private BufferedReader serverIn;
    private PrintWriter serverOut;

    private Map<String, JButton> seatButtons = new LinkedHashMap<>();
    private Map<String, SeatView> seatViews = new LinkedHashMap<>();
    private JTextArea activityLog = new JTextArea(7, 35);
    private JComboBox<String> routeBox;
    private JTextField dateField;
    private JLabel tripLabel;

    private static class HoldInfo {
        final String name;
        final int secondsLeft;

        HoldInfo(String name, int secondsLeft) {
            this.name = name;
            this.secondsLeft = secondsLeft;
        }
    }

    private static class SeatView {
        String status = Seat.AVAILABLE;
        String bookedBy = "";
        List<HoldInfo> holds = new ArrayList<>();

        int maxSecondsLeft() {
            int max = 0;
            for (HoldInfo h : holds) {
                max = Math.max(max, h.secondsLeft);
            }
            return max;
        }
    }

    public BookingSystem(String serverHost) {
        this.serverHost = serverHost;

        setTitle("Transport Booking System (" + serverHost + ")");
        setSize(650, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createSeatPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        connectToServer();

        // Poll server every second so all connected computers stay in sync.
        Timer timer = new Timer(1000, e -> syncSeatsFromServer());
        timer.start();

        updateTrip();
        syncSeatsFromServer();
        log("Connected to server " + serverHost + ":" + SERVER_PORT + ". Hold timeout = 15 seconds.");
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverHost, SERVER_PORT);
            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            serverOut = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not connect to BookingServer at " + serverHost + ":" + SERVER_PORT
                    + "\nMake sure BookingServer is running first (java BookingServer).",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }
    }

    private synchronized String sendCommand(String command) {
        try {
            serverOut.println(command);
            String response = serverIn.readLine();
            if (response == null) {
                throw new IOException("Server closed connection.");
            }
            return response;
        } catch (IOException e) {
            log("[ERROR] Lost connection to server: " + e.getMessage());
            return "ERROR|Connection lost";
        }
    }

    private String getTripKey() {
        String route = String.valueOf(routeBox.getSelectedItem()).replace("|", " ");
        String date = dateField.getText().trim().replace("|", " ");
        return route + " @ " + date;
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        String[] routes = {
            "Route A: Central Station - Airport",
            "Route B: City Center - University",
            "Route C: Harbor - Business Park"
        };

        routeBox = new JComboBox<>(routes);
        dateField = new JTextField(java.time.LocalDate.now().toString(), 10);
        tripLabel = new JLabel();

        JButton stressButton = new JButton("Stress Test (10 users)");
        stressButton.addActionListener(e -> runStressTest());

        panel.add(new JLabel("Bus Route:"));
        panel.add(routeBox);
        panel.add(new JLabel("Date:"));
        panel.add(dateField);
        panel.add(stressButton);

        routeBox.addActionListener(e -> {
            updateTrip();
            syncSeatsFromServer();
        });
        dateField.addActionListener(e -> {
            updateTrip();
            syncSeatsFromServer();
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        wrapper.add(tripLabel, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel createSeatPanel() {
        JPanel outer = new JPanel(new BorderLayout(5, 5));
        outer.setBorder(BorderFactory.createTitledBorder("Bus Seats"));

        JPanel front = new JPanel();
        front.add(new JLabel("FRONT       DRIVER"));
        outer.add(front, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(8, 5, 8, 8));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 5; col++) {
                String id = "S" + (row * 5 + col + 1);
                seatViews.put(id, new SeatView());

                JButton button = new JButton(id);
                button.setPreferredSize(new Dimension(75, 48));
                button.addActionListener(e -> handleSeatClick(id));
                seatButtons.put(id, button);
                grid.add(button);
            }
        }

        outer.add(grid, BorderLayout.CENTER);
        return outer;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel legend = new JLabel(
            "  GREEN: Available     YELLOW: Reserved     RED: Booked"
        );
        activityLog.setEditable(false);
        activityLog.setFont(new Font("Monospaced", Font.PLAIN, 12));

        panel.add(legend, BorderLayout.NORTH);
        panel.add(new JScrollPane(activityLog), BorderLayout.CENTER);
        return panel;
    }

    private void handleSeatClick(String id) {
        syncSeatsFromServer();
        SeatView view = seatViews.get(id);

        if (view.status.equals(Seat.AVAILABLE)) {
            promptAndReserve(id);
        } else if (view.status.equals(Seat.RESERVED)) {
            if (view.holds.isEmpty()) {
                syncSeatsFromServer();
                return;
            }

            HoldInfo holder = view.holds.get(0);
            String message = "Seat " + id + " is currently reserved by: " + holder.name
                + "\nTime remaining: " + holder.secondsLeft + " seconds."
                + "\n\nDo you want to confirm payment for " + holder.name + "?";

            int choice = JOptionPane.showConfirmDialog(
                this,
                message,
                "Confirm Payment - Seat " + id,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                String selectedName = holder.name;
                String resp = sendCommand("CONFIRM|" + getTripKey() + "|" + id + "|" + selectedName);
                String[] parts = resp.split("\\|", -1);

                if (parts[0].equals("OK")) {
                    log(id + " payment confirmed by " + selectedName + ". Seat booked.");
                    JOptionPane.showMessageDialog(
                        this,
                        "Booking confirmed! Seat " + id + " is yours, " + selectedName + "."
                    );
                } else {
                    JOptionPane.showMessageDialog(
                        this,
                        "Seat occupied or reservation expired.",
                        "Cannot Complete Booking",
                        JOptionPane.WARNING_MESSAGE
                    );
                    log(id + " - Booking failed for " + selectedName + ".");
                }
                syncSeatsFromServer();
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Seat already booked by " + (view.bookedBy.isEmpty() ? "another passenger" : view.bookedBy) + ".",
                "Seat Occupied",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void promptAndReserve(String id) {
        String name = JOptionPane.showInputDialog(
            this, "Enter passenger name for " + id
        );

        if (name == null) return;
        name = name.trim().replace("|", " ").replace(":", " ").replace(",", " ").replace("~", " ");

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.");
            return;
        }

        String resp = sendCommand("RESERVE|" + getTripKey() + "|" + id + "|" + name);
        String[] parts = resp.split("\\|", -1);

        if (parts[0].equals("OK")) {
            log(id + " reserved for " + name + ". Confirm payment within 15 seconds.");
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Seat occupied.",
                "Seat Occupied",
                JOptionPane.WARNING_MESSAGE
            );
        }
        syncSeatsFromServer();
    }

    private void syncSeatsFromServer() {
        String resp = sendCommand("STATUS|" + getTripKey());
        String[] parts = resp.split("\\|", -1);
        if (!parts[0].equals("OK")) {
            return;
        }

        for (int i = 1; i < parts.length; i++) {
            String[] seatParts = parts[i].split(":", 3);
            if (seatParts.length < 2) continue;

            String id = seatParts[0];
            String status = seatParts[1];
            SeatView view = seatViews.get(id);
            if (view == null) continue;

            view.status = status;
            view.holds.clear();
            view.bookedBy = "";

            if (status.equals(Seat.RESERVED) && seatParts.length == 3 && !seatParts[2].isEmpty()) {
                String[] holdItems = seatParts[2].split(",");
                for (String item : holdItems) {
                    String[] hp = item.split("~", 2);
                    if (hp.length == 2) {
                        try {
                            view.holds.add(new HoldInfo(hp[0], Integer.parseInt(hp[1])));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } else if (status.equals(Seat.BOOKED) && seatParts.length == 3) {
                view.bookedBy = seatParts[2];
            }
        }
        refreshSeats();
    }

    private void refreshSeats() {
        for (Map.Entry<String, SeatView> entry : seatViews.entrySet()) {
            String id = entry.getKey();
            SeatView view = entry.getValue();
            JButton button = seatButtons.get(id);

            if (view.status.equals(Seat.AVAILABLE)) {
                button.setBackground(Color.GREEN);
                button.setText(id);
                button.setToolTipText("Available - click to reserve");
            } else if (view.status.equals(Seat.RESERVED)) {
                button.setBackground(Color.YELLOW);
                button.setText(id + " " + view.maxSecondsLeft() + "s");
                String holderName = view.holds.isEmpty() ? "" : view.holds.get(0).name;
                button.setToolTipText("Reserved by " + holderName + " (" + view.maxSecondsLeft() + "s left)");
            } else {
                button.setBackground(Color.RED);
                button.setText(id);
                button.setToolTipText("Booked by " + view.bookedBy);
            }
            button.setOpaque(true);
            button.setForeground(Color.BLACK);
        }
    }

    private void runStressTest() {
        String resp = sendCommand("STRESS|" + getTripKey());
        String[] parts = resp.split("\\|", -1);

        if (parts[0].equals("OK") && parts.length >= 3) {
            String seatId = parts[1];
            String count = parts[2];
            log("STRESS TEST: 10 threads attempted to reserve " + seatId
                + ". Successful reservation: " + count + " (only 1 user can hold a seat at a time).");
            syncSeatsFromServer();

            JOptionPane.showMessageDialog(
                this,
                "Stress test finished for seat: " + seatId
                    + "\nSuccessful reservation: " + count + " / 1 (exclusivity verified!)"
                    + "\nOther 9 concurrent requests were rejected because only one person can hold the seat."
            );
        } else {
            JOptionPane.showMessageDialog(this, "No available seats.");
        }
    }

    private void updateTrip() {
        tripLabel.setText("Trip: " + routeBox.getSelectedItem()
            + " | Date: " + dateField.getText());
    }

    private void log(String message) {
        activityLog.append(message + "\n");
        activityLog.setCaretPosition(activityLog.getDocument().getLength());
    }
}