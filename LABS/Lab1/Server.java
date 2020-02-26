import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Server {
    public static void main(String[] args) {
        if (args.length == 1 ) {
            try {
                int port = Integer.parseInt(args[0]);
                ServerProcess server = new ServerProcess(port);
                server.start();
            }
            catch (NumberFormatException e) {
                System.err.println("Error: Invalid port number");
            }
            catch (IllegalArgumentException e) {
                System.err.println("Error: Port number must be greater than zero");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            System.out.println("usage: Server <port number>");
        }
    }
}

class ServerProcess {
    private DatagramSocket socket;
    private HashMap<String,String> data = new HashMap<>();

    public ServerProcess(int port) throws IllegalArgumentException, SocketException {
        if (port < 1024) {
            throw new IllegalArgumentException();
        }

        this.socket = new DatagramSocket(port);
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

    public void start() throws IOException {
        while (true) {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf,buf.length);
            socket.receive(packet);

            String message = new String(packet.getData(),packet.getOffset(),packet.getLength());
            String reply = this.parseMessage(message);

            this.sendMessage(packet.getAddress(),packet.getPort(),reply);
        }
    }
}
