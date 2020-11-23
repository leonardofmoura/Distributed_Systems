import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;

/**
 * Class that is ran by a Node when it receives a message, receives the Socket, 
 * reads the message and handles it, using PeerMethods or Node methods
 * and replies
 */
public class MessageProcessor implements Runnable {

  SSLServerInterface server;

  public MessageProcessor(SSLServerInterface server) {
    this.server = server;
    server.handshake();
  }

  /**
   *  Reads message , delegates the processing and waits for the processing to reply
   */
  @Override
  public void run() {
    byte[] fromClient = new byte[65000];
    int msgSize;
    try {
      if ((msgSize = server.read(fromClient)) != -1) {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(fromClient, 0, msgSize);

        // System.out.println(msgSize);
        if (new String(fromClient).equals("Bye.")) {
          server.write("Bye.".getBytes());
          //server.waitClose();
        } else {
          byte[] answer = processMessage(message.toByteArray());
          if (answer != null) {
            // String answer = new String(meh);
            // System.out.println("YOU: " + answer);
            server.write(answer);
          }
          //server.waitClose();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Processes message, reads header, and sorts them by CHORD or PROTOCOL messages
   * and uses the specific function to process, returning the reply
   */
  public byte[] processMessage(byte[] msg) throws NoSuchAlgorithmException {
    String[] msgParts = new String(msg).split("\\s+|\n");
    NodeReference node = null;
    if (msgParts[0].equals("CHORD")) {
      switch (msgParts[1]) {
        case "FINDSUCCESSOR":
          node = Peer.chordNode.findSuccessor(new BigInteger(msgParts[2]));
          break;
        case "NOTIFY":
          NodeReference notifier = new NodeReference(msgParts[2], Integer.parseInt(msgParts[3]));
          Peer.chordNode.notify(notifier);
          break;
        case "GETPREDECESSOR":
          node = Peer.chordNode.predecessor;
          break;
        case "DELEGATE":
          // Save file in Peer Storage
          if (Peer.saveChunk(msg)) {
            return "SUCCESS".getBytes();
          } // IF saveChunk returns false it means that node doens't have space so 
          // it delegates the chunk to a sucessor
          if(Peer.delegateChunk(msg)){
            return "SUCCESS".getBytes();
          } // if delegation isn't possible return error
          return "ERROR".getBytes();
        default:
          break;
      }
    } else if (msgParts[0].equals("PROTOCOL")) {
      String fileID;
      int chunkNo;
      int copyNo;
      switch (msgParts[1]) {
        case "PUTCHUNK":
          // Save file
          if (Peer.saveChunk(msg)) {
            return "SUCCESS".getBytes();
          }// IF saveChunk returns false it means that node doens't have space so 
          // it delegates the chunk to a sucessor
          if(Peer.delegateChunk(msg)){
            System.out.println("Delegating chunk");
            return "SUCCESS".getBytes();
          } // if delegation isn't possible return error
          return "ERROR".getBytes();
        case "GETCHUNK":
          // Get the information of the needed chunk
          fileID = msgParts[2];
          chunkNo = Integer.parseInt(msgParts[3]);
          copyNo = Integer.parseInt(msgParts[4]);
          byte[] chunk;
          try {
            // Send Chunk
            chunk = Peer.retrieveChunk(fileID, chunkNo, copyNo);
          } catch (IOException e) {
            // If it catches an IOException it means it couldn't retrieve the chunk so it
            // informs the node
            return "ERROR".getBytes();
          }
          return chunk;
        case "DELETE":
          // Get the information of the needed chunk
          fileID = msgParts[2];
          chunkNo = Integer.parseInt(msgParts[3]);
          // Delete chunk
          try {
            if (Peer.deleteSavedChunk(fileID, chunkNo)) {
              return "SUCCESS".getBytes();
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
          return "ERROR".getBytes();
        default:
          break;
      }
    }
    if (node != null) {
      return ("CHORD NODE " + node.ip + " " + node.port).getBytes();
    } else {
      return null;
    }
  }
}
