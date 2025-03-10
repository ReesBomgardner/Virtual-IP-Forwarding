import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

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

            System.out.printf("[ROUTER %s] Running on %s:%d\n", routerId, routerPort.ip.getHostAddress(), routerPort.port);

            try (DatagramSocket socket = new DatagramSocket(routerPort.port)) {
                VirtualPort s1Port = parser.getDevicePort("S1");
                if (s1Port != null) {
                    Frame dummyFrame = new Frame(routerId, "S1", "net1.R1", "net1.S1", "Dummy");
                    DatagramPacket dummyPacket = dummyFrame.writePacket(s1Port.ip, s1Port.port);
                    socket.send(dummyPacket);
                    System.out.println("[ROUTER] Sent initial dummy frame to S1");
                }

                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    Frame frame = new Frame();
                    frame.readPacket(packet);
                    System.out.printf("\n[ROUTER %s] Received frame from %s → %s (Dest IP: %s)\n",
                            routerId, frame.sourceMac, frame.destMac, frame.destIp);

                    String destMac = ConfigParser.getMacForIp(frame.destIp);
                    if (destMac == null) {
                        System.out.println("[ERROR] Destination host not found: " + frame.destIp);
                        continue;
                    }

                    RoutingTable.Entry route = routingTable.findBestRoute(frame.destIp);
                    if (route == null) {
                        System.out.println("[ERROR] No route found for " + frame.destIp);
                        continue;
                    }

                    frame.sourceMac = routerId;

                    if ("Direct".equals(route.nextHop)) {
                        frame.destMac = destMac;
                    } else {
                        frame.destMac = ConfigParser.getMacForIp(route.nextHop);
                        if (frame.destMac == null) {
                            System.out.println("[ERROR] No MAC found for next-hop router: " + route.nextHop);
                            continue;
                        }
                    }

                    VirtualPort targetPort;
                    if ("Direct".equals(route.nextHop)) {
                        targetPort = parser.getDevicePort(route.exitPort);
                    } else {
                        targetPort = parser.getDevicePort(route.nextHop.split("\\.")[1]);
                    }

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