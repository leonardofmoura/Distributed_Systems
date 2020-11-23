public class ControlChannelWorker extends ChannelWorker {
    public ControlChannelWorker(PeerService peer, byte[] msg, int msgLen) {
        super(peer, msg, msgLen);
    }

    @Override
    public void run() {
        try {
            Message msg = Message.parseMessage(super.msg,msgLen);

            if (Integer.parseInt(msg.getSenderId()) == peer.peerId) {
                return;
            }

            //System.out.println("Control Channel: " + msg.getHeader());

            switch (msg.getMessageType()) {
                case "STORED":
                    parseStored(msg);
                    break;
                case "GETCHUNK":
                    parseGetchunk(msg);
                    break;
                case "DELETE":
                    parseDelete(msg);
                    break;
                case "REMOVED":
                    parseRemoved(msg);
                    break;
            }
        }
        catch (MessageParseException e) {
            e.printStackTrace();
            e.printInfo();
        }
    }

    private void parseRemoved(Message msg) {
        System.out.println("System received REMOVED message.");
        peer.removed(msg.getFileId(), Integer.parseInt(msg.getChunckNo()));
    }

    private void parseStored(Message msg) {
        System.out.println("System received STORED message.");
        synchronized (peer) {
            if (!peer.testPeerStoredChunk(msg.getFileId(),Integer.parseInt(msg.getChunckNo()),msg.getSenderId())) {
                peer.recordPeerStoredChunk(msg.getFileId(),Integer.parseInt(msg.getChunckNo()),msg.getSenderId());
            }
        }
    }

    private void parseGetchunk(Message msg) {
        System.out.println("System received GETCHUNK message.");
        peer.getChunk(msg.getFileId(), Integer.parseInt(msg.getChunckNo()));
    }

    private void parseDelete(Message msg) {
        System.out.println("System received DELETE message.");
        peer.deleteChunks(msg.getFileId());
    }
}
