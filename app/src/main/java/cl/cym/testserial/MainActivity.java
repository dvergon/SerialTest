package cl.cym.testserial;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {

    private static final int WRITE_WAIT_MILLIS = 200;
    private static final int READ_WAIT_MILLIS = 300;
    private UsbSerialPort currentConnection;
    private List<UsbSerialDriver> availableDrivers;
    private UsbManager manager;
    private SerialInputOutputManager usbIoManager;
    private BroadcastReceiver usbAttachReceiver;
    private BroadcastReceiver usbDetachReceiver;
    private boolean isConnected = false;
    private ArrayList<String> streamHistory;
    private ArrayAdapter<String> streamHistoryAdapter;
    private Thread pollerThread;
    private boolean pollStartAllowed;
    private ByteHandleUtils byteUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.byteUtils = new ByteHandleUtils();

        this.pollerThread = new Thread();
        this.pollStartAllowed = true;

        usbAttachReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                appSerialConnect();
            }
        };

        usbDetachReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                appSerialConnect();
            }
        };

        IntentFilter filterAttach = new IntentFilter();
        filterAttach.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbAttachReceiver, filterAttach);

        IntentFilter filterDetach = new IntentFilter();
        filterDetach.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachReceiver, filterDetach);

        appSerialConnect();

        ListView streamHistory = (ListView) findViewById(R.id.list_streamHistory);
        this.streamHistory = new ArrayList<String>();
        this.streamHistoryAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1, this.streamHistory);
        streamHistory.setAdapter(this.streamHistoryAdapter);

        //LISTENERS
        /*final Button buttonSend = (Button) findViewById(R.id.btn_send);
        buttonSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                TextView inputText = (TextView) findViewById(R.id.text_message);
                try {
                    String text = inputText.getText().toString();
                    directWrite(text.getBytes());
                    inputText.setText("");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });*/
    }

    private void appSerialConnect(){

        this.getAllDrivers();

        TextView connectedStatus = (TextView) findViewById(R.id.text_status);

        try {
            boolean connected = openSerial(0, 9600);

            if(connected){

                connectedStatus.setText("Connected");
                this.setConnected(true);

                this.pollStartAllowed = true;
                this.pollerThread = new Thread(new SerialPoller(this));
                this.pollerThread.start();

            }else{

                connectedStatus.setText("Disconnected");
                this.setConnected(false);

                this.pollerThread.interrupt();
                this.pollerThread = new Thread();
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

                this.usbIoManager = new SerialInputOutputManager(this.currentConnection, this);
                this.usbIoManager.setReadTimeout(READ_WAIT_MILLIS);
                this.usbIoManager.setWriteTimeout(WRITE_WAIT_MILLIS);
                Executors.newSingleThreadExecutor().submit(this.usbIoManager);
            }
        }

        return connected;
    }

    public void serialWrite(byte[] request) throws IOException {

        if(this.isConnected()) {
            this.currentConnection.write(request, WRITE_WAIT_MILLIS);
        }
    }

    @Override
    public void onNewData(byte[] data) {

        if(this.byteUtils.byteToInt(data[0]) == 127){

            //valid stream. accept and process.

            //check response id
            switch(this.byteUtils.byteToInt(data[1])){

                case 83:
                    //POLL RESPONSE
                    this.streamHistory.add("("+(this.streamHistory.size()+1)+") "+this.byteUtils.intArrayToString(this.byteUtils.byteArrayToUnsignedIntArray(data)));
                    this.streamHistoryAdapter.notifyDataSetChanged();

                    break;
                default:
                    break;
            }

        }else{

            //ignore
        }

        try {
            this.currentConnection.purgeHwBuffers(false, true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.pollStartAllowed = true;
    }

    @Override
    public void onRunError(Exception e) {

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

    //GETTER AND SETTERS

    public boolean isConnected(){

        return this.isConnected;
    }

    public void setConnected(boolean status){

        this.isConnected = status;
    }

    public boolean isPollStartAllowed(){

        return this.pollStartAllowed;
    }

    public UsbSerialPort getCurrentConnection(){

        return this.currentConnection;
    }

    public static int getWriteWaitMillis() {
        return WRITE_WAIT_MILLIS;
    }

    public static int getReadWaitMillis() {
        return READ_WAIT_MILLIS;
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

    public BroadcastReceiver getUsbAttachReceiver() {
        return usbAttachReceiver;
    }

    public void setUsbAttachReceiver(BroadcastReceiver usbAttachReceiver) {
        this.usbAttachReceiver = usbAttachReceiver;
    }

    public BroadcastReceiver getUsbDetachReceiver() {
        return usbDetachReceiver;
    }

    public void setUsbDetachReceiver(BroadcastReceiver usbDetachReceiver) {
        this.usbDetachReceiver = usbDetachReceiver;
    }

    public ArrayList<String> getStreamHistory() {
        return streamHistory;
    }

    public void setStreamHistory(ArrayList<String> streamHistory) {
        this.streamHistory = streamHistory;
    }

    public ArrayAdapter<String> getStreamHistoryAdapter() {
        return streamHistoryAdapter;
    }

    public void setStreamHistoryAdapter(ArrayAdapter<String> streamHistoryAdapter) {
        this.streamHistoryAdapter = streamHistoryAdapter;
    }

    public Thread getPollerThread() {
        return pollerThread;
    }

    public void setPollerThread(Thread pollerThread) {
        this.pollerThread = pollerThread;
    }

    public void setPollStartAllowed(boolean pollStartAllowed) {
        this.pollStartAllowed = pollStartAllowed;
    }

    public ByteHandleUtils getByteUtils() {
        return byteUtils;
    }

    public void setByteUtils(ByteHandleUtils byteUtils) {
        this.byteUtils = byteUtils;
    }
}
