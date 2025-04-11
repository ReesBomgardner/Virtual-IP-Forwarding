import java.nio.ByteBuffer;

/**
 * DistanceVectorFrame creates a special frame for distance vector routing, wrapping
 * a standard frame and serialize/deserialize DistanceVector objects into the frame's payload.
 */

public class DistanceVectorFrame {
    private final Frame frame;

    public DistanceVectorFrame(Frame frame) {
        this.frame = frame;
    }

    public void addDistanceVector(DistanceVector dv) {
        String[] knownSubnets = dv.getKnownSubnets();

        int length = 0;
        for (String subnet : knownSubnets) {
            length += subnet.getBytes().length;
        }

        length += Integer.BYTES * 2 * knownSubnets.length + 1;

        ByteBuffer buffer = ByteBuffer.allocate(length);

        buffer.put((byte) Frame.FrameType.DISTANCE_VECTOR.ordinal());

        for (String subnet : knownSubnets) {
            buffer.putInt(subnet.getBytes().length);
            buffer.put(subnet.getBytes());
            buffer.putInt(dv.getEntry(subnet));
        }

        this.frame.data = buffer.array();
    }

    public DistanceVector getDistanceVector() {
        DistanceVector dv = new DistanceVector();

        ByteBuffer buffer = ByteBuffer.wrap(frame.data);

        buffer.get();

        while (buffer.hasRemaining()) {
            int subnetLength = buffer.getInt();
            byte[] subnetBytes = new byte[subnetLength];
            buffer.get(subnetBytes);
            String subnet = new String(subnetBytes);
            int distance = buffer.getInt();

            dv.addEntry(subnet, distance);
        }

        return dv;
    }
}