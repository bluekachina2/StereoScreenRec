package com.example.stereoscreenrec;

import android.media.MediaRecorder;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * Lets the user pick the video codec, bitrate and frame rate used by the system screen
 * recorder. The recorder configures a MediaRecorder (setVideoEncoder /
 * setVideoEncodingBitRate / setVideoFrameRate); we override those arguments with the values saved
 * in the module's settings screen. "Default" (0) leaves the recorder alone.
 *
 * Install from Hook.handleLoadPackage() after the SystemUI package check.
 */
public final class Hook_video {
    private static final String TAG = "StereoScreenRec: ";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private static volatile XSharedPreferences prefs;

    private Hook_video() {
    }

    private static XSharedPreferences prefs() {
        XSharedPreferences p = prefs;
        if (p == null) {
            p = new XSharedPreferences(VideoPrefs.MODULE_PACKAGE, VideoPrefs.FILE);
            prefs = p;
        } else if (p.hasFileChanged()) {
            p.reload();
        }
        return p;
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            XposedBridge.hookAllMethods(MediaRecorder.class, "setVideoEncoder",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length != 1
                                        || !(param.args[0] instanceof Integer)) {
                                    return;
                                }
                                XSharedPreferences p = prefs();
                                int wanted = p.getInt(VideoPrefs.KEY_CODEC,
                                        VideoPrefs.CODEC_DEFAULT);
                                int original = (Integer) param.args[0];
                                XposedBridge.log(TAG + "setVideoEncoder(" + original
                                        + ") requested by recorder; module setting=" + wanted
                                        + (p.getFile().canRead() ? "" : " (prefs file NOT readable)"));
                                if (wanted != VideoPrefs.CODEC_DEFAULT && wanted != original) {
                                    param.args[0] = Integer.valueOf(wanted);
                                    XposedBridge.log(TAG + "video encoder " + original
                                            + " -> " + wanted);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "setVideoEncoder hook failed: " + t);
                            }
                        }
                    });

            XposedBridge.hookAllMethods(MediaRecorder.class, "setVideoEncodingBitRate",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length != 1
                                        || !(param.args[0] instanceof Integer)) {
                                    return;
                                }
                                float mbps = prefs().getFloat(VideoPrefs.KEY_BITRATE_MBPS, 0f);
                                int original = (Integer) param.args[0];
                                XposedBridge.log(TAG + "setVideoEncodingBitRate(" + original
                                        + ") requested by recorder; module setting=" + mbps + " Mbps");
                                if (mbps > 0f) {
                                    int bps = Math.round(mbps * 1000000f);
                                    if (bps != original) {
                                        param.args[0] = Integer.valueOf(bps);
                                        XposedBridge.log(TAG + "video bitrate " + original
                                                + " -> " + bps);
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "setVideoEncodingBitRate hook failed: " + t);
                            }
                        }
                    });

            XposedBridge.hookAllMethods(MediaRecorder.class, "setVideoFrameRate",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length != 1
                                        || !(param.args[0] instanceof Integer)) {
                                    return;
                                }
                                int fps = prefs().getInt(VideoPrefs.KEY_FPS, 0);
                                int original = (Integer) param.args[0];
                                XposedBridge.log(TAG + "setVideoFrameRate(" + original
                                        + ") requested by recorder; module setting=" + fps);
                                if (fps > 0 && fps != original) {
                                    param.args[0] = Integer.valueOf(fps);
                                    XposedBridge.log(TAG + "video fps " + original + " -> " + fps);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "setVideoFrameRate hook failed: " + t);
                            }
                        }
                    });

            XposedBridge.log(TAG + "video codec/bitrate/fps hooks installed");
        } catch (Throwable t) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + "failed to install video hooks: " + t);
        }
    }
}
