package xyz.winhok.nettap.ui.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;

public final class CaptureSequenceAdapter extends RecyclerView.Adapter<CaptureSequenceAdapter.Holder> {
    private final ArrayList<CaptureUiEvent> events = new ArrayList<>();
    private final Callbacks callbacks;
    private String query = "";

    public CaptureSequenceAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
        setHasStableIds(true);
    }

    public void submit(List<CaptureUiEvent> next, String query) {
        String nextQuery = query == null ? "" : query;
        String previousQuery = this.query;
        ArrayList<CaptureUiEvent> nextEvents = new ArrayList<>();
        if (next != null) {
            nextEvents.addAll(next);
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new CaptureSequenceDiffCallback(
                new ArrayList<>(events),
                nextEvents
        ));
        events.clear();
        events.addAll(nextEvents);
        this.query = nextQuery;
        diff.dispatchUpdatesTo(this);
        if (shouldRebindForQueryChange(previousQuery, nextQuery, events.size())) {
            notifyItemRangeChanged(0, events.size(), nextQuery);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = new TextView(parent.getContext());
        view.setPadding(12, 12, 12, 12);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CaptureUiEvent event = events.get(position);
        String marker = callbacks.isRead(event) ? "READ " : "NEW ";
        String text = marker + event.getMethod() + " " + event.getDisplayStatus() + " " + event.getUrl()
                + "\n" + event.getHook() + " | " + event.getPackageName() + " | " + event.getTimestamp();
        holder.text.setText(TextHighlighter.highlight(holder.text.getContext(), text, query));
        holder.text.setOnClickListener(view -> callbacks.onOpen(event));
        holder.text.setOnLongClickListener(view -> {
            callbacks.onShowActions(event, view);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= events.size()) {
            return RecyclerView.NO_ID;
        }
        return stableIdFor(events.get(position).getId());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(@NonNull TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }

    static boolean shouldRebindForQueryChange(String previousQuery, String nextQuery, int itemCount) {
        String previous = previousQuery == null ? "" : previousQuery;
        String next = nextQuery == null ? "" : nextQuery;
        return itemCount > 0 && !previous.equals(next);
    }

    static long stableIdFor(String eventId) {
        String value = eventId == null ? "" : eventId;
        long hash = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            hash = 31L * hash + value.charAt(i);
        }
        return hash == RecyclerView.NO_ID ? 0L : hash;
    }

    public interface Callbacks {
        boolean isRead(CaptureUiEvent event);

        void onOpen(CaptureUiEvent event);

        void onShowActions(CaptureUiEvent event, View anchor);
    }
}
