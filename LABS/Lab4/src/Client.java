import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class Client {
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            printUsage();
            return;
        }

        //Start the client
        ClientProcess client = new ClientProcess(args[0],Integer.parseInt(args[1]));

        String operation, operands;

        // Send the corresponding packet
        if (args[2].equals("register") && args.length == 5) {
            client.register(args[3],args[4]);
            operation = "REGISTER";
            operands = args[3] + " " + args[4];
        }
        else if (args[2].equals("lookup") && args.length == 4) {
            client.lookup(args[3]);
            operation = "LOOKUP";
            operands = args[3];
        }
        else {
            printUsage();
            return;
        }

        // wait for server answer
        String answer = client.waitForMessage();

        client.log(operation + " " + operands + " : " + answer);
    }

    static void printUsage() {
        System.out.println("usage: Client <mcast_addr> <mcast_port> <operator> <operands>");
    }
}


class ClientProcess {
    Socket socket;
    PrintWriter out;
    BufferedReader in;

    public ClientProcess(String hostname, int port) throws IOException {
        this.socket = new Socket(hostname,port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private void sendMessage(String message) {
        this.out.println(message);
        this.log("Sent " + message);
    }

    public void register(String dnsName, String ipAddr) {
        String message = "REGISTER " + dnsName + " " + ipAddr;
        this.sendMessage(message);
    }

    public void lookup(String dnsName) {
        String message = "LOOKUP " + dnsName;
        this.sendMessage(message);
    }

    public String waitForMessage() throws SocketException {
        //Sets a timeout so that the program does not wait indefinitely
        this.socket.setSoTimeout(5000);

        try {
            String message = in.readLine();
            return message;
        }
        catch (IOException e) {
            e.printStackTrace();
            return "Timeout?????";
        }
    }

    public void log(String message) {
        System.out.println("Client: " + message);
    }
}
