import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Interface for TLS communication using SSLEngine
 */
public class SSLClientInterface extends SSLManager {
    /**
     * Constructs a new SSLClientInterface instance
     * @param managerId The id of the KeyStore manager id.keys
     * @param passphrase The passphrase of the keystore
     * @param hostname The hostname to connect to
     * @param port The port to connect to
     * @throws SSLManagerException When something goes wrong
     */
    public SSLClientInterface(String managerId, String passphrase, String hostname, int port) throws SSLManagerException {
        super();

        InetSocketAddress address = new InetSocketAddress(hostname, port);


        //Initialize the key and trust stores
        try {
            SSLContext context = SSLManager.initSSLContext(managerId, passphrase);

            //Create the socket channel
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();

            //Connect the channel
            Future<Void> connection = channel.connect(new InetSocketAddress(hostname,port));

            //Initialize the SSLManager -> concurrent with connection
            super.init(channel, address, context,true);

            connection.get(); //only exit when connection completes
        }
        catch (InterruptedException | ExecutionException | IOException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }
}
