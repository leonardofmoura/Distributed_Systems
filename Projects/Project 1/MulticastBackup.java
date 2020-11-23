import java.net.DatagramPacket;

/**
 * MulticastControl
 */
public class MulticastBackup extends MulticastChannel {
  public MulticastBackup(PeerService peer, String inet, int port) {
    super(peer, inet,port,"Backup channel");
  }

  @Override
  protected void parseMessage(DatagramPacket packet) {
    BackupChannelWorker worker = (BackupChannelWorker) ChannelWorkerFactory.createWorker(packet.getData(),packet.getLength()
            ,super.peer,ChannelWorkerFactory.WorkerType.BACKUP);
    super.executor.execute(worker);
  }
}
