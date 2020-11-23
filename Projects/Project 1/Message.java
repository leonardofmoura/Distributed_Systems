import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Message {
    private String version;
    private String messageType;
    private String senderId;
    private String fileId;
    private String chunckNo;
    private String replicationDeg;
    private byte[] body;

    private String header;

    static String CRLF = "\r\n\r\n";
    static byte CR = (byte) 0xD;
    static byte LF = (byte) 0xA;

    /**
     * Creates an empty header
     */
    public Message() {
        this.version = null;
        this.messageType = null;
        this.senderId = null;
        this.fileId = null;
        this.chunckNo = null;
        this. replicationDeg = null;
        this.header = "";
        this.body = null;
    }

    /**
     * Creates a new header with the respective parameters
     * @param version
     * @param messageType
     * @param senderId
     * @param fileId
     * @param chunckNo
     * @param replicationDeg
     */
    public Message(String version, String messageType, String senderId, String fileId, String chunckNo, String replicationDeg) {
        this.version = version;
        this.messageType = messageType;
        this.senderId = senderId;
        this.fileId = fileId;
        this.chunckNo = chunckNo;
        this.replicationDeg = replicationDeg;
        this.buildHeader();
    }

    /**
     * Creates a new header with the respective parameters
     * @param version
     * @param messageType
     * @param senderId
     * @param fileId
     * @param chunckNo
     * @param replicationDeg
     * @param body
     */
    public Message(String version, String messageType, String senderId, String fileId, String chunckNo, String replicationDeg, byte[] body) {
        this.version = version;
        this.messageType = messageType;
        this.senderId = senderId;
        this.fileId = fileId;
        this.chunckNo = chunckNo;
        this.replicationDeg = replicationDeg;
        this.body = body;

        this.buildHeader();
    }

    private void addStringToHeader(String str) {
        if (str == null) {
            return;
        }

        if (this.header.isEmpty()) {
            this.header += str;
        }
        else {
            this.header += " " + str;
        }
    }

    /**
     * Builds the header
     */
    private void buildHeader() {
        this.header = "";

        this.addStringToHeader(this.version);
        this.addStringToHeader(this.messageType);
        this.addStringToHeader(this.senderId);
        this.addStringToHeader(this.fileId);
        this.addStringToHeader(this.chunckNo);
        this.addStringToHeader(this.replicationDeg);
        this.addStringToHeader(CRLF);
    }

    public byte[] getBody() {
        return body;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Set the version and update the header
     * @param version
     */
    public void setVersion(String version) {
        this.version = version;
        this.buildHeader();
    }

    public String getMessageType() {
        return messageType;
    }

    /**
     * Set the message type and update the header
     * @param messageType
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
        this.buildHeader();
    }

    public String getSenderId() {
        return senderId;
    }

    /**
     * Set the sender id and update the header
     * @param senderId
     */
    public void setSenderId(String senderId) {
        this.senderId = senderId;
        this.buildHeader();
    }

    public String getFileId() {
        return fileId;
    }

    /**
     * Set the field id and update the header
     * @param fileId
     */
    public void setFileId(String fileId) {
        this.fileId = fileId;
        this.buildHeader();
    }

    public String getChunckNo() {
        return chunckNo;
    }

    /**
     * Set the chunk number and update the header
     * @param chunckNo
     */
    public void setChunckNo(String chunckNo) {
        this.chunckNo = chunckNo;
        this.buildHeader();
    }

    public String getReplicationDeg() {
        return replicationDeg;
    }

    /**
     * Set a new replication degree and update the header
     * @param replicationDeg
     */
    public void setReplicationDeg(String replicationDeg) {
        this.replicationDeg = replicationDeg;
        this.buildHeader();
    }

    /**
     * Method used to get the header string
     * @return the header string
     */
    public String getHeader() {
        return this.header;
    }


    /**
     * Returns the message as a byte array
     * @return
     */
    public byte[] getBytes() {
        if (this.body != null) {
            byte[] headerUtf8 = new String(this.header.getBytes(), StandardCharsets.UTF_8).getBytes();
            byte[] ret = new byte[headerUtf8.length + this.body.length];

            System.arraycopy(headerUtf8,0,ret,0,headerUtf8.length);
            System.arraycopy(this.body,0,ret,headerUtf8.length,this.body.length);
            return ret;
        }
        else {
            return new String(this.header.getBytes(), StandardCharsets.UTF_8).getBytes();
        }
    }


    // =============== MESSAGE PARSING =======================

    /**
     * Parses the putchunk message
     * @param msg token string array
     * @return
     * @throws MessageParseException
     */
    static private Message parsePutChunk(String[] msg, byte[] body) throws MessageParseException {
        if (msg.length != 6) {
            throw new MessageParseException("Invalid PUTCHUNK message");
        }

        return new Message(msg[0],msg[1],msg[2],msg[3],msg[4],msg[5],body);
    }

    /**
     * Parses Stored, getchunk and removed message types
     * @param msg token string array
     * @return
     * @throws MessageParseException
     */
    static private Message parseSimpleMessage(String[] msg) throws MessageParseException {
        if (msg.length != 5) {
            switch (msg[1]) {
                case "STORED":
                    throw new MessageParseException("Invalid STORED message");

                case "GETCHUNK":
                    throw  new MessageParseException("Invalid Getchunk message");

                case "REMOVED":
                    throw  new MessageParseException("Invalid REMOVED message");

                default:
                    throw  new MessageParseException("Invalid simple message");
            }
        }

        return new Message(msg[0],msg[1],msg[2],msg[3],msg[4],null);
    }

    static private Message parseChunk(String[] msg, byte[] body) throws MessageParseException {
        if (msg.length != 5) {
            throw new MessageParseException("Invalid CHUNK message");
        }

        return new Message(msg[0],msg[1],msg[2],msg[3],msg[4],null,body);
    }

    static private Message parseDelete(String[] msg) throws MessageParseException {
        if (msg.length != 4) {
            throw new MessageParseException("Invalid DELETE message");
        }

        return new Message(msg[0],msg[1],msg[2],msg[3],null,null);
    }

    static private int findFlagIndex(byte[] message) {
        for (int i = 0; i < message.length; i++) {
            if (message[i] == CR && message[i+1] == LF && message[i+2] == CR && message[i+3] == LF) {
                return i;
            }
        }

        return -1;
    }

    static Message parseMessage(byte[] message, int msgLen) throws MessageParseException {
        int flagIndex = findFlagIndex(message);

        if (flagIndex == -1) {
            throw new MessageParseException("Flag not found");
        }

        byte[] header = Arrays.copyOfRange(message,0,flagIndex-1);
        byte[] body = null;

        if (flagIndex + 4 < msgLen) {
            body = Arrays.copyOfRange(message,flagIndex+4,msgLen);
        }

        String[] tokens = new String(header, StandardCharsets.UTF_8).split(" ");

        switch (tokens[1]) {
            case "PUTCHUNK":
                return parsePutChunk(tokens,body);

            case "REMOVED":
            case "STORED":
            case "GETCHUNK":
                return parseSimpleMessage(tokens);

            case "CHUNK":
                return parseChunk(tokens,body);

            case "DELETE":
                return parseDelete(tokens);

            default:
                throw new MessageParseException("Invalid message");
        }
    }
}
