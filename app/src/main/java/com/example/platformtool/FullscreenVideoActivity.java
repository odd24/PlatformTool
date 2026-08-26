package com.example.platformtool;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class FullscreenVideoActivity extends AppCompatActivity {
    public static final String EXTRA_INDEX = "video_index";
    public static final String EXTRA_URI = "video_uri";
    public static final String EXTRA_NAME = "video_name";
    private static final int LOOP_ONE = 1;
    private static final int LOOP_LIST = 2;

    private final List<MediaEntry> items = MediaRepository.VIDEO;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView videoView;
    private TextView titleText;
    private TextView timeText;
    private SeekBar seekBar;
    private Button playPauseButton;
    private Button previousButton;
    private Button nextButton;
    private Spinner loopSpinner;
    private MediaPlayer mediaPlayer;
    private int currentIndex;
    private boolean prepared;
    private boolean userSeeking;

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_video);
        bindViews();
        setupControls();
        resolveInitialVideo();
        enterImmersiveMode();
        handler.post(progressUpdater);
    }

    private void bindViews() {
        videoView = findViewById(R.id.fullscreenVideoView);
        titleText = findViewById(R.id.fullscreenVideoTitle);
        timeText = findViewById(R.id.fullscreenVideoTimeText);
        seekBar = findViewById(R.id.fullscreenVideoSeekBar);
        playPauseButton = findViewById(R.id.videoPlayPauseButton);
        previousButton = findViewById(R.id.videoPreviousButton);
        nextButton = findViewById(R.id.videoNextButton);
        loopSpinner = findViewById(R.id.fullscreenLoopSpinner);
    }

    private void setupControls() {
        findViewById(R.id.exitFullscreenButton).setOnClickListener(v -> finish());
        playPauseButton.setOnClickListener(v -> {
            if (!prepared) return;
            if (videoView.isPlaying()) videoView.pause(); else videoView.start();
            updatePlayButton();
        });
        previousButton.setOnClickListener(v -> playRelative(-1));
        nextButton.setOnClickListener(v -> playRelative(1));
        loopSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"不循环", "单个循环", "列表循环"}));
        loopSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (prepared && mediaPlayer != null) mediaPlayer.setLooping(position == LOOP_ONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (prepared) videoView.seekTo(seekBar.getProgress());
                userSeeking = false;
                updateProgress();
            }
        });
    }

    private void resolveInitialVideo() {
        currentIndex = getIntent().getIntExtra(EXTRA_INDEX, -1);
        String fallbackUri = getIntent().getStringExtra(EXTRA_URI);
        String fallbackName = getIntent().getStringExtra(EXTRA_NAME);
        if (currentIndex < 0 || currentIndex >= items.size()) {
            if (fallbackUri == null) {
                Toast.makeText(this, "没有可播放的视频", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            currentIndex = MediaFileHelper.addIfMissing(items,
                    new MediaEntry(Uri.parse(fallbackUri), fallbackName));
        }
        load(currentIndex);
    }

    private void load(int index) {
        if (index < 0 || index >= items.size()) return;
        currentIndex = index;
        prepared = false;
        mediaPlayer = null;
        MediaEntry entry = items.get(index);
        titleText.setText(entry.name);
        timeText.setText("正在加载…");
        setControlsEnabled(false);
        videoView.stopPlayback();
        videoView.setOnPreparedListener(player -> {
            mediaPlayer = player;
            prepared = true;
            player.setLooping(loopSpinner.getSelectedItemPosition() == LOOP_ONE);
            seekBar.setMax(player.getDuration());
            setControlsEnabled(true);
            videoView.start();
            updateProgress();
        });
        videoView.setOnCompletionListener(player -> playAfterCompletion());
        videoView.setOnErrorListener((player, what, extra) -> {
            prepared = false;
            mediaPlayer = null;
            setControlsEnabled(false);
            Toast.makeText(this, "该视频无法播放", Toast.LENGTH_SHORT).show();
            return true;
        });
        videoView.setVideoURI(entry.uri);
        videoView.requestFocus();
    }

    private void playAfterCompletion() {
        if (currentIndex + 1 < items.size()) {
            load(currentIndex + 1);
        } else if (!items.isEmpty() && loopSpinner.getSelectedItemPosition() == LOOP_LIST) {
            load(0);
        } else {
            videoView.seekTo(0);
            updateProgress();
            updatePlayButton();
        }
    }

    private void playRelative(int offset) {
        if (items.isEmpty()) return;
        int next = (currentIndex + offset + items.size()) % items.size();
        load(next);
    }

    private void updateProgress() {
        if (!prepared) return;
        int position = videoView.getCurrentPosition();
        int duration = videoView.getDuration();
        if (!userSeeking) seekBar.setProgress(position);
        timeText.setText(formatTime(position) + " / " + formatTime(duration));
        updatePlayButton();
    }

    private void updatePlayButton() {
        playPauseButton.setText(prepared && videoView.isPlaying() ? "暂停" : "播放");
    }

    private void setControlsEnabled(boolean enabled) {
        seekBar.setEnabled(enabled);
        playPauseButton.setEnabled(enabled);
        previousButton.setEnabled(enabled && items.size() > 1);
        nextButton.setEnabled(enabled && items.size() > 1);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private static String formatTime(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainder = seconds % 60;
        return hours > 0 ? String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.getDefault(), "%02d:%02d", minutes, remainder);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        videoView.stopPlayback();
        super.onDestroy();
    }
}
