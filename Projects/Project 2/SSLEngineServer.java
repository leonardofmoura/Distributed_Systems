import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Serves as a server interface to accept TLS connections from
 */
public class SSLEngineServer {
    private final SSLContext context;
    private final AsynchronousServerSocketChannel channel;

    /**
     * Constructs a new SSLEngineServer instance
     * @param managerId The id of the KeyStore manager id.keys
     * @param passphrase The passphrase of the keystore
     * @param port The port where the server should listen
     * @throws SSLManagerException When something goes wrong
     */
    public SSLEngineServer(String managerId, String passphrase, int port) throws SSLManagerException {

        //Initialize the key and trust stores
        try {
            this.context = SSLManager.initSSLContext(managerId, passphrase);

            //Create the socket channel
            this.channel = AsynchronousServerSocketChannel.open();
            this.channel.bind(new InetSocketAddress(port));
        }
        catch (IOException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    /**
     * Accepts new connections to the server, blocking until a new connection is received
     * @return The interface that can be used in the communication
     * @throws SSLManagerException When something goes wrong
     */
    public SSLServerInterface accept() throws SSLManagerException {
        try {
            Future<AsynchronousSocketChannel> worker = channel.accept();
            AsynchronousSocketChannel newChannel = worker.get();
            return new SSLServerInterface(newChannel, this.context);
        }
        catch (SSLManagerException | InterruptedException | ExecutionException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    public void close() throws IOException {
        this.channel.close();
    }
}
