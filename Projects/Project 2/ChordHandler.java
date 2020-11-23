import java.security.NoSuchAlgorithmException;

/**
   * ChordHandler task runs all the necessary methods to make sure the ChordRing
   * is updated and ready
   */
public class ChordHandler implements Runnable {
  public Node chordNode;

  public ChordHandler(Node node) {
    this.chordNode = node;
  }

  @Override
  public void run() {
    try {
      System.out.println("Chord Handler running");
      chordNode.checkSuccessor();
      chordNode.checkPredecessor();
      chordNode.stabilize();
      chordNode.fixFingers();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    //System.out.println(chordNode);
  }
}
