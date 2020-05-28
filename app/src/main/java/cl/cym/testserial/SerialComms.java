package cl.cym.testserial;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
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

public class SerialComms extends AppCompatActivity implements Runnable {

    private MainActivity activityRef;
    private static final int WRITE_WAIT_MILLIS = 200;
    private static final int READ_WAIT_MILLIS = 200;
    private int baudRate;
    private UsbSerialPort currentConnection;
    private List<UsbSerialDriver> availableDrivers;
    private UsbManager manager;
    private SerialInputOutputManager usbIoManager;
    private ByteHandleUtils byteUtils;
    private ConcurrentLinkedQueue<ByteStream> pendingProcessStreams;
    private ConcurrentLinkedQueue<ByteStream> pendingWritingStreams;
    private ConcurrentLinkedQueue<ByteStream> processedStreams;
    private ConcurrentLinkedQueue<ByteStream> sentStreams;
    private Thread paymentThread;
    private Thread rechargeThread;
    private Thread serialReadingThread;
    private Thread serialWritingThread;
    private boolean connected;
    private boolean reading;
    private boolean writing;

    public SerialComms(MainActivity activityRef, int baudRate){

        this.activityRef = activityRef;
        this.baudRate = baudRate;
        this.reading = false;
        this.writing = false;
        this.connected = false;

        this.pendingProcessStreams = new ConcurrentLinkedQueue<ByteStream>();
        this.pendingWritingStreams = new ConcurrentLinkedQueue<ByteStream>();
        this.processedStreams = new ConcurrentLinkedQueue<ByteStream>();
        this.sentStreams = new ConcurrentLinkedQueue<ByteStream>();
    }

    @Override
    public void run(){

        while(!Thread.interrupted()){

            if(!this.isConnected()){

                this.appSerialConnect();

                this.activityRef.updateStatusText("Connected: "+ isConnected());
            }

            if(!isReading()){

                //if not reading, pick a queued stream and send it
                if(this.pendingWritingStreams.size() > 0){

                    ByteStream currentStream = this.pendingWritingStreams.remove();
                    this.serialWritingThread = new Thread(new SerialWriter(this, currentStream));

                }else{

                    //if there are no pending queued streams, poll
                    byte[] poll = new byte[1];
                    poll[0] = this.byteUtils.intToByte(80);

                    byte[] formattedStream = formatStream(poll, false);

                    ByteStream currentStream = new ByteStream(formattedStream);
                    this.serialWritingThread = new Thread(new SerialWriter(this, currentStream));
                    this.serialWritingThread.start();
                }

                this.serialReadingThread = new Thread(new SerialReader(this, this.READ_WAIT_MILLIS));
                this.serialReadingThread.start();
            }
        }
    }

    public synchronized void purgeHwBuffers(boolean read, boolean write) throws IOException {

        this.currentConnection.purgeHwBuffers(write, read);
    }

    public synchronized void appSerialConnect(){

        this.getAllDrivers();

        try {
            boolean connected = openSerial(0, this.baudRate);

            if(connected){

                this.setConnected(true);

            }else{

                this.setConnected(false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //SERIAL HANDLER
    private void getAllDrivers(){

        this.manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        this.availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(this.manager);
    }

    private boolean openSerial(int deviceIndex, int baudRate) throws IOException {

        boolean connected = false;

        if(this.availableDrivers.size() > 0){

            UsbSerialDriver driver = this.availableDrivers.get(deviceIndex);
            UsbDeviceConnection connection = this.manager.openDevice(driver.getDevice());

            if (connection == null) {
                // add UsbManager.requestPermission(driver.getDevice(), ..) handling here

            }else {

                connected = true;

                this.currentConnection = driver.getPorts().get(deviceIndex); //multiple devices here...?
                this.currentConnection.open(connection);
                this.currentConnection.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            }
        }

        return connected;
    }

    public void serialWrite(byte[] request) throws IOException {

        if(this.isConnected()) {
            this.currentConnection.write(request, WRITE_WAIT_MILLIS);
        }
    }

    //UTILS
    //formats byte stream. adds 127 as header and CRC16 MSL LSB to end
    public byte[] formatStream(byte[] command, boolean addCRC16){

        byte[] dataStream;

        byte[] header = new byte[1];
        header[0] = this.byteUtils.intToByte(127);

        byte[] headerCommand;
        headerCommand = this.byteUtils.combineByteArray(header, command);

        dataStream = headerCommand;

        if(addCRC16){

            byte[] crc16 = calculateCRC16(command);

            byte[] headerCommandTail;
            headerCommandTail = this.byteUtils.combineByteArray(headerCommand, crc16);

            dataStream = headerCommandTail;
        }

        return dataStream;
    }

    //return CRC16 [MSB,LSB] of byte[] CRC16 CCITT XMODEM
    //byte[] crc = cp.calculateCRC16(asd.getBytes());
    //Toast.makeText(MainActivity.this, Byte.toUnsignedInt(crc[6])+","+Byte.toUnsignedInt(crc[7]), Toast.LENGTH_SHORT).show();

    public byte[] calculateCRC16(byte[] bytes){

        byte[] CRC16Long;
        byte[] CRC16Final = new byte[2];

        long crc = CRCUtils.calculateCRC(CRCUtils.Parameters.XMODEM, bytes);

        CRC16Long = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(crc).array();
        //Toast.makeText(getApplicationContext(), intArrayToString(byteArrayToUnsignedIntArray(CRC16Long)) ,Toast.LENGTH_SHORT).show();
        CRC16Final[0] ^= CRC16Long[6] & 0xff;
        CRC16Final[1] ^= CRC16Long[7] & 0xff;

        return CRC16Final;
    }

    public synchronized void queueStream(ByteStream stream){

        this.pendingProcessStreams.add(stream);
    }
    public synchronized void saveSentStream(ByteStream stream){

        this.sentStreams.add(stream);
    }

    //GET+SET

    public static synchronized int getWriteWaitMillis() {
        return WRITE_WAIT_MILLIS;
    }

    public static synchronized int getReadWaitMillis() {
        return READ_WAIT_MILLIS;
    }

    public synchronized UsbSerialPort getCurrentConnection() {
        return currentConnection;
    }

    public void setCurrentConnection(UsbSerialPort currentConnection) {
        this.currentConnection = currentConnection;
    }

    public List<UsbSerialDriver> getAvailableDrivers() {
        return availableDrivers;
    }

    public void setAvailableDrivers(List<UsbSerialDriver> availableDrivers) {
        this.availableDrivers = availableDrivers;
    }

    public UsbManager getManager() {
        return manager;
    }

    public void setManager(UsbManager manager) {
        this.manager = manager;
    }

    public SerialInputOutputManager getUsbIoManager() {
        return usbIoManager;
    }

    public void setUsbIoManager(SerialInputOutputManager usbIoManager) {
        this.usbIoManager = usbIoManager;
    }

    public ByteHandleUtils getByteUtils() {
        return byteUtils;
    }

    public void setByteUtils(ByteHandleUtils byteUtils) {
        this.byteUtils = byteUtils;
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public MainActivity getActivityRef() {
        return activityRef;
    }

    public void setActivityRef(MainActivity activityRef) {
        this.activityRef = activityRef;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    public Thread getPaymentThread() {
        return paymentThread;
    }

    public void setPaymentThread(Thread paymentThread) {
        this.paymentThread = paymentThread;
    }

    public Thread getRechargeThread() {
        return rechargeThread;
    }

    public void setRechargeThread(Thread rechargeThread) {
        this.rechargeThread = rechargeThread;
    }

    public ConcurrentLinkedQueue getPendingProcessStreams() {
        return pendingProcessStreams;
    }

    public void setPendingProcessStreams(ConcurrentLinkedQueue pendingProcessStreams) {
        this.pendingProcessStreams = pendingProcessStreams;
    }

    public ConcurrentLinkedQueue getPendingWritingStreams() {
        return pendingWritingStreams;
    }

    public void setPendingWritingStreams(ConcurrentLinkedQueue pendingWritingStreams) {
        this.pendingWritingStreams = pendingWritingStreams;
    }

    public synchronized boolean isReading() {
        return reading;
    }

    public synchronized void setReading(boolean reading) {
        this.reading = reading;
    }

    public synchronized boolean isWriting() {
        return writing;
    }

    public synchronized void setWriting(boolean writing) {
        this.writing = writing;
    }
}
