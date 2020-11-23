import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * FileInfo class holds all the information about a file: its id,
 *  generated with metadata and data hashing, its path, the desired number of
 *  copies in the syste (repDegree), and a list (chunks) of ChunkInfo that holds information
 *  about its chunks
 */
public class FileInfo {
  private String id;
  private final String path;
  private final int repDegree;
  private final List<ChunkInfo> chunks;

  /**
   * Constructor, given a path and a desired number of copies it creates a File ID
   *  and the empty list of chunkInfo
   */
  public FileInfo(final String path, final int repDegree) {
    this.path = path;
    this.repDegree = repDegree;
    try {
      this.id = this.hasher(path);
    } catch (final Exception e) {
      e.printStackTrace();
    }

    this.chunks = Collections.synchronizedList(new ArrayList<>());
  }

  /**
   * Method that created the File ID, it takes a path and uses its first 200MB, its path
   * and its lastModifiedTime to create a hash using SHA-256, that is decoded to hex
   */
  private String hasher(final String filePath) throws IOException, NoSuchAlgorithmException {
    final Path path = Paths.get(filePath);
    final ByteArrayOutputStream dataWMetaData = new ByteArrayOutputStream();
    final StringBuilder hexString = new StringBuilder();

    final BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
    final FileInputStream fis = new FileInputStream(filePath);
    final byte[] data = new byte[200000000];
    fis.read(data);
    fis.close();

    dataWMetaData.write(filePath.getBytes());
    dataWMetaData.write(attr.lastModifiedTime().toString().getBytes());
    dataWMetaData.write(data);

    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    final byte[] encodedHash = digest.digest(dataWMetaData.toByteArray());
    for (final byte b : encodedHash) {
      final String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }

    return hexString.toString();
  }

  /**
   * Adds a ChunkInfo to the list of chunks that makes up the file
   */
  public void addChunk(final ChunkInfo chunk) {
    this.chunks.add(chunk);
  }

  /**
   * Return the File id
   */
  public String getId() {
    return id;
  }

  /**
   * Return the list of ChunkInfo
   */
  public List<ChunkInfo> getChunks() {
    return chunks;
  }

  /**
   * Return the desired number of copies of the file
   */
  public int getRepDegree() {
    return repDegree;
  }

  /**
   * Return file path
   */
  public String getPath() {
    return path;
  }

  /**
   * Return ChunkInfo searched in the list by its number
   */
  public ChunkInfo getChunkByNo(final int no) {
    final Optional<ChunkInfo> result = chunks.stream()
        .filter(chunk -> chunk.getNo() == no).findFirst();
    return result.orElse(null);
  }

  /**
   * Function that gives the FileInfo information in string format, used to show the state of the
   * file in the system when requested
   */
  @Override
  public String toString() {
    final StringBuilder aux = new StringBuilder("Path: " + path + "\n-\n  FileID: " + id
        + "\n  Desired Replication Degree: " + repDegree + "\n  Chunks: " + chunks.size());
    for (final ChunkInfo chunkInfo : chunks) {

      aux.append("\n-\n").append(chunkInfo.toString());

    }
    return aux.toString();
  }
}
