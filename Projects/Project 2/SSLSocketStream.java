public class SSLSocketStream implements java.lang.AutoCloseable {
  SSLClientInterface socket;

  /**
   * SSLSocket to read from and write to.
   */
  public SSLSocketStream(String ip, int port) throws SSLManagerException {
    socket = new SSLClientInterface("client","123456",ip,port);
    socket.handshake();
  }

  public void write(byte[] b) throws SSLManagerException {
    socket.write(b);
  }

  public int read(byte[] buf) throws SSLManagerException {
    return socket.read(buf);
  }

  public String readLine() throws SSLManagerException {
    return socket.readln();
  }

  public void close() {
    //socket.close();
  }
}
