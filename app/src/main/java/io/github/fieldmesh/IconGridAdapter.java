package io.github.fieldmesh;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class IconGridAdapter extends RecyclerView.Adapter<IconGridAdapter.ViewHolder> {

    private Context mContext;
    private List<IconResIndex.IconItem> mIconItems;
    private OnIconClickListener mListener;

    public interface OnIconClickListener {
        void onIconClick(IconResIndex.IconItem iconItem);
    }

    public IconGridAdapter(Context context, List<IconResIndex.IconItem> iconItems, OnIconClickListener listener) {
        mContext = context;
        mIconItems = iconItems;
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.grid_item_icon, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IconResIndex.IconItem currentIconItem = mIconItems.get(position);

        if (currentIconItem != null) {
            if (holder.iconImageView != null) {
                if (currentIconItem.getIconResourceId() != 0) {
                    holder.iconImageView.setImageDrawable(ContextCompat.getDrawable(mContext, currentIconItem.getIconResourceId()));
                } else {
                    holder.iconImageView.setImageDrawable(null);
                }
            }
            if (holder.nameTextView != null) {
                holder.nameTextView.setText(mContext.getString(currentIconItem.getNameResourceId()));
            }

            holder.itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onIconClick(currentIconItem);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mIconItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImageView;
        TextView nameTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.grid_icon_image);
            nameTextView = itemView.findViewById(R.id.grid_icon_name);
        }
    }
}