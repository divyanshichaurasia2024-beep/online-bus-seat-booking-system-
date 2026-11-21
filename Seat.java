import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Seat {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String RESERVED = "RESERVED";
    public static final String BOOKED = "BOOKED";

    public static class Reservation {
        private final String passengerName;
        private final long reserveTime;

        public Reservation(String passengerName, long reserveTime) {
            this.passengerName = passengerName;
            this.reserveTime = reserveTime;
        }

        public String getPassengerName() {
            return passengerName;
        }

        public long getReserveTime() {
            return reserveTime;
        }

        public int secondsLeft(long holdTimeMs) {
            long elapsed = System.currentTimeMillis() - reserveTime;
            long remaining = Math.max(0L, holdTimeMs - elapsed);
            return (int) Math.ceil(remaining / 1000.0);
        }
    }

    private final String id;
    private String status;
    private String passengerName;
    private final List<Reservation> reservations;

    public Seat(String id) {
        this.id = id;
        this.status = AVAILABLE;
        this.passengerName = null;
        this.reservations = new ArrayList<>();
    }

    public synchronized String getId() {
        return id;
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized String getPassengerName() {
        return passengerName;
    }

    public synchronized List<Reservation> getReservations() {
        return new ArrayList<>(reservations);
    }

    public synchronized boolean reserve(String name) {
        if (!status.equals(AVAILABLE)) {
            return false;
        }
        status = RESERVED;
        passengerName = name;
        reservations.clear();
        reservations.add(new Reservation(name, System.currentTimeMillis()));
        return true;
    }

    public synchronized boolean confirmPayment(String name, long holdTimeMs) {
        removeExpiredReservations(holdTimeMs);
        if (status.equals(RESERVED) && passengerName != null && passengerName.equals(name)) {
            status = BOOKED;
            reservations.clear();
            return true;
        }
        return false;
    }

    public synchronized List<String> removeExpiredReservations(long holdTimeMs) {
        List<String> expiredNames = new ArrayList<>();
        long now = System.currentTimeMillis();
        Iterator<Reservation> it = reservations.iterator();
        while (it.hasNext()) {
            Reservation r = it.next();
            if (now - r.getReserveTime() >= holdTimeMs) {
                expiredNames.add(r.getPassengerName());
                it.remove();
            }
        }
        if (status.equals(RESERVED) && reservations.isEmpty()) {
            status = AVAILABLE;
            passengerName = null;
        }
        return expiredNames;
    }
}
