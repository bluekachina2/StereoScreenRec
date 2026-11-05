package com.example.stereoscreenrec;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Corrects AAC input-buffer presentation timestamps for the stereo
 * SystemUI screen-recorder modification.
 *
 * Install this hook from the existing Hook.handleLoadPackage(), after
 * confirming that the loaded package is com.android.systemui.
 */
public final class Hook_timestamp_fix {
    private static final String TAG = "StereoScreenRec";
    private static final String AAC_MIME = "audio/mp4a-latm";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    /*
     * Weak keys avoid retaining MediaCodec instances after recording ends.
     * The value is the first PTS seen for that codec, so we halve elapsed
     * time while preserving a non-zero starting timestamp if one is used.
     */
    private static final Map<MediaCodec, Long> AAC_ENCODERS =
            Collections.synchronizedMap(new WeakHashMap<MediaCodec, Long>());

    private Hook_timestamp_fix() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            XposedBridge.hookAllMethods(MediaCodec.class, "configure",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                MediaFormat format = null;
                                Integer flags = null;

                                for (Object arg : param.args) {
                                    if (arg instanceof MediaFormat) {
                                        format = (MediaFormat) arg;
                                    } else if (arg instanceof Integer) {
                                        // configure()'s flags argument is the
                                        // final integer argument in the API
                                        // overloads used by Android.
                                        flags = (Integer) arg;
                                    }
                                }

                                if (format == null || flags == null) {
                                    return;
                                }

                                String mime = format.getString(MediaFormat.KEY_MIME);
                                boolean isAac = AAC_MIME.equalsIgnoreCase(mime);
                                boolean isEncoder =
                                        (flags & MediaCodec.CONFIGURE_FLAG_ENCODE) != 0;

                                // Only correct when our hook really made the encoder
                                // stereo; otherwise the recorder's math is right.
                                boolean isStereo = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                                        && format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2;

                                if (isAac && isEncoder && isStereo) {
                                    MediaCodec codec = (MediaCodec) param.thisObject;
                                    AAC_ENCODERS.put(codec, null);
                                    XposedBridge.log(TAG + ": tracking stereo AAC encoder for timestamp correction");
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": unable to inspect MediaCodec.configure(): " + t);
                            }
                        }
                    });

            XposedBridge.hookAllMethods(MediaCodec.class, "queueInputBuffer",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length < 5
                                        || !(param.args[3] instanceof Long)) {
                                    return;
                                }

                                MediaCodec codec = (MediaCodec) param.thisObject;
                                if (!AAC_ENCODERS.containsKey(codec)) {
                                    return;
                                }

                                long ptsUs = (Long) param.args[3];
                                Long firstPts = AAC_ENCODERS.get(codec);

                                if (firstPts == null) {
                                    firstPts = ptsUs;
                                    AAC_ENCODERS.put(codec, firstPts);
                                }

                                long correctedPtsUs =
                                        firstPts + ((ptsUs - firstPts) / 2L);

                                if (correctedPtsUs != ptsUs) {
                                    param.args[3] = Long.valueOf(correctedPtsUs);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": unable to correct AAC input timestamp: " + t);
                            }
                        }
                    });

            XposedBridge.log(TAG + ": AAC timestamp correction hooks installed");
        } catch (Throwable t) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": failed to install AAC timestamp correction hooks: " + t);
        }
    }
}
