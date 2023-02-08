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

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.lifecycle.Lifecycle;

import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder;
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView;
import com.arashivision.sdkmedia.player.config.InstaStabType;
import com.arashivision.sdkmedia.player.listener.PlayerViewListener;


public class PopUpClass {

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

    public void showPopupWindow(Lifecycle lifeCycle, final View view) {

        //Create a View object yourself through inflater
        LayoutInflater inflater = (LayoutInflater) view.getContext().getSystemService(view.getContext().LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.camera_preview_activity, null);

        //Specify the length and width through constants
        int width = LinearLayout.LayoutParams.MATCH_PARENT;
        int height = LinearLayout.LayoutParams.MATCH_PARENT;

        //Make Inactive Items Outside Of PopupWindow
        boolean focusable = true;

        //Create a window with our parameters
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
        //Set the location of the window on the screen
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        popupWindow.getContentView().setFocusableInTouchMode(true);

        //Setup Media Player
        mVideoLayout = popupView.findViewById(R.id.video_layout);
        mVideoLayout.setLifecycle(lifeCycle);
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
        mVideoLayout.setKeepScreenOn(true);

        popupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //Close the window when clicked
                InstaCameraManager.getInstance().closePreviewStream();
                mVideoLayout.destroy();
                popupWindow.dismiss();
                return true;
            }
        });

        popupView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                //Close the window when clicked
                InstaCameraManager.getInstance().closePreviewStream();
                mVideoLayout.destroy();
                popupWindow.dismiss();
                return true;
            }
        });
    }
}