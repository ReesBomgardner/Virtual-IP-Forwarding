import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A virtual router that uses a routing table
 * to get the next hop for each packet and
 * rewrites the source and destination MAC addresses
 */

public class Router {
    private String routerId;
    private ConfigParser parser;
    private RoutingTable routingTable;
    private DistanceVector distanceVector;
    private Map<String, DistanceVector> neighborVectors = new HashMap<>();

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Router <config-file> <router-id>");
            System.exit(1);
        }

        try {
            File configFile = new File(args[0]);
            ConfigParser parser = new ConfigParser(configFile);
            String routerId = args[1];

            VirtualPort routerPort = parser.getDevicePort(routerId);
            if (routerPort == null) {
                System.err.println("Error: Router " + routerId + " not found in config.");
                System.exit(1);
            }

            RoutingTable routingTable = parser.getRoutingTable(routerId);
            if (routingTable == null) {
                System.err.println("Error: No routing table for " + routerId);
                System.exit(1);
            }

            Router router = new Router(routerId, parser, routingTable);
            System.out.printf("[ROUTER %s] Running on %s:%d\n",
                    routerId, routerPort.ip.getHostAddress(), routerPort.port);

            router.start(routerPort);
        } catch (Exception e) {
            System.err.println("Router crashed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Router(String routerId, ConfigParser parser, RoutingTable routingTable) {
        this.routerId = routerId;
        this.parser = parser;
        this.routingTable = routingTable;
        this.distanceVector = new DistanceVector();
        initializeDistanceVector();
    }

    private void initializeDistanceVector() {
        for (RoutingTable.Entry entry : routingTable.getEntries()) {
            if ("Direct".equals(entry.nextHop)) {
                distanceVector.addEntry(entry.subnet, 1);
                System.out.printf("[DV] Added direct route: %s (cost 1)\n", entry.subnet);
            }
        }
        System.out.println("[DV] Initial Distance Vector:\n" + distanceVector);
    }

    private void start(VirtualPort routerPort) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(routerPort.port)) {
            sendDistanceVectorToNeighbors(socket);

            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                Frame frame = new Frame();
                frame.readPacket(packet);

                if (frame.type == 0) {
                    processRoutingUpdate(frame, socket);
                } else {
                    processUserPacket(frame, socket);
                }
            }
        }
    }

    private void processRoutingUpdate(Frame frame, DatagramSocket socket) throws Exception {
        System.out.printf("\n[ROUTER %s] Received DV update from %s\n", routerId, frame.sourceMac);

        DistanceVectorFrame dvFrame = new DistanceVectorFrame(frame);
        DistanceVector neighborDV = dvFrame.getDistanceVector();
        neighborVectors.put(frame.sourceMac, neighborDV);

        String[] updatedSubnets = distanceVector.updateEntries(neighborDV);

        if (updatedSubnets.length > 0) {
            System.out.println("[DV] Updated Distance Vector:\n" + distanceVector);
            updateRoutingTableFromDV();
            sendDistanceVectorToNeighbors(socket);
        }
    }

    private void updateRoutingTableFromDV() {
        List<RoutingTable.Entry> directRoutes = routingTable.getEntries().stream()
                .filter(entry -> "Direct".equals(entry.nextHop))
                .toList();

        routingTable.getEntries().clear();
        routingTable.getEntries().addAll(directRoutes);

        for (String subnet : distanceVector.getKnownSubnets()) {
            if (subnet.startsWith("net")) {
                int minDistance = Integer.MAX_VALUE;
                String bestNeighbor = null;

                for (Map.Entry<String, DistanceVector> entry : neighborVectors.entrySet()) {
                    String neighbor = entry.getKey();
                    DistanceVector neighborDV = entry.getValue();

                    if (neighborDV.map.containsKey(subnet)) {
                        int totalDistance = neighborDV.map.get(subnet) + 1;
                        if (totalDistance < minDistance) {
                            minDistance = totalDistance;
                            bestNeighbor = neighbor;
                        }
                    }
                }

                if (bestNeighbor != null && !directRoutes.stream().anyMatch(e -> e.subnet.equals(subnet))) {
                    String exitPort = getExitPortForNeighbor(bestNeighbor);
                    routingTable.addEntry(subnet, bestNeighbor, exitPort, minDistance);
                    System.out.printf("[ROUTING] Added route: %s via %s (cost %d)\n",
                            subnet, bestNeighbor, minDistance);
                }
            }
        }
    }

    private String getExitPortForNeighbor(String neighbor) {
        if (routerId.equals("R1")) {
            if (neighbor.equals("R2")) return "top";
            if (neighbor.equals("R3")) return "bottom";
        }
        return "unknown";
    }

    private void processUserPacket(Frame frame, DatagramSocket socket) throws Exception {
        System.out.printf("\n[ROUTER %s] Processing user packet from %s → %s (Dest IP: %s)\n",
                routerId, frame.sourceMac, frame.destIp, frame.destIp);

        System.out.println("[ROUTING TABLE] Current routes:");
        for (RoutingTable.Entry entry : routingTable.getEntries()) {
            System.out.printf("  %s → %s via %s (cost %d)\n",
                    entry.subnet, entry.nextHop, entry.exitPort, entry.distance);
        }

        RoutingTable.Entry route = routingTable.findBestRoute(frame.destIp);
        if (route == null) {
            System.out.println("[ERROR] No route found for " + frame.destIp);
            System.out.println("[DV] Current distance vector:");
            for (String subnet : distanceVector.getKnownSubnets()) {
                System.out.printf("  %s: %d\n", subnet, distanceVector.getEntry(subnet));
            }
            return;
        }

        System.out.printf("[ROUTING] Selected route: %s via %s\n", route.subnet, route.nextHop);

        frame.sourceMac = routerId;
        frame.destMac = "Direct".equals(route.nextHop)
                ? ConfigParser.getMacForIp(frame.destIp)
                : route.nextHop;

        VirtualPort targetPort;
        if ("Direct".equals(route.nextHop)) {
            targetPort = parser.getDevicePort(route.exitPort);
        } else {
            targetPort = parser.getDevicePort(route.nextHop);
        }

        if (targetPort != null) {
            socket.send(frame.writePacket(targetPort.ip, targetPort.port));
            System.out.printf("[FORWARDED] %s → %s via %s (%s:%d)\n",
                    frame.destIp, route.nextHop, route.exitPort,
                    targetPort.ip.getHostAddress(), targetPort.port);
        } else {
            System.out.println("[ERROR] Invalid target port for " + route.nextHop);
        }
    }

    private void sendDistanceVectorToNeighbors(DatagramSocket socket) throws Exception {
        List<String> neighbors = parser.getNeighbors(routerId);
        for (String neighbor : neighbors) {
            if (neighbor.startsWith("R")) {
                VirtualPort neighborPort = parser.getDevicePort(neighbor);
                if (neighborPort != null) {
                    Frame frame = new Frame(routerId, neighbor, "DV", "DV", new byte[0], 0);
                    DistanceVectorFrame dvFrame = new DistanceVectorFrame(frame);
                    dvFrame.addDistanceVector(distanceVector);
                    socket.send(frame.writePacket(neighborPort.ip, neighborPort.port));
                    System.out.printf("[DV] Sent DV update to %s\n", neighbor);
                }
            }
        }
    }
}