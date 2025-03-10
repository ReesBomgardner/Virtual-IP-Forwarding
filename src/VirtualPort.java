import java.net.InetAddress;

public class VirtualPort {
    public InetAddress ip;
    public int port;

    public VirtualPort(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}