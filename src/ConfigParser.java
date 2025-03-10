import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.*;

public class ConfigParser {
    private final Map<String, VirtualPort> devices = new HashMap<>();
    private static final Map<String, String> ipToMac = new HashMap<>();
    private final Map<String, String> macToIp = new HashMap<>();
    private final Map<String, List<String>> links = new HashMap<>();
    private final Map<String, RoutingTable> routingTables = new HashMap<>();
    private final Map<String, String> defaultGateways = new HashMap<>();
    private String currentRouter = "";

    public ConfigParser(File configFile) {
        try (FileInputStream fis = new FileInputStream(configFile)) {
            Scanner scanner = new Scanner(fis);
            String currentSection = "";

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                line = line.split("#")[0].trim();
                if (line.isEmpty()) continue;

                if (line.matches("^[A-Za-z0-9]+\\s+\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$")) {
                    String[] parts = line.split("\\s+");
                    String[] addr = parts[1].split(":");
                    InetAddress ip = InetAddress.getByName(addr[0]);
                    int port = Integer.parseInt(addr[1]);
                    devices.put(parts[0], new VirtualPort(ip, port));
                    continue;
                }

                if (line.equals("LINKS")) {
                    currentSection = "LINKS";
                } else if (line.equals("ADDRESS RESOLUTION")) {
                    currentSection = "ADDRESS";
                } else if (line.startsWith("R1 TABLE") || line.startsWith("R2 TABLE")) {
                    currentSection = "ROUTING";
                    currentRouter = line.split(" ")[0];
                    routingTables.putIfAbsent(currentRouter, new RoutingTable());
                } else if (line.equals("DEFAULT GATEWAY")) {
                    currentSection = "DEFAULT_GATEWAY";
                } else {
                    switch (currentSection) {
                        case "LINKS":
                            String[] linkedDevices = line.split("-");
                            if (linkedDevices.length >= 2) {
                                links.computeIfAbsent(linkedDevices[0], k -> new ArrayList<>()).add(linkedDevices[1]);
                                links.computeIfAbsent(linkedDevices[1], k -> new ArrayList<>()).add(linkedDevices[0]);
                            }
                            break;
                        case "ADDRESS":
                            String[] mapping = line.split(" ");
                            if (mapping.length >= 2) {
                                ipToMac.put(mapping[0], mapping[1]);
                                macToIp.put(mapping[1], mapping[0]);
                            }
                            break;
                        case "ROUTING":
                            if (currentRouter.isEmpty()) continue;
                            String[] routeParts = line.split("\\s+");
                            if (routeParts.length >= 3) {
                                String subnet = routeParts[0];
                                String nextHop = routeParts[1];
                                String exitPort = routeParts[2];
                                routingTables.get(currentRouter).addEntry(subnet, nextHop, exitPort);
                            }
                            break;
                        case "DEFAULT_GATEWAY":
                            String[] gatewayParts = line.split(" ");
                            if (gatewayParts.length >= 2) {
                                defaultGateways.put(gatewayParts[0], gatewayParts[1]);
                            }
                            break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Config error: " + e.getMessage());
        }
    }

    public String getVirtualIp(String mac) {
        return macToIp.get(mac);
    }

    public String getDefaultGateway(String host) {
        return defaultGateways.get(host);
    }

    public VirtualPort getDevicePort(String device) {
        return devices.get(device);
    }

    public static String getMacForIp(String ip) {
        return ipToMac.get(ip);
    }

    public List<String> getNeighbors(String device) {
        return links.getOrDefault(device, new ArrayList<>());
    }

    public RoutingTable getRoutingTable(String router) {
        return routingTables.get(router);
    }
}