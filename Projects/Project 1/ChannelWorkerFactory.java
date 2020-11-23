/**
 * Creates a new worker. Returns null for invalid type
 */
public class ChannelWorkerFactory {
    public enum  WorkerType {BACKUP,CONTROL,RESTORE};

    static public ChannelWorker createWorker(byte[] msg, int msgLen, PeerService peer, WorkerType type) {
        switch (type) {
            case BACKUP:
                return new BackupChannelWorker(peer, msg, msgLen);

            case CONTROL:
                return new ControlChannelWorker(peer, msg, msgLen);

            case RESTORE:
                return new RestoreChannelWorker(peer, msg, msgLen);

            default:
                return null;
        }
    }
}
