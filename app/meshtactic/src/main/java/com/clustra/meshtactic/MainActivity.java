package com.clustra.meshtactic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect; // Import Rect for gesture exclusion
import android.os.Build; // To check API level
import android.os.Bundle;
import android.preference.PreferenceManager; // For osmdroid configuration
import android.util.Log; // Import Log for debugging
import android.view.View; // Import View for OnLayoutChangeListener
import android.widget.Toast; // For user feedback

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull; // For onRequestPermissionsResult
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// osmdroid imports
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.CustomZoomButtonsController; // To control zoom buttons visibility

import java.util.ArrayList;
import java.util.Arrays; // For Arrays.asList
import java.util.Collections; // For Collections.singletonList
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivityOsm"; // Tag for logging
    private MapView mapView = null;
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity starting.");

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Log.i(TAG, "Setting osmdroid User-Agent to: " + BuildConfig.APPLICATION_ID);
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        // Configuration.getInstance().setDebugMapTileDownloader(true);
        // Configuration.getInstance().setDebugMode(true);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: Layout inflated.");

        mapView = findViewById(R.id.mapView);
        if (mapView != null) {
            Log.d(TAG, "onCreate: MapView found.");
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            Log.i(TAG, "onCreate: Tile source set to MAPNIK.");
            mapView.setMultiTouchControls(true);
            mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

            GeoPoint startPoint = new GeoPoint(0.0, 0.0);
            mapView.getController().setZoom(3.0);
            mapView.getController().setCenter(startPoint);
            Log.i(TAG, "onCreate: Map controller initialized. Zoom: 3.0, Center: " + startPoint.toDoubleString());

            // --- Add Layout Change Listener for Gesture Exclusion ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // System gesture exclusion is for API 29+
                mapView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        // It's important that the rects are specified in the coordinate system of the
                        // view that is calling setSystemGestureExclusionRects(), which is mapView in this case.
                        Rect exclusionRect = new Rect(0, 0, v.getWidth(), v.getHeight());
                        ViewCompat.setSystemGestureExclusionRects(mapView, Collections.singletonList(exclusionRect));
                        Log.d(TAG, "System gesture exclusion rect set: " + exclusionRect.toString());
                    }
                });
            } else {
                Log.i(TAG, "System gesture exclusion not available on API " + Build.VERSION.SDK_INT);
                // For older APIs, this issue is harder to solve perfectly.
                // You might try mapView.requestDisallowInterceptTouchEvent(true) in a custom
                // OnTouchListener when a pan gesture is detected, but it can be tricky.
            }

        } else {
            Log.e(TAG, "onCreate: MapView not found in layout! Check R.id.mapView.");
            Toast.makeText(this, "Error: MapView not found.", Toast.LENGTH_LONG).show();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            Log.d(TAG, "Applied window insets. Top: " + systemBars.top);
            return insets;
        });

        Log.d(TAG, "onCreate: Requesting permissions if necessary.");
        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity resumed.");
        if (mapView != null) {
            Log.d(TAG, "onResume: Calling mapView.onResume()");
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Activity paused.");
        if (mapView != null) {
            Log.d(TAG, "onPause: Calling mapView.onPause()");
            mapView.onPause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: Received callback for request code " + requestCode);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean allGranted = true;
                for (int i = 0; i < grantResults.length; i++) {
                    Log.i(TAG, "Permission: " + permissions[i] + ", Granted: " + (grantResults[i] == PackageManager.PERMISSION_GRANTED));
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        if (permissions[i].equals(Manifest.permission.INTERNET)) {
                            Log.e(TAG, "INTERNET permission denied. Tiles will not load.");
                            Toast.makeText(this, "Internet permission is required for maps.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
                if (allGranted) {
                    Log.i(TAG, "onRequestPermissionsResult: All requested permissions granted.");
                } else {
                    Log.w(TAG, "onRequestPermissionsResult: Not all permissions were granted.");
                }
            } else {
                Log.w(TAG, "onRequestPermissionsResult: grantResults array is empty.");
            }
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission not granted: " + permission);
                permissionsToRequest.add(permission);
            } else {
                Log.i(TAG, "Permission already granted: " + permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.i(TAG, "Requesting permissions: " + Arrays.toString(permissionsToRequest.toArray()));
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        } else {
            Log.i(TAG, "All necessary permissions already granted.");
        }
    }
}
