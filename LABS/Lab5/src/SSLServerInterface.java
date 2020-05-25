import javax.net.ssl.SSLContext;
import java.nio.channels.AsynchronousSocketChannel;

public class SSLServerInterface extends SSLManager {
    private SSLContext context;
    private AsynchronousSocketChannel channel;

    public SSLServerInterface(AsynchronousSocketChannel channel,SSLContext context) throws SSLManagerException {
        super();

        this.context = context;
        this.channel = channel;

        //initialize the SSLManager
        super.init(channel, context, false);
    }
}
