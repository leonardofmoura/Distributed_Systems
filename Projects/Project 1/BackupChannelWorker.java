public class BackupChannelWorker extends ChannelWorker {
    public BackupChannelWorker(PeerService peer, byte[] msg, int msgLen) {
        super(peer, msg, msgLen);
    }

    @Override
    public void run() {
        try {
            Message msg = Message.parseMessage(super.msg,msgLen);

            if (Integer.parseInt(msg.getSenderId()) == peer.peerId) {
                return;
            }

            System.out.println("Backup Channel: " + msg.getHeader());
            switch (msg.getMessageType()) {
                case "PUTCHUNK":
                    parsePutchunk(msg);
                    break;
            }
        }
        catch (MessageParseException e) {
            e.printStackTrace();
            e.printInfo();
        }
    }

    private void parsePutchunk(Message msg) {
        System.out.println("System received PUTCHUNK message.");
        if(Integer.parseInt(msg.getSenderId()) != peer.peerId)
            peer.store(msg.getFileId(), Integer.parseInt(msg.getChunckNo()), msg.getBody(), msg.getBody().length,Integer.parseInt(msg.getReplicationDeg()));
    }
}
