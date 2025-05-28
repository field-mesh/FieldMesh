package io.github.fieldmesh;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import io.github.fieldmesh.ColorIndex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PinIconSelectionActivity extends AppCompatActivity {

    private int selectedIconResourceId = -1;
    private int selectedColor = Color.WHITE;

    private Map<String, List<Integer>> getSamplePinCategoryIcons() {
        Map<String, List<Integer>> icons = new HashMap<>();

        List<Integer> formsIcons = Arrays.asList(
                R.drawable.circle,
                R.drawable.triangle,
                R.drawable.cross,
                R.drawable.square,
                R.drawable.pin
        );
        icons.put("Forms", formsIcons);

        List<Integer> peopleIcons = Arrays.asList(
                R.drawable.person1,
                R.drawable.medic,
                R.drawable.soldier
        );
        icons.put("People", peopleIcons);

        List<Integer> vehicleIcons = Arrays.asList(
                R.drawable.bycicle,
                R.drawable.motorcycle,
                R.drawable.car,
                R.drawable.truck,
                R.drawable.bus
        );
        icons.put("Vehicles", vehicleIcons);

        List<Integer> aerialIcons = Arrays.asList(
                R.drawable.quadcopter,
                R.drawable.uav,
                R.drawable.plane,
                R.drawable.helli
        );
        icons.put("Aerial", aerialIcons);

        List<Integer> seaIcons = Arrays.asList(
                R.drawable.jets,
                R.drawable.small_boat,
                R.drawable.motorboat,
                R.drawable.ship
        );
        icons.put("Sea", seaIcons);

        List<Integer> militaryIcons = Arrays.asList(
                R.drawable.lightvehicle,
                R.drawable.artillery,
                R.drawable.apc,
                R.drawable.aa,
                R.drawable.tank
        );
        icons.put("Military", militaryIcons);

        List<Integer> hazardIcons = Arrays.asList(
                R.drawable.exclamation,
                R.drawable.biohazard,
                R.drawable.radioactive
        );
        icons.put("Hazard", hazardIcons);

        return icons;
    }

    private void addPinImages(LinearLayout layout, List<Integer> iconResourceIds, String category, int color) {
        int iconSize = (int) getResources().getDimension(R.dimen.icon_size);

        for (Integer iconResourceId : iconResourceIds) {
            ImageView pinImageView = new ImageView(this);
            Drawable originalDrawable = ContextCompat.getDrawable(this, iconResourceId);

            if (originalDrawable instanceof VectorDrawableCompat) {
                VectorDrawableCompat vectorDrawable = (VectorDrawableCompat) originalDrawable.mutate();
                vectorDrawable.setTint(color);
                pinImageView.setImageDrawable(vectorDrawable);
            } else if (originalDrawable instanceof VectorDrawable) {
                VectorDrawable vectorDrawable = (VectorDrawable) originalDrawable.mutate();
                vectorDrawable.setTint(color);
                pinImageView.setImageDrawable(vectorDrawable);
            } else {
                pinImageView.setImageResource(iconResourceId);
            }

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    iconSize,
                    iconSize,
                    1f
            );
            int padding = (int) getResources().getDimension(R.dimen.icon_padding);
            layoutParams.setMargins(padding, padding, padding, padding);
            pinImageView.setLayoutParams(layoutParams);

            final int currentColor = color;
            final int currentIconResource = iconResourceId;
            pinImageView.setOnClickListener(v -> {
                selectedIconResourceId = currentIconResource;
                selectedColor = currentColor;
                Drawable currentIcon = ((ImageView) v).getDrawable();
                String colorName = getColorName(currentColor);
                String iconName = getResourceName(currentIconResource);
                Toast.makeText(this, "Selected " + category + " Icon: " + iconName + " (" + colorName + ")", Toast.LENGTH_SHORT).show();

                Intent resultIntent = new Intent();
                resultIntent.putExtra("iconResourceId", selectedIconResourceId);
                resultIntent.putExtra("color", selectedColor);
                setResult(RESULT_OK, resultIntent);
                finish();
            });

            layout.addView(pinImageView);
        }
    }

    private void addColorPalette() {
        LinearLayout colorPaletteLayout = findViewById(R.id.color_palette);
        int colorSize = (int) getResources().getDimension(R.dimen.color_size);
        List<Integer> colors = Arrays.asList(
                ContextCompat.getColor(this, R.color.white),
                ContextCompat.getColor(this, R.color.red),
                ContextCompat.getColor(this, R.color.green),
                Color.BLACK
        );

        for (int color : colors) {
            View colorView = new View(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(colorSize, colorSize);
            int margin = (int) getResources().getDimension(R.dimen.color_margin);
            layoutParams.setMargins(margin, margin, margin, margin);
            colorView.setLayoutParams(layoutParams);
            colorView.setBackgroundColor(color);

            colorView.setOnClickListener(v -> {
                int selectedColor = ((android.graphics.drawable.ColorDrawable) v.getBackground()).getColor();
                this.selectedColor = selectedColor;
                updateIconsWithColor(selectedColor);
            });

            colorPaletteLayout.addView(colorView);
        }
    }

    private void updateIconsWithColor(int color) {
        LinearLayout formsPinsLayout = findViewById(R.id.forms_pins);
        LinearLayout peoplePinsLayout = findViewById(R.id.people_pins);
        LinearLayout vehiclesPinsLayout = findViewById(R.id.vehicles_pins);
        LinearLayout aerialPinsLayout = findViewById(R.id.aerial_pins);
        LinearLayout seaPinsLayout = findViewById(R.id.sea_pins);
        LinearLayout militaryPinsLayout = findViewById(R.id.military_pins);
        LinearLayout hazardPinsLayout = findViewById(R.id.hazard_pins);

        Map<String, List<Integer>> pinCategoryIcons = getSamplePinCategoryIcons();

        formsPinsLayout.removeAllViews();
        if (pinCategoryIcons.containsKey("Forms")) {
            addPinImages(formsPinsLayout, pinCategoryIcons.get("Forms"), "Forms", color);
        }

        peoplePinsLayout.removeAllViews();
        if (pinCategoryIcons.containsKey("People")) {
            addPinImages(peoplePinsLayout, pinCategoryIcons.get("People"), "People", color);
        }

        vehiclesPinsLayout.removeAllViews();
        if (pinCategoryIcons.containsKey("Vehicles")) {
            addPinImages(vehiclesPinsLayout, pinCategoryIcons.get("Vehicles"), "Vehicles", color);
        }

        aerialPinsLayout.removeAllViews();
        if (pinCategoryIcons.containsKey("Aerial")) {
            addPinImages(aerialPinsLayout, pinCategoryIcons.get("Aerial"), "Aerial", color);
        }

        seaPinsLayout.removeAllViews();
        if (pinCategoryIcons.containsKey("Sea")) {
            addPinImages(seaPinsLayout, pinCategoryIcons.get("Sea"), "Sea", color);
        }

        militaryPinsLayout.removeAllViews();
        if (pinCategoryIcons.containsKey("Military")) {
            addPinImages(militaryPinsLayout, pinCategoryIcons.get("Military"), "Military", color);
        }

        hazardPinsLayout.removeAllViews();
        if (pinCategoryIcons.containsKey("Hazard")) {
            addPinImages(hazardPinsLayout, pinCategoryIcons.get("Hazard"), "Hazard", color);
        }
    }

    private String getColorName(int color) {
        if (color == Color.WHITE) {
            return "White";
        } else if (color == Color.RED) {
            return "Red";
        } else if (color == Color.GREEN) {
            return "Green";
        } else if (color == Color.BLACK) {
            return "Black";
        }
        return "Unknown Color";
    }

    private String getResourceName(int resourceId) {
        try {
            return getResources().getResourceEntryName(resourceId);
        } catch (Exception e) {
            return "unknown_icon";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_icon_selection);

        addColorPalette();
        updateIconsWithColor(selectedColor);
    }
}