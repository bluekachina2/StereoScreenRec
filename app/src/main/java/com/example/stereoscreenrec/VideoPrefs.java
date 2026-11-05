package com.example.stereoscreenrec;

/** Shared names/values between the settings screen and the SystemUI hook. */
public final class VideoPrefs {
    public static final String MODULE_PACKAGE = "com.example.stereoscreenrec";
    public static final String FILE = "settings";

    /** MediaRecorder.VideoEncoder value to force; 0 = leave the recorder's choice. */
    public static final String KEY_CODEC = "video_codec";
    /** Video bitrate in Mbit/s (decimal allowed); 0 = leave the recorder's choice. */
    public static final String KEY_BITRATE_MBPS = "video_bitrate_mbps";

    /** Video frame rate in fps; 0 = leave the recorder's choice. */
    public static final String KEY_FPS = "video_fps";

    // Same numbers as MediaRecorder.VideoEncoder.H264 / HEVC (inlined constants).
    public static final int CODEC_DEFAULT = 0;
    public static final int CODEC_H264 = 2;
    public static final int CODEC_HEVC = 5;

    private VideoPrefs() {
    }
}
