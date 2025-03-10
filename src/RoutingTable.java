import java.util.ArrayList;
import java.util.List;

public class RoutingTable {
    public static class Entry {
        private final String subnet;
        final String nextHop;
        final String exitPort;

        public Entry(String subnet, String nextHop, String exitPort) {
            this.subnet = subnet;
            this.nextHop = nextHop;
            this.exitPort = exitPort;
        }

        @Override
        public String toString() {
            return String.format("%-8s → %-10s via %s", subnet, nextHop, exitPort);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void addEntry(String subnet, String nextHop, String exitPort) {
        entries.add(new Entry(subnet, nextHop, exitPort));
    }

    public Entry findBestRoute(String destIp) {
        Entry bestMatch = null;
        for (Entry entry : entries) {
            if (destIp.startsWith(entry.subnet)) {
                if (bestMatch == null || entry.subnet.length() > bestMatch.subnet.length()) {
                    bestMatch = entry;
                }
            }
        }
        return bestMatch;
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