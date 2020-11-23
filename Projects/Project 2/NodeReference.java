import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NodeReference {
  public BigInteger id;
  public String ip;
  public int port;

  NodeReference(String ip, int port) throws NoSuchAlgorithmException {
    this.ip = ip;
    this.port = port;
    this.id = getHash(ip, port);
  }

  NodeReference(String ip, String port) throws NoSuchAlgorithmException {
    this.ip = ip;
    this.port = Integer.parseInt(port);
    this.id = getHash(this.ip, this.port);
  }

  /**
   * Find successor.
   */
  public NodeReference findSuccessor(BigInteger id) throws NoSuchAlgorithmException {
    String ipAddress;
    int portNumber;
    try (SSLSocketStream socket = new SSLSocketStream(ip, port)) {
      byte[] fromClient = new byte[65000];
      int msgSize;

      socket.write(("CHORD FINDSUCCESSOR " + id).getBytes());
      if ((msgSize = socket.read(fromClient)) != -1) {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(fromClient, 0, msgSize);
        String msg = message.toString();
        String[] answer = msg.split("\\s+|\n");
        ipAddress = answer[2];
        portNumber = Integer.parseInt(answer[3]);
        NodeReference node = new NodeReference(ipAddress, portNumber);
        socket.close();
        return node;
      } else {
        System.out.println("ERROR: Chord findSuccessor answer was empty.");
      }
    } catch (SSLManagerException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Notify.
   */
  public void notify(NodeReference n) {
    try (SSLSocketStream socket = new SSLSocketStream(ip, port)) {
      socket.close();
      socket.write(("CHORD NOTIFY " + n.ip + " " + n.port).getBytes());
    } catch (SSLManagerException e) {
      
      e.printStackTrace();
    }
  }

  /**
   * Get predecessor.
   */
  public NodeReference getPredecessor() throws NoSuchAlgorithmException {
    String ipAddress;
    String portNumber;

    try (SSLSocketStream socket = new SSLSocketStream(ip, port)) {
      byte[] fromClient = new byte[65000];
      int msgSize;

      socket.write(("CHORD GETPREDECESSOR").getBytes());
      if ((msgSize = socket.read(fromClient)) != -1) {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(fromClient, 0, msgSize);
        String msg = message.toString();
        String[] answer = msg.split("\\s+|\n");
        if (answer[2].equals("NULL")) {
          return null;
        }
        ipAddress = answer[2];
        portNumber = answer[3];
        NodeReference node = new NodeReference(ipAddress, portNumber);

        socket.close();
        return node;
      } else {
        System.out.println("ERROR: Chord getPredecessor answer was empty.");
      }
    } catch (SSLManagerException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Failed? .
   */
  public boolean hasFailed() {
    try {
      SSLClientInterface socket = new SSLClientInterface("client","123456",ip,port);
      boolean res = !socket.handshake();
      socket.close();
      return res;
    } catch (SSLManagerException | IOException e) {
      return true;
    }
  }

  private BigInteger getHash(String ip, int port) throws NoSuchAlgorithmException {
    String unhashedId = ip + ';' + port;
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] messageDigest = md.digest(unhashedId.getBytes());
    BigInteger toNum = new BigInteger(1, messageDigest);
    while (toNum.compareTo(new BigInteger("1000000000")) == 1) {
      toNum = toNum.divide(new BigInteger("10"));
    }
    return toNum;
  }
}
