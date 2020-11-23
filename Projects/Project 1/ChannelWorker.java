public abstract class ChannelWorker implements Runnable{
    protected byte[] msg;
    protected PeerService peer;
    protected int msgLen;

    public ChannelWorker(PeerService peer,byte[] msg, int msgLen) {
        this.msg = msg;
        this.peer = peer;
        this.msgLen = msgLen;
    }
}
