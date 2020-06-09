package cl.cym.testserial;

public class ByteStream {

    private byte[] stream;
    private String status;
    private boolean isProcessed;
    private long creationTS;
    private long processingTS;
    private String type;

    public ByteStream(byte[] stream, String type){

        this.stream = stream;
        this.creationTS = System.currentTimeMillis();
        this.isProcessed = false;
        this.status = "received";
        this.type = type;

    }

    public byte[] getStream() {
        return stream;
    }

    public void setStream(byte[] stream) {
        this.stream = stream;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public void setProcessed(boolean processed) {
        isProcessed = processed;
    }

    public long getCreationTS() {
        return creationTS;
    }

    public void setCreationTS(long creationTS) {
        this.creationTS = creationTS;
    }

    public long getProcessingTS() {
        return processingTS;
    }

    public void setProcessingTS(long processingTS) {
        this.processingTS = processingTS;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString(){

        return ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(this.stream));
    }
}
