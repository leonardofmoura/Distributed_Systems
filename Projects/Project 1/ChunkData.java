public class ChunkData {
  final String fileId;
  final int nr;
  final byte[] chunk;
  final int size;

  /**
   * Constructor for ChunkData class. It serves for storing information about a
   * chunk: its id number, the chunk information, and its size.
   *
   * @param fileId id string of file
   * @param nr     id number of the chunk
   * @param chunk  chunk information
   * @param size   size of the chunk
   */
  public ChunkData(String fileId, int nr, byte[] chunk, int size) {
    this.fileId = fileId;
    this.nr = nr;
    this.chunk = chunk;
    this.size = size;
  }

}
