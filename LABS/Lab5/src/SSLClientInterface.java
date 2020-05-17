import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class SSLClientInterface extends SSLManager {
    private String passphrase;
    private String managerId;
    private InetSocketAddress address;

    private SSLContext context;
    private SocketChannel channel;

    public SSLClientInterface(String managerId, String passphrase, String hostname, int port) throws SSLManagerException {
        super();

        this.passphrase = passphrase;
        this.managerId = managerId;
        this.address = new InetSocketAddress(hostname, port);


        //Initialize the key and trust stores
        try {
            this.context = SSLManager.initSSLContext(this.managerId,this.passphrase);

            //Create the socket channel
            this.channel = SocketChannel.open();

            //Connect the channel
            this.channel.connect(new InetSocketAddress(hostname,port));

            //Initialize the SSLManager
            super.init(this.channel,address,this.context,true);
        }
        catch (IOException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }
}
