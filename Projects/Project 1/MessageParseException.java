public class MessageParseException extends Exception {
    private static final long serialVersionUID = 1L;
    private String type;

    MessageParseException(String type) {
        super();
        this.type = type;
    }

    public void printInfo() {
        System.err.println("MessageParseExcpetion: " + this.type);
    }
}
