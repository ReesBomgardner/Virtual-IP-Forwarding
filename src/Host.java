import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A virtual host in the network that sends and
 * receives frames to/from other hosts via S1 & S2
 */

public class Host {
    private static String sourceMac;
    private static String sourceIp;
    private static String defaultGateway;
    static VirtualPort switchPort;
    private static VirtualPort hostPort;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Host <config-file> <host-mac>");
            System.exit(1);
        }

        File configFile = new File(args[0]);
        if (!configFile.exists()) {
            System.err.println("Config file not found: " + configFile.getAbsolutePath());
            System.exit(1);
        }

        ConfigParser parser = new ConfigParser(configFile);
        sourceMac = args[1];

        // Get host's virtual port from config file
        hostPort = parser.getDevicePort(sourceMac);
        if (hostPort == null) {
            System.err.println("Error: No IP/port configured for host " + sourceMac);
            System.exit(1);
        }

        // Get host's virtual IP address from config file
        sourceIp = parser.getVirtualIp(sourceMac);
        if (sourceIp == null) {
            System.err.println("Error: No virtual IP found for host " + sourceMac);
            System.exit(1);
        }

        // Get host's default gateway from config file
        defaultGateway = parser.getDefaultGateway(sourceMac);
        if (defaultGateway == null) {
            System.err.println("Error: No default gateway found for host " + sourceMac);
            System.exit(1);
        }

        // Find the switch connected to host
        List<String> neighbors = parser.getNeighbors(sourceMac);
        String switchId = neighbors.stream()
                .filter(neighbor -> neighbor.startsWith("S"))
                .findFirst()
                .orElse(null);

        if (switchId == null) {
            System.err.println("Error: No switch found for host " + sourceMac);
            System.exit(1);
        }

        // Get switch's virtual port from config file
        switchPort = parser.getDevicePort(switchId);
        if (switchPort == null) {
            System.err.println("Error: No switch port found for switch " + switchId);
            System.exit(1);
        }

        try (DatagramSocket socket = new DatagramSocket(hostPort.port)) {
            System.out.printf("Host %s (%s) running on port %d\n",
                    sourceMac, sourceIp, hostPort.port);

            // Send initialization message to switch
            String initialMessage = "Host " + sourceMac + " has connected";
            Frame initialFrame = new Frame(sourceMac, switchId, sourceIp, "Initialization", initialMessage);
            DatagramPacket initPacket = initialFrame.writePacket(switchPort.ip, switchPort.port);
            socket.send(initPacket);
            System.out.println("[HOST] Sent initialization message to the switch.");

            // Start thread to receive packets
            ExecutorService executor = Executors.newFixedThreadPool(1);
            executor.submit(() -> receivePackets(socket));

            // Start interactive packet sending loop
            sendPacketsInteractively(socket);

            executor.shutdown();
        } catch (Exception e) {
            System.err.println("Host error: " + e.getMessage());
        }
    }

    // Interactively send packets to other hosts
    private static void sendPacketsInteractively(DatagramSocket socket) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                System.out.println("\nEnter destination virtual IP (e.g., net1.A, net3.C):");
                String destIp = scanner.nextLine().trim();

                System.out.println("Enter message:");
                String message = scanner.nextLine().trim();

                // Determine destination MAC address
                String destMac;
                if (isInSameSubnet(destIp)) {
                    destMac = ConfigParser.getMacForIp(destIp); // Same subnet
                } else {
                    destMac = ConfigParser.getMacForIp(defaultGateway); // Different subnet (use default gateway)
                }

                if (destMac == null) {
                    System.err.println("Error: Could not resolve MAC for " + destIp);
                    continue;
                }

                // Create and send frame
                Frame frame = new Frame(sourceMac, destMac, sourceIp, destIp, message);
                DatagramPacket packet = frame.writePacket(switchPort.ip, switchPort.port);
                socket.send(packet);
                System.out.println("Sent message to " + destIp);
            } catch (Exception e) {
                System.err.println("Failed to send packet: " + e.getMessage());
            }
        }
    }

    // Checks if destination IP is in the same subnet as host
    private static boolean isInSameSubnet(String destIp) {
        String sourceSubnet = sourceIp.split("\\.")[0];
        String destSubnet = destIp.split("\\.")[0];
        return sourceSubnet.equals(destSubnet);
    }

    // Listens for incoming packets and processes them
    private static void receivePackets(DatagramSocket socket) {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                Frame frame = new Frame();
                frame.readPacket(packet);

                // Check if frame is intended for current host
                if (frame.destMac.equals(sourceMac) || frame.destIp.equals(sourceIp)) {
                    System.out.printf("\n[Received from %s (%s)]\n%s\n",
                            frame.sourceIp, frame.sourceMac, frame.message);
                } else {
                    System.out.printf("Received frame for %s (%s) (ignoring)\n", frame.destMac, frame.destIp);
                }
            } catch (Exception e) {
                System.err.println("Error receiving packet: " + e.getMessage());
            }
        }
    }
}