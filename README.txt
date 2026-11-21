TRANSPORT BOOKING SYSTEM (Multi-System Java Swing Version)
=========================================================

OVERVIEW
--------
This is a networked client-server bus booking application. It allows multiple
users on different laptops/PCs connected to the same Wi-Fi or local network
to book seats in real-time without conflicts.


PROJECT FILES
-------------
1. BookingServer.java - Central TCP server running on port 5000; holds live seat states.
2. Main.java          - Client entry point; prompts for server IP and launches GUI.
3. BookingSystem.java - Client Swing GUI for seat selection and booking.
4. Seat.java          - Model for seat status, 15-second holds, and payment logic.


================================================================================
HOW TO RUN ACROSS MULTIPLE DEVICES OVER WI-FI
================================================================================

PREREQUISITE:
- Both laptops/computers must be connected to the SAME Wi-Fi network or Mobile Hotspot.
- Default Port: 5000

--------------------------------------------------------------------------------
STEP 1: START THE CENTRAL SERVER (ON LAPTOP 1 ONLY)
--------------------------------------------------------------------------------
Choose ONE computer to act as the Server Host (Laptop 1).

Method A - Using VS Code:
1. Open the project folder in VS Code.
2. Open `BookingServer.java`.
3. Click "Run" -> "Run Without Debugging" (or click the Run button at the top right).

Method B - Using Terminal / Command Prompt:
1. Open Command Prompt or PowerShell in the project directory.
2. Compile the files:
      javac *.java
3. Start the server:
      java BookingServer

The server will print its assigned IP address, for example:
   BookingServer started on port 5000
   Local IP for other computers on the same network: 192.168.1.15
   Waiting for clients...

* Note down this IP address (e.g., 192.168.1.15).
* (If it does not display the IP, open terminal on Laptop 1 and run `ipconfig`, 
  then look for "IPv4 Address" under your Wi-Fi adapter).

--------------------------------------------------------------------------------
STEP 2: RUN CLIENT ON LAPTOP 1 (SERVER MACHINE)
--------------------------------------------------------------------------------
1. In VS Code or Terminal on Laptop 1, run `Main.java`:
      java Main
2. A pop-up will ask for the Server IP.
3. Leave it as `localhost` and click OK.

--------------------------------------------------------------------------------
STEP 3: RUN CLIENT ON LAPTOP 2, 3, etc. (OTHER COMPUTERS)
--------------------------------------------------------------------------------
1. Ensure Laptop 2 is connected to the exact SAME Wi-Fi network.
2. Copy or clone this project folder to Laptop 2.
3. In VS Code or Terminal on Laptop 2, run `Main.java`:
      java Main
4. A pop-up will ask for the Server IP.
5. Replace `localhost` with Laptop 1's IP address (e.g., 192.168.1.15).
6. Click OK.

Both laptops are now connected to the same live server! Any seat held or booked
on one laptop will immediately update on all other connected screens.

--------------------------------------------------------------------------------
TROUBLESHOOTING & FIREWALL (IF LAPTOP 2 CANNOT CONNECT)
--------------------------------------------------------------------------------
If Laptop 2 fails to connect to Laptop 1, Windows Firewall on Laptop 1 is likely
blocking incoming traffic on Port 5000.

To unblock on Laptop 1 (Server Host):
1. Press Windows Key, search for "Windows Defender Firewall with Advanced Security".
2. Click "Inbound Rules" -> "New Rule...".
3. Select "Port" -> Click Next.
4. Select "TCP" and enter Specific local ports: 5000 -> Click Next.
5. Select "Allow the connection" -> Click Next -> Next.
6. Name it "Bus Booking Server (Port 5000)" and click Finish.

Alternatively, ensure your network profile in Windows is set to "Private network" 
instead of "Public network".


================================================================================
HOW THE BOOKING LOGIC WORKS
================================================================================
- Concurrency Control: Only ONE user can hold or book a specific seat at a time.
- Hold Timer: When a user selects a seat, they get a 15-second reservation hold 
  (Seat turns Yellow).
- Other Users: Other connected laptops see the seat turn Yellow and cannot book it.
- Confirmed Booking: If the user completes payment within 15 seconds, the seat turns 
  Red (Booked) permanently for that trip.
- Hold Expiry: If 15 seconds elapse without payment confirmation, the seat 
  automatically reverts to Green (Available) for anyone to book.
