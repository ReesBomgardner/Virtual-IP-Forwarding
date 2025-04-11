import java.util.ArrayList;
import java.util.HashMap;

/**
 * DistanceVector implements the Distance Vector Routing protocol, maintaining
 * a map of subnets and updating the vector using the Bellman-Ford equation
 */

public class DistanceVector {
    final HashMap<String, Integer> map;

    public DistanceVector() {
        this.map = new HashMap<>();
    }

    public void addEntry(String subnet, int distance) {
        map.put(subnet, distance);
    }

    public int getEntry(String subnet) {
        return this.map.get(subnet);
    }

    public String[] updateEntries(DistanceVector neighbor) {
        ArrayList<String> updatedEntries = new ArrayList<>();

        for (String subnet : neighbor.map.keySet()) {
            int value = neighbor.map.get(subnet);
            if (!map.containsKey(subnet) || value + 1 < map.get(subnet)) {
                this.map.put(subnet, value + 1);
                updatedEntries.add(subnet);
            }
        }
        return updatedEntries.toArray(String[]::new);
    }

    public String[] getKnownSubnets() {
        return map.keySet().toArray(String[]::new);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Distance Vector:\n");
        for (String subnet: map.keySet()) {
            int distance = getEntry(subnet);
            result.append(String.format("%s: %d\n", subnet, distance));
        }

        return result.toString();
    }
}