package com.blackboxembedded.wunderlinqinsta360.util;

import com.arashivision.graphicpath.render.source.AssetInfo;
import com.arashivision.graphicpath.render.util.OffsetUtil;
import com.arashivision.insta360.basemedia.asset.WindowCropInfo;
import com.arashivision.insta360.basemedia.model.offset.OffsetData;
import com.arashivision.insta360.basemedia.util.OffsetUtils;
import com.arashivision.onecamera.OneDriverInfo;
import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder;

public class PreviewParamsUtil {

    public static CaptureParamsBuilder getCaptureParamsBuilder() {
        AssetInfo assetInfo = InstaCameraManager.getInstance().getConvertAssetInfo();
        AssetInfo stabAssetInfo = InstaCameraManager.getInstance().getStabConvertAssetInfo();
        return new CaptureParamsBuilder()
                .setCameraType(InstaCameraManager.getInstance().getCameraType())
                .setRenderModelType(InstaCameraManager.getInstance().getSupportRenderModelType())
                .setMediaOffset(InstaCameraManager.getInstance().getMediaOffset())
                .setMediaOffsetV2(InstaCameraManager.getInstance().getMediaOffsetV2())
                .setMediaOffsetV3(InstaCameraManager.getInstance().getMediaOffsetV3())
                .setOffsetData(PreviewParamsUtil.getPlayerOffsetData(assetInfo))
                .setAssetInfo(assetInfo)
                .setAssetInfo(assetInfo)
                .setStabOffset(PreviewParamsUtil.getPlayerOffsetData(stabAssetInfo).getOffsetV1())
                .setCameraSelfie(InstaCameraManager.getInstance().isCameraSelfie())
                .setGyroTimeStamp(InstaCameraManager.getInstance().getGyroTimeStamp())
                .setBatteryType(InstaCameraManager.getInstance().getBatteryType())
                .setWindowCropInfo(windowCropInfoConversion(InstaCameraManager.getInstance().getWindowCropInfo()));
    }

    public static WindowCropInfo windowCropInfoConversion(com.arashivision.onecamera.camerarequest.WindowCropInfo cameraWindowCropInfo) {
        if (cameraWindowCropInfo == null) return null;
        WindowCropInfo windowCropInfo = new WindowCropInfo();
        windowCropInfo.setDesHeight(cameraWindowCropInfo.getDstHeight());
        windowCropInfo.setDesWidth(cameraWindowCropInfo.getDstWidth());
        windowCropInfo.setSrcHeight(cameraWindowCropInfo.getSrcHeight());
        windowCropInfo.setSrcWidth(cameraWindowCropInfo.getSrcWidth());
        windowCropInfo.setOffsetX(cameraWindowCropInfo.getOffsetX());
        windowCropInfo.setOffsetY(cameraWindowCropInfo.getOffsetY());
        return windowCropInfo;
    }

    public static OffsetData getPlayerOffsetData(AssetInfo assetInfo) {
        OffsetUtil.OffsetConvertOptions options = new OffsetUtil.OffsetConvertOptions();

        switch (InstaCameraManager.getInstance().getOffsetState()) {
            case OneDriverInfo.Options.OffsetState.SPHERE_PROTECT:
            case OneDriverInfo.Options.OffsetState.PROTECT_A:
                options.enableProtectiveShellSphere = true;
                break;
            case OneDriverInfo.Options.OffsetState.DIVING_WATER:
                options.enableDivingWater = true;
                break;
            case OneDriverInfo.Options.OffsetState.PROTECT_S:
                options.enableProtectiveShell = true;
                break;
            case OneDriverInfo.Options.OffsetState.PROTECT_AUTO:
                switch (InstaCameraManager.getInstance().getOffsetDetectedType()) {
                    case OneDriverInfo.Options.OffsetDetectedType.AUTO_A:
                        options.enableProtectiveShellSphere = true;
                        break;
                    case OneDriverInfo.Options.OffsetDetectedType.AUTO_S:
                        options.enableProtectiveShell = true;
                        break;
                    case OneDriverInfo.Options.OffsetDetectedType.AUTO_AS:
                        options.enableAverageShell = true;
                        break;
                }
                break;
        }
        return OffsetUtils.convertOffset(
                assetInfo,
                new OffsetData(InstaCameraManager.getInstance().getMediaOffset(), InstaCameraManager.getInstance().getMediaOffsetV2(), InstaCameraManager.getInstance().getMediaOffsetV3(), null),
                options);
    }
}
