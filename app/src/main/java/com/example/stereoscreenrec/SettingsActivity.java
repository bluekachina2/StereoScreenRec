package com.example.stereoscreenrec;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Minimal settings screen: video codec, bitrate and frame rate for the system screen recorder. */
public class SettingsActivity extends Activity {
    private static final String[] CODEC_LABELS = {
            "Default (don't change)", "H.264 / AVC", "H.265 / HEVC"
    };
    private static final int[] CODEC_VALUES = {
            VideoPrefs.CODEC_DEFAULT, VideoPrefs.CODEC_H264, VideoPrefs.CODEC_HEVC
    };

    private SharedPreferences prefs;
    private Spinner codecSpinner;
    private EditText bitrateEdit;
    private EditText fpsEdit;

    @SuppressWarnings({"deprecation", "WorldReadableFiles"})
    private SharedPreferences openPrefs() {
        try {
            // LSPosed makes MODE_WORLD_READABLE usable for module settings.
            return getSharedPreferences(VideoPrefs.FILE, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            return getSharedPreferences(VideoPrefs.FILE, Context.MODE_PRIVATE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = openPrefs();

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("System screen recorder – video");
        title.setTextSize(20f);
        root.addView(title);

        TextView codecLabel = new TextView(this);
        codecLabel.setText("Video codec");
        codecLabel.setPadding(0, pad, 0, 0);
        root.addView(codecLabel);

        codecSpinner = new Spinner(this);
        codecSpinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, CODEC_LABELS));
        int savedCodec = prefs.getInt(VideoPrefs.KEY_CODEC, VideoPrefs.CODEC_DEFAULT);
        for (int i = 0; i < CODEC_VALUES.length; i++) {
            if (CODEC_VALUES[i] == savedCodec) {
                codecSpinner.setSelection(i);
            }
        }
        root.addView(codecSpinner);

        TextView bitrateLabel = new TextView(this);
        bitrateLabel.setText("Video bitrate (Mbit/s, 0 = don't change)");
        bitrateLabel.setPadding(0, pad, 0, 0);
        root.addView(bitrateLabel);

        bitrateEdit = new EditText(this);
        bitrateEdit.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        float savedMbps = prefs.getFloat(VideoPrefs.KEY_BITRATE_MBPS, 0f);
        bitrateEdit.setText(savedMbps > 0f ? trim(savedMbps) : "0");
        root.addView(bitrateEdit);

        TextView fpsLabel = new TextView(this);
        fpsLabel.setText("Frame rate (fps, 0 = don't change)");
        fpsLabel.setPadding(0, pad, 0, 0);
        root.addView(fpsLabel);

        fpsEdit = new EditText(this);
        fpsEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        fpsEdit.setText(String.valueOf(prefs.getInt(VideoPrefs.KEY_FPS, 0)));
        root.addView(fpsEdit);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        root.addView(save);

        TextView note = new TextView(this);
        note.setText("Applies to the next recording. If the module is enabled for "
                + "System UI in LSPosed, no reboot is needed after changing these values. "
                + "Set both to default to restore stock behavior.");
        note.setPadding(0, pad, 0, 0);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private static String trim(float f) {
        return f == (long) f ? String.valueOf((long) f) : String.valueOf(f);
    }

    private void save() {
        float mbps;
        try {
            String s = bitrateEdit.getText().toString().trim();
            mbps = s.isEmpty() ? 0f : Float.parseFloat(s);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid bitrate", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mbps < 0f || mbps > 200f) {
            Toast.makeText(this, "Bitrate must be between 0 and 200 Mbit/s",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int fps;
        try {
            String s = fpsEdit.getText().toString().trim();
            fps = s.isEmpty() ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid frame rate", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fps < 0 || fps > 240) {
            Toast.makeText(this, "Frame rate must be between 0 and 240 fps",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int codec = CODEC_VALUES[codecSpinner.getSelectedItemPosition()];
        prefs.edit()
                .putInt(VideoPrefs.KEY_CODEC, codec)
                .putFloat(VideoPrefs.KEY_BITRATE_MBPS, mbps)
                .putInt(VideoPrefs.KEY_FPS, fps)
                .commit();

        // Fallback if MODE_WORLD_READABLE was refused: loosen file permissions.
        try {
            File dir = getFilesDir().getParentFile();
            File f = new File(new File(dir, "shared_prefs"), VideoPrefs.FILE + ".xml");
            if (dir != null) {
                dir.setExecutable(true, false);
            }
            f.setReadable(true, false);
        } catch (Throwable ignored) {
        }

        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }
}
