package com.alfread.statusvault;

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.Holder> {
    public interface Listener {
        void onDownload(StatusItem item);
        void onSelectionChanged();
    }

    private final List<StatusItem> items;
    private final Listener listener;

    public StatusAdapter(List<StatusItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<StatusItem> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    public List<StatusItem> getItems() { return items; }

    public void selectAll(boolean selected) {
        for (StatusItem item : items) item.selected = selected;
        notifyDataSetChanged();
        listener.onSelectionChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_status, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        StatusItem item = items.get(position);
        File file = item.file;
        h.name.setText(file.getName());
        h.meta.setText(StatusRepository.prettySize(file.length()));
        h.check.setOnCheckedChangeListener(null);
        h.check.setChecked(item.selected);
        h.check.setOnCheckedChangeListener((buttonView, checked) -> {
            item.selected = checked;
            listener.onSelectionChanged();
        });
        h.download.setOnClickListener(v -> listener.onDownload(item));

        Bitmap bitmap = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                bitmap = ThumbnailUtils.createVideoThumbnail(file, new android.util.Size(640, 360), null);
            } else {
                bitmap = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
            }
        } catch (Exception ignored) {}
        if (bitmap != null) h.thumb.setImageBitmap(bitmap);
        else h.thumb.setImageDrawable(null);
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView thumb;
        CheckBox check;
        TextView name, meta;
        Button download;
        Holder(View view) {
            super(view);
            thumb = view.findViewById(R.id.img_thumb);
            check = view.findViewById(R.id.check);
            name = view.findViewById(R.id.tv_name);
            meta = view.findViewById(R.id.tv_meta);
            download = view.findViewById(R.id.btn_item_download);
        }
    }
}
