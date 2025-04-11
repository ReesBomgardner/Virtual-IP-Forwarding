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
    private final Map<String, VirtualPort> macToPort = new HashMap<>();
    private VirtualPort switchPort;
    private final ConfigParser parser;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Switch <config-file> <switch-id>");
            System.exit(1);
        }

        try {
            File configFile = new File(args[0]);
            ConfigParser parser = new ConfigParser(configFile);
            String switchId = args[1];

            VirtualPort switchPort = parser.getDevicePort(switchId);
            if (switchPort == null) {
                System.err.println("Error: Switch " + switchId + " not found in config.");
                System.exit(1);
            }

            System.out.printf("[SWITCH %s] Running on %s:%d\n",
                    switchId, switchPort.ip.getHostAddress(), switchPort.port);
            new Switch(switchPort, parser).run();
        } catch (Exception e) {
            System.err.println("Switch crashed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Switch(VirtualPort switchPort, ConfigParser parser) {
        this.switchPort = switchPort;
        this.parser = parser;
    }

    public void run() {
        try (DatagramSocket socket = new DatagramSocket(switchPort.port)) {
            System.out.println("[MAC TABLE] Initialized (empty)");
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                Frame frame = new Frame();
                frame.readPacket(packet);

                if (frame.type == 0) {
                    System.out.println("[SWITCH] Ignoring routing update packet");
                    continue;
                }

                System.out.printf("\n[SWITCH %s] Received frame: %s → %s (%s → %s)\n",
                        switchPort.port, frame.sourceMac, frame.destMac,
                        frame.sourceIp, frame.destIp);

                VirtualPort sourcePort = new VirtualPort(packet.getAddress(), packet.getPort());
                macToPort.put(frame.sourceMac, sourcePort);

                System.out.println("[MAC TABLE] Current state:");
                macToPort.forEach((mac, port) -> {
                    System.out.printf("  %s → %s:%d\n", mac, port.ip.getHostAddress(), port.port);
                });

                if (frame.destMac.startsWith("R")) {
                    VirtualPort routerPort = parser.getDevicePort(frame.destMac);
                    if (routerPort != null) {
                        socket.send(frame.writePacket(routerPort.ip, routerPort.port));
                        System.out.printf("[FORWARDED] To router %s (%s:%d)\n",
                                frame.destMac, routerPort.ip.getHostAddress(), routerPort.port);
                        continue;
                    }
                }

                VirtualPort destPort = macToPort.get(frame.destMac);
                if (destPort != null) {
                    socket.send(frame.writePacket(destPort.ip, destPort.port));
                    System.out.printf("[FORWARDED] To %s (%s:%d)\n",
                            frame.destMac, destPort.ip.getHostAddress(), destPort.port);
                } else {
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