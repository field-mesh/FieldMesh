package io.github.fieldmesh;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.List;


class ColorItem {
    int colorResourceId;
    int nameResourceId;

    public ColorItem(int colorResourceId, int nameResourceId) {
        this.colorResourceId = colorResourceId;
        this.nameResourceId = nameResourceId;
    }

    public int getColorResourceId() {
        return colorResourceId;
    }

    public int getNameResourceId() {
        return nameResourceId;
    }
}

public class ColorArrayAdapter extends ArrayAdapter<ColorItem> {

    private Context mContext;
    private List<ColorItem> mColorItems;

    public ColorArrayAdapter(@NonNull Context context, @NonNull List<ColorItem> colorItems) {
        super(context, 0, colorItems);
        mContext = context;
        mColorItems = colorItems;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItem = convertView;
        if (listItem == null) {
            listItem = LayoutInflater.from(mContext).inflate(R.layout.list_item_color_with_name, parent, false);
        }

        ColorItem currentColorItem = mColorItems.get(position);

        View colorSwatchView = listItem.findViewById(R.id.dialog_color_swatch);
        TextView nameTextView = listItem.findViewById(R.id.dialog_color_name);

        if (currentColorItem != null) {
            if (colorSwatchView != null) {
                colorSwatchView.setBackground(new ColorDrawable(ContextCompat.getColor(mContext, currentColorItem.getColorResourceId())));
            }
            if (nameTextView != null) {
                nameTextView.setText(mContext.getString(currentColorItem.getNameResourceId()));
            }
        }
        return listItem;
    }
}