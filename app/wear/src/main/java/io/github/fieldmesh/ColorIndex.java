package io.github.fieldmesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ColorIndex {

    public static List<Integer> colors = Arrays.asList(
            R.color.white,
            R.color.red,
            R.color.yellow,
            R.color.black,
            R.color.green,
            R.color.blue
    );

    public static List<Integer> colorsName = Arrays.asList(
            R.string.rNameColorWhite,
            R.string.rNameColorRed,
            R.string.rNameColorYellow,
            R.string.rNameColorBlack,
            R.string.rNameColorGreen,
            R.string.rNameColorBlue
    );

    public static List<ColorItem> getColorItemsList() {
        List<ColorItem> items = new ArrayList<>();
        int size = Math.min(colors.size(), colorsName.size());
        for (int i = 0; i < size; i++) {
            items.add(new ColorItem(colors.get(i), colorsName.get(i)));
        }
        return items;
    }

    public static int getIndexByColorId(Integer colorId) {
        if (colorId == null) {
            throw new IllegalArgumentException("colorId cannot be null");
        }
        if (!colors.contains(colorId)) {
            throw new IllegalArgumentException("Invalid colorId: " + colorId);
        }
        return colors.indexOf(colorId);
    }

    public static int getColorByIndex(int index) {
        return colors.get(index);
    }
}