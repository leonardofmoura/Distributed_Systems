import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Server {
    public static void main(String[] args) {
        if (args.length != 1 ) {
            printUsage();
            return;
        }

        int port = Integer.parseInt(args[0]);

        try {
            new ServerProcess(port).start();
        }
        catch (IOException e) {
            System.err.println("Error while starting the server");
            e.printStackTrace();
        }
    }

    public static void printUsage() {
        System.out.println("usage: Server <service_port>");
    }
}

class ServerProcess {
    private ServerSocket serverSocket;
    private HashMap<String,String> data = new HashMap<>();

    ServerProcess(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
    }

    public void start() throws IOException {
        this.log("Started service");

        while (true) {
            new ServerProcessThread(serverSocket.accept(),this).start();
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
    private Socket socket;
    private ServerProcess serverProcess;

    public ServerProcessThread(Socket clientSocket, ServerProcess process) {
        super("ServerProcessThread");
        this.socket = clientSocket;
        this.serverProcess = process;

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

        return reply;
    }

    @Override
    public void run() {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String message = in.readLine();

            this.serverProcess.log(message);

            String reply = this.parseMessage(message);

            out.println(reply);

            socket.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
