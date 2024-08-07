/*
WunderLINQ Client Application
Copyright (C) 2020  Keith Conger, Black Box Embedded, LLC

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.blackboxembedded.wunderlinqinsta360;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.net.wifi.WifiManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener;

import java.util.Arrays;

public class DeviceControlActivity extends BaseObserveCameraActivity implements View.OnTouchListener, ICaptureStatusListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private SharedPreferences sharedPrefs;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;

    private CameraStatus cameraStatus;

    private View view;
    private ProgressBar progressBar;
    private ImageView modeImageView;
    private Button shutterButton;

    private int highlightColor;

    private GestureDetectorListener gestureDetector;

    private String SSID;
    private String password;
    private WifiManager wifiManager;
    ConnectivityManager connectivityManager;

    private byte[] response;
    private int responsePosition = 0;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected()");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress, mDeviceName);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_NOTFICATION_ENABLED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_SERVICE_DISCONNECTED);
        return intentFilter;
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
            } else if (BluetoothLeService.ACTION_SERVICE_DISCONNECTED.equals(action)){
                finish();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                finish();
            } else if (BluetoothLeService.ACTION_NOTFICATION_ENABLED.equals(action)) {
                mBluetoothLeService.requestCameraWifi();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Bundle bd = intent.getExtras();
                if(bd != null){
                    if(bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) != null) {
                        if (bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE).contains(GattAttributes.INSTA360_COMMANDRESPONSE_CHARACTERISTIC)) {
                            byte[] data = bd.getByteArray(BluetoothLeService.EXTRA_BYTE_VALUE);
                            String characteristicValue = Utils.ByteArraytoHex(data);
                            if (sharedPrefs.getBoolean("prefDebugLogging", false)) {
                                Log.d(TAG, "UUID: " + bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) + " DATA: " + characteristicValue);
                            }

                            if(response == null){
                                responsePosition = 0;
                                response = new byte[(byte)data[0]];
                                System.arraycopy(data, 0, response, 0, data.length);
                                responsePosition = responsePosition + data.length;
                            } else {
                                if (responsePosition != response[0]){
                                    System.arraycopy(data, 0, response, responsePosition, data.length);
                                    responsePosition = responsePosition + data.length;
                                }
                            }
                            if (responsePosition  == response[0]){
                                processResponse(response);
                                response = null;
                                responsePosition = 0;
                            }
                        }
                    }
                }
            }
        }
    };

    void processResponse(byte[] message) {
        if (sharedPrefs.getBoolean("prefDebugLogging", false)) {
            Log.d(TAG, "processResponse: " + Utils.ByteArraytoHex(message));
        }
        switch (message[4]){
            case 0x04:
                if (message[7] == (byte)0xC8) {
                    int startPosition = 25;
                    // Find the end position
                    int endPosition = startPosition;
                    while (endPosition < message.length && message[endPosition] != 0x12) {
                        endPosition++;
                    }
                    // Copy the array from startPosition to endPosition
                    byte[] SSID = new byte[endPosition - startPosition];
                    System.arraycopy(message, startPosition, SSID, 0, SSID.length);
                    if (sharedPrefs.getBoolean("prefDebugLogging", false)) {
                        Log.d(TAG, "Camera SSID: " + new String(SSID));
                    }
                    //Get Password
                    // Copy the array from startPosition to endPosition
                    byte[] WIFI_PASSWORD = new byte[message[endPosition + 1]];
                    System.arraycopy(message, endPosition + 2, WIFI_PASSWORD, 0, WIFI_PASSWORD.length);
                    if (sharedPrefs.getBoolean("prefDebugLogging", false)) {
                        Log.d(TAG, "Camera PASS: " + new String(WIFI_PASSWORD));
                    }
                    connectToWifi(new String(SSID), new String(WIFI_PASSWORD));
                }
                break;
            case 0x05:
                //Keep Alive
                break;
            default:
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_control_activity);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);

        if(mDeviceAddress == null){
            final Intent mainIntent = new Intent(this, DeviceScanActivity.class);
            startActivity(mainIntent);
        }

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getSupportActionBar().setTitle(mDeviceName);
        view = findViewById(R.id.controlLayOut);
        progressBar = findViewById(R.id.progress_loader);
        modeImageView = findViewById(R.id.modeIV);
        shutterButton = findViewById(R.id.shutterBtn);
        shutterButton.setOnClickListener(mClickListener);
        shutterButton.setVisibility(View.INVISIBLE);

        gestureDetector = new GestureDetectorListener(this) {

            @Override
            public void onSwipeUp() {
                nextMode();
            }

            @Override
            public void onSwipeDown() {
                previousMode();
            }

            @Override
            public void onSwipeLeft() {
                rightKey();
            }

            @Override
            public void onSwipeRight() {
                leftKey();
            }
        };
        view.setOnTouchListener(this);

        highlightColor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getInt("prefHighlightColor", getResources().getColor(R.color.colorAccent));
        shutterButton.setBackgroundColor(highlightColor);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        if (mBluetoothLeService != null) {
            Log.d(TAG,"Attempting connection to: " + mDeviceName + "-" + mDeviceAddress);
            mBluetoothLeService.connect(mDeviceAddress, mDeviceName);
        }

        cameraStatus = new CameraStatus();
        cameraStatus.busy = false;
        cameraStatus.mode = 0;

        // Capture Status Callback
        InstaCameraManager.getInstance().setCaptureStatusListener(this);
    }

    @Override
    protected void onResume() {
        Log.d(TAG,"onResume()");
        super.onResume();
        ContextCompat.registerReceiver(this, mGattUpdateReceiver, makeGattUpdateIntentFilter(), ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        Log.d(TAG,"onPause()");
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG,"onDestroy()");
        super.onDestroy();
        if (mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
        }
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        InstaCameraManager.getInstance().closeCamera();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUIElements();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouch(v, event);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                //Toggle Shutter
                toggleShutter();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                //Scroll through modes
                nextMode();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                //Scroll through modes
                previousMode();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                leftKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                //Open Camera Preview
                rightKey();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                SoundManager.playSound(this, R.raw.enter);
                String wunderLINQApp = "wunderlinq://datagrid";
                Intent intent = new
                        Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(Uri.parse(wunderLINQApp));
                try {
                    startActivity(intent);
                } catch ( ActivityNotFoundException ex  ) {
                }
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.shutterBtn:
                    //Toggle Shutter
                    toggleShutter();
                    break;
            }
        }
    };

    private void updateUIElements(){
        Log.d(TAG,"updateUIElements()");
        int type = InstaCameraManager.getInstance().getCurrentCaptureType();
        if(type == -1){
            cameraStatus.busy = false;
        } else if(type == 1003){
            cameraStatus.busy = true;
            cameraStatus.mode = 0;
        } else if(type == 1004){
            cameraStatus.busy = true;
            cameraStatus.mode = 1;
        } else if(type == 1002){
            cameraStatus.busy = true;
            cameraStatus.mode = 2;
        } else if(type == 1005){
            cameraStatus.busy = true;
            cameraStatus.mode = 3;
        }
        Log.d(TAG,"CAPTURE_TYPE: " + type);
        switch (cameraStatus.mode) {
            case 0:
                //Normal
                modeImageView.setImageResource(R.drawable.ic_video_camera);
                if (cameraStatus.busy){
                    shutterButton.setText(R.string.task_title_stop_record);
                } else {
                    shutterButton.setText(R.string.task_title_start_record);
                }
                break;
            case 1:
                //HDR
                modeImageView.setImageResource(R.drawable.hdr);
                if (cameraStatus.busy){
                    shutterButton.setText(R.string.task_title_stop_hdr);
                } else {
                    shutterButton.setText(R.string.task_title_start_hdr);
                }
                break;
            case 2:
                //Interval
                modeImageView.setImageResource(R.drawable.interval);
                if (cameraStatus.busy){
                    shutterButton.setText(R.string.task_title_stop_interval);
                } else {
                    shutterButton.setText(R.string.task_title_start_interval);
                }
                break;
            case 3:
                //Timelapse
                modeImageView.setImageResource(R.drawable.timelapse);
                if (cameraStatus.busy){
                    shutterButton.setText(R.string.task_title_stop_timelapse);
                } else {
                    shutterButton.setText(R.string.task_title_start_timelapse);
                }
                break;
            default:
                modeImageView.setImageResource(0);
                Log.e(TAG,"Unknown mode: " + cameraStatus.mode);
                break;
        }

        progressBar.setVisibility(View.INVISIBLE);
        modeImageView.setVisibility(View.VISIBLE);
        shutterButton.setVisibility(View.VISIBLE);
    }

    private void leftKey(){
        SoundManager.playSound(this, R.raw.enter);
        finish();
    }

    private void rightKey(){
        SoundManager.playSound(this, R.raw.enter);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefEnablePreview", false)) {
            startPreview();
        }
    }

    private void toggleShutter(){
        SoundManager.playSound(this, R.raw.enter);
        switch (cameraStatus.mode) {
            case 0:
                if (cameraStatus.busy) {
                    InstaCameraManager.getInstance().stopNormalRecord();
                    cameraStatus.busy = false;
                } else {
                    if (checkSdCardEnabled()) {
                        cameraStatus.busy = true;
                        InstaCameraManager.getInstance().startNormalRecord();
                    }
                }
                break;
            case 1:
                if (cameraStatus.busy) {
                    InstaCameraManager.getInstance().stopHDRRecord();
                    cameraStatus.busy = false;
                } else {
                    if (checkSdCardEnabled()) {
                        cameraStatus.busy = true;
                        InstaCameraManager.getInstance().startHDRRecord();
                    }
                }
                break;
            case 2:
                if (cameraStatus.busy) {
                    InstaCameraManager.getInstance().stopIntervalShooting();
                    cameraStatus.busy = false;
                } else {
                    if (checkSdCardEnabled()) {
                        if (checkSdCardEnabled()) {
                            cameraStatus.busy = true;
                            InstaCameraManager.getInstance().setIntervalShootingTime(3000);
                            InstaCameraManager.getInstance().startIntervalShooting();
                        }
                    }
                }
                break;
            case 3:
                if (cameraStatus.busy) {
                    InstaCameraManager.getInstance().stopTimeLapse();
                    cameraStatus.busy = false;
                } else {
                    if (checkSdCardEnabled()) {
                        cameraStatus.busy = true;
                        InstaCameraManager.getInstance().setTimeLapseInterval(500);
                        InstaCameraManager.getInstance().startTimeLapse();
                    }
                }
                break;
        }
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUIElements();
            }
        }, 3000);

    }

    private void nextMode() {
        SoundManager.playSound(this, R.raw.directional);
        //Next Camera Mode
        if(!cameraStatus.busy) {
            if (cameraStatus.mode == 3) {
                cameraStatus.mode = 0;
            } else {
                cameraStatus.mode = cameraStatus.mode + 1;
            }
        }
        updateUIElements();
    }

    private void previousMode() {
        SoundManager.playSound(this, R.raw.directional);
        //Previous Camera Mode
        if(!cameraStatus.busy) {
            if (cameraStatus.mode == 0) {
                cameraStatus.mode = 3;
            } else {
                cameraStatus.mode = cameraStatus.mode - 1;
            }
        }
        updateUIElements();
    }

    public void disconnectFromWifi(){
        //Unregistering network callback instance supplied to requestNetwork call disconnects phone from the connected network
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    /**
     * Connect to the specified wifi network.
     *
     * @param ssid     - The wifi network SSID
     * @param password - the wifi password
     */
    private void connectToWifi(String ssid, String password) {
        Log.d(TAG,"connectToWifi(" + ssid + ")");
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            try {
                WifiConfiguration wifiConfig = new WifiConfiguration();
                wifiConfig.SSID = "\"" + ssid + "\"";
                wifiConfig.preSharedKey = "\"" + password + "\"";
                int netId = wifiManager.addNetwork(wifiConfig);
                wifiManager.disconnect();
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();

            } catch ( Exception e) {
                e.printStackTrace();
            }
        } else {
            WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build();

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(wifiNetworkSpecifier)
                    .build();

            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.requestNetwork(networkRequest, networkCallback);
        }
    }
    private boolean isCameraConnected() {
        return InstaCameraManager.getInstance().getCameraConnectedType() != InstaCameraManager.CONNECT_TYPE_NONE;
    }


    private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.e(TAG,"onAvailable");
            connectivityManager.bindProcessToNetwork(network);

            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI);

        }

        @Override
        public void onLosing(@NonNull Network network, int maxMsToLive) {
            super.onLosing(network, maxMsToLive);
            Log.e(TAG,"onLosing");
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.e(TAG, "losing active connection");
            connectivityManager.bindProcessToNetwork(null);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            Log.e(TAG,"onUnavailable");
        }
    };

    private void startPreview() {
        // Stuff that updates the UI
        progressBar.setVisibility(View.INVISIBLE);
        modeImageView.setVisibility(View.VISIBLE);
        shutterButton.setVisibility(View.VISIBLE);
        startActivity(new Intent(DeviceControlActivity.this, PreviewActivity.class));
    }

    private boolean checkSdCardEnabled() {
        if (!InstaCameraManager.getInstance().isSdCardEnabled()) {
            Toast.makeText(this, R.string.sd_card_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    public void onCameraStatusChanged(boolean enabled) {
        super.onCameraStatusChanged(enabled);
        if (enabled) {
            Log.d(TAG,"Camera Enabled");
            progressBar.setVisibility(View.INVISIBLE);
            modeImageView.setVisibility(View.VISIBLE);
            shutterButton.setVisibility(View.VISIBLE);
            updateUIElements();
        } else {
            //CameraBindNetworkManager.getInstance().unbindNetwork();
            //NetworkManager.getInstance().clearBindProcess();
            Log.d(TAG,"Camera NOT Enabled");
        }
    }

    @Override
    public void onCameraConnectError(int errorCode) {
        super.onCameraConnectError(errorCode);
        Log.d(TAG,"onCameraConnectError: " + errorCode);
        //CameraBindNetworkManager.getInstance().unbindNetwork();
        //Toast.makeText(this, getResources().getString(R.string.main_toast_camera_connect_error, errorCode), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCameraSensorModeChanged(int cameraSensorMode) {
        super.onCameraSensorModeChanged(cameraSensorMode);
        Log.d(TAG,"Sensor Mode: " + cameraSensorMode);
    }

    @Override
    public void onCaptureStarting() {
        Log.d(TAG,"onCaptureStarting()");
    }

    @Override
    public void onCaptureWorking() {
        Log.d(TAG,"onCaptureWorking()");
        cameraStatus.busy = true;
        updateUIElements();
    }

    @Override
    public void onCaptureStopping() {
        Log.d(TAG,"onCaptureStopping()");
    }

    @Override
    public void onCaptureFinish(String[] filePaths) {
        Log.i(TAG, "onCaptureFinish, filePaths = " + ((filePaths == null) ? "null" : Arrays.toString(filePaths)));
        cameraStatus.busy = false;
        updateUIElements();
    }

    @Override
    public void onCaptureTimeChanged(long captureTime) {
        Log.d(TAG,"onCaptureTimeChanged()");
    }

    @Override
    public void onCaptureCountChanged(int captureCount) {
        Log.d(TAG,"onCaptureCountChanged()");
    }
}
