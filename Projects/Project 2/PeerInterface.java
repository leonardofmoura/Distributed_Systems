import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Peer Interface for RMI
 */
public interface PeerInterface extends Remote {
  public void backup(String path, int repDegree) throws RemoteException;

  public void restore(String path) throws RemoteException;

  public void delete(String path) throws RemoteException;

  public void spaceReclaim(long newMaxStorage) throws RemoteException, IOException;

  public void printState() throws RemoteException;
}
