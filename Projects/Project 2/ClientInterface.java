import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ClientInterface {
  /**
   * ClientInterface communicates with RMI with the Node and gives it the wanted protocol to perform
   *
   * @param args interface arguments
   */
  public static void main(String[] args) {
    // Arguments check
    if (args.length == 3 && (args[1].equalsIgnoreCase("DELETE") || args[1].equalsIgnoreCase("RESTORE") || args[1].equalsIgnoreCase("RECLAIM"))
        || args.length == 4 && args[1].equalsIgnoreCase("BACKUP")
        || args.length == 2 && args[1].equalsIgnoreCase("STATE")) {
    } else {
      System.err.println("Usage: java ClientInterface <PeerID>  <Protocol> :");
      System.err.println("   Backup protocol:  Backup <File_Path> <Replication_Degree>");
      System.err.println("   Restore protocol: Restore <File_Path>");
      System.err.println("   Delete protocol:  Delete <File_Path>");
      System.err.println("   Reclaim protocol: Reclaim <New_Max_Storage>");
      return;
    }


    try {
      // Getting the registry
      Registry registry = LocateRegistry.getRegistry(null);
      PeerInterface interfaceStub = (PeerInterface) registry.lookup("Peer" + args[0]);
      switch (args[1].toUpperCase()) {
        case "BACKUP":
          interfaceStub.backup(args[2], Integer.parseInt(args[3]));
          break;
        case "RESTORE":
          interfaceStub.restore(args[2]);
          break;
        case "DELETE":
          interfaceStub.delete(args[2]);
          break;
        case "RECLAIM":
          try {
            interfaceStub.spaceReclaim(Long.parseLong(args[2]));
          } catch (NumberFormatException e) {
            System.out.println(e.getMessage());
            System.err.println("<New_Max_Storage> must be a Number");
            System.exit(-1);
          }
          break;
        case "STATE":
          interfaceStub.printState();
          break;
        default:
          break;
      }

    } catch (Exception e) {
      System.err.println("Client Interface exception: " + e.toString());
      e.printStackTrace();
    }
  }
}
