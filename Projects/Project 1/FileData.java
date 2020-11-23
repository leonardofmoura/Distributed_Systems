import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;

public class FileData {
  static int MAX_CHUNK_SIZE = 64000;
  private static final byte[] HEX_CHARS = "0123456789ABCDEF".getBytes();

  final String id;
  final File file;
  int replicationDegree;
  ArrayList<ChunkData> chunks;

  /**
   * Constructor for FileData class. It stores the information about a file.
   * id is the sha256 hash of the properties of the file
   * file stores the path of the file
   * replicationDegree indicates the minimum replication of chunks needed
   * chunks is a list of all chunks that form the file
   *
   * @param path path of the file
   * @param repDegree replication degree of chunks
   */
  public FileData(String path, int repDegree) {
    file = new File(path);
    id = hashFile();
    replicationDegree = repDegree;
    chunks = splitFile();
  }

  /**
   * Hash the properties of the file
   *
   * @return hash string
   */
  private String hashFile() {
    try {
      String text = file.getName() + file.lastModified() + file.getParent() + file.length();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

      return encodeId(hash);
    } catch (Exception e) {
      System.exit(1);
    }
    return "";
  }

  private String encodeId(byte[] hash) {
    byte[] encodedId = new byte[hash.length * 2];
    for (int i = 0; i  < hash.length; i++) {
      int hexIndex = hash[i] & 0xff;
      encodedId[i*2] = HEX_CHARS[hexIndex >>> 4];
      encodedId[i*2+1] = HEX_CHARS[hexIndex & 0x0F];
    }

    return new String(encodedId, StandardCharsets.UTF_8);
  }

  /**
   * Split file into chunks of MAX_CHUNK_SIZE, less for final chunk.
   * If file size is multiple of MAX_CHUNK_SIZE, last chunk has size 0.
   *
   * @return list of chunks
   */
  private ArrayList<ChunkData> splitFile() {
    ArrayList<ChunkData> chunks = new ArrayList<>();
    int nr = 0;
    byte[] buf = new byte[MAX_CHUNK_SIZE];

    try {
      FileInputStream fstream = new FileInputStream(file);
      BufferedInputStream bstream = new BufferedInputStream(fstream);
      int bytes = 0;

      while ((bytes = bstream.read(buf)) > 0) {
        byte[] chunkBuf = Arrays.copyOf(buf, bytes);
        ChunkData c = new ChunkData(id, nr++, chunkBuf, bytes);
        chunks.add(c);
        buf = new byte[MAX_CHUNK_SIZE];
      }

      if (file.length() % MAX_CHUNK_SIZE == 0) {
        ChunkData c = new ChunkData(id, nr++, null, 0);
        chunks.add(c);
      }

      bstream.close();
    } catch (Exception e) {
      System.exit(1);
    }

    return chunks;
  }
}
