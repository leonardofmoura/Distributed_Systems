import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Server {
    public static void main(String[] args) {
        if (args.length != 1 ) {
            printUsage();
            return;
        }

        int port = Integer.parseInt(args[0]);

        new ServerProcess(port).start();
    }

    public static void printUsage() {
        System.out.println("usage: Server <service_port>");
    }
}

class ServerProcess {
    private SSLEngineServer server;
    private HashMap<String,String> data = new HashMap<>();
    private boolean running;

    ServerProcess(int port){
        try {
            this.server= new SSLEngineServer("server","123456",port);
        } catch (SSLManagerException e) {
            e.printStackTrace();
        }

    }

    public void start() {
        this.log("Started service");
        running = true;

        while (running) {
            try {
                new ServerProcessThread(server.accept(),this).start();
            }
            catch (SSLManagerException e) { //Server Closes
                System.out.println("Channel Closed");
            }
        }
    }

    protected void log(String message) {
        System.out.println("Server: " + message);
    }

    protected String register(String dns, String ipAddr) {
        int numVals = this.getNumKeysValue(ipAddr);

        this.data.put(dns,ipAddr);

        String reply = numVals + " " + dns + " " + ipAddr;
        this.log("Register " + dns + " to " + ipAddr);

        return reply;
    }

    protected String lookup(String dns) {
        String ipAddr = this.data.get(dns);
        int numVals = this.getNumKeysValue(ipAddr);

        String reply = numVals + " " + dns + " " + ipAddr;
        this.log("Lookup " + dns + ": " + ipAddr);

        return reply;
    }

    protected void close() {
        this.running = false;
        try {
            this.server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getNumKeysValue(String value) {
        int numVals = 0;
        for (Map.Entry<String,String> entry : data.entrySet()) {
            if (entry.getValue().equals(value)) {
                numVals++;
            }
        }

        return numVals;
    }
}

class ServerProcessThread extends Thread {
    private SSLServerInterface serverInterface;
    private ServerProcess serverProcess;

    public ServerProcessThread(SSLServerInterface serverInterface, ServerProcess process) {
        super("ServerProcessThread");
        this.serverInterface = serverInterface;
        this.serverProcess = process;
        try {
            this.serverInterface.handshake();
        }
        catch (SSLManagerException e) {
            e.printStackTrace();
        }
    }

    private String parseMessage(String message) {
        String[] tokenMessage = message.split(" ");
        String reply = "-1";

        if (tokenMessage[0].equals("REGISTER") && tokenMessage.length == 3) {
            reply = this.serverProcess.register(tokenMessage[1],tokenMessage[2]);
        }
        else if (tokenMessage[0].equals("LOOKUP") && tokenMessage.length == 2){
            reply = this.serverProcess.lookup(tokenMessage[1]);
        }
        else if (tokenMessage[0].equals("CLOSE") && tokenMessage.length == 1){
            this.serverProcess.close();
            reply = "closing server...";
        }

        return reply;
    }

    @Override
    public void run() {
        try {
            byte[] bytes = new byte[100000];
            int read = serverInterface.read(bytes);
            String message = new String(bytes,0,read, StandardCharsets.UTF_8);

            this.serverProcess.log(message);

            String reply = this.parseMessage(message);

            serverInterface.write(reply.getBytes());

            serverInterface.waitClose();
        }
        catch (SSLManagerException e) {
            e.printStackTrace();
        }
    }
}
