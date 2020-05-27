package cl.cym.testserial;

import java.io.IOException;

public class SerialPoller implements Runnable {

    private MainActivity mainActivity;

    public SerialPoller(MainActivity mainActivity){

        this.mainActivity = mainActivity;
    }

    @Override
    public void run(){

        while(!Thread.interrupted()){

                try {
                    this.mainActivity.getCurrentConnection().purgeHwBuffers(true, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                byte[] poll = new byte[1];
                poll[0] = this.mainActivity.getByteUtils().intToByte(80);

                byte[] formattedStream = this.mainActivity.formatStream(poll, false);

                try {
                    this.mainActivity.serialWrite(formattedStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                this.mainActivity.setPollStartAllowed(false);

            try {
                Thread.sleep(this.mainActivity.getWriteWaitMillis()+this.mainActivity.getReadWaitMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
