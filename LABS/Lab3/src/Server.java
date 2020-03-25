import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

public class Server implements DnsService {
    private HashMap<String,String> data = new HashMap<>();

    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
        if (args.length != 1) {
            Server.printUsage();
            return;
        }

        String remoteObjectName = args[0];

        try {
            //Server instantiation
            Server server = new Server();

            //Exporting the remote server object to the stub
            DnsService stub = (DnsService) UnicastRemoteObject.exportObject(server,0);

            //Binding the stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(remoteObjectName,stub);
        }
        catch (Exception e) {
            //System.out.println("Server exception " + e.toString());
            e.printStackTrace();
        }

        System.out.println("Server started");
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

    @Override
    public String lookup(String dns){
        String ipAddr = this.data.get(dns);

        if (ipAddr == null) {
            System.out.println("Lookup " + dns + ":: ERROR");
        }
        else {
            System.out.println("Lookup " + dns + ":: " + ipAddr);
        }

        return ipAddr;
    }

    @Override
    public int register(String dns, String ipAddr){
        if (data.containsKey(dns)) {
            System.out.println("Register " + dns + " to " + ipAddr + ":: ERROR");
            return -1;
        }

        int numVals = this.getNumKeysValue(ipAddr);
        this.data.put(dns, ipAddr);

        System.out.println("Register " + dns + " to " + ipAddr + ":: " + numVals);

        return numVals;
    }

    public static void printUsage() {
        System.out.println("Usage: Server <remote_object_name>");
    }
}
