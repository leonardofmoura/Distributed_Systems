import java.net.DatagramPacket;

/**
 * MulticastControl
 */
public class MulticastControl extends MulticastChannel {
  public MulticastControl(PeerService peer, String inet, int port) {
    super(peer, inet, port, "Control Channel");
  }

  @Override
  protected void parseMessage(DatagramPacket packet) {
    ControlChannelWorker worker = (ControlChannelWorker) ChannelWorkerFactory.createWorker(packet.getData(),packet.getLength(),super.peer, ChannelWorkerFactory.WorkerType.CONTROL);
    super.executor.execute(worker);
  }
}
