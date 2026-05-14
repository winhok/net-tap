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
import java.util.List;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.data.QueryParam;

public final class QueryParamListAdapter extends RecyclerView.Adapter<QueryParamListAdapter.Holder> {
    private final ArrayList<QueryParam> params = new ArrayList<>();
    private String query = "";

    public void submit(List<QueryParam> next) {
        submit(next, "");
    }

    public void submit(List<QueryParam> next, String query) {
        replaceParams(next, query);
        notifyDataSetChanged();
    }

    void replaceParams(List<QueryParam> next) {
        replaceParams(next, "");
    }

    void replaceParams(List<QueryParam> next, String query) {
        params.clear();
        if (next != null) {
            params.addAll(next);
        }
        this.query = TextHighlighter.normalizeQuery(query);
    }

    String highlightQuery() {
        return query;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = new TextView(parent.getContext());
        view.setPadding(12, 8, 12, 8);
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
                        view.getContext().getString(R.string.detail_query_params),
                        rowText
                ));
                Toast.makeText(view.getContext(), R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return params.size();
    }

    String copyTextAt(int position) {
        QueryParam param = params.get(position);
        return param.getName() + " = " + param.getValue();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(@NonNull TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }
}
