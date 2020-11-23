import java.net.DatagramPacket;

/**
 * MulticastRestore
 */
public class MulticastRestore extends MulticastChannel {

  public MulticastRestore(PeerService peer, String inet, int port) {
    super(peer, inet, port, "Restore Channel");
  }

  @Override
  protected void parseMessage(DatagramPacket packet) {
    RestoreChannelWorker worker = (RestoreChannelWorker) ChannelWorkerFactory.createWorker(packet.getData(),packet.getLength(), super.peer, ChannelWorkerFactory.WorkerType.RESTORE);
    super.executor.execute(worker);
  }
}
