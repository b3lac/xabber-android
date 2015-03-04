package com.xabber.android.ui.adapter;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.xabber.androiddev.R;

public class ChatScrollIndicatorAdapter {

    private final Activity activity;
    private final LinearLayout linearLayout;

    public ChatScrollIndicatorAdapter(Activity activity, LinearLayout linearLayout) {
        this.activity = activity;
        this.linearLayout = linearLayout;
    }

    public void select(int selectedPosition) {
        for (int index = 0; index < linearLayout.getChildCount(); index++) {
            final View view = linearLayout.getChildAt(index);
            final AccountViewHolder accountViewHolder = (AccountViewHolder) view.getTag();

            accountViewHolder.selection.setVisibility(index == selectedPosition ? View.VISIBLE : View.GONE);
            accountViewHolder.body.setVisibility(index == selectedPosition ? View.GONE : View.VISIBLE);
        }
    }

    public void update(int size) {

        final LayoutInflater inflater
                = (LayoutInflater) activity.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        while (linearLayout.getChildCount() < size) {
            final View view = inflater.inflate(R.layout.chat_scroll_indicator_item, linearLayout, false);
            linearLayout.addView(view);
            final AccountViewHolder accountViewHolder = new AccountViewHolder(view);

            accountViewHolder.body.getBackground().setAlpha(127);
            accountViewHolder.selection.getBackground().setAlpha(127);
            view.setTag(accountViewHolder);
        }

        while (linearLayout.getChildCount() > size) {
            linearLayout.removeViewAt(size);
        }
    }

    private static class AccountViewHolder {
        final View body;
        final View selection;

        public AccountViewHolder(View view) {
            body = view.findViewById(R.id.indicator_item_body);
            selection = view.findViewById(R.id.indicator_item_selection);
        }
    }
}
