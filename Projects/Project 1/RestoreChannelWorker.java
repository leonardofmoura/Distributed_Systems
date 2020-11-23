public class RestoreChannelWorker extends ChannelWorker {
    public RestoreChannelWorker(PeerService peer, byte[] msg, int msgLen) {
        super(peer, msg, msgLen);
    }

    @Override
    public void run() {
        try {
            Message msg = Message.parseMessage(this.msg,msgLen);

            if (Integer.parseInt(msg.getSenderId()) == peer.peerId) {
                return;
            }

            System.out.println("Restore Channel: " + msg.getHeader());

            switch (msg.getMessageType()) {
                case "CHUNK":
                    parseChunk(msg);
                    break;
            }
        }
        catch (MessageParseException e) {
            e.printStackTrace();
            e.printInfo();
        }
    }

    private void parseChunk(Message msg) {
        System.out.println("System received CHUNK message.");
        peer.saveChunk(msg.getFileId(), Integer.parseInt(msg.getChunckNo()), msg.getBody());
    }
}
