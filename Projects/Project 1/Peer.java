import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * The main function used to invoke a peer
 */
public class Peer {
  static String VERSION = "1.0";
  static int peerId;

  // <version> <peerId> <access_point> <control_addr> <control_port> <backup_addr>
  // <backup_port> <restore_addr> <restore_port>
  public static void main(String[] args) {
    if (args.length != 9) {
      printUsage();
      System.exit(1);
    }

    String version = args[0];
    int peerId = Integer.parseInt(args[1]);
    String peerAccessPoint = args[2];

    String controlAddr = args[3];
    int controlPort = Integer.parseInt(args[4]);

    String backupAddr = args[5];
    int backupPort = Integer.parseInt(args[6]);

    String restoreAddr = args[7];
    int restorePort = Integer.parseInt(args[8]);

    //create the peer
    Peer.peerId = peerId;
    PeerService peer = new PeerService(peerId, version, controlAddr, controlPort, backupAddr, backupPort, restoreAddr, restorePort);
    peer.start();

    PeerInterface stub = null;

    try {
      stub = (PeerInterface) UnicastRemoteObject.exportObject(peer,0);
    }
    catch (RemoteException e) {
      System.err.println("Error: Could not export peer");
      e.printStackTrace();
      System.exit(1);
    }

    try {
      // The peer is responsible for creating the rmiRegistry
      Registry registry = LocateRegistry.createRegistry(1099);
      System.out.println("Registry created");
      registry.bind(peerAccessPoint,stub);
      System.out.println("Peer " + peerId + " started");
    }
    catch (RemoteException e) {
      // If it was not able to create the registry, it tries to find it
      System.out.println("Could not create registry on port 1099");
      System.out.println("Trying to locate registry");

      try {
        Registry registry = LocateRegistry.getRegistry();
        registry.bind(peerAccessPoint,stub);
        System.out.println("Peer " + peerId + " started");
      }
      catch (RemoteException ex) {
        //If it can not create the registry exit
        System.err.println("Error: Could not locate RMI registry");
        System.exit(1);
      }
      catch (AlreadyBoundException ex) {
        System.err.println("Error: " + peerAccessPoint + " already bound");
        System.exit(1);
      }
    }
    catch (AlreadyBoundException e) {
      System.err.println("Error: " + peerAccessPoint + "already bound");
      System.exit(1);
    }
  }

  public static void printUsage() {
    System.out.println("usage: java Peer <version> <peer_id> <peer_access_point> <access_point> <control_addr> <control_port> <backup_addr> <backup_port> <restore_addr> <restore_port>");
  }
}
