package cl.cym.testserial;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static SerialComms serialComms;
    private static Thread serialCommsThread;
    private BroadcastReceiver usbAttachReceiver;
    private BroadcastReceiver usbDetachReceiver;
    private BroadcastReceiver permissionReceiver;
    private ArrayList<String> streamHistory;
    private ArrayAdapter<String> streamHistoryAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.serialComms = SerialComms.getInstance();

        synchronized (serialComms){
            this.serialComms.setActivityRef(this);
            this.serialComms.setBaudRate(57600);
            this.serialComms.setManager((UsbManager) getSystemService(Context.USB_SERVICE));
            this.serialComms.setAvailableDrivers(UsbSerialProber.getDefaultProber().findAllDrivers(SerialComms.getManager()));
        }

        usbAttachReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                /*synchronized (serialComms){
                    serialComms.setConnected(false);
                }

                serialCommsThread = new Thread(serialComms);
                serialCommsThread.start();*/
            }
        };

        usbDetachReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                /*serialCommsThread.interrupt();
                serialCommsThread = new Thread();*/
            }
        };

        IntentFilter filterAttach = new IntentFilter();
        filterAttach.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbAttachReceiver, filterAttach);

        IntentFilter filterDetach = new IntentFilter();
        filterDetach.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachReceiver, filterDetach);

        ListView streamHistory = (ListView) findViewById(R.id.list_streamHistory);
        this.streamHistory = new ArrayList<String>();
        this.streamHistoryAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1, this.streamHistory);
        streamHistory.setAdapter(this.streamHistoryAdapter);



        //LISTENERS
        final Button buttonStart = (Button) findViewById(R.id.btn_start);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                serialCommsThread = new Thread(serialComms);
                serialCommsThread.start();
            }
        });

        final Button buttonSent = (Button) findViewById(R.id.btn_sent);
        buttonSent.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                synchronized (serialComms) {
                    String[] strArray = Arrays.stream(SerialComms.getSentStreams().toArray()).map(Object::toString).toArray(String[]::new);

                    setStreamHistory(new ArrayList<String>(Arrays.asList(strArray)));

                    updateListContent();

                    updateStatusText(strArray.length+"");
                }

            }
        });

        final Button buttonProcess = (Button) findViewById(R.id.btn_process);
        buttonProcess.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                synchronized (serialComms){
                    String[] strArray = Arrays.stream(SerialComms.getPendingProcessStreams().toArray()).map(Object::toString).toArray(String[]::new);

                    setStreamHistory(new ArrayList<String>(Arrays.asList(strArray)));

                    updateListContent();

                    updateStatusText(strArray.length+"");
                }
            }
        });

        final Button buttonWriting = (Button) findViewById(R.id.btn_writing);
        buttonWriting.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                synchronized (serialComms){

                    String[] strArray = Arrays.stream(serialComms.getPendingWritingStreams().toArray()).map(Object::toString).toArray(String[]::new);

                    setStreamHistory(new ArrayList<String>(Arrays.asList(strArray)));

                    updateListContent();

                    updateStatusText(strArray.length+"");
                }
            }
        });
    }

    //GETTER AND SETTERS

    public synchronized void addStreamToHistory(String stream){

        this.streamHistory.add(stream);
        this.streamHistoryAdapter.notifyDataSetChanged();
    }

    public void updateListContent(){
        this.streamHistoryAdapter.notifyDataSetChanged();
    }

    public synchronized void updateStatusText(String text){

        TextView connectedStatus = (TextView) findViewById(R.id.text_status);
        connectedStatus.setText(text);
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

    public synchronized void setWritingStatus(String status){

        TextView connectedStatus = (TextView) findViewById(R.id.writingStatus);
        connectedStatus.setText("Writing: " + status);
    }

    public synchronized void setReadingStatus(String status){

        TextView connectedStatus = (TextView) findViewById(R.id.readingStatus);
        connectedStatus.setText("Reading: " + status);
    }
}
