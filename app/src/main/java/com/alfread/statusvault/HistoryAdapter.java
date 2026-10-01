package com.alfread.statusvault;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
    private final List<HistoryItem> items;
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    public HistoryAdapter(List<HistoryItem> items) { this.items = items; }
    public void setItems(List<HistoryItem> next) { items.clear(); items.addAll(next); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        HistoryItem item = items.get(position);
        h.name.setText(item.name);
        h.meta.setText(item.mode + " • " + StatusRepository.prettySize(item.size) + " • " + dateFormat.format(new Date(item.timestamp)));
        h.status.setText(item.result);
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView name, meta, status;
        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.tv_history_name);
            meta = v.findViewById(R.id.tv_history_meta);
            status = v.findViewById(R.id.tv_history_status);
        }
    }
}
