import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLEngineServer {
    private final String managerId;
    private final String passphrase;
    private final int port;

    private SSLContext context;
    private ServerSocketChannel channel;

    public SSLEngineServer(String managerId, String passphrase, int port) throws SSLManagerException {
        this.managerId = managerId;
        this.passphrase = passphrase;
        this.port = port;

        //Initialize the key and trust stores
        try {
            this.context = SSLManager.initSSLContext(this.managerId, this.passphrase);

            //Create the socket channel
            this.channel = ServerSocketChannel.open();
            this.channel.socket().bind(new InetSocketAddress(port));
        }
        catch (IOException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    public SSLServerInterface accept() throws SSLManagerException {
        try {
            return new SSLServerInterface(channel.accept(), this.context);
        }
        catch (SSLManagerException | IOException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }
}
