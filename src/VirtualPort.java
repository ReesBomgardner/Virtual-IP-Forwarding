import java.net.InetAddress;

/**
 * A virtual port created from IP addresses and port numbers
 */

public class VirtualPort {
    public InetAddress ip;
    public int port;

    public VirtualPort(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}