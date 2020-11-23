import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The peer service is responsible for providing the interface to the test
 * application
 */
public class PeerService implements PeerInterface {
    final static ScheduledThreadPoolExecutor executer = new ScheduledThreadPoolExecutor(200);

    final int peerId;
    final String version;
    final MulticastControl mcControl;
    final MulticastBackup mcBackup;
    final MulticastRestore mcRestore;
    final LocalStorage storage;

    Thread mcBackupThread;
    Thread mcControlThread;
    Thread mcRestoreThread;

    public PeerService(int peerId, String version, String control_addr, int control_port, String backup_addr,
            int backup_port, String restore_addr, int restore_port) {
        this.peerId = peerId;
        this.version = version;
        this.mcControl = new MulticastControl(this, control_addr, control_port);
        this.mcBackup = new MulticastBackup(this, backup_addr, backup_port);
        this.mcRestore = new MulticastRestore(this, restore_addr, restore_port);

        this.storage = LocalStorage.deserialize(this);

        // Create threads
        this.mcBackupThread = new Thread(mcBackup);
        this.mcControlThread = new Thread(mcControl);
        this.mcRestoreThread = new Thread(mcRestore);
    }

    /**
     * Starts the service
     */
    public void start() {
        this.mcBackupThread.start();
        this.mcControlThread.start();
        this.mcRestoreThread.start();
    }

    @Override
    public String backup(String path, int repDegree) {
        FileData fd = new FileData(path, repDegree);
        storage.backup(fd,repDegree);
        for (ChunkData chunk : fd.chunks) {
            Message message = new Message(version, "PUTCHUNK", Integer.toString(peerId), fd.id, Integer.toString(chunk.nr), Integer.toString(fd.replicationDegree), chunk.chunk);
            mcBackup.sendMessage(message);
            System.out.println("System sent PUTCHUNK message.");
            executer.schedule(new PutchunkRepeater(storage, mcBackup, message, 1, fd.id, chunk.nr, repDegree, 4), 1, TimeUnit.SECONDS);
        }

        return "File backed up";

        /**
         * TODO enhancement
         *
         * Enhancement: This scheme can deplete the backup space rather rapidly, and
         * cause too much activity on the nodes once that space is full. Can you think
         * of an alternative scheme that ensures the desired replication degree, avoids
         * these problems, and, nevertheless, can interoperate with peers that execute
         * the chunk backup protocol described above?
         */
    }

    @Override
    public String restore(String filePath) {
        //Discover the fileId
        String fileId = storage.getFileId(filePath);

        if (fileId == null) {
            return "File not found";
        }

        //Restore the file
        FileData fd = storage.getFile(fileId);
        storage.restore(fileId);
        HashSet<ChunkData> set = new HashSet<>(fd.chunks);

        if (fd != null) {
            for (ChunkData chunk : set) {
                Message message = new Message(version, "GETCHUNK", Integer.toString(peerId), fd.id, Integer.toString(chunk.nr), null);
                mcControl.sendMessage(message);
                System.out.println("System sent GETCHUNK message." + chunk.fileId + "_" + chunk.nr + "," + chunk.size);
            }
            executer.schedule(new FileBuilder(storage, fileId, fd.file.getPath(), fd.chunks.size()), 10, TimeUnit.SECONDS);
        }

        return "Restoring file...";
    }

    @Override
    public String delete(String filePath) {
        //Discover the fileId
        String fileId = storage.getFileId(filePath);

        if (fileId == null) {
            return "File not found";
        }

        //Delete the file
        Message message = new Message(version, "DELETE", Integer.toString(peerId), fileId, null, null);
        mcControl.sendMessage(message);
        System.out.println("System sent DELETE message.");

        //Delete the file locally
        storage.deleteFile(fileId);

        return "Deleting file...";

        /**
         * TODO enhancement
         *
         * Enhancement: If a peer that backs up some chunks of the file is not running
         * at the time the initiator peer sends a DELETE message for that file, the
         * space used by these chunks will never be reclaimed. Can you think of a change
         * to the protocol, possibly including additional messages, that would allow to
         * reclaim storage space even in that event?
         */

    }

    @Override
    public String reclaim(int finalSize) {
        // Special case to remove all chunks
        if (finalSize == 0) {
            ArrayList<String> list = storage.reclaim();
            for (String id : list) {
                Message message = new Message(version, "REMOVED", Integer.toString(peerId), id.substring(0, 64), id.substring(64), null);
                mcControl.sendMessage(message);
                storage.removeChunk(id);
                System.out.println("System sent REMOVED message.");
            }
            storage.updateMax(finalSize);
            return "Cleared all Space";
        }

        // If final size bigger than current, just update
        if (storage.getMaxSpace() < finalSize) {
            storage.updateMax(finalSize);
        }
        else { // else remove chunks until condition met
            if (storage.getUsedSpace() <= finalSize) {
                storage.updateMax(finalSize);
            }
            else {
                ArrayList<String> list = storage.reclaim();

                for (String id : list) {
                    Message message = new Message(version, "REMOVED", Integer.toString(peerId), id.substring(0, 64),
                            id.substring(64), null);
                    mcControl.sendMessage(message);
                    storage.removeChunk(id);
                    System.out.println("System sent REMOVED message.");
                    if (storage.getUsedSpace() <= finalSize)
                        break;
                }
                storage.updateMax(finalSize);
            }
        }

        return "Reclaimed Space";
    }

    @Override
    public String state() {
        StringBuilder builder = new StringBuilder();

        builder.append("Backed up files.\n");
        builder.append("===================================\n");
        for(Map.Entry<String, FileData> e : storage.getFiles().entrySet()) {
            FileData fd = e.getValue();
            builder.append("File id:                \t").append(fd.id).append("\n");
            builder.append("File path:              \t").append(fd.file.getPath()).append("\n");
            builder.append("File replication degree:\t").append(fd.replicationDegree).append("\n");
            builder.append("Chunks:");

            for(ChunkData cd : fd.chunks) {
                builder.append("\tChunk nr:                         \t").append(cd.nr).append("\n");
                builder.append("\tChunk replication degree:         \t").append(storage.getReplication(fd.id, cd.nr)).append("\n");
            }

            builder.append("===================================\n");
        }

        builder.append("\n");

        builder.append("Stored chunks.\n");
        builder.append("===================================\n");
        for(Map.Entry<String, ChunkData> e : storage.getChunks().entrySet()) {
            ChunkData cd = e.getValue();
            builder.append("Chunk nr:                         \t").append(cd.nr).append("\n");
            builder.append("Chunk replication degree:         \t").append(storage.getReplication(cd.fileId, cd.nr)).append("\n");
            builder.append("===================================\n");
        }

        return builder.toString();
    }

    @Override
    public String serialize() {
        LocalStorage.serialize(storage);
        return "done";
    }

    /**
     * Store chunk in peer and send STORED message
     *
     * @param fileId file id
     * @param nr     chunk number
     * @param chunk  chunk content
     * @param size   size of chunk
     */
    public void store(String fileId, int nr, byte[] chunk, int size, int desiredReplication) {
        if(storage.getRecentRemoved(fileId+nr)) {
            storage.cancelChunkBackup(fileId, nr);
            System.out.println("Recently removed chunks is being backed up by another peer.");
        }


        // If chunk is not stored, then store it
        if (storage.getChunk(fileId, nr) == null) {

            // The chunk is not present -> store it
            ChunkData cd = new ChunkData(fileId, nr, chunk, size);
            storage.store(cd, desiredReplication);
            System.out.println("Peer stored chunk.");


            Random rand = new Random();
            int delay = rand.nextInt(401);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                System.out.println("Sleep failed");
            }
        }

        // Send message to confirm chunk is stored
        Message message = new Message(version, "STORED", Integer.toString(peerId), fileId, Integer.toString(nr), null);
        mcControl.sendMessage(message);
        System.out.println("System sent STORED message.");
    }

    /**
     * Get chunk from storage
     *
     * @param fileId file id
     * @param nr     chunk nr
     */
    public void getChunk(String fileId, int nr) {
        ChunkData chunk = storage.getChunk(fileId, nr);

        // This peer has the chunk
        if (chunk != null) {
            Message message = new Message(version, "CHUNK", Integer.toString(peerId), chunk.fileId,
                    Integer.toString(chunk.nr), null, chunk.chunk);

            mcRestore.sendMessage(message);
            System.out.println("System sent CHUNK message.");
        }

        /**
         * TODO enhancement
         *
         * Enhancement: If chunks are large, this protocol may not be desirable: only
         * one peer needs to receive the chunk, but we are using a multicast channel for
         * sending the chunk. Can you think of a change to the protocol that would
         * eliminate this problem, and yet interoperate with non-initiator peers that
         * implement the protocol described in this section? Your enhancement must use
         * TCP to get full credit.
         *
         * TRANSLATION Enhancement: Use TCP to send chunks
         */
    }

    /**
     * Delete all chunks of file
     *
     * @param fileId
     */
    public void deleteChunks(String fileId) {
        storage.delete(fileId);
        System.out.println("Peer deleted file.");
    }

    /**
     * Respond to REMOVED message
     *
     * @param fileId
     * @param nr
     */
    public void removed(String fileId, int nr) {
        /**
         * TODO when removed is received repeat backup
         *
         * Upon receiving this message, a peer that has a local copy of the chunk shall
         * update its local count of this chunk. If this count drops below the desired
         * replication degree of that chunk, it shall initiate the chunk backup
         * subprotocol after a random delay uniformly distributed between 0 and 400 ms.
         * If during this delay, a peer receives a PUTCHUNK message for the same file
         * chunk, it should back off and restrain from starting yet another backup
         * subprotocol for that file chunk.
         */

        // If peer has chunk, wait random interval
        ChunkData chunk;
        if((chunk = storage.getChunk(fileId, nr)) != null) {
            storage.addRecentRemoved(fileId, nr);

            Random rand = new Random();
            int delay = rand.nextInt(401);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                System.out.println("Sleep failed");
            }

            // If backup not initiated yet, do it
            if(!storage.getRecentPutchunk(fileId+nr)) {
                // TODO os proximos todo é porque temos de ir buscar o desired replication
                // TODO ---------------------------------------------------------------------------------------------------------------\/\/\/\/ WTF
                Message message = new Message(version, "PUTCHUNK", Integer.toString(peerId), fileId, Integer.toString(nr), Integer.toString(5), chunk.chunk);
                mcBackup.sendMessage(message);
            System.out.println("System sent PUTCHUNK message.");
            // TODO ----------------------------------------------------------------------------\/\/\/\/ WTF no "5"
                executer.schedule(new PutchunkRepeater(storage, mcBackup, message, 1, fileId, chunk.nr, 5, 4), 1, TimeUnit.SECONDS);
            }

            storage.clearRemoved(fileId+nr);
        }
    }

    /**
     * Store chunk for restoring
     *
     * @param fileId
     * @param nr
     * @param chunk
     */
    public void saveChunk(String fileId, int nr, byte[] chunk) {
        storage.saveChunk(new ChunkData(fileId, nr, chunk, chunk.length));
        System.out.println("Peer received new chunk for restoring.");
    }

    /**
     * Store that peer has stored given chunk
     *
     * @param fileId file id
     * @param chunkNo chunk number
     * @param peerId peer
     */
    public void recordPeerStoredChunk(String fileId, int chunkNo, String peerId) {
        this.storage.recordStore(fileId, chunkNo, peerId);
    }

    /**
     * Check if chunk is stored in given peer
     *
     * @param fileId file id
     * @param chunkNo chunk number
     * @param peerId peer
     * @return
     */
    public boolean testPeerStoredChunk(String fileId, int chunkNo, String peerId) {
        return storage.testStoredChunkInPeer(fileId, chunkNo, peerId);
    }

    /**
     * Tests if a file was stored using this peer as the initiator peer
     *
     * @param fileId
     * @return
     */
    public boolean testFilePresent(String fileId) {
        return this.storage.testFilePresent(fileId);
    }

    /**
     * Remove all chunks with higher replication than desired
     */
    public void cleanup() {
        // Get list of all unneeded chunks with actual replication above desired
        // replication
        ArrayList<String> list = storage.reclaim(0);
        for (String id : list) {
            Message message = new Message(version, "REMOVED", Integer.toString(peerId), id.substring(0, 64),
                    id.substring(64), null);
            mcControl.sendMessage(message);
            System.out.println("System sent REMOVED message.");
            storage.removeChunk(id);
        }
    }
}
