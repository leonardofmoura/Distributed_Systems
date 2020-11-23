import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Peer extends PeerMethods {
  public static Node chordNode;
  public static boolean shutdown = false;
  public static String ipAddress = null;
  public static int portNumber;
  public static ScheduledExecutorService pool;
  public static Storage storage;
  public static int id;
  public static boolean givingChunks = false;

  /**
   * When started, a Peer initiates his storage, his ID and calls run function
   */
  public static void main(String[] args) throws NoSuchAlgorithmException {

    Peer peer = new Peer();
    peer.run(args);
    id = Integer.parseInt(args[0]);
    storage = new Storage(id);
  }

  /**
   * When initiated a Peer sets his keystore and trust stores properties and can 
   *  either create a chord ring or join a already existing one.
   * Also creates his ThreadPoolExecetor, schedules Chord Synchronyzation, and initiates PeerThread
   *  that is always waiting for messages
   * Note: If Peer creates the ChordRing it also initiates rmiregistry
   */
  public void run(String[] args) throws NoSuchAlgorithmException {
    System.setProperty("javax.net.ssl.trustStore", "truststore");
    System.setProperty("javax.net.ssl.trustStorePassword", "123456");

    // Arguments check
    if (!((args.length == 4 && args[3].equalsIgnoreCase("CREATE"))
        || (args.length == 6 && args[3].equalsIgnoreCase("JOIN")))) {
      System.err.println("Usage: Peer <PeerID> <IpAddress> <PortNumber> <ChordOption> :");
      System.err.println("   Create Option: Peer <PeerID> <IpAddress> <PortNumber> create");
      System.err.println("   Join Option: Peer <PeerID> <IpAddress> <PortNumber>"
          + "join <ChordMemberIpAddress> <ChordMemberPortNumber>");
      System.exit(-1);
    }
    try {
      ipAddress = args[1];
      portNumber = Integer.parseInt(args[2]);

      String chordOption = args[3];

      chordNode = new Node(ipAddress, portNumber, this);
      // If
      if (chordOption.equalsIgnoreCase("CREATE")) {
        try {
          LocateRegistry.createRegistry(1099);
        } catch (RemoteException e) {
          System.err.println("ERROR: Failed to start RMI on port : 1099");
          System.exit(-1);
        }
        chordNode.create();
      } else if (chordOption.equalsIgnoreCase("JOIN")) {
        chordNode.join(args[4], Integer.parseInt(args[5]));
      } else {
        System.out.println("ERROR: Failed to initiate Peer.");
        return;
      }
    } catch (NumberFormatException e) {
      System.err.println("<PortNumber> must be a integer");
      System.exit(-1);
    }

    // Binding Peer to RMI for ClientInterface use
    try {
      PeerMethods peer = new PeerMethods();
      PeerInterface interfaceStub = (PeerInterface) UnicastRemoteObject.exportObject(peer, 0);
      Registry registry = LocateRegistry.getRegistry();
      registry.bind("Peer" + args[0], interfaceStub);
    } catch (Exception e) {
      e.getStackTrace();
      return;
    }
    // We start a Thread Pool with 50 available Threads
    pool = Executors.newScheduledThreadPool(50);

    // Create task where stabilizes and notifies chord and gives it to ThreadPool to
    // execute it every second
    Runnable chordhandle = new ChordHandler(chordNode);
    pool.scheduleAtFixedRate(chordhandle, 1, 5, TimeUnit.SECONDS);
    // Create task where a thread is permanently listening to the server socket and
    // gives it to the ThreadPool
    Runnable listener = new PeerThread(this);
    pool.execute(listener);
  }

}
