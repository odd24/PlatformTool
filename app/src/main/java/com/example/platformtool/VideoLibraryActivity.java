package com.example.platformtool;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoLibraryActivity extends AppCompatActivity {
    private final List<MediaEntry> items = MediaRepository.VIDEO;
    private final List<String> labels = new ArrayList<>();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private ArrayAdapter<String> adapter;
    private TextView countText;

    private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::addFile);
    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::scanFolder);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_library);
        setTitle("视频文件库");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        countText = findViewById(R.id.videoCountText);
        ListView listView = findViewById(R.id.videoLibraryListView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> openVideo(position));
        findViewById(R.id.addVideoButton).setOnClickListener(v -> filePicker.launch(new String[]{"video/*"}));
        findViewById(R.id.scanVideoButton).setOnClickListener(v -> folderPicker.launch(null));
        refreshList();
    }

    private void addFile(Uri uri) {
        if (uri == null) return;
        MediaFileHelper.persistReadPermission(getContentResolver(), uri);
        int index = MediaFileHelper.addIfMissing(items,
                new MediaEntry(uri, MediaFileHelper.displayName(getContentResolver(), uri)));
        refreshList();
        openVideo(index);
    }

    private void scanFolder(Uri treeUri) {
        if (treeUri == null) return;
        MediaFileHelper.persistReadPermission(getContentResolver(), treeUri);
        Toast.makeText(this, "正在扫描视频目录…", Toast.LENGTH_SHORT).show();
        scanExecutor.execute(() -> {
            List<MediaEntry> found = MediaDirectoryScanner.scan(getContentResolver(), treeUri, false);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                int added = MediaFileHelper.merge(items, found);
                refreshList();
                Toast.makeText(this, String.format(Locale.getDefault(),
                        "扫描完成：找到 %d 个，新增 %d 个", found.size(), added), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void refreshList() {
        labels.clear();
        for (int i = 0; i < items.size(); i++) {
            labels.add(String.format(Locale.getDefault(), "%d   %s", i + 1, items.get(i).name));
        }
        adapter.notifyDataSetChanged();
        countText.setText(String.format(Locale.getDefault(), "%d 个视频", items.size()));
    }

    private void openVideo(int index) {
        if (index < 0 || index >= items.size()) return;
        MediaEntry entry = items.get(index);
        Intent intent = new Intent(this, FullscreenVideoActivity.class);
        intent.putExtra(FullscreenVideoActivity.EXTRA_INDEX, index);
        intent.putExtra(FullscreenVideoActivity.EXTRA_URI, entry.uri.toString());
        intent.putExtra(FullscreenVideoActivity.EXTRA_NAME, entry.name);
        startActivity(intent);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onDestroy() {
        scanExecutor.shutdownNow();
        super.onDestroy();
    }
}
