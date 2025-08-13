package io.github.fieldmesh;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.layer.BackgroundLayer;
import gov.nasa.worldwind.layer.BlueMarbleLayer;

public class WorldWindActivity extends AppCompatActivity {
    private WorldWindow worldWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worldwind);

        FrameLayout globe = findViewById(R.id.globe);
        worldWindow = new WorldWindow(this);
        globe.addView(worldWindow);

        worldWindow.getLayers().addLayer(new BackgroundLayer());
        worldWindow.getLayers().addLayer(new BlueMarbleLayer());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (worldWindow != null) {
            worldWindow.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (worldWindow != null) {
            worldWindow.onPause();
        }
        super.onPause();
    }
}
