import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Testing Application
 */

public class TestApp {
  static String VERSION = "1.0";

  // <peerAccessPoint> <sub_protocol> <access_point> <operand1> <operand2>
  public static void main(String[] args) {
      if (args.length < 2 ) {
          printUsage();
          System.exit(1);
      }

      String peerAccessPoint = args[0];
      String protocol = args[1];

      //Connect to the peer
      Registry registry = null;
      try {
          registry = LocateRegistry.getRegistry(null);
          PeerInterface stub = (PeerInterface) registry.lookup(peerAccessPoint);

          switch (protocol) {
              case "BACKUP":
                  if (args.length != 4) {
                      printUsage();
                      System.out.println("Invalid BACKUP: arguments are <file_path> <desired_replication_degree>");
                  }
                  else {
                      String res = stub.backup(args[2],Integer.parseInt(args[3]));
                      System.out.println(res);
                  }
                  return;

              case "RESTORE":
                  if (args.length != 3) {
                      printUsage();
                      System.out.println("Invalid RESTORE: argument is <file_path>");
                  }
                  else {
                      String res = stub.restore(args[2]);
                      System.out.println(res);
                  }
                  return;

              case "DELETE":
                  if (args.length != 3) {
                      printUsage();
                      System.out.println("Invalid DELETE: argument is <file_path>");
                  }
                  else {
                      String res = stub.delete(args[2]);
                      System.out.println(res);
                  }
                  return;

              case "RECLAIM":
                  if (args.length != 3) {
                      printUsage();
                      System.out.println("Invalid RECLAIM: argument is <space_to_free>");
                  }
                  else {
                      String res = stub.reclaim(Integer.parseInt(args[2]));
                      System.out.println(res);
                  }
                  return;

                case "STATE":
                    if (args.length != 2) {
                        printUsage();
                        System.out.println("Invalid STATE: state has no arguments");
                    }
                    else {
                        String res = stub.state();
                        System.out.println(res);
                    }
                    return;

              case "SERIALIZE":
                  if (args.length != 2) {
                      printUsage();
                      System.out.println("Invalid SERIALIZE: serialize has no arguments");
                  }
                  else {
                      String res = stub.serialize();
                      System.out.println(res);
                  }
                  return;

              default:
                  printUsage();
                  System.out.println("Invalid sub protocol");
          }

      } catch (RemoteException | NotBoundException e) {
          e.printStackTrace();
      }
  }

  public static void printUsage() {
      System.out.println("Usage: java TestApp <peerAccessPoint> <sub_protocol> <access_point> <operand1> <operand2>");
  }
}
