package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.SectionViews;

public final class AboutFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        Dimens.setPaddingDp(root, 12, 12);
        scrollView.addView(root);
        LinearLayout section = SectionViews.addSection(inflater, root);
        TextView title = new TextView(requireContext());
        title.setText(R.string.app_name);
        title.setTextSize(20);
        Dimens.setPaddingDp(title, 12, 8);
        section.addView(title);
        TextView body = new TextView(requireContext());
        body.setText(R.string.about_body);
        Dimens.setPaddingDp(body, 12, 8);
        section.addView(body);
        return scrollView;
    }
}
