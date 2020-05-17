import javax.net.ssl.SSLContext;
import java.nio.channels.SocketChannel;

public class SSLServerInterface extends SSLManager {
    private SSLContext context;
    private SocketChannel channel;

    public SSLServerInterface(SocketChannel channel,SSLContext context) throws SSLManagerException {
        super();

        this.context = context;
        this.channel = channel;

        //initialize the SSLManager
        super.init(channel, context, false);
    }
}
