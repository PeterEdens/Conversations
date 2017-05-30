package com.example.sharedresourceslib;

/**
 * Created by petere on 5/26/2017.
 */

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class IconSpinnerAdapter extends BaseAdapter{

    Context context;
    int icons[];
    String[] statusNames;
    LayoutInflater inflter;

    public IconSpinnerAdapter(Context applicationContext, int[] icons, String[] statusNames) {
        this.context = applicationContext;
        this.icons = icons;
        this.statusNames = statusNames;
        inflter = (LayoutInflater.from(applicationContext));
    }

    @Override
    public int getCount() {
        return icons.length;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = inflter.inflate(R.layout.icon_spinner_row, null);
        ImageView icon = (ImageView) view.findViewById(R.id.imageView);
        TextView names = (TextView) view.findViewById(R.id.textView);
        icon.setImageResource(icons[i]);
        names.setText(statusNames[i]);
        return view;
    }
}