package io.github.fieldmesh;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class ColorIndex {

    public static List<Integer> colors = Arrays.asList(
            R.color.white,
            R.color.red,
            R.color.yellow,
            R.color.black,
            R.color.green,
            R.color.blue,
            R.color.orange,
            R.color.purple,
            R.color.pink,
            R.color.brown,
            R.color.gray,
            R.color.cyan,
            R.color.magenta,
            R.color.lime,
            R.color.navy,
            R.color.teal,
            R.color.maroon,
            R.color.olive,
            R.color.silver,
            R.color.gold
    );

    public static List<Integer> colorsName = Arrays.asList(
            R.string.rNameColorWhite,
            R.string.rNameColorRed,
            R.string.rNameColorYellow,
            R.string.rNameColorBlack,
            R.string.rNameColorGreen,
            R.string.rNameColorBlue,
            R.string.rNameColorOrange,
            R.string.rNameColorPurple,
            R.string.rNameColorPink,
            R.string.rNameColorBrown,
            R.string.rNameColorGray,
            R.string.rNameColorCyan,
            R.string.rNameColorMagenta,
            R.string.rNameColorLime,
            R.string.rNameColorNavy,
            R.string.rNameColorTeal,
            R.string.rNameColorMaroon,
            R.string.rNameColorOlive,
            R.string.rNameColorSilver,
            R.string.rNameColorGold
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