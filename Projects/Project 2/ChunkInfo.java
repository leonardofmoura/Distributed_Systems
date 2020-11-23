/**
 * FileInfo class holds all the information about a chunk: the file id that it belongs to,
 *  its number, that represents its index when the file was split,  the desired number of
 *  copies in the system (wantedRepDegree), the current ammount (currRepDegree) started as 0,  its size in bytes, and a boolean that represents
 *  if the file is currently kept in storage or was delegated to sucessor, if it was delegated,
 *  stores the NodeReference of the Node that is storing it
 */
public class ChunkInfo {
  private int no;
  private String fileID;
  private int wantedRepDegree;
  private int currRepDegree = 0;
  private int size;
  private int copyNo;
  private boolean delegated;
  private NodeReference receiver;

  /**
   * Constructor that receives Chunk number, File Id, wanted number of copies and its size,
   *  and sets delegated as false
   */
  public ChunkInfo(int no, String fileID, int repDegree, int size) {
    this.no = no;
    this.fileID = fileID;
    this.size = size;
    this.wantedRepDegree = repDegree;
    this.delegated = false;
  }

  /**
   * Constructor that receives Chunk number, File Id, wanted number of copies and its size,
   *  and sets delegated as false, also sets its copyNo
   */
  public ChunkInfo(int no, String fileID, int repDegree, int size, int copyNo) {
    this.no = no;
    this.fileID = fileID;
    this.size = size;
    this.wantedRepDegree = repDegree;
    this.delegated = false;
    this.copyNo = copyNo;
  }


  /**
   * Return current Number of copies in the System
   */
  public int getCurrRepDegree() {
    return currRepDegree;
  }

  /**
   * Return file id.
   */
  public String getFileID() {
    return fileID;
  }

  /**
   * Return chunk number.
   */
  public int getNo() {
    return no;
  }

  public int getCopyNo() {
    return copyNo;
  }

  /**
   * Return chunk size.
   */
  public int getSize() {
    return size;
  }

  /**
   * Return wanted replication degree.
   */
  public int getWantedRepDegree() {
    return wantedRepDegree;
  }

  /**
   * Set current replication degree.
   */
  public void setCurrRepDegree(int currRepDegree) {
    this.currRepDegree = currRepDegree;
  }

  /**
   * Increment current replication degree.
   */
  public void incrementCurrRepDegree() {
    this.currRepDegree += 1;
  }

  /**
   * Decrement current replication degree.
   */
  public void decrementCurrRepDegree() {
    this.currRepDegree -= 1;
  }

  /**
   *  Sets delegated as true and stores the NodeReference to 
   *   the node that stored the chunk
   */
  public void delegate(NodeReference receiver){
    this.delegated = true;
    this.receiver = receiver;
  }

  /**
   * Return the NodeReference to the node that has this chunk
   */
  public NodeReference getReceiver() {
    return receiver;
  }

  /**
   * Return the delegated boolean value
   */
  public boolean getDelegated(){
    return delegated;
  }

  /**
   * Function that gives the ChunkInfo information in string format, used to show the state of the
   * chunk in the system when requested
   */
  @Override
  public String toString() {
    return "  FileID: " + fileID + "\n  ChunkNr: " + no + "\n  Size: " + (size / 1000)
        + "\n  Current Replication Degree: " + currRepDegree;
  }
  
  /**
   * 
   * Return the File Id of the file the chunk belongs too
   */
  public String getChunkID() {
    return fileID + "_" + no;
  }
}
