import java.io.IOException;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

//TODO cleanup exceptions

/**
 * The generic class for a multicast channel
 */
public abstract class MulticastChannel implements Runnable {
    protected final PeerService peer;
    protected InetAddress addr;
    protected int port;
    protected String channelName;
    protected ThreadPoolExecutor executor;

    public MulticastChannel(PeerService peer, String inet, int port, String channelName) {
        this.peer = peer;
        try {
            this.port = port;
            this.addr = InetAddress.getByName(inet);
            this.channelName = channelName;
            this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void run() {
        byte[] buf = new byte[512000];

        try (MulticastSocket socket = new MulticastSocket(this.port)) {
            socket.joinGroup(this.addr);

            System.out.println("Starting " + this.channelName);
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                this.parseMessage(packet);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void sendMessage(Message message) {
        try {
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.getBytes().length, this.addr, this.port);

            socket.send(packet);
            System.out.println("Sent: " + message.getHeader());

            socket.close();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    abstract protected void parseMessage(DatagramPacket packet);
}
