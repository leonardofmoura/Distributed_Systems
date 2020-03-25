import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DnsService extends Remote {
    /**
     * lookup a dns on the server
     * @param dns the dns to lookup
     * @return the ip address found or null if no ip was found
     * @throws RemoteException
     */
    String lookup(String dns) throws RemoteException;

    /**
     * register a new dns on the server
     * @param dns the dns to register
     * @param ipAddr the ip to register the dns to
     * @return the number of names already registered to that ip or -1 if the dns is already taken
     * @throws RemoteException
     */
    int register(String dns, String ipAddr) throws RemoteException;
}
