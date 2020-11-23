import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages TLS communication using SSLEngine
 * Deals with all the nuances of the class mentioned above, abstracting all its complex functionality
 */
public abstract class SSLManager {
    static boolean DEBUG = false;
    static String[] CIPHERS = {"TLS_RSA_WITH_AES_256_CBC_SHA","TLS_RSA_WITH_AES_128_CBC_SHA"};

    private SSLContext context;
    private SSLSession session;
    private SSLEngine engine;
    private AsynchronousSocketChannel channel;

    private boolean init = false;

    private ByteBuffer outAppData;  //The outgoing data before being wrapped by SSLEngine
    private ByteBuffer outNetData;  //The outgoing data after being wrapped by SSLEngine, ready to be sent over the network
    private ByteBuffer inAppData;   //The incoming data after being unwrapped by SSlEngine
    private ByteBuffer inNetData;   //The incoming data before being unwrapped by SSlEngine

    /**
     * Creates a new SSLContext with the provided id and passphrase, using KeyStores in this folder
     * @param managerId The id of the KeyStore manager id.keys
     * @param passphrase The passphrase of the keystore
     * @return The initialized SSLContext
     * @throws SSLManagerException When something goes wrong
     */
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

    /**
     * Empty constructor for the SSLManager class
     * When used init() needs to be called afterword
     */
    public SSLManager() {}

    /**
     * Constructor for the SSLManager class
     * @param channel The channel where the connection should be made
     * @param address The address of the host where the channel is connected
     * @param context The SSLContext for the secure communication
     * @param client True if the SSLManager should operate in client mode, false otherwise (server mode)
     * @throws SSLManagerException When something goes wrong
     */
    public SSLManager(AsynchronousSocketChannel channel,InetSocketAddress address, SSLContext context, boolean client) throws SSLManagerException {
        this.init(channel,address,context,client);
    }

    /**
     * Initializes all the buffers necessary for the communication
     */
    private void initEngineBuffers() {
        //Create buffers for application
        this.outAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.outNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.inAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.inNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }

    /**
     * Sets all the necessary settings for SSLEngine and creates the SSLSession
     * instance used in the communication
     * @param client True if the SSLManager should operate in client mode, false otherwise (server mode)
     */
    private void initEngine(boolean client) {
        this.engine.setUseClientMode(client);

        if (!client) {
            this.engine.setNeedClientAuth(true);
        }

        this.engine.setEnabledCipherSuites(CIPHERS);

        //Get the sslSession from the engine
        this.session = engine.getSession();

        this.initEngineBuffers();
        this.init = true;
    }

    /**
     * Initializes the SSLManager if it has been built with the empty constructor
     * @param channel The channel where the connection should be made
     * @param address The address of the host where the channel is connected
     * @param context The SSLContext for the secure communication
     * @param client True if the SSLManager should operate in client mode, false otherwise (server mode)
     * @throws SSLManagerException When something goes wrong
     */
    protected void init(AsynchronousSocketChannel channel,InetSocketAddress address, SSLContext context, boolean client) throws SSLManagerException {
        this.context = context;
        this.channel = channel;

        //Initialize the SSLEngine
        try {
            //Create the SSLEngine and set client mode
            this.engine = this.context.createSSLEngine(address.getHostName(),address.getPort());
            this.initEngine(client);

        }
        catch (Exception e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    /**
     * Initializes the SSLManager if it has been built with the empty constructor
     * @param channel The channel where the connection should be made
     * @param context The SSLContext for the secure communication
     * @param client True if the SSLManager should operate in client mode, false otherwise (server mode)
     * @throws SSLManagerException When something goes wrong
     */
    protected void init(AsynchronousSocketChannel channel, SSLContext context, boolean client) throws SSLManagerException {
        this.context = context;
        this.channel = channel;

        //Initialize the SSLEngine
        try {
            //Create the SSLEngine and set client mode
            this.engine = this.context.createSSLEngine();
            this.initEngine(client);
        }
        catch (Exception e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    /**
     * Checks if the SSLManager was initialized
     * @throws SSLManagerException The manager was not initialized
     */
    private void checkInit() throws SSLManagerException {
        if (!init) {
            throw new SSLManagerException("The SSLManager was not initialized: call the init method");
        }
    }

    /**
     * Prints a message to the console if debug mode is on
     * @param msg The message to print
     */
    private void debugPrint(String msg) {
        if(DEBUG) {
            System.out.println(msg);
        }
    }

    /**
     * Unwraps a packet using the SSLEngine and deals with all the possible status
     * @return The SSLEngineResult corresponding to the unwrap
     * @throws SSLManagerException When something goes wrong
     * @throws TimeoutException When the unwrap resulted in a read and that read timed out
     */
    //Assumes all buffers start in write mode
    private SSLEngineResult unwrap() throws SSLManagerException, TimeoutException {
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
                    //inNetData needs data -> read from the socket

                    if (!engine.isInboundDone()) {
                        Future<Integer> fBytes = channel.read(this.inNetData);
                        int bytes = fBytes.get(1000, TimeUnit.MILLISECONDS);

                        if (bytes < 0) {
                            //Nothing to read
                            return res;
                        }

                        this.debugPrint("\tchannel read: " + bytes + "bytes");
                    }

                    //Read the data again
                    return this.unwrap();

                case CLOSED:
                    this.debugPrint("\tclosed");

                    //Finish the handshake

                    while (processHandshakeStatus()) {
                        //Simply process the status
                    }

                    return res;

                case OK:
                    this.debugPrint("\tOK");
                    //The unwrap was successful

                    return res;

                default:
                    throw new SSLManagerException("Invalid status after unwrap: " + res.getStatus());
            }
        }
        catch (IOException | ExecutionException | InterruptedException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    /**
     * Wraps a message and sends it through the channel
     * @return The SSLEngineResult corresponding to the unwrap
     * @throws SSLManagerException When something goes wrong
     * @throws TimeoutException When the unwrap resulted in a read and that read timed out
     */
    private SSLEngineResult wrapAndSend() throws SSLManagerException, TimeoutException {
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
                    //Send anything still in the buffer
                    this.outNetData.flip();

                    //Send the data through the socket
                    Future<Integer> send = channel.write(this.outNetData);
                    int sent = send.get(1000, TimeUnit.MILLISECONDS);
                    this.debugPrint("\tchannel write: " + sent + "bytes");

                    return res;

                case OK:
                    this.debugPrint("OK");

                    //Turn outNetData to read mode
                    this.outNetData.flip();

                    //Send the data through the socket
                    Future<Integer> sending = channel.write(this.outNetData);
                    int bytes = sending.get(1000, TimeUnit.MILLISECONDS);
                    this.debugPrint("\tchannel write: " + bytes + "bytes");
                    this.outNetData.compact();

                    return res;

                default:
                    return res;
            }
        }
        catch (IOException | ExecutionException | InterruptedException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

    /**
     * Processes the current handshake status
     * @return True if the function needs to be called again, false otherwise
     * @throws SSLManagerException When something goes wrong
     * @throws TimeoutException When the unwrap resulted in a read and that read timed out
     */
    private boolean processHandshakeStatus() throws SSLManagerException, TimeoutException {
        switch (engine.getHandshakeStatus()) {
            case NEED_UNWRAP:
                this.debugPrint("NEED UNWRAP");
                SSLEngineResult unwrapRes = this.unwrap();
                this.debugPrint("UNWRAPPED");

                return true;

            case NEED_WRAP:
                this.debugPrint("NEED WRAP");
                SSLEngineResult wrapRes = this.wrapAndSend();
                this.debugPrint("WRAPPED");

                return true;

            case NEED_TASK:
                //Run all the tasks
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    new Thread(task).start();
                }
                return true;

            case FINISHED:
            case NOT_HANDSHAKING:
                return false;

            default:
                throw new SSLManagerException("Unknown handhake status " + engine.getHandshakeStatus());
        }
    }

    /**
     * Preforms a handshake
     * @return True if the handshake was successful and false otherwise
     */
    public boolean handshake() {
        //Begin the handshake
        try {
            this.checkInit();

            engine.beginHandshake();
            this.debugPrint("System started handshake");

            while (processHandshakeStatus()) {
                //Simply process the handshake
            }

            this.debugPrint("Handshake finished");
            return true;
        }
        catch (SSLException | SSLManagerException | TimeoutException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    /**
     * Unwraps a message and copies it to the array passed as parameter
     * @param message The array where the message will be placed
     * @return The number of bytes of the message read
     * @throws SSLManagerException When something goes wrong
     */
    public int read(byte[] message) throws SSLManagerException {
        debugPrint("READ CALLED");

        try {
            this.unwrap();
        } catch (TimeoutException e) {
            return -1;
        }
        this.inAppData.flip();

        int packLen = this.inAppData.remaining();

        this.inAppData.get(message,0,packLen);

        debugPrint("READ " + packLen + " bytes");

        return packLen;
    }

    /**
     * Unwraps a message using SSLEngine and returns it as a String
     * @return The unwraped message
     * @throws SSLManagerException When something goes wrong
     */
    public String readln() throws SSLManagerException {
        debugPrint("READLN CALLED");

        try {
            this.unwrap();
        } catch (TimeoutException e) {
            throw new SSLManagerException(e.getMessage());
        }

        this.inAppData.flip();

        int pacLen = this.inAppData.remaining();
        byte[] preString = new byte[pacLen];
        this.inAppData.get(preString,0,pacLen);

        debugPrint("READ " + pacLen + " bytes");

        return new String(preString, StandardCharsets.UTF_8);
    }


    /**
     * Wraps a message using SSLEngine and sends it through the channel
     * @param msg The message to be sent
     * @return The number of bytes written to the channel
     * @throws SSLManagerException When something goes wrong
     */
    public int write(byte[] msg) throws SSLManagerException {
        debugPrint("WRITE CALLED");

        this.outAppData.put(msg);

        SSLEngineResult res = null;
        try {
            res = this.wrapAndSend();
        } catch (TimeoutException e) {
            return -1;
        }

        debugPrint("WRITTEN " + res.bytesProduced() + " bytes");

        return res.bytesProduced();
    }

    public boolean close() throws SSLManagerException, IOException {
        /*
        if (!engine.isOutboundDone()) {
            engine.closeOutbound();

            while (true) {
                try {
                    if (!processHandshakeStatus()) break;
                } catch (TimeoutException e) {
                    return false;
                }
            }
        }
        else if (!engine.isInboundDone()) {
            engine.closeInbound();
            try {
                processHandshakeStatus();
            } catch (TimeoutException e) {
                return false;
            }
        }

         */

        channel.close();
        return true;
    }

    public boolean waitClose() throws SSLManagerException {
        //to wait for a close we simply need to unwrap and the function processes the handshake
        debugPrint("WAITING CLOSING HANDSHAKE");
        try {
            this.unwrap();
        } catch (TimeoutException e) {
            return false;
        }
        debugPrint("CLOSE HANDSHAKE COMPLETED");
        return true;
    }
}
