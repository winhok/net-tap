package xyz.winhok.nettap.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import xyz.winhok.nettap.R;

public final class SectionViews {
    private SectionViews() {
    }

    public static LinearLayout addSection(LayoutInflater inflater, ViewGroup parent) {
        View card = inflater.inflate(R.layout.section_card, parent, false);
        parent.addView(card);
        return card.findViewById(R.id.section_content);
    }

    public static View tile(
            LayoutInflater inflater,
            ViewGroup parent,
            @DrawableRes int icon,
            @StringRes int title,
            @StringRes int subtitle,
            View.OnClickListener listener
    ) {
        View tile = inflater.inflate(R.layout.list_tile, parent, false);
        ((ImageView) tile.findViewById(R.id.tile_leading)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.tile_title)).setText(title);
        TextView subtitleView = tile.findViewById(R.id.tile_subtitle);
        if (subtitle != 0) {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
        tile.setOnClickListener(listener);
        parent.addView(tile);
        return tile;
    }

    public static TextView header(ViewGroup parent, @StringRes int title) {
        TextView header = new TextView(parent.getContext());
        header.setText(title);
        header.setTextColor(parent.getContext().getColor(R.color.nettap_primary));
        header.setTextSize(13f);
        Dimens.setPaddingDp(header, 12, 8);
        parent.addView(header);
        return header;
    }
}
