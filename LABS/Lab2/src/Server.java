import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {
    public static void main(String[] args) {
        if (args.length == 3 ) {
            try {
                int servicePort = Integer.parseInt(args[0]);
                ServerProcess server = new ServerProcess(servicePort);

                int mcastPort = Integer.parseInt(args[2]);
                String serverAddr = server.getAddress();

                MultiCastProcess multiCastProcess = new MultiCastProcess(args[1],mcastPort,serverAddr,servicePort);

                server.start(); //ensure the server is running when we start multicasting

                ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
                executor.scheduleAtFixedRate(multiCastProcess,1,1, TimeUnit.SECONDS);
            }
            catch (NumberFormatException e) {
                System.err.println("Error: Invalid port number");
            }
            catch (IllegalArgumentException e) {
                System.err.println("Error: Port number must be greater than 1024");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            printUsage();
        }
    }

    public static void printUsage() {
        System.out.println("usage: Server <srvc_port> <mcast_addr> <mcast_port>");
    }
}

class ServerProcess extends Thread{
    private DatagramSocket socket;
    private HashMap<String,String> data = new HashMap<>();
    private Thread t;
    private String address;

    public ServerProcess(int port) throws IllegalArgumentException, SocketException {
        if (port < 1024) {
            throw new IllegalArgumentException();
        }

        this.socket = new DatagramSocket(port);
        this.address = socket.getLocalAddress().getHostAddress();
        this.log("Service started");
    }

    private void sendMessage(InetAddress addr, int port, String message) throws IOException {
        byte[] buffer = message.getBytes();

        DatagramPacket packet = new DatagramPacket(buffer,buffer.length,addr,port);

        socket.send(packet);
    }

    private String parseMessage(String message) {
        String[] tokenMessage = message.split(" ");
        String reply = "-1";

        if (tokenMessage[0].equals("REGISTER") && tokenMessage.length == 3) {
            reply = this.register(tokenMessage[1],tokenMessage[2]);
        }
        else if (tokenMessage[0].equals("LOOKUP") && tokenMessage.length == 2){
            reply = this.lookup(tokenMessage[1]);
        }

        return reply;
    }

    private String register(String dns, String ipAddr) {
        int numVals = this.getNumKeysValue(ipAddr);

        this.data.put(dns,ipAddr);

        String reply = numVals + " " + dns + " " + ipAddr;
        this.log("Register " + dns + " to " + ipAddr);

        return reply;
    }

    private String lookup(String dns) {
        String ipAddr = this.data.get(dns);
        int numVals = this.getNumKeysValue(ipAddr);

        String reply = numVals + " " + dns + " " + ipAddr;
        this.log("Lookup " + dns + ": " + ipAddr);

        return reply;
    }

    private void log(String message) {
        System.out.println("Server: " + message);
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

    public String getAddress() {
        return this.address;
    }

    @Override
    public void run() {
        while (true) {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf,buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }

            String message = new String(packet.getData(),packet.getOffset(),packet.getLength());
            String reply = this.parseMessage(message);

            try {
                this.sendMessage(packet.getAddress(),packet.getPort(),reply);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class MultiCastProcess implements Runnable{
    private MulticastSocket socket;
    private String mcastAddr;
    private String serverAddr;
    int mcastPort;
    int serverPort;

    public MultiCastProcess(String mcastAddr, int mcastPort, String serverAddr, int serverPort) throws IOException {
        if (mcastPort < 1024) {
            throw new IllegalArgumentException();
        }

        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        this.mcastAddr = mcastAddr;
        this.mcastPort = mcastPort;

        this.socket = new MulticastSocket();

        socket.setTimeToLive(1);
    }

    @Override
    public void run() {
        String message = serverAddr + " " + serverPort;
        byte[] buf = message.getBytes();

        try {
            InetAddress group = InetAddress.getByName(this.mcastAddr);
            DatagramPacket packet = new DatagramPacket(buf,buf.length,group,mcastPort);

            socket.send(packet);
            System.out.println("multicast: " + this.mcastAddr + " " + this.mcastPort  + " : " + this.serverAddr + " " + serverPort);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
