import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The interface between the test application and the peers Nota: todos os
 * metodos têm de dar throw a RemoteException because RMI
 */
public interface PeerInterface extends Remote {
    /**
     * Backup file from peer in other peers by sending PUTCHUNK message for each
     * chunk of file.
     *
     * @param path      path of the file to backup
     * @param repDegree desired replication degree for each chunk
     * @throws RemoteException
     */
    String backup(String path, int repDegree) throws RemoteException;

    /**
     * Restore file backed up in the other peers.
     *
     * @param filePath file path
     */
    String restore(String filePath) throws RemoteException;

    /**
     * Delete all chunks of a file backed up.
     *
     * @param filePath file path
     */
    String delete(String filePath) throws RemoteException;

    /**
     * Reclaim disk space. Set space used by system to finalSize.
     *
     * @param finalSize size in KB
     */
    String reclaim(int finalSize) throws RemoteException;

    /**
     * Print in console state of the current peer.
     *
     */
    String state() throws RemoteException;

    /**
     * Serialize storage
     */
    String serialize() throws RemoteException;
}
