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

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private SerialComms serialComms;
    private Thread serialCommsThread;
    private BroadcastReceiver usbAttachReceiver;
    private BroadcastReceiver usbDetachReceiver;
    private ArrayList<String> streamHistory;
    private ArrayAdapter<String> streamHistoryAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.serialComms = new SerialComms(this, 9600);

        usbAttachReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                //serialComms.appSerialConnect();
                serialCommsThread = new Thread(serialComms);
                serialCommsThread.start();
            }
        };

        usbDetachReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

               // serialComms.appSerialConnect();
                serialCommsThread.interrupt();
                serialCommsThread = new Thread();
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

    //GETTER AND SETTERS

    public synchronized void addStreamToHistory(String stream){

        this.streamHistory.add(stream);
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
}
