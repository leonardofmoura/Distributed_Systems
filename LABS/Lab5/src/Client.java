import java.io.*;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class Client {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
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
        else if (args[2].equals("close") && args.length == 3) {
            operation = "CLOSE";
            operands = "none";
            client.shutdownServer();
        }
        else {
            printUsage();
            return;
        }

        // wait for server answer
        String answer = client.waitForMessage();
        client.close(); //close the socket

        client.log(operation + " " + operands + " : " + answer);
    }

    static void printUsage() {
        System.out.println("usage: Client <server_addr> <server_port> <operator> <operands>");
    }
}


class ClientProcess {
    private SSLClientInterface clientInterface;

    public ClientProcess(String hostname, int port) throws IOException {

        //Configure the client
        try {
            this.clientInterface = new SSLClientInterface("client","123456",hostname,port);
            System.out.println("Starting handshake");
            this.clientInterface.handshake();
        } catch (SSLManagerException e) {
            e.printStackTrace();
        }

    }


    private void sendMessage(String message) {
        try {
            this.clientInterface.write(message.getBytes());
            this.log("Sent " + message);
        }
        catch (SSLManagerException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            this.clientInterface.close();
        }
        catch (SSLManagerException | IOException e) {
            e.printStackTrace();
        }
    }

    public void register(String dnsName, String ipAddr) {
        String message = "REGISTER " + dnsName + " " + ipAddr;
        this.sendMessage(message);
    }

    public void lookup(String dnsName) {
        String message = "LOOKUP " + dnsName;
        this.sendMessage(message);
    }

    public void shutdownServer() {
        this.sendMessage("CLOSE");
    }

    public String waitForMessage() {
        //Sets a timeout so that the program does not wait indefinitely
        //this.interafce.setSoTimeout(5000);

        try {
            byte[] bytes = new byte[100000];

            clientInterface.read(bytes);

            return new String(bytes, StandardCharsets.UTF_8);
        }
        catch (SSLManagerException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void log(String message) {
        System.out.println("Client: " + message);
    }
}
