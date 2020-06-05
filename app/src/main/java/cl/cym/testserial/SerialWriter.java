package cl.cym.testserial;

import java.io.IOException;

public class SerialWriter implements Runnable {

    private SerialComms serialComms;
    private ByteStream byteStream;

    public SerialWriter(SerialComms serialComms, ByteStream byteStream){

        this.serialComms = serialComms;
        this.byteStream = byteStream;
    }

    @Override
    public void run(){

        try {
            synchronized (serialComms){
                SerialComms.serialWrite(this.byteStream.getStream());

                SerialComms.saveSentStream(this.byteStream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SerialComms getSerialComms() {
        return serialComms;
    }

    public void setSerialComms(SerialComms serialComms) {
        this.serialComms = serialComms;
    }
}
