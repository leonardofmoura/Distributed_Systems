import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LocalStorage {
  final private ConcurrentHashMap<String, FileData> files;
  final private ConcurrentHashMap<String, ChunkData> chunks;
  final private ConcurrentHashMap<String, CopyOnWriteArrayList<ChunkData>> restoringChunks;
  final private ConcurrentHashMap<String, CopyOnWriteArrayList<String>> storingPeers;
  final private ConcurrentHashMap<String, Integer> desiredReplication;
  final private CopyOnWriteArrayList<String> recentPutchunk;
  final private CopyOnWriteArrayList<String> recentRemoved;
  final private PeerService peer;

  private long availableSpace;
  private long maxSpace;

  /**
   * Constructor for LocalStorage class. It serves to catalogue all the files
   * backed up and chunks stored in the system. files is a a map where the key is
   * the file id and the value is the file data. chunks is a map where the key is
   * the concatenation of file id with chunk number and the value is the chunk.
   * is an int array. the int array is used to know the replication of each chunk:
   * if the chunk with id number X is stored 3 times, then the value in the array
   * with index X is 3. storingPeers is an hash map that stores which peers have
   * stored a chunk. The key is the concatenation of the file id and the chunk
   * number and the value is a list with the peer ids that have sent a stored
   * message for that chunk. availableSpace KBytes available for storage.
   */
  public LocalStorage(PeerService peer) {
    files = new ConcurrentHashMap<>();
    chunks = new ConcurrentHashMap<>();
    restoringChunks = new ConcurrentHashMap<>();
    storingPeers = new ConcurrentHashMap<>();
    desiredReplication = new ConcurrentHashMap<>();
    recentPutchunk = new CopyOnWriteArrayList<>();
    recentRemoved = new CopyOnWriteArrayList<>();
    this.peer = peer;
    availableSpace = 1000000000;
    maxSpace = 1000000000;

    // Create store folder if it does not exist
    new File("peer" + peer.peerId + "/storage").mkdirs();
  }

  /**
   * Backup file fd in file list.
   *
   * @param fd file data
   */
  public void backup(FileData fd, int desiredReplication) {
    if (files.get(fd.id) != null)
      return;

    // record the file
    files.put(fd.id, fd);

    // initialize storing peers for each chunk
    CopyOnWriteArrayList<String> storeList;
    for (ChunkData chunk : fd.chunks) {
      storeList = new CopyOnWriteArrayList<>();
      storingPeers.put(fd.id + chunk.nr, storeList);
    }

    //initialize replication degree
    for (ChunkData chunk : fd.chunks) {
      this.desiredReplication.put(fd.id + chunk.nr, desiredReplication);
    }

  }

  /**
   * Store chunk cd in chunk list.
   *
   * @param cd chunk data
   */
  public void store(ChunkData cd, int desiredReplication) {
    if (availableSpace - cd.size <= 0) {
      // In case there is no space, tell peer to remove unneeded chunks
      peer.cleanup();
    }

    //Store if after the cleanup there is available space
    if (availableSpace - cd.size > 0) {
      this.storeChunkInDisk(cd,desiredReplication);
    }
  }

  /**
   * Creates the chunk file in the disk storage folder
   * @param cd
   * @param desiredReplication
   */
  private void storeChunkInDisk(ChunkData cd, int desiredReplication) {
    Path path = Paths.get("peer" + peer.peerId + "/storage/" + cd.fileId + "_" + cd.nr);

    try {
      AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.CREATE,
              StandardOpenOption.WRITE);

      ByteBuffer buffer = ByteBuffer.wrap(cd.chunk);

      fileChannel.write(buffer, 0);
    } catch (IOException e) {
      e.printStackTrace();
    }

    chunks.put(cd.fileId + cd.nr, cd);
    availableSpace -= cd.size;

    //Create the replication List if it does not exists
    if (storingPeers.get(cd.fileId + cd.nr) == null) {
      storingPeers.put(cd.fileId + cd.nr, new CopyOnWriteArrayList<>());
    }

    // add self to replication list
    this.recordStore(cd.fileId, cd.nr, String.valueOf(peer.peerId));

    // add the desired replication of the chunk
    this.desiredReplication.put(cd.fileId + cd.nr, desiredReplication);
  }

  /**
   * Returns ordered list of chunks with highest difference between actual
   * replication and desired replication
   *
   * @return ordered list of fileId+chunkNo
   */
  public ArrayList<String> reclaim() {
    ArrayList<String> list = new ArrayList<>();

    // Get map with difference between actual replication degree and desired
    // replication degree
    Map<String, Integer> repMap = new LinkedHashMap<>();
    for (Map.Entry<String, CopyOnWriteArrayList<String>> e : storingPeers.entrySet()) {
      repMap.put(e.getKey(), e.getValue().size() - desiredReplication.get(e.getKey()));
    }

    // Sort map to get highest replication first
    List<Map.Entry<String, Integer>> repList = new LinkedList<>(repMap.entrySet());
    Collections.sort(repList, new Comparator<Map.Entry<String, Integer>>() {
      public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
        return o2.getValue() - o1.getValue();
      }
    });

    // Put ordered keys into final list
    for (Map.Entry<String, Integer> e : repList) {
      list.add(e.getKey());
    }

    return list;
  }

  /**
   * Returns ordered list of chunks with highest difference between actual
   * replication and desired replication and only if they are above certain value
   *
   * @param diff minimum difference, all chunks must have above this diff
   * @return ordered list of fileId+chunkNo
   */
  public ArrayList<String> reclaim(int diff) {
    ArrayList<String> list = new ArrayList<>();

    // Get map with difference between actual replication degree and desired
    // replication degree
    Map<String, Integer> repMap = new LinkedHashMap<>();
    for (Map.Entry<String, CopyOnWriteArrayList<String>> e : storingPeers.entrySet()) {
      repMap.put(e.getKey(), e.getValue().size() - desiredReplication.get(e.getKey()));
    }

    // Sort map to get highest replication first
    List<Map.Entry<String, Integer>> repList = new LinkedList<>(repMap.entrySet());
    Collections.sort(repList, new Comparator<Map.Entry<String, Integer>>() {
      public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
        return o2.getValue() - o1.getValue();
      }
    });

    // Put ordered keys into final list
    for (Map.Entry<String, Integer> e : repList) {
      if(e.getValue() > diff)
        list.add(e.getKey());
    }

    return list;
  }

  /**
   * Remove stored chunk with given fileId+chunkNo key
   *
   * @param id fileId+chunkNo key
   */
  public void removeChunk(String id) {
    ChunkData cd;
    if ((cd = chunks.get(id)) != null) {
      availableSpace += cd.size;
      chunks.remove(id);
      desiredReplication.remove(id);
      storingPeers.remove(id);

    }
  }

  /**
   * Remove stored chunk with given fileId+chunkNo key
   *
   * @param fileId
   * @param chunkNo
   */
  public void removeChunk(String fileId, String chunkNo) {
    ChunkData cd;
    if ((cd = chunks.get(fileId + chunkNo)) != null) {
      availableSpace += cd.size;
      chunks.remove(fileId + chunkNo);
      desiredReplication.remove(fileId + chunkNo);
      storingPeers.remove(fileId + chunkNo);

      //delete the file
      try {
        Files.delete(Paths.get("peer" + peer.peerId + "/storage/" + fileId + "_" + chunkNo));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Delete chunk file from system
   *
   * @param fileId file id
   */
  public void delete(String fileId) {
    //Remove from chunk map
    for (Map.Entry<String, ChunkData> entry: this.chunks.entrySet()) {
      if (entry.getKey().substring(0,64).equals(fileId)) {
        //Add available space
        int size = entry.getValue().size;
        availableSpace += size;

        try {
          Files.delete(Paths.get("peer" + peer.peerId + "/storage/" + fileId + "_" + entry.getValue().nr));
        } catch (IOException e) {
          e.printStackTrace();
        }

        //Remove entry
        chunks.remove(entry.getKey());
      }
    }

    //Remove from desired replication map
    for (Map.Entry<String, Integer> entry: this.desiredReplication.entrySet()) {
      if (entry.getKey().substring(0,64).equals(fileId)) {
        desiredReplication.remove(entry.getKey());
      }
    }

    //Remove from replication map
    for (Map.Entry<String, CopyOnWriteArrayList<String>> entry: storingPeers.entrySet()) {
      if (entry.getKey().substring(0,64).equals(fileId)) {
        storingPeers.remove(entry.getKey());
      }
    }

  }

  /**
   * Return chunk data if it exists, null otherwise.
   *
   * @param fileId file id
   * @param nr     chunk number
   * @return the chunk number nr of file fileId, or null
   */
  public ChunkData getChunk(String fileId, int nr) {
    return chunks.get(fileId + nr);
  }

  /**
   * Return file data if it exists, null otherwise.
   *
   * @param fileId file id
   * @return file data with id fileId
   */
  public FileData getFile(String fileId) {
    return files.get(fileId);
  }

  /**
   * Return replication degree of chunk number nr of file with id fileId.
   *
   * @param fileId file id
   * @param nr     chunk number
   * @return
   */
  public int getReplication(String fileId, int nr) {
    CopyOnWriteArrayList<String> list;
    if ((list = storingPeers.get(fileId+nr)) != null) {
      return list.size();
    }
    return 0;
  }

  /**
   * Save chunk in restoringChunks map. Chunks will be used to restore the file.
   *
   * @param cd chunk information
   */
  public void saveChunk(ChunkData cd) {
    String id = cd.fileId;
    CopyOnWriteArrayList<ChunkData> list;
    synchronized(this) {
      if ((list = restoringChunks.get(id)) != null) {
        for(ChunkData chunk : list) {
          if(chunk.nr == cd.nr) return;
        }
        list.add(cd);
      }
    };
  }

  /**
   * Prepare restoringChunks map to receive chunks
   *
   * @param fileId id of file
   */
  public void restore(String fileId) {
    if (restoringChunks.get(fileId) == null) {
      restoringChunks.put(fileId, new CopyOnWriteArrayList<>());
    }
  }

  /**
   * Get list of chunks to use in restore
   *
   * @param fileId file id
   * @return
   */
  public CopyOnWriteArrayList<ChunkData> getRestoring(String fileId) {
    return restoringChunks.get(fileId);
  }

  /**
   * Remove restoring list from map
   *
   * @param fileId file id
   */
  public void removeRestoring(String fileId) {
    restoringChunks.remove(fileId);
  }

  /**
   * Records on the storing peers map that a peer has stored a certain chunk
   *
   * @param fileId
   * @param chunkNumber
   * @param peerId
   */
  public void recordStore(String fileId, int chunkNumber, String peerId) {
    CopyOnWriteArrayList<String> list;

    if ((list = storingPeers.get(fileId + chunkNumber)) != null) {
      list.add(peerId);
    }
  }

  /**
   * Tests if there is a record of if a certain peer has stored a certain chunk
   *
   * @param fileId
   * @param chunkNumber
   * @param peerId
   * @return
   */
  public boolean testStoredChunkInPeer(String fileId, int chunkNumber, String peerId) {
    CopyOnWriteArrayList<String> list;

    if ((list = storingPeers.get(fileId + chunkNumber)) != null) {
      return list.contains(peerId);
    }

    return false;
  }

  /**
   * Tests if a file is present in the file map (i.e. this was the initiator peer)
   *
   * @param fileId
   * @return
   */
  public boolean testFilePresent(String fileId) {
    return this.files.containsKey(fileId);
  }

  /**
   * Store given LocalStorage in a file
   *
   * @param storage
   */
  public static void serialize(LocalStorage storage) {
    File file = new File("peer" + Peer.peerId + "/storage.peer");
    if (file.exists()) {
      file.delete();
    }
    try (FileOutputStream fileStream = new FileOutputStream(file);
        ObjectOutputStream objStream = new ObjectOutputStream(fileStream)) {
      file.createNewFile();
      objStream.writeObject(storage);
    } catch (IOException e) {
      System.out.println("Error while serializing storage");
      System.exit(100);
    }
  }

  /**
   * Create LocalStorage object from file
   *
   * @param peer peer of storage
   * @return storage from file, or new one if file is missing
   */
  public static LocalStorage deserialize(PeerService peer) {
    File file = new File("peer" + peer.peerId + "/storage.peer");
    LocalStorage s = new LocalStorage(peer);

    if (file.exists()) {
      try (FileInputStream fileStream = new FileInputStream(file);
          ObjectInputStream objStream = new ObjectInputStream(fileStream)) {
        s = (LocalStorage) objStream.readObject();
      } catch (Exception e) {
        System.out.println("Error while serializing storage");
        System.exit(100);
      }
    }

    return s;
  }

  /**
   * Get free space in storage
   *
   * @return
   */
  public long getAvailableSpace() {
    return availableSpace;
  }

  /**
   * Get occupied space in storage
   *
   * @return
   */
  public long getUsedSpace() {
    return maxSpace - availableSpace;
  }

  /**
   * Get maximum space in storage
   *
   * @return
   */
  public long getMaxSpace() {
    return maxSpace;
  }

  /**
   * Change max space and update available space
   *
   * @param finalSize final max size
   */
  public void updateMax(int finalSize) {
    maxSpace = finalSize;
    availableSpace = maxSpace;
    for(Map.Entry<String, ChunkData> e : chunks.entrySet()) {
      availableSpace -= e.getValue().size;
    }
  }

  public void addRecentRemoved(String fileId, int nr) {
    recentRemoved.add(fileId+nr);
  }

  public void cancelChunkBackup(String fileId, int nr) {
    recentPutchunk.add(fileId+nr);
  }

  public boolean getRecentRemoved(String id) {
    return recentRemoved.contains(id);
  }

  public boolean getRecentPutchunk(String id) {
    return recentPutchunk.contains(id);
  }

  public void clearRemoved(String id) {
    recentPutchunk.remove(id);
    recentRemoved.remove(id);
  }

  /**
   * Return files map
   *
   * @return the files
   */
  public ConcurrentHashMap<String, FileData> getFiles() {
    return files;
  }

  /**
   * Return chunks map
   *
   * @return the chunks
   */
  public ConcurrentHashMap<String, ChunkData> getChunks() {
    return chunks;
  }

  public String getFileId(String filePath) {
    for (Map.Entry<String,FileData> entry: files.entrySet()) {
      if (entry.getValue().file.getPath().equals(filePath)) {
        return entry.getValue().id;
      }
    }

    return null;
  }

  public void deleteFile(String fileId) {
    this.files.remove(fileId);

    for (Map.Entry<String, CopyOnWriteArrayList<String>> entry: storingPeers.entrySet()) {
      if (entry.getKey().substring(0,64).equals(fileId)) {
        storingPeers.remove(entry.getKey());
      }
    }
  }
}
