package cl.cym.testserial;

import android.util.Log;

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
            synchronized (SerialComms.getInstance()){

                Log.v("SerialWriter", "about to send: "+ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(this.byteStream.getStream())));

                SerialComms.serialWrite(this.byteStream.getStream());

                SerialComms.saveSentStream(this.byteStream);

                SerialComms.setWriting(false);
            }


        } catch (IOException e) {
            e.printStackTrace();

            synchronized (SerialComms.getInstance()){

                SerialComms.setWriting(false);
            }
        }
    }

    public SerialComms getSerialComms() {
        return serialComms;
    }

    public void setSerialComms(SerialComms serialComms) {
        this.serialComms = serialComms;
    }
}
