import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SSLEngineServer {
    private final String managerId;
    private final String passphrase;
    private final int port;

    private SSLContext context;
    private AsynchronousServerSocketChannel channel;

    public SSLEngineServer(String managerId, String passphrase, int port) throws SSLManagerException {
        this.managerId = managerId;
        this.passphrase = passphrase;
        this.port = port;

        //Initialize the key and trust stores
        try {
            this.context = SSLManager.initSSLContext(this.managerId, this.passphrase);

            //Create the socket channel
            this.channel = AsynchronousServerSocketChannel.open();
            this.channel.bind(new InetSocketAddress(port));
        }
        catch (IOException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

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
