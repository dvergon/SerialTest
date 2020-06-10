package cl.cym.testserial;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class SerialComms extends AppCompatActivity implements Runnable, SerialInputOutputManager.Listener {

    private static SerialComms serial;
    private static ByteStreamProcessor streamProcessor;
    private static MainActivity activityRef;
    private static final int WRITE_WAIT_MILLIS = 500;
    private static final int READ_WAIT_MILLIS = 1000;
    private static int baudRate = 57600;
    private static UsbSerialPort currentConnection;
    private static List<UsbSerialDriver> availableDrivers;
    private static UsbManager manager;
    private static SerialInputOutputManager usbIoManager;
    private static ConcurrentLinkedQueue<ByteStream> pendingProcessStreams;
    private static ConcurrentLinkedQueue<ByteStream> pendingWritingStreams;
    private static ConcurrentLinkedQueue<ByteStream> processedStreams;
    private static ConcurrentLinkedQueue<ByteStream> sentStreams;
    private static Thread paymentThread;
    private static Thread rechargeThread;
    private static Thread serialReadingThread;
    private static Thread serialWritingThread;
    private static Thread streamProcessorThread;
    private static volatile boolean connected;
    private static volatile boolean reading;
    private static volatile boolean writing;
    private static volatile boolean waitingCommandResponse;
    private static long lastReadTS;
    private static long lastWriteTS;
    private static long lastCommandWriteTS;

    public SerialComms(){

        SerialComms.reading = false;
        SerialComms.writing = false;
        SerialComms.connected = false;
        SerialComms.waitingCommandResponse = false;

        SerialComms.pendingProcessStreams = new ConcurrentLinkedQueue<ByteStream>();
        SerialComms.pendingWritingStreams = new ConcurrentLinkedQueue<ByteStream>();
        SerialComms.processedStreams = new ConcurrentLinkedQueue<ByteStream>();
        SerialComms.sentStreams = new ConcurrentLinkedQueue<ByteStream>();
    }

    public static synchronized SerialComms getInstance(){

        if(serial == null){

            serial = new SerialComms();
        }

        return serial;
    }

    @Override
    public void onNewData(byte[] data){

        synchronized (SerialComms.getInstance()){
            Log.v("SerialComms", "about to queue: "+ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(data)));
            SerialComms.queueStream(new ByteStream(data, "read"), "processing");
            SerialComms.setWriting(false);
            SerialComms.setReading(false);
            setLastReadTS(System.currentTimeMillis());
        }
    }

    @Override
    public void onRunError(Exception e){

        synchronized (SerialComms.getInstance()){

            SerialComms.setReading(false);
        }
    }

    @Override
    public void run(){

        while(!Thread.interrupted()){

            if(!SerialComms.isConnected()){

                appSerialConnect();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        SerialComms.activityRef.updateStatusText("Connected: "+ isConnected());
                    }
                });

                SerialComms.streamProcessorThread = new Thread(ByteStreamProcessor.getInstance(getInstance()));
                SerialComms.streamProcessorThread.start();
            }

            synchronized (getInstance()){

                //check if connected
                if(SerialComms.isConnected()){

                    //check if not reading
                    if(!SerialComms.isReading()){

                        //if not reading, check if there is something to write
                        if(SerialComms.pendingWritingStreams.size() > 0){
                            //if there is something to write, ask if already writing
                            if(!isWriting() && isWritingAvailable()){

                                //if writing is available, get item from queue and write
                                SerialComms.setWriting(true);
                                SerialComms.setReading(true);

                                ByteStream currentStream = SerialComms.pendingWritingStreams.poll();
                                Log.v("Serialcomms", "just polled pending writing: "+ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(currentStream.getStream())));


                                SerialComms.setSerialWritingThread(new Thread(new SerialWriter(SerialComms.getInstance(), currentStream)));
                                SerialComms.startWritingThread();
                                this.lastWriteTS = System.currentTimeMillis();

                            }
                        }else{
                            //if there is nothing to write, poll
                            if(!isWriting() && !isWaitingCommandResponse() && isWritingAvailable()){

                                SerialComms.setWriting(true);
                                SerialComms.setReading(true);

                                byte[] poll = new byte[1];
                                poll[0] = ByteHandleUtils.intToByte(80);

                                byte[] formattedStream = formatStream(poll, false);

                                ByteStream currentStream = new ByteStream(formattedStream, "poll");

                                try {
                                    SerialComms.serialWrite(formattedStream);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                this.lastWriteTS = System.currentTimeMillis();

                                //SerialComms.setSerialWritingThread(new Thread(new SerialWriter(SerialComms.getInstance(), currentStream)));
                                //SerialComms.startWritingThread();
                            }
                        }
                    }
                    //timeout for reading
                    if(System.currentTimeMillis() - getLastWriteTS() >= SerialComms.getReadWaitMillis()){

                        try {
                            SerialComms.purgeHwBuffers(true, false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        setReading(false);
                        setWriting(false);
                        setWaitingCommandResponse(false);

                        synchronized (ByteStreamProcessor.getInstance()){

                            ByteStreamProcessor.resetAllDeviceStatus();
                        }

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    //timeout for writing
                    /*if(System.currentTimeMillis() - getLastWriteTS() >= SerialComms.getWriteWaitMillis()){

                        try {
                            SerialComms.purgeHwBuffers(false, true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        setWriting(false);
                        setWaitingCommandResponse(false);
                    }*/
                }
            }
        }
    }

    public synchronized void addStreamToList(final String stream){

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                SerialComms.activityRef.addStreamToHistory(stream);
            }
        });
    }

    public static synchronized void purgeHwBuffers(boolean read, boolean write) throws IOException {

        SerialComms.currentConnection.purgeHwBuffers(write, read);
    }

    public synchronized void appSerialConnect(){

        //SerialComms.getAllDrivers();

        try {
            boolean connected = SerialComms.openSerial(0, SerialComms.baudRate);

            if(connected){

                SerialComms.setConnected(true);

            }else{

                SerialComms.setConnected(false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        usbIoManager = new SerialInputOutputManager(currentConnection, this);
        Executors.newSingleThreadExecutor().submit(usbIoManager);
    }

    //SERIAL HANDLER
    /*private void getAllDrivers(){

        SerialComms.manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        SerialComms.availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(SerialComms.manager);
    }*/

    private static boolean openSerial(int deviceIndex, int baudRate) throws IOException {

        boolean connected = false;

        if(SerialComms.availableDrivers.size() > 0){

            UsbSerialDriver driver = SerialComms.availableDrivers.get(deviceIndex);
            UsbDeviceConnection connection = SerialComms.manager.openDevice(driver.getDevice());

            if (connection == null) {
                // add UsbManager.requestPermission(driver.getDevice(), ..) handling here

            }else {

                connected = true;

                SerialComms.currentConnection = driver.getPorts().get(deviceIndex); //multiple devices here...?
                SerialComms.currentConnection.open(connection);
                SerialComms.currentConnection.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            }
        }

        return connected;
    }

    public static synchronized void serialWrite(byte[] request) throws IOException {

        if(SerialComms.isConnected()) {
            SerialComms.currentConnection.write(request, WRITE_WAIT_MILLIS);
        }
    }

    //UTILS
    //formats byte stream. adds 127 as header and CRC16 MSL LSB to end
    public static byte[] formatStream(byte[] command, boolean addCRC16){

        byte[] dataStream;

        byte[] header = new byte[1];
        header[0] = ByteHandleUtils.intToByte(127);

        byte[] headerCommand;
        headerCommand = ByteHandleUtils.combineByteArray(header, command);

        dataStream = headerCommand;

        if(addCRC16){

            byte[] crc16 = SerialComms.calculateCRC16(command);

            byte[] headerCommandTail;
            headerCommandTail = ByteHandleUtils.combineByteArray(headerCommand, crc16);

            dataStream = headerCommandTail;
        }

        return dataStream;
    }

    //return CRC16 [MSB,LSB] of byte[] CRC16 CCITT XMODEM
    //byte[] crc = cp.calculateCRC16(asd.getBytes());
    //Toast.makeText(MainActivity.this, Byte.toUnsignedInt(crc[6])+","+Byte.toUnsignedInt(crc[7]), Toast.LENGTH_SHORT).show();

    public static byte[] calculateCRC16(byte[] bytes){

        byte[] CRC16Long;
        byte[] CRC16Final = new byte[2];

        long crc = CRCUtils.calculateCRC(CRCUtils.Parameters.XMODEM, bytes);

        CRC16Long = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(crc).array();
        //Toast.makeText(getApplicationContext(), intArrayToString(byteArrayToUnsignedIntArray(CRC16Long)) ,Toast.LENGTH_SHORT).show();
        CRC16Final[0] ^= CRC16Long[6] & 0xff;
        CRC16Final[1] ^= CRC16Long[7] & 0xff;

        return CRC16Final;
    }

    public static synchronized void queueStream(ByteStream stream, String type){

        switch(type){
            case "processing":
                Log.v("SerialComms", "about to queue processing: "+ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(stream.getStream())));
                SerialComms.pendingProcessStreams.add(stream);
                break;
            case "writing":
                Log.v("SerialComms", "about to queue writing: "+ByteHandleUtils.intArrayToString(ByteHandleUtils.byteArrayToUnsignedIntArray(stream.getStream())));
                SerialComms.pendingWritingStreams.add(stream);
                break;
            default:
                break;
        }
    }
    public static synchronized void saveSentStream(ByteStream stream){

        SerialComms.sentStreams.add(stream);
    }

    //GET+SET
    public static SerialComms getSerial() {
        return serial;
    }

    public static void setSerial(SerialComms serial) {
        SerialComms.serial = serial;
    }

    public static MainActivity getActivityRef() {
        return activityRef;
    }

    public static void setActivityRef(MainActivity activityRef) {
        SerialComms.activityRef = activityRef;
    }

    public static int getWriteWaitMillis() {
        return WRITE_WAIT_MILLIS;
    }

    public static int getReadWaitMillis() {
        return READ_WAIT_MILLIS;
    }

    public static int getBaudRate() {
        return baudRate;
    }

    public static void setBaudRate(int baudRate) {
        SerialComms.baudRate = baudRate;
    }

    public static synchronized UsbSerialPort getCurrentConnection() {
        return currentConnection;
    }

    public static void setCurrentConnection(UsbSerialPort currentConnection) {
        SerialComms.currentConnection = currentConnection;
    }

    public static List<UsbSerialDriver> getAvailableDrivers() {
        return availableDrivers;
    }

    public static void setAvailableDrivers(List<UsbSerialDriver> availableDrivers) {
        SerialComms.availableDrivers = availableDrivers;
    }

    public static UsbManager getManager() {
        return manager;
    }

    public static void setManager(UsbManager manager) {
        SerialComms.manager = manager;
    }

    public static SerialInputOutputManager getUsbIoManager() {
        return usbIoManager;
    }

    public static void setUsbIoManager(SerialInputOutputManager usbIoManager) {
        SerialComms.usbIoManager = usbIoManager;
    }

    public static synchronized ConcurrentLinkedQueue<ByteStream> getPendingProcessStreams() {

        return pendingProcessStreams;
    }

    public static synchronized void setPendingProcessStreams(ConcurrentLinkedQueue<ByteStream> pendingProcessStreams) {
        SerialComms.pendingProcessStreams = pendingProcessStreams;
    }

    public static synchronized ConcurrentLinkedQueue<ByteStream> getPendingWritingStreams() {
        return pendingWritingStreams;
    }

    public static synchronized void setPendingWritingStreams(ConcurrentLinkedQueue<ByteStream> pendingWritingStreams) {
        SerialComms.pendingWritingStreams = pendingWritingStreams;
    }

    public static synchronized ConcurrentLinkedQueue<ByteStream> getProcessedStreams() {
        return processedStreams;
    }

    public static void setProcessedStreams(ConcurrentLinkedQueue<ByteStream> processedStreams) {
        SerialComms.processedStreams = processedStreams;
    }

    public static synchronized ConcurrentLinkedQueue<ByteStream> getSentStreams() {
        return sentStreams;
    }

    public static synchronized void setSentStreams(ConcurrentLinkedQueue<ByteStream> sentStreams) {
        SerialComms.sentStreams = sentStreams;
    }

    public static Thread getPaymentThread() {
        return paymentThread;
    }

    public static void setPaymentThread(Thread paymentThread) {
        SerialComms.paymentThread = paymentThread;
    }

    public static Thread getRechargeThread() {
        return rechargeThread;
    }

    public static void setRechargeThread(Thread rechargeThread) {
        SerialComms.rechargeThread = rechargeThread;
    }

    public static Thread getSerialReadingThread() {
        return serialReadingThread;
    }

    public static void setSerialReadingThread(Thread serialReadingThread) {
        SerialComms.serialReadingThread = serialReadingThread;
    }

    public static Thread getSerialWritingThread() {
        return serialWritingThread;
    }

    public static synchronized void setSerialWritingThread(Thread serialWritingThread) {
        SerialComms.serialWritingThread = serialWritingThread;
    }

    public static boolean isConnected() {
        return connected;
    }

    public static void setConnected(boolean connected) {
        SerialComms.connected = connected;
    }

    public static synchronized boolean isReading() {
        return reading;
    }

    public static synchronized void setReading(boolean reading) {
        SerialComms.reading = reading;

       activityRef.runOnUiThread(new Runnable() {

            @Override
            public void run() {

                SerialComms.activityRef.setReadingStatus(SerialComms.isReading()+"");
            }
        });
    }

    public synchronized static boolean isWriting() {
        return writing;
    }

    public static synchronized void setWriting(boolean writing) {
        SerialComms.writing = writing;


        activityRef.runOnUiThread(new Runnable() {

            @Override
            public void run() {

                SerialComms.activityRef.setWritingStatus(SerialComms.isWriting()+"");
            }
        });
    }

    public boolean isWritingAvailable(){

        boolean available = System.currentTimeMillis() - this.lastWriteTS >= this.WRITE_WAIT_MILLIS;

        return available;
    }

    public boolean isReadingAvailable(){

        boolean available = System.currentTimeMillis() - this.lastReadTS >= this.READ_WAIT_MILLIS;;

        return available;
    }

    public static ByteStreamProcessor getStreamProcessor() {
        return streamProcessor;
    }

    public static void setStreamProcessor(ByteStreamProcessor streamProcessor) {
        SerialComms.streamProcessor = streamProcessor;
    }

    public static Thread getStreamProcessorThread() {
        return streamProcessorThread;
    }

    public static void setStreamProcessorThread(Thread streamProcessorThread) {
        SerialComms.streamProcessorThread = streamProcessorThread;
    }

    public static long getLastReadTS() {
        return lastReadTS;
    }

    public static void setLastReadTS(long lastReadTS) {
        SerialComms.lastReadTS = lastReadTS;
    }

    public static long getLastWriteTS() {
        return lastWriteTS;
    }

    public static void setLastWriteTS(long lastWriteTS) {
        SerialComms.lastWriteTS = lastWriteTS;
    }

    public static synchronized void startWritingThread(){

        SerialComms.serialWritingThread.start();
    }

    public static synchronized void startReadingThread(){

        SerialComms.serialReadingThread.start();
    }

    public static synchronized void setWaitingCommandResponse(boolean status){

        SerialComms.waitingCommandResponse = status;
    }

    public static synchronized boolean isWaitingCommandResponse() {
        return waitingCommandResponse;
    }

    public static synchronized long getLastCommandWriteTS() {
        return lastCommandWriteTS;
    }

    public static synchronized void setLastCommandWriteTS(long lastCommandWriteTS) {
        SerialComms.lastCommandWriteTS = lastCommandWriteTS;
    }

    public static synchronized void listAction(String action){

        SerialComms.activityRef.runOnUiThread(new Runnable() {

            @Override
            public void run() {

                SerialComms.activityRef.addStreamToHistory(action);
            }
        });
    }
}
