package cl.cym.testserial;

import java.io.IOException;

public class SerialReader implements Runnable {

    private static SerialComms serialComms;
    private byte[] readBuffer;
    private byte[] finalRead;
    private int noReadLoopCount;
    private int maxReadTimeout;

    public SerialReader(SerialComms serialComms, int maxReadTimeout){

        this.serialComms = serialComms;
        this.maxReadTimeout = maxReadTimeout;
        this.noReadLoopCount = 0;
        this.readBuffer = new byte[10240];
        this.finalRead = new byte[3];
        this.finalRead[0] = 127;
        this.finalRead[1] = 127;
        this.finalRead[2] = 127;
    }

    @Override
    public void run(){

        boolean isReading = true;
        SerialComms.setReading(isReading);

        long readingStartTS = System.currentTimeMillis();
        this.noReadLoopCount = 0;

        while(isReading){

            try {
                int len = this.serialComms.getCurrentConnection().read(this.readBuffer, SerialComms.getReadWaitMillis());

                byte[] readFragment;

                if(len > 0){

                    readFragment = new byte[len];

                    for(int index = 0; index < len; index++){

                        readFragment[index] = this.readBuffer[index];
                    }

                    //check final read for default values
                    boolean defaultValues = true;

                    for(int index = 0; index < this.finalRead.length; index++){

                        if(this.finalRead[index] != 127){

                            defaultValues = false;
                        }
                    }

                    if(defaultValues){

                        this.finalRead = readFragment;

                    }else{

                        byte[] tempFinalRead = ByteHandleUtils.combineByteArray(this.finalRead, readFragment);

                        this.finalRead = tempFinalRead;
                    }

                }else{

                    this.noReadLoopCount++;
                }

                this.readBuffer = new byte[10240];

                if(System.currentTimeMillis() - readingStartTS >= this.maxReadTimeout || this.noReadLoopCount >= 3){

                    isReading = false;

                    synchronized (serialComms){

                        serialComms.setReading(isReading);
                    }

                    /*remove 189 last byte
                    if(ByteHandleUtils.byteToInt(this.finalRead[this.finalRead.length-1]) == 189){

                        byte[] tempStream = new byte[this.finalRead.length-1];

                        for(int index = 0; index < tempStream.length; index++){

                            tempStream[index] = this.finalRead[index];
                        }

                        this.finalRead = tempStream;
                    }*/

                    synchronized (serialComms){
                        SerialComms.queueStream(new ByteStream(this.finalRead), "processing");
                    }


                    //this.getSerialComms().addStreamToList(ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(this.finalRead)));
                }

            } catch (IOException e) {
                e.printStackTrace();
                isReading = false;
                SerialComms.setReading(isReading);
            }
        }
    }

    public byte[] getReadBuffer() {
        return readBuffer;
    }

    public void setReadBuffer(byte[] readBuffer) {
        this.readBuffer = readBuffer;
    }

    public byte[] getFinalRead() {
        return finalRead;
    }

    public void setFinalRead(byte[] finalRead) {
        this.finalRead = finalRead;
    }

    public SerialComms getSerialComms() {
        return serialComms;
    }

    public void setSerialComms(SerialComms serialComms) {
        this.serialComms = serialComms;
    }

    public int getNoReadLoopCount() {
        return noReadLoopCount;
    }

    public void setNoReadLoopCount(int noReadLoopCount) {
        this.noReadLoopCount = noReadLoopCount;
    }

    public int getMaxReadTimeout() {
        return maxReadTimeout;
    }

    public void setMaxReadTimeout(int maxReadTimeout) {
        this.maxReadTimeout = maxReadTimeout;
    }
}
