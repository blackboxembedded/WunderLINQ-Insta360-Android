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

import com.arashivision.onecamera.camerarequest.WifiInfo;
import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener;

import java.util.Arrays;

public class DeviceControlActivity extends BaseObserveCameraActivity implements View.OnTouchListener, ICaptureStatusListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";

    private SharedPreferences sharedPrefs;

    private CameraStatus cameraStatus;

    private View view;
    private ProgressBar progressBar;
    private ImageView modeImageView;
    private Button shutterButton;

    private int highlightColor;

    private String mDeviceName = "Insta360";

    private GestureDetectorListener gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_control_activity);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);

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
    }

    @Override
    protected void onPause() {
        Log.d(TAG,"onPause()");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG,"onDestroy()");
        super.onDestroy();
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
            WifiInfo wifiInfo = InstaCameraManager.getInstance().getWifiInfo();
            if (wifiInfo != null){
                final Intent intent = new Intent(DeviceControlActivity.this, PreviewActivity.class);
                intent.putExtra(PreviewActivity.EXTRAS_WIFI_NAME, wifiInfo.getSsid());
                intent.putExtra(PreviewActivity.EXTRAS_WIFI_PWD, wifiInfo.getPwd());
                startActivity(intent);
            }
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

    private boolean isCameraConnected() {
        return InstaCameraManager.getInstance().getCameraConnectedType() != InstaCameraManager.CONNECT_TYPE_NONE;
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
            Log.d(TAG,"Camera NOT Enabled");
        }
    }

    @Override
    public void onCameraConnectError(int errorCode) {
        super.onCameraConnectError(errorCode);
        Log.d(TAG,"onCameraConnectError: " + errorCode);
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
