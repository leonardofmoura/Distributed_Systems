import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.cert.CertificateException;

public class Client {
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            printUsage();
            return;
        }

        //Start the client
        ClientProcess client = new ClientProcess(args[0],Integer.parseInt(args[1]));

        String operation, operands;

        /*
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

         */
    }

    static void printUsage() {
        System.out.println("usage: Client <mcast_addr> <mcast_port> <operator> <operands>");
    }
}


class ClientProcess {
    private SSLClientInterface socket;

    public ClientProcess(String hostname, int port) throws IOException {

        //Configure the client
        try {
            this.socket = new SSLClientInterface("client","123456",hostname,port);
            System.out.println("Starting handshake");
            this.socket.handshake();
        } catch (SSLManagerException e) {
            e.printStackTrace();
        }

    }

    /*
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

     */

    public void log(String message) {
        System.out.println("Client: " + message);
    }
}
