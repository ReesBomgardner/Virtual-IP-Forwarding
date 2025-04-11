import java.util.ArrayList;
import java.util.List;

/**
 * A routing table for the router to store entries for
 * subnets and give the best route for a destination IP
 */

public class RoutingTable {
    public static class Entry {
        public final String subnet;
        public final String nextHop;
        public final String exitPort;
        public int distance;

        public Entry(String subnet, String nextHop, String exitPort, int distance) {
            this.subnet = subnet;
            this.nextHop = nextHop;
            this.exitPort = exitPort;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return String.format("%-8s → %-10s via %s (cost: %d)", subnet, nextHop, exitPort, distance);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void addEntry(String subnet, String nextHop, String exitPort, int distance) {
        entries.add(new Entry(subnet, nextHop, exitPort, distance));
    }

    public Entry findBestRoute(String destIp) {
        Entry bestMatch = null;
        for (Entry entry : entries) {
            if (destIp.startsWith(entry.subnet)) {
                if (bestMatch == null ||
                        entry.distance < bestMatch.distance ||
                        (entry.distance == bestMatch.distance && entry.subnet.length() > bestMatch.subnet.length())) {
                    bestMatch = entry;
                }
            }
        }
        return bestMatch;
    }

    public List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    @Override
    public String toString() {
        if (entries.isEmpty()) return "No routes configured";
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries) {
            sb.append(entry).append("\n");
        }
        return sb.toString();
    }
}