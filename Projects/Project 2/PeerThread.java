public class PeerThread implements Runnable {
  public Peer peer;

  public PeerThread(Peer peer) {
    this.peer = peer;
  }

  @Override
  public void run() {
    SSLEngineServer server = null;

    try {
      server = new SSLEngineServer("server","123456",Peer.portNumber);
    } catch (SSLManagerException e) {
      e.printStackTrace();
    }
    try {
      while (true) {
        // waits for a connection to occur and creates a Message Processor task and
        // gives it to ThreadPool
        SSLServerInterface serverInterface = server.accept();
        Runnable task = new MessageProcessor(serverInterface);
        Peer.pool.execute(task);
      }
    } catch (SSLManagerException e) {
      e.printStackTrace();
    }
  }
}
