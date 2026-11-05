package com.example.stereoscreenrec;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaFormat;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Forces the system screen recorder (runs inside com.android.systemui)
 * to request stereo instead of mono for its audio capture + AAC encoder.
 * Only touches mono -> stereo values, only inside SystemUI.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "StereoScreenRec: ";
    private static volatile boolean loggedMask, loggedBuf, loggedEnc;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.android.systemui".equals(lp.packageName)) return;

        try {
            // AudioRecord format: mono mask -> stereo mask
            XposedHelpers.findAndHookMethod(AudioFormat.Builder.class, "setChannelMask",
                    int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if ((int) p.args[0] == AudioFormat.CHANNEL_IN_MONO) {
                                p.args[0] = AudioFormat.CHANNEL_IN_STEREO;
                                if (!loggedMask) {
                                    loggedMask = true;
                                    XposedBridge.log(TAG + "setChannelMask MONO -> STEREO");
                                }
                            }
                        }
                    });

            // buffer sizing must match the stereo format
            XposedHelpers.findAndHookMethod(AudioRecord.class, "getMinBufferSize",
                    int.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if ((int) p.args[1] == AudioFormat.CHANNEL_IN_MONO) {
                                p.args[1] = AudioFormat.CHANNEL_IN_STEREO;
                                if (!loggedBuf) {
                                    loggedBuf = true;
                                    XposedBridge.log(TAG + "getMinBufferSize MONO -> STEREO");
                                }
                            }
                        }
                    });

            // AAC encoder format: 1 channel -> 2
            XposedHelpers.findAndHookMethod(MediaFormat.class, "createAudioFormat",
                    String.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if ("audio/mp4a-latm".equals(p.args[0]) && (int) p.args[2] == 1) {
                                p.args[2] = 2;
                                if (!loggedEnc) {
                                    loggedEnc = true;
                                    XposedBridge.log(TAG + "AAC channel count 1 -> 2");
                                }
                            }
                        }
                    });

            XposedBridge.log(TAG + "hooks installed in SystemUI");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "failed: " + t);
        }
    }
}
