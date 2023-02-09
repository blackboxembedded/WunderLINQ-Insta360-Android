package com.blackboxembedded.wunderlinqinsta360;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener;
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder;
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView;
import com.arashivision.sdkmedia.player.config.InstaStabType;
import com.arashivision.sdkmedia.player.listener.PlayerViewListener;

public class PreviewActivity extends BaseObserveCameraActivity implements IPreviewStatusListener {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    private InstaCapturePlayerView mVideoLayout;

    private CaptureParamsBuilder createParams() {
        CaptureParamsBuilder builder = new CaptureParamsBuilder()
                .setCameraType(InstaCameraManager.getInstance().getCameraType())
                .setMediaOffset(InstaCameraManager.getInstance().getMediaOffset())
                .setMediaOffsetV2(InstaCameraManager.getInstance().getMediaOffsetV2())
                .setMediaOffsetV3(InstaCameraManager.getInstance().getMediaOffsetV3())
                .setCameraSelfie(InstaCameraManager.getInstance().isCameraSelfie())
                .setGyroTimeStamp(InstaCameraManager.getInstance().getGyroTimeStamp())
                .setBatteryType(InstaCameraManager.getInstance().getBatteryType())
                .setStabType(InstaStabType.STAB_TYPE_AUTO)
                .setStabEnabled(false);
        // Normal Mode
        builder.setRenderModelType(CaptureParamsBuilder.RENDER_MODE_AUTO);

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
        mVideoLayout.setLifecycle(getLifecycle());

        InstaCameraManager.getInstance().startPreviewStream(InstaCameraManager.getInstance().getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_NORMAL).get(0));
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

        InstaCameraManager.getInstance().setPreviewStatusChangedListener(this);
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
                } catch ( ActivityNotFoundException ex  ) {
                }
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public void onOpening() {
        // Preview Opening
        Log.d(TAG,"onOpening()");
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
    }

    private void leftKey(){ finish(); }
}