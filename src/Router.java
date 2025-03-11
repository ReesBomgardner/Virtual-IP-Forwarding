import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;

/**
 * A virtual router that uses a routing table
 * to get the next hop for each packet and
 * rewrites the source and destination MAC addresses
 */

public class Router {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Router <config-file> <router-id>");
            System.exit(1);
        }

        try {
            File configFile = new File(args[0]);
            ConfigParser parser = new ConfigParser(configFile);
            String routerId = args[1];

            // Get router's virtual port from config file
            VirtualPort routerPort = parser.getDevicePort(routerId);
            if (routerPort == null) {
                System.err.println("Error: Router " + routerId + " not found in config.");
                System.exit(1);
            }

            // Get router's routing table from config file
            RoutingTable routingTable = parser.getRoutingTable(routerId);
            if (routingTable == null) {
                System.err.println("Error: No routing table for " + routerId);
                System.exit(1);
            }

            System.out.printf("[ROUTER %s] Running on %s:%d\n", routerId, routerPort.ip.getHostAddress(), routerPort.port);

            try (DatagramSocket socket = new DatagramSocket(routerPort.port)) {
                // Send an initialization frame to S1 & S2
                List<String> neighbors = parser.getNeighbors(routerId);
                String switchId = neighbors.stream()
                        .filter(neighbor -> neighbor.startsWith("S")) // Find switch
                        .findFirst()
                        .orElse(null);

                if (switchId != null) {
                    VirtualPort switchPort = parser.getDevicePort(switchId);
                    if (switchPort != null) {
                        Frame initialFrame = new Frame(routerId, switchId, "net1.R1", "net1." + switchId, "Initialization");
                        DatagramPacket initialPacket = initialFrame.writePacket(switchPort.ip, switchPort.port);
                        socket.send(initialPacket);
                        System.out.println("[ROUTER] Sent initial dummy frame to " + switchId);
                    }
                }

                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Receive a frame

                    Frame frame = new Frame();
                    frame.readPacket(packet); // Deserialize the frame
                    System.out.printf("\n[ROUTER %s] Received frame from %s → %s (Dest IP: %s)\n",
                            routerId, frame.sourceMac, frame.destMac, frame.destIp);

                    // Look up the destination MAC address for the destination IP
                    String destMac = ConfigParser.getMacForIp(frame.destIp);
                    if (destMac == null) {
                        System.out.println("[ERROR] Destination host not found: " + frame.destIp);
                        continue;
                    }

                    // Find the best route for the destination IP
                    RoutingTable.Entry route = routingTable.findBestRoute(frame.destIp);
                    if (route == null) {
                        System.out.println("[ERROR] No route found for " + frame.destIp);
                        continue;
                    }

                    // Rewrite the source MAC address to the router's MAC address
                    frame.sourceMac = routerId;

                    // Rewrite the destination MAC address based on the route
                    if ("Direct".equals(route.nextHop)) {
                        frame.destMac = destMac; // Directly connected subnet
                    } else {
                        frame.destMac = ConfigParser.getMacForIp(route.nextHop); // Next-hop router
                        if (frame.destMac == null) {
                            System.out.println("[ERROR] No MAC found for next-hop router: " + route.nextHop);
                            continue;
                        }
                    }

                    // Determine the target port for forwarding the frame
                    VirtualPort targetPort;
                    if ("Direct".equals(route.nextHop)) {
                        targetPort = parser.getDevicePort(route.exitPort); // Directly connected subnet
                    } else {
                        targetPort = parser.getDevicePort(route.nextHop.split("\\.")[1]); // Next hop router
                    }

                    // Forward the frame to the target port
                    if (targetPort != null) {
                        socket.send(frame.writePacket(targetPort.ip, targetPort.port));
                        System.out.printf("[FORWARDED] %s → %s via %s (%s:%d)\n",
                                frame.destIp, route.nextHop, route.exitPort, targetPort.ip, targetPort.port);
                    } else {
                        System.out.println("[ERROR] Invalid target port for " + route.nextHop);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Router crashed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}