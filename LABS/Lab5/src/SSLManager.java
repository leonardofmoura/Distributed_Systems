import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.cert.CertificateException;

public abstract class SSLManager {
    static boolean DEBUG = true;

    private SSLContext context;
    private SSLSession session;
    private SSLEngine engine;
    private SocketChannel channel;

    private boolean init;

    private ByteBuffer outAppData;  //The outgoing data before being wrapped by SSLEngine
    private ByteBuffer outNetData;  //The outgoing data after being wrapped by SSLEngine, ready to be sent over the network
    private ByteBuffer inAppData;   //The incoming data after being unwrapped by SSlEngine
    private ByteBuffer inNetData;   //The incoming data before being unwrapped by SSlEngine

    static SSLContext initSSLContext(String managerId, String passphrase) throws SSLManagerException {
        char[] pass = passphrase.toCharArray();

        try {
            KeyStore keys = KeyStore.getInstance("JKS");
            keys.load(new FileInputStream(managerId + ".keys"),pass);

            KeyStore trust = KeyStore.getInstance("JKS");
            trust.load(new FileInputStream("truststore"),pass);


            //Init the key manager
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keys,pass);

            //Init the trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trust);

            //Init the SSLContext for TLS
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagerFactory.getKeyManagers(),trustManagerFactory.getTrustManagers(),null);

            return context;
        }
        catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyManagementException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    public SSLManager() {
        this.init = false;
    }

    public SSLManager(SocketChannel channel,InetSocketAddress address, SSLContext context, boolean client) throws SSLManagerException {
        this.init(channel,address,context,client);
    }

    private void initEngineBuffers() {
        //Create buffers for application
        this.outAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.outNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.inAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.inNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }

    protected void init(SocketChannel channel,InetSocketAddress address, SSLContext context, boolean client) throws SSLManagerException {
        this.context = context;
        this.channel = channel;

        //Initialize the SSLEngine
        try {
            //Create the SSLEngine and set client mode
            this.engine = this.context.createSSLEngine(address.getHostName(),address.getPort());
            this.engine.setUseClientMode(client);

            //Get the sslSession from the engine
            this.session = engine.getSession();

            this.initEngineBuffers();
            this.init = true;
        }
        catch (Exception e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    protected void init(SocketChannel channel, SSLContext context, boolean client) throws SSLManagerException {
        this.context = context;
        this.channel = channel;

        //Initialize the SSLEngine
        try {
            //Create the SSLEngine and set client mode
            this.engine = this.context.createSSLEngine();
            this.engine.setUseClientMode(client);

            //Get the sslSession from the engine
            this.session = engine.getSession();

            this.initEngineBuffers();
            this.init = true;
        }
        catch (Exception e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    private void checkInit() throws SSLManagerException {
        if (!init) {
            throw new SSLManagerException("The SSLManager was not initialized: call the init method");
        }
    }

    private void debugPrint(String msg) {
        if(DEBUG) {
            System.out.println(msg);
        }
    }

    private SSLEngineResult unwrap() throws SSLManagerException {
       try {
           //Process the incoming data
           this.inNetData.flip(); //prepare the buffer for read
           SSLEngineResult res = engine.unwrap(this.inNetData,this.inAppData);
           this.inNetData.compact(); //Save the remaining data in case another packet was received

           //Process the status
           switch (res.getStatus()) {
               case BUFFER_OVERFLOW:
                   this.debugPrint("\tbuffer overflow");
                   if (this.session.getApplicationBufferSize() > this.inAppData.capacity()) {
                       //Enlarge the inAppData buffer if it is too small
                       this.inAppData = ByteBuffer.allocate(this.session.getApplicationBufferSize());
                   }
                   else {
                       //The buffer size is enough, so we clear the buffer
                       //It has data stuck that caused the overflow
                       this.inAppData.clear();
                   }
                   //Retry the operation
                   return this.unwrap();

               case BUFFER_UNDERFLOW:
                   this.debugPrint("\tbuffer underflow");
                   //inNetData does needs data -> read from the socket

                   if (!engine.isInboundDone()) {
                       int bytes = this.channel.read(this.inNetData);
                       if (DEBUG) {
                           System.out.println("\tchannel read: " + bytes + "bytes");
                       }
                   }

                   //TODO check if the data is total when unwrapped

                   //Read the data again
                   return this.unwrap();

               case CLOSED:
                   this.debugPrint("\tclosed");
                   //TODO do something here
                   //The engine is closed -> can terminate transport layer
                   return res;

               case OK:
                   this.debugPrint("\tOK");
                   //The unwrap was successful
                   return res;

               default:
                   throw new SSLManagerException("Invalid status after unwrap: " + res.getStatus());
           }
       }
       catch (IOException e) {
           throw new SSLManagerException(e.getMessage());
       }
    }

    private SSLEngineResult wrapAndSend() throws SSLManagerException {
        try {
            //Empty the network buffer
            outNetData.clear();
            this.outAppData.flip();

            SSLEngineResult res = engine.wrap(this.outAppData,this.outNetData);
            outAppData.compact();

            //Process the status
            switch (res.getStatus()) {
                case BUFFER_OVERFLOW:
                    this.debugPrint("Buffer Overflow");
                    if (this.session.getPacketBufferSize() > this.outNetData.capacity()) {
                        //Enlarge the inAppData buffer if it is too small
                        this.outNetData = ByteBuffer.allocate(this.session.getPacketBufferSize());
                    }
                    else {
                        //The buffer size is enough, so we clear the buffer
                        //It has data stuck that caused the overflow
                        this.outNetData.clear();
                    }
                    //Retry the operation
                    return this.wrapAndSend();

                case BUFFER_UNDERFLOW:
                    //this should never happen
                    return res;

                case CLOSED:
                    this.debugPrint("Closed");
                    //TODO deal with this
                    return res;

                case OK:
                    this.debugPrint("OK");
                    //Turn outNetData to read mode
                    this.outNetData.flip();

                    //Send the data through the socket
                    int bytes = channel.write(this.outNetData);
                    this.debugPrint("\tchannel write: " + bytes + "bytes");
                    this.outNetData.compact();

                    return res;

                default:
                    return res;
            }
        }
        catch (IOException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    public void handshake() throws SSLManagerException {
        this.checkInit();

        //Begin the handshake
        try {
            engine.beginHandshake();
            System.out.println("System started handshake");
            SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();

            while (status != SSLEngineResult.HandshakeStatus.FINISHED && status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                switch (status) {
                    case NEED_UNWRAP:
                        this.debugPrint("NEED UNWRAP");
                        SSLEngineResult unwrapRes = this.unwrap();
                        this.debugPrint("UNWRAPPED");

                        status = unwrapRes.getHandshakeStatus();
                        break;

                    case NEED_WRAP:
                        this.debugPrint("NEED WRAP");
                        SSLEngineResult wrapRes = this.wrapAndSend();
                        this.debugPrint("WRAPPED");

                        status = wrapRes.getHandshakeStatus();
                        break;

                    case NEED_TASK:
                        //Run all the tasks
                        //TODO maybe make this concurrent with threads
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                            this.debugPrint("Executed task task");
                        }
                        status = engine.getHandshakeStatus();
                        break;
                }
            }

            System.out.println("Handshake finished with status" + status.toString());
        }
        catch (SSLException e) {
           throw new SSLManagerException(e.getMessage());
        }
    }
}
