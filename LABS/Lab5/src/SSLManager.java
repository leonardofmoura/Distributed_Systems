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

public abstract class SSLManager {
    static boolean DEBUG = false;

    private SSLContext context;
    private SSLSession session;
    private SSLEngine engine;
    private AsynchronousSocketChannel channel;

    private boolean init = false;
    private boolean handshakeOngoing = false;

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

    public SSLManager() {}

    public SSLManager(AsynchronousSocketChannel channel,InetSocketAddress address, SSLContext context, boolean client) throws SSLManagerException {
        this.init(channel,address,context,client);
    }

    private void initEngineBuffers() {
        //Create buffers for application
        this.outAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.outNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.inAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.inNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }

    protected void init(AsynchronousSocketChannel channel,InetSocketAddress address, SSLContext context, boolean client) throws SSLManagerException {
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

    protected void init(AsynchronousSocketChannel channel, SSLContext context, boolean client) throws SSLManagerException {
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

                    //Process any handshake data
                    if (!this.handshakeOngoing) {
                        while (this.processHandshakeStatus());
                    }

                    return res;

                default:
                    throw new SSLManagerException("Invalid status after unwrap: " + res.getStatus());
            }
        }
        catch (IOException | ExecutionException | InterruptedException e) {
            throw new SSLManagerException(e.getMessage());
        }
    }

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
                    this.debugPrint("\tchannel write: " + send + "bytes");

                    //TODO deal with this
                    return res;

                case OK:
                    this.debugPrint("OK");

                    //Process any required handshake data
                    if (!this.handshakeOngoing) {
                        while (processHandshakeStatus()) {
                            //Simply process the status
                        }
                    }

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

    private boolean processHandshakeStatus() throws SSLManagerException, TimeoutException {
        switch (engine.getHandshakeStatus()) {
            case NEED_UNWRAP:
                this.handshakeOngoing = true;
                this.debugPrint("NEED UNWRAP");
                SSLEngineResult unwrapRes = this.unwrap();
                this.debugPrint("UNWRAPPED");

                return true;

            case NEED_WRAP:
                this.handshakeOngoing = true;
                this.debugPrint("NEED WRAP");
                SSLEngineResult wrapRes = this.wrapAndSend();
                this.debugPrint("WRAPPED");

                return true;

            case NEED_TASK:
                this.handshakeOngoing = true;
                //Run all the tasks
                //TODO maybe make this concurrent with threads
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                    this.debugPrint("Executed task task");
                }
                return true;

            case FINISHED:
            case NOT_HANDSHAKING:
                this.handshakeOngoing = false;
                return false;

            default:
                throw new SSLManagerException("Unknown handhake status " + engine.getHandshakeStatus());
        }
    }

    public boolean handshake() {
        //Begin the handshake
        try {
            this.checkInit();

            engine.beginHandshake();
            System.out.println("System started handshake");

            while (processHandshakeStatus()) {
                //Simply process the handshake
            }

            System.out.println("Handshake finished");
            return true;
        }
        catch (SSLException | SSLManagerException | TimeoutException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

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
