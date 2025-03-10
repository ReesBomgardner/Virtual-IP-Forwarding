import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Frame {
    public String sourceMac;
    public String destMac;
    public String sourceIp;
    public String destIp;
    public String message;

    public Frame(String sMAC, String dMAC, String sIP, String dIP, String msg) {
        this.sourceMac = sMAC;
        this.destMac = dMAC;
        this.sourceIp = sIP;
        this.destIp = dIP;
        this.message = msg;
    }

    public Frame() {
        this("", "", "", "", "");
    }

    public void readPacket(DatagramPacket packet) {
        ByteBuffer buffer = ByteBuffer.wrap(packet.getData());

        this.sourceMac = readString(buffer);
        this.destMac = readString(buffer);
        this.sourceIp = readString(buffer);
        this.destIp = readString(buffer);
        this.message = readString(buffer);

        System.out.printf("[FRAME] Deserialized frame: %s → %s (%s → %s)\n", sourceMac, destMac, sourceIp, destIp);
    }

    private String readString(ByteBuffer buffer) {
        int length = buffer.getInt();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes);
    }

    public DatagramPacket writePacket(InetAddress destIP, int destPort) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        writeString(buffer, sourceMac);
        writeString(buffer, destMac);
        writeString(buffer, sourceIp);
        writeString(buffer, destIp);
        writeString(buffer, message);

        byte[] payload = Arrays.copyOf(buffer.array(), buffer.position());
        System.out.printf("[FRAME] Serialized frame: %s → %s (%s → %s)\n", sourceMac, destMac, sourceIp, destIp);
        return new DatagramPacket(payload, payload.length, destIP, destPort);
    }

    private void writeString(ByteBuffer buffer, String value) {
        buffer.putInt(value.getBytes().length);
        buffer.put(value.getBytes());
    }
}