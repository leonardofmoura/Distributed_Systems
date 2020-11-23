import java.util.concurrent.TimeUnit;

public class PutchunkRepeater implements Runnable {
  final private LocalStorage storage;
  final private MulticastBackup multicast;
  final private Message message;
  final private String fileId;
  final private int nr;
  final private int repDegree;
  final private int timesToRepeat;
  private int delay;
  private int timesRun;

  public PutchunkRepeater(LocalStorage storage, MulticastBackup multicast, Message message, int delay, String fileId, int nr, int repDegree, int timesToRepeat) {
    this.storage = storage;
    this.multicast = multicast;
    this.message = message;
    this.delay = delay;
    this.fileId = fileId;
    this.nr = nr;
    this.repDegree = repDegree;
    this.timesToRepeat = timesToRepeat;
    this.timesRun = 0;
  }

  @Override
  public void run() {
    int currentRepDegree = storage.getReplication(fileId, nr);
    if(currentRepDegree < repDegree) {
      multicast.sendMessage(message);
      System.out.println("System repeated PUTCHUNK message.");

      timesRun += 1;
      delay *= 2;
      if(timesRun < timesToRepeat) {
        PeerService.executer.schedule(this, delay, TimeUnit.SECONDS);
      }
    }
  }
}
