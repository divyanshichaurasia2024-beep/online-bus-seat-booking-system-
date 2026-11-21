import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String serverIp = JOptionPane.showInputDialog(
                null,
                "Enter Booking Server IP (leave 'localhost' if running on this PC):",
                "localhost"
            );
            if (serverIp == null || serverIp.trim().isEmpty()) {
                return;
            }
            BookingSystem app = new BookingSystem(serverIp.trim());
            app.setVisible(true);
        });
    }
}
