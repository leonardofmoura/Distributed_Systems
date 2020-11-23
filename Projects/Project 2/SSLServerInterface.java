import javax.net.ssl.SSLContext;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * Provides an interface for a server to communicate with a client
 */
public class SSLServerInterface extends SSLManager {
    private SSLContext context;
    private AsynchronousSocketChannel channel;

    /**
     * Creates a new SSLServerInterface instance
     * @param channel The channel where the communication should be preformed
     * @param context The SSLContext for the secure communication
     * @throws SSLManagerException When something goes wrong
     */
    public SSLServerInterface(AsynchronousSocketChannel channel,SSLContext context) throws SSLManagerException {
        super();

        this.context = context;
        this.channel = channel;

        //initialize the SSLManager
        super.init(channel, context, false);
    }
}
