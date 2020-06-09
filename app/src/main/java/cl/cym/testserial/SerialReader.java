package cl.cym.testserial;

import android.util.Log;

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
        /*synchronized (SerialComms.getInstance()){

            SerialComms.setReading(true);
        }*/

        long readingStartTS = System.currentTimeMillis();
        this.noReadLoopCount = 0;

        //while(isReading){

            try {

                int len = 0;

                synchronized (SerialComms.getInstance()){
                    len = SerialComms.getCurrentConnection().read(this.readBuffer, 0);
                }

                if(len > 0){

                    this.finalRead = new byte[len];

                    for(int index = 0; index < len; index++){

                        this.finalRead[index] = this.readBuffer[index];
                    }

                    synchronized (SerialComms.getInstance()){
                        Log.v("SerialReader", "about the queue: "+ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(this.finalRead)));
                        SerialComms.queueStream(new ByteStream(this.finalRead, "read"), "processing");
                        SerialComms.setReading(false);
                    }

                }else{

                    synchronized (SerialComms.getInstance()){
                        SerialComms.setReading(false);
                    }
                }

               /*byte[] readFragment;

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

                    if(this.finalRead.length > 3){

                        synchronized (SerialComms.getInstance()){

                            SerialComms.queueStream(new ByteStream(this.finalRead), "processing");
                            SerialComms.setReading(false);
                        }

                    }else{

                        boolean defaultValues = true;

                        for(int index = 0; index < this.finalRead.length; index++){

                            if(this.finalRead[index] != 127){

                                defaultValues = false;
                            }
                        }

                        if(!defaultValues){

                            synchronized (SerialComms.getInstance()){

                                SerialComms.queueStream(new ByteStream(this.finalRead), "processing");
                                SerialComms.setReading(isReading);
                            }
                        }
                    }

                }*/

            } catch (IOException e) {
                e.printStackTrace();
                isReading = false;
                synchronized (SerialComms.getInstance()){
                    SerialComms.setReading(false);
                }
            }
        //}
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
