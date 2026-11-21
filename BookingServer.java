import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BookingServer {
    public static final int PORT = 5000;
    public static final long HOLD_TIME = 15_000; // 15 seconds

    private final Map<String, Map<String, Seat>> trips = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new BookingServer().start();
    }

    public void start() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::releaseExpiredReservations, 1, 1, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            System.out.println("BookingServer started on port " + PORT);
            System.out.println("Local IP for other computers on the same network: " + localIp);
            System.out.println("Waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scheduler.shutdown();
        }
    }

    private Map<String, Seat> getOrCreateTripSeats(String tripKey) {
        return trips.computeIfAbsent(tripKey, k -> {
            Map<String, Seat> map = new LinkedHashMap<>();
            for (int i = 1; i <= 40; i++) {
                String id = "S" + i;
                map.put(id, new Seat(id));
            }
            return map;
        });
    }

    private void releaseExpiredReservations() {
        for (Map.Entry<String, Map<String, Seat>> entry : trips.entrySet()) {
            String tripKey = entry.getKey();
            for (Seat seat : entry.getValue().values()) {
                List<String> expired = seat.removeExpiredReservations(HOLD_TIME);
                for (String name : expired) {
                    System.out.println("[EXPIRED] " + tripKey + " | " + seat.getId()
                        + " hold expired for " + name
                        + (seat.getStatus().equals(Seat.AVAILABLE) ? " (seat released)" : ""));
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = processCommand(line);
                out.println(response);
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }

    private String processCommand(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 2) {
            return "ERROR|Invalid command";
        }

        String cmd = parts[0];
        String tripKey = parts[1];
        Map<String, Seat> seats = getOrCreateTripSeats(tripKey);

        switch (cmd) {
            case "STATUS":
                return buildStatusResponse(seats);

            case "RESERVE": {
                if (parts.length < 4) return "ERROR|Missing parameters";
                String seatId = parts[2];
                String name = sanitize(parts[3]);
                Seat seat = seats.get(seatId);
                if (seat == null) return "ERROR|Invalid seat";

                if (seat.reserve(name)) {
                    System.out.println("[RESERVE] " + tripKey + " | " + seatId + " reserved for " + name);
                    return "OK|RESERVED";
                } else {
                    return "FAIL|OCCUPIED|" + safe(seat.getPassengerName());
                }
            }

            case "CONFIRM": {
                if (parts.length < 4) return "ERROR|Missing parameters";
                String seatId = parts[2];
                String name = sanitize(parts[3]);
                Seat seat = seats.get(seatId);
                if (seat == null) return "ERROR|Invalid seat";

                if (seat.confirmPayment(name, HOLD_TIME)) {
                    System.out.println("[BOOKED] " + tripKey + " | " + seatId + " confirmed by " + name);
                    return "OK|BOOKED|" + name;
                } else if (seat.getStatus().equals(Seat.BOOKED)) {
                    System.out.println("[FAILED] " + tripKey + " | " + seatId
                        + " payment failed for " + name + " (already booked by " + seat.getPassengerName() + ")");
                    return "FAIL|ALREADY_BOOKED|" + safe(seat.getPassengerName());
                } else {
                    System.out.println("[EXPIRED] " + tripKey + " | " + seatId
                        + " payment failed for " + name + " (hold expired)");
                    return "FAIL|EXPIRED|" + name;
                }
            }

            case "STRESS": {
                String freeSeatId = null;
                for (Seat s : seats.values()) {
                    if (s.getStatus().equals(Seat.AVAILABLE)) {
                        freeSeatId = s.getId();
                        break;
                    }
                }
                if (freeSeatId == null) {
                    return "FAIL|NO_SEATS";
                }

                final Seat seat = seats.get(freeSeatId);
                final AtomicInteger successfulReservations = new AtomicInteger(0);
                Thread[] threads = new Thread[10];
                for (int i = 0; i < 10; i++) {
                    final String name = "Racer-" + (i + 1);
                    threads[i] = new Thread(() -> {
                        if (seat.reserve(name)) {
                            successfulReservations.incrementAndGet();
                        }
                    });
                }
                for (Thread t : threads) t.start();
                try {
                    for (Thread t : threads) t.join();
                } catch (InterruptedException ignored) {
                }

                System.out.println("[STRESS] " + tripKey + " | 10 threads competed for " + freeSeatId
                    + " (" + successfulReservations.get() + "/10 succeeded)");
                return "OK|" + freeSeatId + "|" + successfulReservations.get();
            }

            default:
                return "ERROR|Unknown command";
        }
    }

    private String buildStatusResponse(Map<String, Seat> seats) {
        StringBuilder sb = new StringBuilder("OK");
        for (Seat seat : seats.values()) {
            sb.append("|").append(seat.getId()).append(":");
            synchronized (seat) {
                String status = seat.getStatus();
                sb.append(status);
                if (status.equals(Seat.RESERVED)) {
                    sb.append(":");
                    List<Seat.Reservation> holds = seat.getReservations();
                    for (int i = 0; i < holds.size(); i++) {
                        if (i > 0) sb.append(",");
                        Seat.Reservation r = holds.get(i);
                        sb.append(sanitize(r.getPassengerName()))
                          .append("~")
                          .append(r.secondsLeft(HOLD_TIME));
                    }
                } else if (status.equals(Seat.BOOKED)) {
                    sb.append(":").append(sanitize(safe(seat.getPassengerName())));
                }
            }
        }
        return sb.toString();
    }

    private String sanitize(String input) {
        return input.replace("|", " ")
                    .replace(":", " ")
                    .replace(",", " ")
                    .replace("~", " ")
                    .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
