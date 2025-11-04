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
package com.blackboxembedded.wunderlinqinsta360.util;

import com.arashivision.graphicpath.render.source.AssetInfo;
import com.arashivision.graphicpath.render.util.OffsetUtil;
import com.arashivision.insta360.basemedia.asset.WindowCropInfo;
import com.arashivision.insta360.basemedia.model.offset.OffsetData;
import com.arashivision.insta360.basemedia.util.OffsetUtils;
import com.arashivision.onecamera.OneDriverInfo;
import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkcamera.camera.model.CaptureMode;
import com.arashivision.sdkmedia.params.RenderModel;
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilderV2;

public class PreviewParamsUtil {

    public static CaptureParamsBuilderV2 getCaptureParamsBuilder() {
        CaptureParamsBuilderV2 builder = new CaptureParamsBuilderV2();

        // Match sample behavior:
        builder.setRenderModel(RenderModel.AUTO);
        // Optional (if you want plane stitch layout):
        // builder.renderModel = RenderModel.PLANE_STITCH;
        // builder.setScreenRatio(2, 1);

        // Keep FlowState cache small/zero unless you need it
        builder.setStabCacheFrameNum(0);

        return builder;
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
                new OffsetData(
                        InstaCameraManager.getInstance().getMediaOffset(),
                        InstaCameraManager.getInstance().getMediaOffsetV2(),
                        InstaCameraManager.getInstance().getMediaOffsetV3(),
                        null
                ),
                options
        );
    }
}