package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;

public final class AboutFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        TextView title = new TextView(requireContext());
        title.setText(R.string.app_name);
        title.setTextSize(20);
        root.addView(title);
        TextView body = new TextView(requireContext());
        body.setText(R.string.about_body);
        root.addView(body);
        return root;
    }
}
