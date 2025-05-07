package com.blackboxembedded.wunderlinqinsta360;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.arashivision.graphicpath.render.source.AssetInfo;
import com.arashivision.insta360.basecamera.camera.BaseCamera;
import com.arashivision.insta360.basecamera.camera.CameraType;
import com.arashivision.insta360.basemedia.asset.WindowCropInfo;
import com.arashivision.onecamera.camerarequest.WifiInfo;
import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener;
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution;
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder;
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView;
import com.arashivision.sdkmedia.player.config.InstaStabType;
import com.arashivision.sdkmedia.player.listener.PlayerViewListener;
import com.blackboxembedded.wunderlinqinsta360.util.PreviewParamsUtil;

public class PreviewActivity extends BaseObserveCameraActivity implements IPreviewStatusListener {

    private final static String TAG = PreviewActivity.class.getSimpleName();

    private WifiManager wifiManager;
    ConnectivityManager connectivityManager;

    private InstaCapturePlayerView mVideoLayout;
    private PreviewStreamResolution mCurrentResolution;

    private CaptureParamsBuilder createParams() {
        CaptureParamsBuilder builder = PreviewParamsUtil.getCaptureParamsBuilder()
                .setStabType(InstaStabType.STAB_TYPE_OFF)
                .setStabEnabled(false);

        if (mCurrentResolution != null) {
            builder.setResolutionParams(mCurrentResolution.width, mCurrentResolution.height, mCurrentResolution.fps);
        }

        return builder;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_preview_activity);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Setup Media Player
        mVideoLayout = findViewById(R.id.video_layout);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = InstaCameraManager.getInstance().getWifiInfo();
            if (wifiInfo != null) {
                connectToWifi(wifiInfo.getSsid(), wifiInfo.getPwd());
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG,"onStop()");
        if (isFinishing()) {
            // Auto close preview after page loses focus
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(null);
            InstaCameraManager.getInstance().closePreviewStream();
            mVideoLayout.destroy();
        }
    }

    @Override
    public void onOpening() {
        // Preview Opening
        Log.d(TAG,"onOpening()");
        if (mCurrentResolution == null) {
            InstaCameraManager.getInstance().startPreviewStream();
        } else {
            InstaCameraManager.getInstance().startPreviewStream(mCurrentResolution);
        }
    }

    @Override
    public void onOpened() {
        Log.d(TAG,"onOpened()");
        // Preview stream is on and can be played
        InstaCameraManager.getInstance().setStreamEncode();
        mVideoLayout.setPlayerViewListener(new PlayerViewListener() {
            @Override
            public void onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(mVideoLayout.getPipeline());
            }

            @Override
            public void onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null);
            }
        });
        mVideoLayout.prepare(createParams());
        mVideoLayout.play();
        mVideoLayout.switchNormalMode();
    }

    @Override
    public void onIdle() {
        // Preview Stopped
        Log.d(TAG,"Preview Stopped");
        mVideoLayout.destroy();
    }

    @Override
    public void onError() {
        // Preview Failed
        Log.d(TAG,"Preview Failed");
        InstaCameraManager.getInstance().closePreviewStream();
    }

    @Override
    public void onCameraPreviewStreamParamsChanged(BaseCamera baseCamera, boolean isPreviewStreamParamsChanged) {
        Log.d(TAG, "liveStreamParams isPreviewStreamParamsChanged:" + isPreviewStreamParamsChanged);
        if (!isPreviewStreamParamsChanged) {
            Log.d(TAG, "liveStreamParams has nothing changed, ignored");
            return;
        }
        WindowCropInfo curWindowCropInfo = mVideoLayout.getWindowCropInfo();
        WindowCropInfo cameraWindowCropInfo = PreviewParamsUtil.windowCropInfoConversion(baseCamera.getWindowCropInfo());
        if (mVideoLayout.isPlaying() && curWindowCropInfo != null && cameraWindowCropInfo != null) {
            if (curWindowCropInfo.getSrcWidth() != cameraWindowCropInfo.getSrcWidth()
                    || curWindowCropInfo.getSrcHeight() != cameraWindowCropInfo.getSrcHeight()
                    || curWindowCropInfo.getDesWidth() != cameraWindowCropInfo.getDesWidth()
                    || curWindowCropInfo.getDesHeight() != cameraWindowCropInfo.getDesHeight()
                    || curWindowCropInfo.getOffsetX() != cameraWindowCropInfo.getOffsetX()
                    || curWindowCropInfo.getOffsetY() != cameraWindowCropInfo.getOffsetY()) {
                Log.d(TAG, "liveStreamParams changed windowCropInfo: " + baseCamera.getWindowCropInfo().toString());
                mVideoLayout.setWindowCropInfo(cameraWindowCropInfo);
                AssetInfo assetInfo = InstaCameraManager.getInstance().getConvertAssetInfo();
                AssetInfo stabAssetInfo = InstaCameraManager.getInstance().getStabConvertAssetInfo();
                mVideoLayout.setOffset(PreviewParamsUtil.getPlayerOffsetData(assetInfo), PreviewParamsUtil.getPlayerOffsetData(stabAssetInfo).getOffsetV1());
            }
        }
    }

    @Override
    public void onCameraStatusChanged(boolean enabled, int connectType) {
        super.onCameraStatusChanged(enabled, connectType);
        Log.d(TAG,"onCameraStatusChanged");
        if (enabled) {
            Log.d(TAG, "onCameraStatusChanged: Camera Enabled");
            mVideoLayout.setLifecycle(getLifecycle());
            mCurrentResolution = InstaCameraManager.getInstance().getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_NORMAL).get(0);
            if (mCurrentResolution == null) {
                InstaCameraManager.getInstance().startPreviewStream();
            } else {
                InstaCameraManager.getInstance().startPreviewStream(mCurrentResolution);
            }
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(this);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                leftKey();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                String wunderLINQApp = "wunderlinq://datagrid";
                Intent intent = new
                        Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(Uri.parse(wunderLINQApp));
                try {
                    startActivity(intent);
                } catch ( ActivityNotFoundException ignored) {
                }
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private void leftKey(){ finish(); }

    /**
     * Connect to the specified wifi network.
     *
     * @param ssid     - The wifi network SSID
     * @param password - the wifi password
     */
    private void connectToWifi(String ssid, String password) {
        Log.d(TAG,"connectToWifi()");
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

    public void disconnectFromWifi(){
        //Unregistering network callback instance supplied to requestNetwork call disconnects phone from the connected network
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.d(TAG,"onAvailable");
            connectivityManager.bindProcessToNetwork(network);

            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI);

        }

        @Override
        public void onLosing(@NonNull Network network, int maxMsToLive) {
            super.onLosing(network, maxMsToLive);
            Log.d(TAG,"onLosing");
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.d(TAG, "losing active connection");
            connectivityManager.bindProcessToNetwork(null);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            Log.d(TAG,"onUnavailable");
        }
    };
}