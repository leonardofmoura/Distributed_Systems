import java.io.IOException;
import java.net.*;

public class Client {
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            printUsage();
            return;
        }

        //Get server ip through multicast
        MulticastProcess multicastProcess = new MulticastProcess(args[0],Integer.parseInt(args[1]));
        String[] serverData = multicastProcess.waitServerBroadcast();

        //Start the client
        ClientProcess client = new ClientProcess();
        int port = Integer.parseInt(serverData[1]);
        String addr = serverData[0];
        String operation;
        String operands;

        // Send the corresponding packet
        if (args[2].equals("register") && args.length == 5) {
            client.register(addr,port,args[3],args[4]);
            operation = "REGISTER";
            operands = args[3] + " " + args[4];
        }
        else if (args[2].equals("lookup") && args.length == 4) {
            client.lookup(addr,port,args[3]);
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
    private DatagramSocket socket;

    public ClientProcess() throws SocketException {
        this.socket = new DatagramSocket();
    }

    private void sendMessage(String addr, int port, String message) throws IOException {
        byte[] buf = message.getBytes();

        InetAddress inetAddr = InetAddress.getByName(addr);
        DatagramPacket packet = new DatagramPacket(buf,buf.length,inetAddr,port);

        socket.send(packet);
    }

    public void log(String message) {
        System.out.println("Client: " + message);
    }

    public void register(String addr, int port,String dnsName, String ipAddr) throws IOException {
        String message = "REGISTER " + dnsName + " " + ipAddr;
        this.sendMessage(addr,port,message);
    }

    public void lookup(String addr, int port, String dnsName) throws IOException {
        String message = "LOOKUP " + dnsName;
        this.sendMessage(addr,port,message);
    }

    public String waitForMessage() throws IOException {
        byte[] buffer = new byte[1024];

        DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
        socket.setSoTimeout(5000);

        try {
            socket.receive(packet);
        }
        catch (SocketTimeoutException e) {
            String errMsg = "ERROR -> Connection timed out";
            return errMsg;
        }

        String message = new String(packet.getData(),packet.getOffset(),packet.getLength());
        return message;
    }
}

class MulticastProcess {
    private MulticastSocket socket;

    public MulticastProcess(String mcastAddr, int mcastPort) throws IOException {
        socket = new MulticastSocket(mcastPort);

        socket.joinGroup(InetAddress.getByName(mcastAddr));
    }

    public String[] waitServerBroadcast() throws IOException {
        byte[] buffer = new byte[1024];

        DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
        socket.setSoTimeout(5000);

        socket.receive(packet);

        String msg = new String(packet.getData(),packet.getOffset(),packet.getLength());
        System.out.println(msg);
        String[] message = msg.split(" ");

        System.out.println("multicast: " + message[0] + " " + message[1]);

        return message;
    }
}
