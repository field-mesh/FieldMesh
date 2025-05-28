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

