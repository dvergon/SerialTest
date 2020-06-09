package cl.cym.testserial;

import android.util.Log;

import java.io.IOException;

public class SerialWriter implements Runnable {

    private SerialComms serialComms;
    private ByteStream byteStream;

    public SerialWriter(SerialComms serialComms, ByteStream byteStream) {

        this.serialComms = serialComms;
        this.byteStream = byteStream;
    }

    @Override
    public void run() {
        synchronized (SerialComms.getInstance()) {
            try {

                Log.v("SerialWriter", "about to send: " + ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(this.byteStream.getStream())));

                switch (this.byteStream.getType()){
                    case "poll":
                        SerialComms.serialWrite(this.byteStream.getStream());

                        SerialComms.saveSentStream(this.byteStream);

                        SerialComms.setWriting(false);
                        break;

                    case "command":

                        SerialComms.serialWrite(this.byteStream.getStream());

                        SerialComms.saveSentStream(this.byteStream);

                        SerialComms.setWriting(false);

                        SerialComms.setLastCommandWriteTS(System.currentTimeMillis());

                        SerialComms.setWaitingCommandResponse(true);

                        break;

                    default:
                        break;
                }

            } catch (IOException e) {
                e.printStackTrace();
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
