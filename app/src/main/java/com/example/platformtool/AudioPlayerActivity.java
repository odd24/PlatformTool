package com.example.platformtool;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioPlayerActivity extends PlatformActivity {
    private static final int LOOP_ONE = 1;
    private static final int LOOP_LIST = 2;

    private final List<MediaEntry> items = MediaRepository.AUDIO;
    private final List<String> labels = new ArrayList<>();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ArrayAdapter<String> adapter;
    private ListView listView;
    private TextView countText;
    private TextView nowPlayingText;
    private TextView timeText;
    private SeekBar seekBar;
    private Button playPauseButton;
    private Button previousButton;
    private Button nextButton;
    private Button scanButton;
    private Spinner loopSpinner;
    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean prepared;
    private boolean userSeeking;
    private boolean autoPlay;

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::addFile);
    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::scanFolder);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        setTitle("音乐播放器");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        bindViews();
        setupListAndControls();
        MediaLibraryStore.ensureLoaded(this, items, true);
        refreshList();
        handler.post(progressUpdater);
    }

    private void bindViews() {
        listView = findViewById(R.id.audioListView);
        countText = findViewById(R.id.audioCountText);
        nowPlayingText = findViewById(R.id.audioNowPlayingText);
        timeText = findViewById(R.id.audioBottomTimeText);
        seekBar = findViewById(R.id.audioBottomSeekBar);
        playPauseButton = findViewById(R.id.audioPlayPauseButton);
        previousButton = findViewById(R.id.audioPreviousButton);
        nextButton = findViewById(R.id.audioNextButton);
        scanButton = findViewById(R.id.scanAudioButton);
        loopSpinner = findViewById(R.id.audioBottomLoopSpinner);
    }

    private void setupListAndControls() {
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, labels);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> load(position, true));
        findViewById(R.id.addAudioButton).setOnClickListener(v -> filePicker.launch(new String[]{"audio/*"}));
        scanButton.setOnClickListener(v -> folderPicker.launch(null));

        loopSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"不循环", "单曲循环", "列表循环"}));
        loopSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (prepared && player != null) player.setLooping(position == LOOP_ONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        playPauseButton.setOnClickListener(v -> togglePlayback());
        previousButton.setOnClickListener(v -> playRelative(-1));
        nextButton.setOnClickListener(v -> playRelative(1));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (prepared && player != null) player.seekTo(seekBar.getProgress());
                userSeeking = false;
                updateProgress();
            }
        });
    }

    private void addFile(Uri uri) {
        if (uri == null) return;
        MediaFileHelper.persistReadPermission(getContentResolver(), uri);
        int index = MediaFileHelper.addIfMissing(items,
                new MediaEntry(uri, MediaFileHelper.displayName(getContentResolver(), uri)));
        MediaLibraryStore.save(this, items, true);
        refreshList();
        load(index, false);
    }

    private void scanFolder(Uri treeUri) {
        if (treeUri == null) return;
        MediaFileHelper.persistReadPermission(getContentResolver(), treeUri);
        scanButton.setEnabled(false);
        scanButton.setText("扫描中…");
        Toast.makeText(this, "正在扫描音乐目录…", Toast.LENGTH_SHORT).show();
        scanExecutor.execute(() -> {
            List<MediaEntry> found = MediaDirectoryScanner.scan(getContentResolver(), treeUri, true);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                int added = MediaFileHelper.merge(items, found);
                MediaLibraryStore.save(this, items, true);
                refreshList();
                scanButton.setEnabled(true);
                scanButton.setText("扫描目录");
                Toast.makeText(this, String.format(Locale.getDefault(),
                        "扫描完成：找到 %d 首，新增 %d 首", found.size(), added), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void refreshList() {
        labels.clear();
        for (int i = 0; i < items.size(); i++) labels.add(String.format(Locale.getDefault(), "%d   %s", i + 1, items.get(i).name));
        adapter.notifyDataSetChanged();
        countText.setText(String.format(Locale.getDefault(), "%d 首音乐", items.size()));
        if (currentIndex >= 0 && currentIndex < items.size()) listView.setItemChecked(currentIndex, true);
    }

    private void load(int index, boolean shouldAutoPlay) {
        if (index < 0 || index >= items.size()) return;
        releasePlayer();
        currentIndex = index;
        autoPlay = shouldAutoPlay;
        MediaEntry entry = items.get(index);
        nowPlayingText.setText(entry.name);
        listView.setItemChecked(index, true);
        listView.smoothScrollToPosition(index);
        setControlsEnabled(false);
        timeText.setText("正在加载…");
        player = new MediaPlayer();
        player.setOnPreparedListener(mediaPlayer -> {
            prepared = true;
            mediaPlayer.setLooping(loopSpinner.getSelectedItemPosition() == LOOP_ONE);
            seekBar.setMax(mediaPlayer.getDuration());
            setControlsEnabled(true);
            updateProgress();
            if (autoPlay) mediaPlayer.start();
            updatePlayButton();
        });
        player.setOnCompletionListener(mediaPlayer -> playAfterCompletion());
        player.setOnErrorListener((mediaPlayer, what, extra) -> {
            Toast.makeText(this, "音乐文件无法播放", Toast.LENGTH_SHORT).show();
            releasePlayer();
            setControlsEnabled(false);
            return true;
        });
        try {
            player.setDataSource(this, entry.uri);
            player.prepareAsync();
        } catch (IOException | RuntimeException exception) {
            Toast.makeText(this, "无法打开音乐文件", Toast.LENGTH_SHORT).show();
            releasePlayer();
            setControlsEnabled(false);
        }
    }

    private void playAfterCompletion() {
        if (currentIndex + 1 < items.size()) {
            load(currentIndex + 1, true);
        } else if (!items.isEmpty() && loopSpinner.getSelectedItemPosition() == LOOP_LIST) {
            load(0, true);
        } else if (player != null) {
            player.seekTo(0);
            updateProgress();
            updatePlayButton();
        }
    }

    private void togglePlayback() {
        if (!prepared || player == null) return;
        if (player.isPlaying()) player.pause(); else player.start();
        updatePlayButton();
    }

    private void playRelative(int offset) {
        if (items.isEmpty()) return;
        int next = currentIndex < 0 ? 0 : (currentIndex + offset + items.size()) % items.size();
        load(next, true);
    }

    private void updateProgress() {
        if (!prepared || player == null) return;
        try {
            int position = player.getCurrentPosition();
            int duration = player.getDuration();
            if (!userSeeking) seekBar.setProgress(position);
            timeText.setText(formatTime(position) + " / " + formatTime(duration));
            updatePlayButton();
        } catch (IllegalStateException ignored) { }
    }

    private void updatePlayButton() {
        playPauseButton.setText(prepared && player != null && player.isPlaying() ? "暂停" : "播放");
    }

    private void setControlsEnabled(boolean enabled) {
        seekBar.setEnabled(enabled);
        playPauseButton.setEnabled(enabled);
        previousButton.setEnabled(enabled && items.size() > 1);
        nextButton.setEnabled(enabled && items.size() > 1);
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            try { player.reset(); } catch (RuntimeException ignored) { }
            player.release();
            player = null;
        }
    }

    private static String formatTime(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainder = seconds % 60;
        return hours > 0 ? String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.getDefault(), "%02d:%02d", minutes, remainder);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        scanExecutor.shutdownNow();
        releasePlayer();
        super.onDestroy();
    }
}
