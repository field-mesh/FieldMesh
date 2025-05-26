package io.github.meshtactic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MessageClient.OnMessageReceivedListener {

    private static final String TAG = "MainActivityOsmWear";
    private MapView mapView = null;
    private MyLocationNewOverlay mLocationOverlay;
    private Marker phoneLocationMarker;
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    private boolean isSatelliteView = false;
    private CopyrightOverlay mCopyrightOverlay;

    private MessageClient messageClient;
    private String connectedPhoneNodeId = null;

    private static final String PATH_REQUEST_FULL_SYNC = "/meshtactic/request_full_sync";
    private static final String PATH_MAP_DATA_BATCH = "/meshtactic/map_data_batch";
    private static final String PATH_CLEAR_MAP_DATA = "/meshtactic/clear_map_data";
    private static final String PATH_PHONE_LOCATION_UPDATE_TO_WEAR = "/meshtactic/phone_location_update";
    private static final String PATH_NODE_LOCATIONS_UPDATE_TO_WEAR = "/meshtactic/node_locations_update";

    private List<Marker> wearDrawnPins = new ArrayList<>();
    private List<Polyline> wearDrawnLines = new ArrayList<>();
    private List<Polygon> wearDrawnPolygonsAndCircles = new ArrayList<>();
    // Note: Node markers will be part of wearDrawnPins if drawWearPin is used.
    // Identification for removal will be based on title prefix.

    private boolean shouldFollowPhoneLocation = true;
    private Handler uiHandler;
    private GeoPoint lastKnownPhonePosition = null;
    private static final int SCROLL_AMOUNT_PIXELS = 60;
    private static final int SCROLL_AMOUNT_PIXELS_DIAGONAL = (int) (SCROLL_AMOUNT_PIXELS / Math.sqrt(2));
    private Handler followStateHandler;
    private Runnable reEnableFollowRunnable;
    private static final long USER_INTERACTION_COOLDOWN_MS = 10000;
    private boolean isProgrammaticMapChange = false;

    private static final String NODE_MARKER_TITLE_PREFIX = "Node: ";
    // IMPORTANT: Ensure IconResIndex.getIconResIdbyIndex(MESH_NODE_PIN_ICON_INDEX) returns R.drawable.radio
    private static final int MESH_NODE_PIN_ICON_INDEX = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity starting.");

        uiHandler = new Handler(Looper.getMainLooper());
        followStateHandler = new Handler(Looper.getMainLooper());
        initializeFollowTimerLogic();

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        try {
            Configuration.getInstance().setUserAgentValue(io.github.meshtactic.BuildConfig.APPLICATION_ID);
            Log.i(TAG, "Setting osmdroid User-Agent to: " + io.github.meshtactic.BuildConfig.APPLICATION_ID);
        } catch (Exception e) {
            Log.e(TAG, "Error setting User-Agent from BuildConfig, using package name as fallback.", e);
            Configuration.getInstance().setUserAgentValue(ctx.getPackageName());
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: Layout inflated.");

        mapView = findViewById(R.id.mapView);
        if (mapView != null) {
            Log.d(TAG, "onCreate: MapView found.");
            mapView.setMultiTouchControls(true);
            mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

            mCopyrightOverlay = new CopyrightOverlay(ctx);
            mapView.getOverlays().add(mCopyrightOverlay);
            Log.i(TAG, "onCreate: CopyrightOverlay added.");

            updateTileSource();

            mapView.getController().setZoom(15.0);
            mapView.getController().setCenter(new GeoPoint(0.0, 0.0));
            Log.i(TAG, "onCreate: Map controller initially set.");

            mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(ctx), mapView);
            mLocationOverlay.setDrawAccuracyEnabled(true);
            Log.i(TAG, "onCreate: MyLocationNewOverlay for watch GPS initialized (but kept disabled).");

            phoneLocationMarker = new Marker(mapView);
            phoneLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            try {
                Drawable phoneIconDrawable = ContextCompat.getDrawable(this, R.drawable.osm_ic_center_map);
                if (phoneIconDrawable == null) {
                    phoneIconDrawable = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.person);
                    if (phoneIconDrawable != null) {
                        DrawableCompat.setTint(phoneIconDrawable.mutate(), Color.BLUE);
                    }
                }
                if (phoneIconDrawable != null) {
                    int originalWidth = phoneIconDrawable.getIntrinsicWidth();
                    int originalHeight = phoneIconDrawable.getIntrinsicHeight();
                    if (originalWidth > 0 && originalHeight > 0) {
                        int scaledWidth = originalWidth / 2;
                        int scaledHeight = originalHeight / 2;
                        Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        phoneIconDrawable.setBounds(0, 0, scaledWidth, scaledHeight);
                        phoneIconDrawable.draw(canvas);
                        phoneLocationMarker.setIcon(new BitmapDrawable(getResources(), bitmap));
                    } else {
                        phoneLocationMarker.setIcon(phoneIconDrawable);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting phone location marker icon", e);
                Drawable defaultIcon = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.person);
                if (defaultIcon != null) {
                    DrawableCompat.setTint(defaultIcon.mutate(), Color.BLUE);
                    phoneLocationMarker.setIcon(defaultIcon);
                }
            }
            phoneLocationMarker.setTitle("Phone Location");
            phoneLocationMarker.setInfoWindow(null);
            phoneLocationMarker.setEnabled(false);

            Overlay gestureLockOverlay = new Overlay() {
                @Override
                public void draw(Canvas c, MapView osmv, boolean shadow) {}

                @Override
                public boolean onScroll(MotionEvent pEvent1, MotionEvent pEvent2, float pDistanceX, float pDistanceY, MapView pMapView) {
                    Log.v(TAG, "GestureLockOverlay: User attempted onScroll by touch - resetting follow timer.");
                    notifyUserInteractionAndDisableFollow();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent pEvent1, MotionEvent pEvent2, float velocityX, float velocityY, MapView pMapView) {
                    Log.v(TAG, "GestureLockOverlay: User attempted onFling by touch - resetting follow timer.");
                    notifyUserInteractionAndDisableFollow();
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e, MapView mv) {
                    Log.v(TAG, "GestureLockOverlay: onDoubleTap, allowing map zoom and resetting follow timer.");
                    isProgrammaticMapChange = true;
                    mv.getController().zoomInFixing((int) e.getX(), (int) e.getY());
                    notifyUserInteractionAndDisableFollow();
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e, MapView mv) {
                    Log.d(TAG, "GestureLockOverlay: onSingleTapConfirmed - Toggling tile source and resetting follow timer.");
                    isSatelliteView = !isSatelliteView;
                    updateTileSource();
                    notifyUserInteractionAndDisableFollow();
                    return true;
                }
            };
            mapView.getOverlays().add(gestureLockOverlay);
            Log.i(TAG, "onCreate: GestureLockOverlay updated with follow timer logic.");

            mapView.setMapListener(new MapListener() {
                @Override
                public boolean onScroll(ScrollEvent event) {
                    Log.v(TAG, "MapListener: onScroll event. Programmatic: " + isProgrammaticMapChange);
                    if (isProgrammaticMapChange) {
                        isProgrammaticMapChange = false;
                    } else {
                        Log.d(TAG, "MapListener: Unflagged scroll detected.");
                    }
                    return false;
                }

                @Override
                public boolean onZoom(ZoomEvent event) {
                    Log.d(TAG, "MapListener: onZoom event. Programmatic change flag: " + isProgrammaticMapChange + ". Zoom level: " + event.getZoomLevel());
                    boolean wasProgrammaticChange = isProgrammaticMapChange;

                    if (isProgrammaticMapChange) {
                        isProgrammaticMapChange = false;
                    }

                    if (!wasProgrammaticChange) {
                        Log.d(TAG, "MapListener: User pinch-zoom detected - resetting follow timer.");
                        notifyUserInteractionAndDisableFollow();
                    }

                    if (shouldFollowPhoneLocation && phoneLocationMarker != null && phoneLocationMarker.isEnabled() && phoneLocationMarker.getPosition() != null) {
                        final GeoPoint targetCenter = new GeoPoint(phoneLocationMarker.getPosition());
                        uiHandler.post(() -> {
                            if (mapView != null && shouldFollowPhoneLocation && phoneLocationMarker.isEnabled() && phoneLocationMarker.getPosition() != null) {
                                Log.d(TAG, "MapListener/onZoom: Re-centering map on phone's location (post-zoom) because shouldFollowPhoneLocation is true. Current Zoom: " + mapView.getZoomLevelDouble());
                                mapView.getController().setCenter(targetCenter);
                                mapView.invalidate();
                            }
                        });
                    }
                    return true;
                }
            });
            Log.i(TAG, "onCreate: MapListener updated for zoom/scroll with follow timer logic.");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mapView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    Rect exclusionRect = new Rect(0, 0, v.getWidth(), v.getHeight());
                    ViewCompat.setSystemGestureExclusionRects(mapView, Collections.singletonList(exclusionRect));
                    Log.d(TAG, "System gesture exclusion rect set: " + exclusionRect.toString());
                });
            } else {
                Log.i(TAG, "System gesture exclusion not available on API " + Build.VERSION.SDK_INT);
            }

            setupMapControlButtons();

        } else {
            Log.e(TAG, "onCreate: MapView not found in layout!");
            Toast.makeText(this, "Error: MapView not found.", Toast.LENGTH_LONG).show();
            return;
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
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });

        messageClient = Wearable.getMessageClient(this);
        if (mapView != null) {
            new Handler(Looper.getMainLooper()).postDelayed(this::requestFullSyncFromPhone, 2500);
        }
    }

    private void initializeFollowTimerLogic() {
        reEnableFollowRunnable = () -> {
            Log.i(TAG, "User interaction cooldown finished. Re-enabling follow mode.");
            shouldFollowPhoneLocation = true;
            if (mapView != null && lastKnownPhonePosition != null) {
                Log.d(TAG, "Animating to phone location post-cooldown.");
                mapView.getController().animateTo(lastKnownPhonePosition);
            } else if (shouldFollowPhoneLocation) {
                Log.d(TAG, "Follow mode re-enabled, but no phone location to animate to yet.");
            }
        };
    }

    private void notifyUserInteractionAndDisableFollow() {
        Log.i(TAG, "User interaction detected. Disabling follow mode and resetting cooldown timer.");
        shouldFollowPhoneLocation = false;
        followStateHandler.removeCallbacks(reEnableFollowRunnable);
        followStateHandler.postDelayed(reEnableFollowRunnable, USER_INTERACTION_COOLDOWN_MS);
    }

    private void setupMapControlButtons() {
        if (mapView == null) return;

        Button buttonMoveLeft = findViewById(R.id.buttonMoveLeft);
        Button buttonMoveRight = findViewById(R.id.buttonMoveRight);
        Button buttonMoveTop = findViewById(R.id.buttonMoveTop);
        Button buttonMoveBottom = findViewById(R.id.buttonMoveBottom);
        Button buttonMoveLeftTop = findViewById(R.id.buttonMoveLeftTop);
        Button buttonMoveRightTop = findViewById(R.id.buttonMoveRightTop);
        Button buttonMoveLeftBottom = findViewById(R.id.buttonMoveLeftBottom);
        Button buttonMoveRightBottom = findViewById(R.id.buttonMoveRightBottom);
        Button buttonZoomIn = findViewById(R.id.buttonZoomIn);
        Button buttonZoomOut = findViewById(R.id.buttonZoomOut);

        View.OnClickListener panClickListener = v -> {
            Log.d(TAG, "Manual pan initiated by button, disabling follow temporarily.");
            isProgrammaticMapChange = true;

            int dx = 0, dy = 0;
            int id = v.getId();

            if (id == R.id.buttonMoveLeft) dx = -SCROLL_AMOUNT_PIXELS;
            else if (id == R.id.buttonMoveRight) dx = SCROLL_AMOUNT_PIXELS;
            else if (id == R.id.buttonMoveTop) dy = -SCROLL_AMOUNT_PIXELS;
            else if (id == R.id.buttonMoveBottom) dy = SCROLL_AMOUNT_PIXELS;
            else if (id == R.id.buttonMoveLeftTop) { dx = -SCROLL_AMOUNT_PIXELS_DIAGONAL; dy = -SCROLL_AMOUNT_PIXELS_DIAGONAL; }
            else if (id == R.id.buttonMoveRightTop) { dx = SCROLL_AMOUNT_PIXELS_DIAGONAL; dy = -SCROLL_AMOUNT_PIXELS_DIAGONAL; }
            else if (id == R.id.buttonMoveLeftBottom) { dx = -SCROLL_AMOUNT_PIXELS_DIAGONAL; dy = SCROLL_AMOUNT_PIXELS_DIAGONAL; }
            else if (id == R.id.buttonMoveRightBottom) { dx = SCROLL_AMOUNT_PIXELS_DIAGONAL; dy = SCROLL_AMOUNT_PIXELS_DIAGONAL; }


            if (dx != 0 || dy != 0) {
                mapView.scrollBy(dx, dy);
            }
            notifyUserInteractionAndDisableFollow();
        };

        buttonMoveLeft.setOnClickListener(panClickListener);
        buttonMoveRight.setOnClickListener(panClickListener);
        buttonMoveTop.setOnClickListener(panClickListener);
        buttonMoveBottom.setOnClickListener(panClickListener);
        buttonMoveLeftTop.setOnClickListener(panClickListener);
        buttonMoveRightTop.setOnClickListener(panClickListener);
        buttonMoveLeftBottom.setOnClickListener(panClickListener);
        buttonMoveRightBottom.setOnClickListener(panClickListener);

        buttonZoomIn.setOnClickListener(v -> {
            Log.d(TAG, "Button Zoom In clicked, disabling follow temporarily.");
            isProgrammaticMapChange = true;
            mapView.getController().zoomIn();
            notifyUserInteractionAndDisableFollow();
        });

        buttonZoomOut.setOnClickListener(v -> {
            Log.d(TAG, "Button Zoom Out clicked, disabling follow temporarily.");
            isProgrammaticMapChange = true;
            mapView.getController().zoomOut();
            notifyUserInteractionAndDisableFollow();
        });
        Log.i(TAG, "Map control buttons initialized with follow timer logic.");
    }


    private void requestFullSyncFromPhone() {
        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
            if (nodes != null && !nodes.isEmpty()) {
                connectedPhoneNodeId = nodes.get(0).getId();
                Log.i(TAG, "Requesting full map data sync from phone node: " + connectedPhoneNodeId);
                messageClient.sendMessage(connectedPhoneNodeId, PATH_REQUEST_FULL_SYNC, new byte[0])
                        .addOnSuccessListener(voidItem -> Log.d(TAG, "Full sync request sent successfully to " + connectedPhoneNodeId))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to send full sync request to " + connectedPhoneNodeId, e));
            } else {
                Log.w(TAG, "No connected phone node found to request sync.");
                Toast.makeText(MainActivity.this, "Phone not connected for map sync", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to get connected nodes.", e));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity resumed.");
        if (mapView != null) {
            mapView.onResume();
        }
        messageClient.addListener(this);

        shouldFollowPhoneLocation = true;
        Log.d(TAG, "onResume: shouldFollowPhoneLocation reset to true. Clearing any pending follow cooldowns.");
        followStateHandler.removeCallbacks(reEnableFollowRunnable);

        if (mapView != null && lastKnownPhonePosition != null) {
            Log.d(TAG, "onResume: Centering on last known phone position.");
            mapView.getController().animateTo(lastKnownPhonePosition);
        }

        boolean noMapObjectsPresent = wearDrawnPins.isEmpty() &&
                wearDrawnLines.isEmpty() &&
                wearDrawnPolygonsAndCircles.isEmpty() &&
                !hasNodeMarkers(); // Check if any node markers exist

        if (mapView != null && noMapObjectsPresent) {
            new Handler(Looper.getMainLooper()).postDelayed(this::requestFullSyncFromPhone, 1000);
        }
    }

    private boolean hasNodeMarkers() {
        for (Marker m : wearDrawnPins) {
            if (m.getTitle() != null && m.getTitle().startsWith(NODE_MARKER_TITLE_PREFIX)) {
                return true;
            }
        }
        return false;
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Activity paused.");
        if (mLocationOverlay != null) {
            mLocationOverlay.disableMyLocation();
        }
        if (mapView != null) {
            mapView.onPause();
        }
        messageClient.removeListener(this);
        followStateHandler.removeCallbacks(reEnableFollowRunnable);
    }

    private void updateTileSource() {
        if (mapView == null) {
            Log.e(TAG, "updateTileSource: MapView is null!");
            return;
        }
        Log.d(TAG, "updateTileSource: Updating. isSatellite: " + isSatelliteView);

        if (isSatelliteView) {
            OnlineTileSourceBase esriTileSource = new OnlineTileSourceBase(
                    "ESRI WorldImagery", 0, 19, 256, ".jpeg",
                    new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"},
                    "") {
                @Override
                public String getTileURLString(long pMapTileIndex) {
                    return getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + mImageFilenameEnding;
                }
            };
            mapView.setTileSource(esriTileSource);
            mapView.setTilesScaledToDpi(true);
            if (mCopyrightOverlay != null) mCopyrightOverlay.setCopyrightNotice(esriTileSource.getCopyrightNotice());
            if (mapView.getZoomLevelDouble() > 19) mapView.getController().setZoom(19.0);
            Log.i(TAG, "Tile source set to ESRI World Imagery.");
        } else {
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setTilesScaledToDpi(false);
            if (mCopyrightOverlay != null) mCopyrightOverlay.setCopyrightNotice(TileSourceFactory.MAPNIK.getCopyrightNotice());
            Log.i(TAG, "Tile source set to MAPNIK (default).");
        }
        mapView.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: Received callback for request code " + requestCode);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0) {
                for (int i = 0; i < grantResults.length; i++) {
                    Log.i(TAG, "Permission: " + permissions[i] + ", Granted: " + (grantResults[i] == PackageManager.PERMISSION_GRANTED));
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            Log.w(TAG, "ACCESS_FINE_LOCATION permission denied by user.");
                        }
                    }
                }
            } else {
                Log.w(TAG, "onRequestPermissionsResult: grantResults array is empty.");
            }
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission not granted: " + permission);
                permissionsToRequest.add(permission);
            } else {
                Log.i(TAG, "Permission already granted: " + permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            Log.i(TAG, "Requesting permissions: " + Arrays.toString(permissionsToRequest.toArray()));
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_PERMISSIONS_REQUEST_CODE);
        } else {
            Log.i(TAG, "All necessary permissions already granted.");
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.i(TAG, "Wear: Message received from: " + messageEvent.getSourceNodeId() + ", Path: " + messageEvent.getPath());
        connectedPhoneNodeId = messageEvent.getSourceNodeId();

        if (PATH_MAP_DATA_BATCH.equals(messageEvent.getPath())) {
            byte[] data = messageEvent.getData();
            String jsonDataString = new String(data);
            Log.d(TAG, "Wear: Received map data batch.");
            runOnUiThread(() -> processMapDataBatch(jsonDataString));
        } else if (PATH_CLEAR_MAP_DATA.equals(messageEvent.getPath())) {
            Log.i(TAG, "Wear: Received request to clear map data.");
            runOnUiThread(this::clearAllWearMapObjects);
        } else if (PATH_PHONE_LOCATION_UPDATE_TO_WEAR.equals(messageEvent.getPath())) {
            byte[] data = messageEvent.getData();
            String jsonDataString = new String(data);
            Log.d(TAG, "Wear: Received phone location update: " + jsonDataString);
            runOnUiThread(() -> processPhoneLocationUpdate(jsonDataString));
        } else if (PATH_NODE_LOCATIONS_UPDATE_TO_WEAR.equals(messageEvent.getPath())) {
            byte[] data = messageEvent.getData();
            String jsonDataString = new String(data);
            Log.d(TAG, "Wear: Received node locations update: " + jsonDataString.substring(0, Math.min(jsonDataString.length(), 200)) + "...");
            runOnUiThread(() -> processNodeLocationsUpdate(jsonDataString));
        }
    }

    private void processPhoneLocationUpdate(String jsonDataString) {
        if (mapView == null || phoneLocationMarker == null) {
            Log.e(TAG, "Wear: MapView or phoneLocationMarker is null, cannot process phone location.");
            return;
        }
        try {
            JSONObject locationJson = new JSONObject(jsonDataString);
            double lat = locationJson.getDouble("lat");
            double lon = locationJson.getDouble("lon");
            GeoPoint phonePosition = new GeoPoint(lat, lon);

            lastKnownPhonePosition = phonePosition;

            phoneLocationMarker.setPosition(lastKnownPhonePosition);
            phoneLocationMarker.setEnabled(true);

            if (!mapView.getOverlays().contains(phoneLocationMarker)) {
                mapView.getOverlays().add(phoneLocationMarker);
            }

            if (locationJson.has("bearing")) {
                phoneLocationMarker.setRotation((float)locationJson.getDouble("bearing"));
            } else {
                phoneLocationMarker.setRotation(0f);
            }

            if (shouldFollowPhoneLocation) {
                Log.d(TAG, "Phone location update: Auto-centering map because shouldFollowPhoneLocation is true.");
                mapView.getController().animateTo(lastKnownPhonePosition);
            } else {
                Log.d(TAG, "Phone location update: Marker updated, but map not re-centered because shouldFollowPhoneLocation is false (user interaction or cooldown active).");
            }

            mapView.invalidate();
            Log.i(TAG, "Wear: Updated phone's location marker to: " + lastKnownPhonePosition);

        } catch (JSONException e) {
            Log.e(TAG, "Wear: Error processing phone location JSON: " + e.getMessage(), e);
        }
    }


    private void clearAllWearMapObjects() {
        if (mapView == null) return;
        Log.d(TAG, "Wear: Clearing all drawn map objects.");

        if (phoneLocationMarker != null) {
            mapView.getOverlays().remove(phoneLocationMarker);
        }

        // Iterate backwards or use Iterator to avoid ConcurrentModificationException if removing from list while iterating
        for (Iterator<Marker> iterator = wearDrawnPins.iterator(); iterator.hasNext(); ) {
            Marker marker = iterator.next();
            mapView.getOverlays().remove(marker);
            iterator.remove();
        }
        // wearDrawnPins.clear(); // Already cleared by iterator.remove()

        for (Iterator<Polyline> iterator = wearDrawnLines.iterator(); iterator.hasNext(); ) {
            Polyline polyline = iterator.next();
            mapView.getOverlays().remove(polyline);
            iterator.remove();
        }
        // wearDrawnLines.clear();

        for (Iterator<Polygon> iterator = wearDrawnPolygonsAndCircles.iterator(); iterator.hasNext(); ) {
            Polygon polygon = iterator.next();
            mapView.getOverlays().remove(polygon);
            iterator.remove();
        }
        // wearDrawnPolygonsAndCircles.clear();

        mapView.invalidate();
        Log.i(TAG, "Wear: All custom map objects cleared from overlays.");
    }

    private void processNodeLocationsUpdate(String jsonDataString) {
        if (mapView == null) {
            Log.e(TAG, "Wear: MapView is null, cannot process node locations.");
            return;
        }
        Log.d(TAG, "Wear: Processing node locations update on UI thread.");

        // Remove only old node markers (identified by title prefix) from wearDrawnPins and map
        List<Marker> markersToRemove = new ArrayList<>();
        for (Marker m : wearDrawnPins) {
            if (m.getTitle() != null && m.getTitle().startsWith(NODE_MARKER_TITLE_PREFIX)) {
                markersToRemove.add(m);
            }
        }
        for (Marker m : markersToRemove) {
            mapView.getOverlays().remove(m);
            wearDrawnPins.remove(m);
        }
        Log.d(TAG, "Wear: Cleared " + markersToRemove.size() + " old node markers.");

        try {
            JSONArray nodesArray = new JSONArray(jsonDataString);
            Log.d(TAG, "Wear: Received " + nodesArray.length() + " nodes in update.");
            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject nodeData = nodesArray.getJSONObject(i);
                double lat = nodeData.getDouble("lat");
                double lon = nodeData.getDouble("lon");
                String shortName = nodeData.optString("shortName", "N/A");
                boolean isOld = nodeData.optBoolean("isOld", false);

                GeoPoint nodePosition = new GeoPoint(lat, lon);
                int nodePinColor = isOld ? Color.GRAY : Color.rgb(255, 255, 255); // Light Blue for fresh, Gray for old
                String nodePinTitle = shortName;

                // Call drawWearPin to draw the node. It will add the marker to wearDrawnPins and the map.
                // IMPORTANT: Ensure IconResIndex.getIconResIdbyIndex(MESH_NODE_PIN_ICON_INDEX) returns R.drawable.radio
                drawWearPin(nodePosition, nodePinColor, nodePinTitle, MESH_NODE_PIN_ICON_INDEX);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Wear: Error processing node locations JSON: " + e.getMessage(), e);
            Toast.makeText(this, "Error updating node locations", Toast.LENGTH_SHORT).show();
        }
        mapView.invalidate();
        Log.i(TAG, "Wear: Node locations processed. Current wearDrawnPins count: " + wearDrawnPins.size());
    }


    private void processMapDataBatch(String jsonDataString) {
        if (mapView == null) {
            Log.e(TAG, "Wear: MapView is null, cannot process map data batch.");
            return;
        }
        Log.d(TAG, "Wear: Processing map data batch on UI thread.");
        clearAllWearMapObjects(); // This clears wearDrawnPins, which will include any node markers too.

        try {
            JSONObject batchData = new JSONObject(jsonDataString);

            if (batchData.has("lines")) {
                JSONArray linesArray = batchData.getJSONArray("lines");
                for (int i = 0; i < linesArray.length(); i++) {
                    JSONObject lineData = linesArray.getJSONObject(i);
                    JSONArray pointsArray = lineData.getJSONArray("points");
                    ArrayList<GeoPoint> geoPoints = new ArrayList<>();
                    for (int j = 0; j < pointsArray.length(); j++) {
                        JSONObject pt = pointsArray.getJSONObject(j);
                        geoPoints.add(new GeoPoint(pt.getDouble("lat"), pt.getDouble("lon")));
                    }
                    int color = lineData.getInt("color");
                    if (geoPoints.size() >= 2) drawWearLine(geoPoints, color);
                }
            }
            if (batchData.has("polygons")) {
                JSONArray polysArray = batchData.getJSONArray("polygons");
                for (int i = 0; i < polysArray.length(); i++) {
                    JSONObject polyData = polysArray.getJSONObject(i);
                    JSONArray pointsArray = polyData.getJSONArray("points");
                    ArrayList<GeoPoint> geoPoints = new ArrayList<>();
                    for (int j = 0; j < pointsArray.length(); j++) {
                        JSONObject pt = pointsArray.getJSONObject(j);
                        geoPoints.add(new GeoPoint(pt.getDouble("lat"), pt.getDouble("lon")));
                    }
                    int fillColor = polyData.getInt("fillColor");
                    int strokeColor = polyData.getInt("strokeColor");
                    if (geoPoints.size() >= 3) drawWearPolygon(geoPoints, fillColor, strokeColor);
                }
            }
            if (batchData.has("circles")) {
                JSONArray circlesArray = batchData.getJSONArray("circles");
                for (int i = 0; i < circlesArray.length(); i++) {
                    JSONObject circleData = circlesArray.getJSONObject(i);
                    double lat = circleData.getDouble("lat");
                    double lon = circleData.getDouble("lon");
                    double radius = circleData.getDouble("radius");
                    int fillColor = circleData.getInt("fillColor");
                    int strokeColor = circleData.getInt("strokeColor");
                    drawWearCircle(new GeoPoint(lat, lon), radius, fillColor, strokeColor);
                }
            }
            if (batchData.has("pins")) {
                JSONArray pinsArray = batchData.getJSONArray("pins");
                for (int i = 0; i < pinsArray.length(); i++) {
                    JSONObject pinData = pinsArray.getJSONObject(i);
                    double lat = pinData.getDouble("lat");
                    double lon = pinData.getDouble("lon");
                    int argbColor = pinData.getInt("color");
                    String label = pinData.optString("label", "");
                    int iconIndex = pinData.optInt("iconIndex", -1);
                    // Ensure custom pins from batch don't use the NODE_MARKER_TITLE_PREFIX unless intended
                    drawWearPin(new GeoPoint(lat, lon), argbColor, label, iconIndex);
                }
            }

            if (lastKnownPhonePosition != null && phoneLocationMarker != null) {
                phoneLocationMarker.setPosition(lastKnownPhonePosition);
                phoneLocationMarker.setEnabled(true);
                if (!mapView.getOverlays().contains(phoneLocationMarker)) {
                    mapView.getOverlays().add(phoneLocationMarker);
                    Log.d(TAG, "processMapDataBatch: Phone location marker re-added to overlays.");
                } else {
                    mapView.getOverlays().remove(phoneLocationMarker);
                    mapView.getOverlays().add(phoneLocationMarker);
                    Log.d(TAG, "processMapDataBatch: Phone location marker moved to top of overlays.");
                }
            } else if (phoneLocationMarker != null) {
                phoneLocationMarker.setEnabled(false);
                mapView.getOverlays().remove(phoneLocationMarker);
            }

            mapView.invalidate();
            Log.i(TAG, "Wear: Map data batch processed and drawn.");
        } catch (Exception e) {
            Log.e(TAG, "Wear: Error processing map data batch JSON: " + e.getMessage(), e);
            Toast.makeText(this, "Error updating map data", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawWearPin(GeoPoint position, int argbColor, String labelText, int iconIndex) {
        if (mapView == null) return;
        Marker customMarker = new Marker(mapView);
        customMarker.setPosition(position);
        customMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); // Changed for general pins, nodes might prefer ANCHOR_BOTTOM
        if (labelText != null && labelText.startsWith(NODE_MARKER_TITLE_PREFIX)) {
            customMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); // Specific for node markers
        }
        customMarker.setTitle(labelText);
        customMarker.setInfoWindow(null);

        int iconResourceId = IconResIndex.getIconResIdbyIndex(iconIndex);
        // If this is a node marker (identified by iconIndex or title prefix), force R.drawable.radio
        if (iconIndex == MESH_NODE_PIN_ICON_INDEX || (labelText !=null && labelText.startsWith(NODE_MARKER_TITLE_PREFIX)) ) {
            iconResourceId = R.drawable.radio;
        }

        if (iconResourceId == -1) { // Fallback if IconResIndex doesn't find it or it's not a node
            iconResourceId = org.osmdroid.library.R.drawable.marker_default;
        }

        Drawable iconDrawable = ContextCompat.getDrawable(this, iconResourceId);

        if (iconDrawable != null) {
            Drawable wrappedDrawable = DrawableCompat.wrap(iconDrawable.mutate());
            DrawableCompat.setTint(wrappedDrawable, argbColor);

            // Simplified icon drawing for nodes, or use complex logic for others
            if (labelText != null && labelText.startsWith(NODE_MARKER_TITLE_PREFIX) && iconResourceId == R.drawable.radio) {
                // Simplified rendering for radio icon node markers
                int iconSize = (int) (24 * getResources().getDisplayMetrics().density); // Adjust size as needed
                Bitmap bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                wrappedDrawable.setBounds(0, 0, iconSize, iconSize);
                wrappedDrawable.draw(canvas);
                customMarker.setIcon(new BitmapDrawable(getResources(), bitmap));
                // Optionally, add shortName as a small text label below/beside if needed, but title is set.
            } else {
                // Existing complex label drawing logic for other pins
                Drawable outlineDrawable = ContextCompat.getDrawable(this, iconResourceId); // Re-fetch for outline
                if (outlineDrawable != null) {
                    outlineDrawable = DrawableCompat.wrap(outlineDrawable.mutate());
                    DrawableCompat.setTint(outlineDrawable, Color.BLACK); // Outline

                    DisplayMetrics dm = getResources().getDisplayMetrics();
                    float density = dm.density;
                    float scaleFactor = 1.0f / 1.1f;
                    int baseIconSizeOriginal = 24;
                    int outlineOffsetOriginal = 2;
                    int textPaddingOriginal = 2;
                    float textSizeOriginal = 10;

                    int baseIconSize = (int) (baseIconSizeOriginal * density * scaleFactor);
                    int outlineOffset = (int) (outlineOffsetOriginal * density * scaleFactor);
                    int textPadding = (int) (textPaddingOriginal * density * scaleFactor);
                    float textSize = textSizeOriginal * density * scaleFactor;

                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(textSize);
                    textPaint.setAntiAlias(true);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.setFakeBoldText(true);

                    Rect textBounds = new Rect();
                    textPaint.getTextBounds(labelText, 0, labelText.length(), textBounds);
                    int textHeight = textBounds.height();
                    int textWidth = (int) textPaint.measureText(labelText);

                    int labelBgWidth = textWidth + textPadding * 2;
                    int labelBgHeight = textHeight + textPadding * 2;
                    labelBgWidth = Math.max(labelBgWidth, baseIconSize + outlineOffset * 2);

                    int finalWidth = labelBgWidth;
                    int finalHeight = (baseIconSize + outlineOffset * 2) + (int)(1 * density * scaleFactor) + labelBgHeight;

                    Bitmap finalBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(finalBitmap);

                    int iconDrawSize = baseIconSize;
                    int fullIconRenderSize = baseIconSize + outlineOffset * 2;
                    int iconLeftWithOutline = (finalWidth - fullIconRenderSize) / 2;
                    int iconTopWithOutline = 0;

                    outlineDrawable.setBounds(
                            iconLeftWithOutline, iconTopWithOutline,
                            iconLeftWithOutline + fullIconRenderSize, iconTopWithOutline + fullIconRenderSize);
                    outlineDrawable.draw(canvas);

                    // Use the already tinted wrappedDrawable for the main icon
                    wrappedDrawable.setBounds(
                            iconLeftWithOutline + outlineOffset, iconTopWithOutline + outlineOffset,
                            iconLeftWithOutline + outlineOffset + iconDrawSize, iconTopWithOutline + outlineOffset + iconDrawSize);
                    wrappedDrawable.draw(canvas);

                    Paint labelBgPaint = new Paint();
                    labelBgPaint.setColor(Color.argb(180, 0, 0, 0));
                    labelBgPaint.setAntiAlias(true);

                    int labelBgTop = iconTopWithOutline + fullIconRenderSize + (int)(1 * density * scaleFactor);
                    RectF labelBgRect = new RectF(0, labelBgTop, finalWidth, labelBgTop + labelBgHeight);
                    canvas.drawRoundRect(labelBgRect, 4 * density * scaleFactor, 4 * density * scaleFactor, labelBgPaint);

                    float textX = finalWidth / 2f;
                    float textY = labelBgTop + (labelBgHeight / 2f) - (textPaint.descent() + textPaint.ascent()) / 2f;
                    canvas.drawText(labelText, textX, textY, textPaint);

                    customMarker.setIcon(new BitmapDrawable(getResources(), finalBitmap));
                } else {
                    customMarker.setIcon(wrappedDrawable); // Fallback to just tinted if outline fails
                }
            }
        } else {
            Log.w(TAG, "Icon drawable was null for res ID: " + iconResourceId + ". Cannot draw custom pin icon.");
            Drawable defaultIconFallback = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default);
            if (defaultIconFallback != null) {
                DrawableCompat.setTint(defaultIconFallback.mutate(), argbColor);
                customMarker.setIcon(defaultIconFallback);
            }
        }

        mapView.getOverlays().add(customMarker);
        wearDrawnPins.add(customMarker); // All pins, including nodes, go here now.
    }


    private void drawWearLine(ArrayList<GeoPoint> points, int color) {
        if (mapView == null) return;
        Polyline line = new Polyline(mapView);
        line.setPoints(points);
        line.setColor(color);
        line.getOutlinePaint().setStrokeWidth(5f * getResources().getDisplayMetrics().density);
        line.setInfoWindow(null);
        mapView.getOverlays().add(line);
        wearDrawnLines.add(line);
    }

    private void drawWearPolygon(ArrayList<GeoPoint> points, int fillColor, int strokeColor) {
        if (mapView == null) return;
        Polygon polygon = new Polygon(mapView);
        polygon.setPoints(points);
        polygon.getFillPaint().setColor(fillColor);
        polygon.getOutlinePaint().setColor(strokeColor);
        polygon.getOutlinePaint().setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        polygon.setInfoWindow(null);
        mapView.getOverlays().add(polygon);
        wearDrawnPolygonsAndCircles.add(polygon);
    }

    private void drawWearCircle(GeoPoint center, double radius, int fillColor, int strokeColor) {
        if (mapView == null) return;
        Polygon circlePolygon = new Polygon(mapView);
        ArrayList<GeoPoint> circlePoints = Polygon.pointsAsCircle(center, radius);
        circlePolygon.setPoints(circlePoints);
        circlePolygon.getFillPaint().setColor(fillColor);
        circlePolygon.getOutlinePaint().setColor(strokeColor);
        circlePolygon.getOutlinePaint().setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        circlePolygon.setInfoWindow(null);
        mapView.getOverlays().add(circlePolygon);
        wearDrawnPolygonsAndCircles.add(circlePolygon);
    }
}
