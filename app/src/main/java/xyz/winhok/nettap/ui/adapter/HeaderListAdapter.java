package xyz.winhok.nettap.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.TextHighlighter;

public final class HeaderListAdapter extends RecyclerView.Adapter<HeaderListAdapter.Holder> {
    private final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
    private final ArrayList<Map.Entry<String, String>> entries = new ArrayList<>();
    private String query = "";

    public void submit(Map<String, String> next) {
        submit(next, "");
    }

    public void submit(Map<String, String> next, String query) {
        int oldSize = entries.size();
        replaceHeaders(next, query);
        notifyReplacement(oldSize, entries.size());
    }

    void replaceHeaders(Map<String, String> next) {
        replaceHeaders(next, "");
    }

    void replaceHeaders(Map<String, String> next, String query) {
        headers.clear();
        if (next != null) {
            headers.putAll(next);
        }
        entries.clear();
        entries.addAll(headers.entrySet());
        this.query = TextHighlighter.normalizeQuery(query);
    }

    String highlightQuery() {
        return query;
    }

    private void notifyReplacement(int oldSize, int newSize) {
        int changed = Math.min(oldSize, newSize);
        if (changed > 0) {
            notifyItemRangeChanged(0, changed);
        }
        if (newSize > oldSize) {
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else if (oldSize > newSize) {
            notifyItemRangeRemoved(newSize, oldSize - newSize);
        }
    }

    public Map<String, String> snapshot() {
        return new LinkedHashMap<>(headers);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = new TextView(parent.getContext());
        Dimens.setPaddingDp(view, 12, 8);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        String rowText = copyTextAt(position);
        holder.text.setText(TextHighlighter.highlight(holder.text.getContext(), rowText, query));
        holder.text.setOnLongClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) view.getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        view.getContext().getString(R.string.detail_headers),
                        rowText
                ));
                Toast.makeText(view.getContext(), R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public List<Map.Entry<String, String>> entriesSnapshot() {
        return new ArrayList<>(entries);
    }

    String copyTextAt(int position) {
        Map.Entry<String, String> header = entries.get(position);
        return header.getKey() + ": " + header.getValue();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(@NonNull TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }
}
