import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * A virtual network switch that keeps a MAC address table to map
 * MAC addresses to ports, and forwards/floods frames to MAC address.
 */

public class Switch {
    private final Map<String, VirtualPort> macToPort = new HashMap<>(); // Maps MAC addresses to virtual ports
    private VirtualPort switchPort; // The switch's own virtual port (IP and port)

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Switch <config-file> <switch-id>");
            System.exit(1);
        }

        try {
            File configFile = new File(args[0]);
            ConfigParser parser = new ConfigParser(configFile);
            String switchId = args[1];

            // Get the switch's virtual port from the config file
            VirtualPort switchPort = parser.getDevicePort(switchId);
            if (switchPort == null) {
                System.err.println("Error: Switch " + switchId + " not found in config.");
                System.exit(1);
            }

            System.out.printf("[SWITCH %s] Running on %s:%d\n", switchId, switchPort.ip.getHostAddress(), switchPort.port);
            new Switch(switchPort, parser).run(); // Start the switch

        } catch (Exception e) {
            System.err.println("Switch crashed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Switch(VirtualPort switchPort, ConfigParser parser) {
        this.switchPort = switchPort;
    }

    /**
     * The main loop of the switch. It listens for incoming frames,
     * updates the MAC address table, and forwards or floods frames as needed.
     */
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(switchPort.port)) {
            System.out.println("[MAC TABLE] Initialized (empty)");
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Receive a frame

                Frame frame = new Frame();
                frame.readPacket(packet); // Deserialize the frame

                System.out.printf("\n[SWITCH %s] Received frame: %s → %s (%s → %s)\n",
                        switchPort.port, frame.sourceMac, frame.destMac, frame.sourceIp, frame.destIp);

                // Update the MAC address table with the source MAC and port
                VirtualPort sourcePort = new VirtualPort(packet.getAddress(), packet.getPort());
                macToPort.put(frame.sourceMac, sourcePort);

                System.out.println("[MAC TABLE] Current state:");
                macToPort.forEach((mac, port) -> {
                    System.out.printf("  %s → %s:%d\n", mac, port.ip.getHostAddress(), port.port);
                });

                // Forward the frame to the destination port if known, otherwise flood it
                VirtualPort destPort = macToPort.get(frame.destMac);
                if (destPort != null) {
                    socket.send(frame.writePacket(destPort.ip, destPort.port)); // Forward to the destination
                    System.out.printf("[FORWARDED] To %s (%s:%d)\n",
                            frame.destMac, destPort.ip.getHostAddress(), destPort.port);
                } else {
                    // Flood the frame to all ports except the source port
                    macToPort.forEach((mac, port) -> {
                        if (!mac.equals(frame.sourceMac)) {
                            try {
                                socket.send(frame.writePacket(port.ip, port.port));
                                System.out.printf("[FLOODED] To %s (%s:%d)\n",
                                        mac, port.ip.getHostAddress(), port.port);
                            } catch (Exception e) {
                                System.err.println("Flood error: " + e.getMessage());
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Switch error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}