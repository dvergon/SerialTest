package cl.cym.testserial;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static SerialComms serialComms;
    private static Thread serialCommsThread;
    private BroadcastReceiver usbAttachReceiver;
    private BroadcastReceiver usbDetachReceiver;
    private BroadcastReceiver permissionReceiver;
    private ArrayList<String> streamHistory;
    private ArrayAdapter<String> streamHistoryAdapter;
    private static SoundPool soundPool;
    private static HashMap<Integer, Integer> soundPoolMap;
    private static final int correctSound = R.raw.successvolup;
    private static final int errorSound = R.raw.errorvolup;
    private static ImageView payArrow;
    private static ImageView billArrow;
    private static TextView payText;
    private static TextView billText;
    private static ConstraintLayout mainContent;
    private static ConstraintLayout overlay;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        payArrow = (ImageView)findViewById(R.id.img_payArrow);
        billArrow = (ImageView)findViewById(R.id.img_billArrow);
        payText = (TextView)findViewById(R.id.text_pay);
        billText = (TextView)findViewById(R.id.text_billAcceptor);
        mainContent = (ConstraintLayout)findViewById(R.id.main_content);
        overlay = (ConstraintLayout)findViewById(R.id.overlay);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        this.soundPool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(audioAttributes).build();
        this.soundPoolMap = new HashMap<Integer, Integer>(3);

        this.soundPoolMap.put(correctSound, soundPool.load(getApplicationContext(), R.raw.successvolup,1));
        this.soundPoolMap.put(errorSound, soundPool.load(getApplicationContext(), R.raw.errorvolup,2));


        this.serialComms = SerialComms.getInstance();

        synchronized (serialComms){
            this.serialComms.setActivityRef(this);
            this.serialComms.setBaudRate(57600);
            this.serialComms.setManager((UsbManager) getSystemService(Context.USB_SERVICE));
            this.serialComms.setAvailableDrivers(UsbSerialProber.getDefaultProber().findAllDrivers(SerialComms.getManager()));
            this.serialComms.setStreamProcessor(ByteStreamProcessor.getInstance(this.serialComms));
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

                /*synchronized (serialComms){
                    serialComms.setConnected(false);
                }

                serialCommsThread.interrupt();
                serialCommsThread = new Thread();*/
            }
        };

        IntentFilter filterAttach = new IntentFilter();
        filterAttach.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbAttachReceiver, filterAttach);

        IntentFilter filterDetach = new IntentFilter();
        filterDetach.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachReceiver, filterDetach);

        synchronized (serialComms){
            serialComms.setConnected(false);
        }

        serialCommsThread = new Thread(serialComms);
        serialCommsThread.start();

        changeToPayStatus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    //GETTER AND SETTERS

    public void updateListContent(){
        this.streamHistoryAdapter.notifyDataSetChanged();
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

    public synchronized void setActionText(String text){

        TextView tv1 = (TextView)findViewById(R.id.text_actions);
        tv1.setText(text);
    }

    public synchronized void setActionDetailText(String text){

        TextView tv1 = (TextView)findViewById(R.id.text_actiondetail);
        tv1.setText(text);
    }

    public synchronized void playSound(int sound){

        float volume = (float) 1;

        soundPool.play(soundPoolMap.get(sound), volume, volume, 1, 0, 1f);
    }

    public static synchronized int getCorrectSound(){

        return correctSound;
    }

    public static synchronized int getErrorSound(){

        return errorSound;
    }

    public synchronized void changeToIdleStatus(){

        overlay.setVisibility(View.VISIBLE);
    }

    public synchronized void changeToPayStatus(){

        overlay.setVisibility(View.GONE);

        payText.setVisibility(View.VISIBLE);
        payArrow.setVisibility(View.VISIBLE);

        billText.setVisibility(View.GONE);
        billArrow.setVisibility(View.GONE);
    }

    public synchronized void changeToRechargeStatus(){

        overlay.setVisibility(View.GONE);

        payText.setVisibility(View.GONE);
        payArrow.setVisibility(View.GONE);

        billArrow.setVisibility(View.VISIBLE);
        billText.setVisibility(View.VISIBLE);
    }
}
