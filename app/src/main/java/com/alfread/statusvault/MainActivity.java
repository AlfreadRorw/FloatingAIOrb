package com.alfread.statusvault;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements StatusAdapter.Listener {
    private static final int REQ_READ = 7001;

    private LinearLayout screenStatus, screenHistory;
    private android.view.View screenSettings;
    private TextView tvAccessTitle, tvAccessDetail, tvCount, tvSelected, tvHistoryStats, tvSourcePath;
    private ProgressBar progress;
    private Button btnAccess, btnDownloadSelected, btnScan;
    private StatusAdapter statusAdapter;
    private HistoryAdapter historyAdapter;
    private final List<StatusItem> statusItems = new ArrayList<>();
    private final List<HistoryItem> historyItems = new ArrayList<>();
    private HistoryStore historyStore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        historyStore = new HistoryStore(this);

        bindViews();
        setupLists();
        setupDock();
        setupActions();
        loadHistory();
        updateAccessCard();
    }

    private void bindViews() {
        screenStatus = findViewById(R.id.screen_status);
        screenHistory = findViewById(R.id.screen_history);
        screenSettings = findViewById(R.id.screen_settings);
        tvAccessTitle = findViewById(R.id.tv_access_title);
        tvAccessDetail = findViewById(R.id.tv_access_detail);
        btnAccess = findViewById(R.id.btn_access);
        tvCount = findViewById(R.id.tv_count);
        tvSelected = findViewById(R.id.tv_selected);
        tvHistoryStats = findViewById(R.id.tv_history_stats);
        tvSourcePath = findViewById(R.id.tv_source_path);
        progress = findViewById(R.id.progress);
        btnDownloadSelected = findViewById(R.id.btn_download_selected);
        btnScan = findViewById(R.id.btn_scan);
    }

    private void setupLists() {
        statusAdapter = new StatusAdapter(statusItems, this);
        RecyclerView statusRecycler = findViewById(R.id.recycler_status);
        statusRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        statusRecycler.setAdapter(statusAdapter);

        historyAdapter = new HistoryAdapter(historyItems);
        RecyclerView historyRecycler = findViewById(R.id.recycler_history);
        historyRecycler.setLayoutManager(new LinearLayoutManager(this));
        historyRecycler.setAdapter(historyAdapter);
    }

    private void setupDock() {
        findViewById(R.id.dock_status).setOnClickListener(v -> showScreen(0));
        findViewById(R.id.dock_history).setOnClickListener(v -> showScreen(1));
        findViewById(R.id.dock_settings).setOnClickListener(v -> showScreen(2));
    }

    private void setupActions() {
        btnAccess.setOnClickListener(v -> requestStorageAccess());
        btnScan.setOnClickListener(v -> scanAsync());
        findViewById(R.id.btn_select_all).setOnClickListener(v -> {
            boolean all = !statusItems.isEmpty();
            for (StatusItem item : statusItems) {
                if (!item.selected) { all = true; break; }
                all = false;
            }
            statusAdapter.selectAll(all);
        });
        btnDownloadSelected.setOnClickListener(v -> downloadSelected());
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear history")
                    .setMessage("Delete the app's download history? Files in Download will not be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", (d, w) -> {
                        historyStore.clear();
                        loadHistory();
                    }).show();
        });
        findViewById(R.id.btn_open_folder).setOnClickListener(v -> openDownloadFolder());
        findViewById(R.id.btn_storage_settings).setOnClickListener(v -> requestStorageAccess());

        RadioButton move = findViewById(R.id.radio_move);
        RadioButton copy = findViewById(R.id.radio_copy);
        move.setOnClickListener(v -> getPreferences(MODE_PRIVATE).edit().putBoolean("move_mode", true).apply());
        copy.setOnClickListener(v -> getPreferences(MODE_PRIVATE).edit().putBoolean("move_mode", false).apply());
        boolean moveMode = getPreferences(MODE_PRIVATE).getBoolean("move_mode", true);
        move.setChecked(moveMode);
        copy.setChecked(!moveMode);
    }

    private void showScreen(int which) {
        screenStatus.setVisibility(which == 0 ? View.VISIBLE : View.GONE);
        screenHistory.setVisibility(which == 1 ? View.VISIBLE : View.GONE);
        screenSettings.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
        if (which == 1) loadHistory();
        if (which == 0 && hasStorageAccess()) scanAsync();
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateAccessCard() {
        boolean granted = hasStorageAccess();
        if (granted) {
            tvAccessTitle.setText("Storage ready");
            tvAccessDetail.setText(StatusRepository.SOURCE);
            btnAccess.setText("Manage access");
            scanAsync();
        } else {
            tvAccessTitle.setText("Storage access required");
            tvAccessDetail.setText("Allow file access so the app can scan the WhatsApp .Statuses folder.");
            btnAccess.setText("Grant access");
        }
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessCard();
    }

    private void scanAsync() {
        if (!hasStorageAccess()) return;
        btnScan.setEnabled(false);
        tvCount.setText("Scanning…");
        new Thread(() -> {
            final List<StatusItem> result = StatusRepository.scan();
            runOnUiThread(() -> {
                statusAdapter.setItems(result);
                btnScan.setEnabled(true);
                updateSelectionText();
                tvCount.setText(result.size() + (result.size() == 1 ? " video" : " videos"));
                if (result.isEmpty()) tvCount.setText("0 videos • check WhatsApp Status availability");
            });
        }).start();
    }

    @Override public void onDownload(StatusItem item) {
        downloadItems(java.util.Collections.singletonList(item));
    }

    @Override public void onSelectionChanged() {
        updateSelectionText();
    }

    private void updateSelectionText() {
        int selected = 0;
        for (StatusItem item : statusItems) if (item.selected) selected++;
        tvSelected.setText(selected + (selected == 1 ? " selected" : " selected"));
        btnDownloadSelected.setEnabled(selected > 0 && hasStorageAccess());
    }

    private void downloadSelected() {
        List<StatusItem> selected = new ArrayList<>();
        for (StatusItem item : statusItems) if (item.selected) selected.add(item);
        if (selected.isEmpty()) {
            toast("Select at least one video");
            return;
        }
        downloadItems(selected);
    }

    private void downloadItems(List<StatusItem> items) {
        if (!hasStorageAccess()) {
            requestStorageAccess();
            return;
        }
        boolean moveMode = getPreferences(MODE_PRIVATE).getBoolean("move_mode", true);
        setDownloading(true);
        new Thread(() -> {
            int done = 0;
            for (StatusItem item : items) {
                final int itemIndex = done;
                long sourceSize = item.file.length();
                try {
                    StatusRepository.transfer(this, item.file, moveMode, percent -> {
                        int overall = (itemIndex * 100 + percent) / Math.max(1, items.size());
                        runOnUiThread(() -> progress.setProgress(overall));
                    });
                    historyStore.add(new HistoryItem(
                            item.file.getName(),
                            StatusRepository.DESTINATION,
                            sourceSize,
                            System.currentTimeMillis(),
                            moveMode ? "MOVE" : "COPY",
                            "DONE"
                    ));
                    item.selected = false;
                } catch (Exception e) {
                    historyStore.add(new HistoryItem(
                            item.file.getName(),
                            StatusRepository.DESTINATION,
                            sourceSize,
                            System.currentTimeMillis(),
                            moveMode ? "MOVE" : "COPY",
                            "FAILED"
                    ));
                }
                done++;
            }
            runOnUiThread(() -> {
                setDownloading(false);
                loadHistory();
                scanAsync();
                toast("Download job finished");
            });
        }).start();
    }

    private void setDownloading(boolean downloading) {
        progress.setVisibility(downloading ? View.VISIBLE : View.GONE);
        progress.setProgress(0);
        btnDownloadSelected.setEnabled(!downloading);
        btnScan.setEnabled(!downloading);
    }

    private void loadHistory() {
        historyItems.clear();
        historyItems.addAll(historyStore.load());
        historyAdapter.setItems(historyItems);
        int done = 0;
        for (HistoryItem item : historyItems) if ("DONE".equals(item.result)) done++;
        tvHistoryStats.setText(historyItems.size() + " records • " + done + " completed");
    }

    private void openDownloadFolder() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "resource/folder");
                startActivity(intent);
            } catch (Exception ignored) {
                toast("Open your file manager and go to Download");
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ) updateAccessCard();
    }
}
