import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client {
    public static void main(String[] args) {
        if (args.length < 4 ) {
            Client.printUsage();
            return;
        }

        try {
            //Get the registry
            Registry registry = LocateRegistry.getRegistry(args[0]);

            //Look for the remote object in the registry
            DnsService stub = (DnsService) registry.lookup(args[1]);

            //Call the remote method using the stub object
            if (args[2].equals("register") && args.length == 5) {
                int result = stub.register(args[3],args[4]);

                if (result == -1) {
                    System.out.println("Register " + args[3] + " to " + args[4] + ":: ERROR");
                }
                else {
                    System.out.println("Register " + args[3] + " to " + args[4] + ":: " + result);
                }
            }
            else if (args[2].equals("lookup") && args.length == 4) {
                String ipAddr = stub.lookup(args[3]);

                if (ipAddr == null) {
                    System.out.println("Lookup " + args[3] + ":: ERROR");
                }
                else {
                    System.out.println("Lookup " + args[3] + ":: " + ipAddr);
                }
            }
            else {
                printUsage();
                return;
            }

        } catch (Exception e) {
            System.out.println("Client exception " + e.toString());
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: Client <host_name> <remote_object_name> <operation> <operands>");
    }
}
