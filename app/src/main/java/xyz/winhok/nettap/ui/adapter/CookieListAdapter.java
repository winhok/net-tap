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
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.data.CookieEntry;

public final class CookieListAdapter extends RecyclerView.Adapter<CookieListAdapter.Holder> {
    private final ArrayList<CookieEntry> cookies = new ArrayList<>();
    private String query = "";

    public void submit(List<CookieEntry> next) {
        submit(next, "");
    }

    public void submit(List<CookieEntry> next, String query) {
        int oldSize = cookies.size();
        replaceCookies(next, query);
        notifyReplacement(oldSize, cookies.size());
    }

    void replaceCookies(List<CookieEntry> next) {
        replaceCookies(next, "");
    }

    void replaceCookies(List<CookieEntry> next, String query) {
        cookies.clear();
        if (next != null) {
            cookies.addAll(next);
        }
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

    public List<CookieEntry> snapshot() {
        return new ArrayList<>(cookies);
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
        String clipboardText = clipboardTextAt(position);
        holder.text.setText(TextHighlighter.highlight(holder.text.getContext(), rowText, query));
        holder.text.setOnLongClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) view.getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        view.getContext().getString(R.string.detail_cookies),
                        clipboardText
                ));
                Toast.makeText(view.getContext(), R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return cookies.size();
    }

    private static String format(CookieEntry cookie) {
        StringBuilder text = new StringBuilder();
        text.append(cookie.getSource()).append("  ")
                .append(cookie.getName()).append('=').append(cookie.getValue());
        if (cookie.isSecure()) {
            text.append("  Secure");
        }
        if (cookie.isHttpOnly()) {
            text.append("  HttpOnly");
        }
        if (cookie.getSameSite() != null) {
            text.append("  SameSite=").append(cookie.getSameSite());
        }
        return text.toString();
    }

    String copyTextAt(int position) {
        return format(cookies.get(position));
    }

    String clipboardTextAt(int position) {
        CookieEntry cookie = cookies.get(position);
        return cookie.getName() + "=" + cookie.getValue();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(@NonNull TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }
}
