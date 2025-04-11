import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A virtual network frame containing a source and
 * destination MAC and IP addresses and a message
 */

public class Frame {
    public String sourceMac;
    public String destMac;
    public String sourceIp;
    public String destIp;
    public byte[] data;
    public int type;

    public enum FrameType {
        DISTANCE_VECTOR,
        USER_MESSAGE
    }

    public Frame(String sMAC, String dMAC, String sIP, String dIP, byte[] data, int type) {
        this.sourceMac = sMAC;
        this.destMac = dMAC;
        this.sourceIp = sIP;
        this.destIp = dIP;
        this.data = data;
        this.type = type;
    }

    public Frame() {
        this("", "", "", "", new byte[0], 1);
    }

    public void readPacket(DatagramPacket packet) {
        ByteBuffer buffer = ByteBuffer.wrap(packet.getData());

        this.type = buffer.get();
        this.sourceMac = readString(buffer);
        this.destMac = readString(buffer);
        this.sourceIp = readString(buffer);
        this.destIp = readString(buffer);

        int dataLength = buffer.getInt();
        this.data = new byte[dataLength];
        buffer.get(this.data);

        System.out.printf("[FRAME] Deserialized frame: %s → %s (%s → %s) Type: %s\n",
                sourceMac, destMac, sourceIp, destIp, type == 0 ? "ROUTING" : "USER");
    }

    private String readString(ByteBuffer buffer) {
        int length = buffer.getInt();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes);
    }

    public DatagramPacket writePacket(InetAddress destIP, int destPort) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        buffer.put((byte)type);
        writeString(buffer, sourceMac);
        writeString(buffer, destMac);
        writeString(buffer, sourceIp);
        writeString(buffer, destIp);
        buffer.putInt(data.length);
        buffer.put(data);

        byte[] payload = Arrays.copyOf(buffer.array(), buffer.position());
        System.out.printf("[FRAME] Serialized frame: %s → %s (%s → %s) Type: %s\n",
                sourceMac, destMac, sourceIp, destIp, type == 0 ? "ROUTING" : "USER");
        return new DatagramPacket(payload, payload.length, destIP, destPort);
    }

    private void writeString(ByteBuffer buffer, String value) {
        buffer.putInt(value.getBytes().length);
        buffer.put(value.getBytes());
    }
}