package io.github.fieldmesh;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.Html;
import android.text.InputFilter;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.MBTilesFileArchive;
import org.osmdroid.tileprovider.modules.ArchiveFileFactory;
import org.osmdroid.tileprovider.modules.MapTileApproximater;
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import android.view.KeyEvent;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;
import com.geeksville.mesh.NodeInfo;


public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener, MapEventsReceiver, MapListener {
    private static final String TAG = "FieldMeshMainActivity";

    private IMeshService geeksvilleMeshServiceActivity;
    private boolean isGeeksvilleMeshServiceActivityBound = false;
    private MapDataDatabaseHelper mapDataDbHelper;

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static int IS_EDIT_MODE = 0;
    private static final int EDIT_MODE_PIN = 1;
    private static final int EDIT_MODE_LINE = 2;
    private static final int EDIT_MODE_POLY = 3;
    private static final int EDIT_MODE_CIRCLE = 4;

    private static MapView mapView = null;
    private IMapController mapController = null;

    private ImageButton btnTileToggle, btnFollowToggle, btnRotateToggle, launchMeshtasticButton,
            addPinButton, addLineButton, addPolyButton, addCircleButton,
            undoButton, closeButton, doneButton, infoButton;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor magnetometer, accelerometer, rotationVectorSensor;
    private Marker myLocationMarker;
    private boolean isFollowing = true;
    private boolean isSatellite = false;
    private boolean isRotationEnabled = false;
    private AttributionOverlay attributionOverlay;
    private TextView tvSatelliteStatus, tvNumSatellites, tvAccuracy,
            meshNodesTextView, myNodeIdTextView, meshStatusTextView, tvSyncStatus;

    private GnssStatus gnssStatus;
    private List<Marker> customPins = new ArrayList<>();
    private float[] accelerometerReading = new float[3];
    private float[] magnetometerReading = new float[3];
    private float[] rotationVectorReading = new float[3];
    private float kalmanAngle = 0.0f;
    private float kalmanBias = 0.0f;
    private float kalmanProcessNoise = 0.02f;
    private float kalmanMeasurementNoise = 0.2f;
    private float kalmanErrorEstimate = 1.0f;
    private GeoPoint circleCenter = null;
    private GeoPoint pendingShapeCenter = null;
    private double pendingShapeRadius = 0.0;
    private Marker temporaryCircleCenterMarker = null;
    private List<GeoPoint> currentLinePoints = new ArrayList<>();
    private Polyline temporaryLineOverlay;
    private List<Polyline> drawnLines = new ArrayList<>();
    private List<GeoPoint> currentPolygonPoints = new ArrayList<>();
    private Polygon temporaryPolygonOverlay;
    private List<Polygon> drawnPolygons = new ArrayList<>();
    private Handler periodicNodeUpdateHandler;
    private Runnable nodeUpdateRunnable;
    private static final long NODE_UPDATE_INTERVAL_MS = 10 * 1000;
    private boolean mIsInMultiWindowMode = false;
    private Handler mServiceStartHandler;
    private Runnable mServiceStartRunnable;
    private BroadcastReceiver mapDataRefreshReceiver;
    private BroadcastReceiver syncStatusUpdateReceiver;

    private static final String TILE_SOURCE_OSM = "OpenStreetMap (Online)";
    private static final String TILE_SOURCE_ESRI = "ESRI Satellite (Online)";
    private static final String MBTILES_SUBDIRECTORY = "offline_maps";
    private String currentTileSourceName = TILE_SOURCE_OSM;
    private File currentMbtilesFile = null;


    private class AttributionOverlay extends Overlay {
        private final Paint textPaint;
        private String attributionText = "© OpenStreetMap contributors";
        public AttributionOverlay(Context context) {
            super();
            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(16 * getResources().getDisplayMetrics().density);
            textPaint.setAntiAlias(true);
        }
        public void setAttribution(String attribution) { attributionText = attribution; }
        @Override
        public void draw(android.graphics.Canvas canvas, MapView mv, boolean shadow) {
            if (!shadow) {
                float x = 10 * getResources().getDisplayMetrics().density;
                float y = mv.getHeight() - (10 * getResources().getDisplayMetrics().density);
                String[] lines = attributionText.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    canvas.drawText(lines[i], x, y - (lines.length - 1 - i) * textPaint.getTextSize(), textPaint);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private final GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
            gnssStatus = status;
            try {
                updateTopBarInfoDisplay();
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in onSatelliteStatusChanged", e);
            }
        }
    };

    private final ServiceConnection geeksvilleServiceConnectionActivity = new ServiceConnection() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Activity: Geeksville MeshService connected");
            geeksvilleMeshServiceActivity = IMeshService.Stub.asInterface(service);
            isGeeksvilleMeshServiceActivityBound = true;
            ImageButton launchBtn = findViewById(R.id.btn_open_mesh);
            Drawable drw = ContextCompat.getDrawable(MainActivity.this, R.drawable.meshtactic_logo);
            if (drw != null) {
                drw = DrawableCompat.wrap(drw.mutate());
                DrawableCompat.setTint(drw, Color.GREEN);
                launchBtn.setImageDrawable(drw);
            }
            try {
                if (geeksvilleMeshServiceActivity != null) {
                    meshNodesTextView.setText("Nodes: " + geeksvilleMeshServiceActivity.getNodes().size());
                    myNodeIdTextView.setText("My ID: " + formatNodeIdForDisplay(geeksvilleMeshServiceActivity.getMyId()));
                    meshStatusTextView.setText("Mesh: OK");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Activity: RemoteException in onServiceConnected for Geeksville service", e);
                meshStatusTextView.setText("Mesh: Error");
            }
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Activity: Geeksville MeshService disconnected");
            geeksvilleMeshServiceActivity = null;
            isGeeksvilleMeshServiceActivityBound = false;
            ImageButton launchBtn = findViewById(R.id.btn_open_mesh);
            Drawable drw = ContextCompat.getDrawable(MainActivity.this, R.drawable.meshtactic_logo);
            if (drw != null) {
                drw = DrawableCompat.wrap(drw.mutate());
                DrawableCompat.setTint(drw, Color.RED);
                launchBtn.setImageDrawable(drw);
            }
            meshNodesTextView.setText("Nodes: N/A");
            myNodeIdTextView.setText("My ID: N/A");
            meshStatusTextView.setText("Mesh: OFFLINE");
        }
    };

    private void bindToGeeksvilleMeshServiceForActivity() {
        Intent intent = new Intent();
        intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService");
        try {
            boolean bindResult = bindService(intent, geeksvilleServiceConnectionActivity, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "Activity: Geeksville MeshService binding attempt: " + bindResult);
        } catch (Exception e) {
            Log.e(TAG, "Activity: Failed to bind to Geeksville MeshService", e);
        }
    }

    private void unbindFromGeeksvilleMeshServiceForActivity() {
        if (isGeeksvilleMeshServiceActivityBound) {
            try {
                unbindService(geeksvilleServiceConnectionActivity);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Activity: Geeksville MeshService not registered or already unbound: " + e.getMessage());
            }
            isGeeksvilleMeshServiceActivityBound = false;
            geeksvilleMeshServiceActivity = null;
        }
    }


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity creating...");

        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        boolean firstTime = prefs.getBoolean("first_time", true);

        if (firstTime) {
            Intent intent = new Intent(this, InfoActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);


        Intent serviceIntent = new Intent(this, MeshReceiverService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        mServiceStartHandler = new Handler(Looper.getMainLooper());

        mapDataDbHelper = new MapDataDatabaseHelper(this);

        Configuration.getInstance().load(getApplicationContext(), getPreferences(MODE_PRIVATE));
        mapView = findViewById(R.id.mapview);
        addPinButton = findViewById(R.id.btn_pin_add);
        addCircleButton = findViewById(R.id.btn_circle_add);
        addLineButton = findViewById(R.id.btn_line_add);
        addPolyButton = findViewById(R.id.btn_poly_add);
        undoButton = findViewById(R.id.undo);
        closeButton = findViewById(R.id.close);
        doneButton = findViewById(R.id.done);
        btnTileToggle = findViewById(R.id.btn_tile_toggle);
        btnFollowToggle = findViewById(R.id.btn_follow_toggle);
        btnRotateToggle = findViewById(R.id.btn_rotate_toggle);
        tvSatelliteStatus = findViewById(R.id.tv_satellite_status);
        tvNumSatellites = findViewById(R.id.tv_num_satellites);
        tvAccuracy = findViewById(R.id.tv_accuracy);
        infoButton = findViewById(R.id.infoButton);
        meshNodesTextView = findViewById(R.id.mesh_node_number);
        myNodeIdTextView = findViewById(R.id.my_node_id);
        meshStatusTextView = findViewById(R.id.mesh_status);
        tvSyncStatus = findViewById(R.id.tv_sync_status);

        ImageButton toggleTools = findViewById(R.id.btn_Toggle_Tools);
        LinearLayout toolMenu = findViewById(R.id.tools_layout);
        LinearLayout editingTools = findViewById(R.id.editingTools);
        LinearLayout info = findViewById(R.id.info);

        toggleTools.setOnClickListener(v -> {
            if (toolMenu.getVisibility() == View.VISIBLE) {
                toolMenu.setVisibility(View.GONE);
                info.setVisibility(View.VISIBLE);
                toggleTools.setImageResource(R.drawable.plus);
                toggleTools.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)));
            } else {
                toolMenu.setVisibility(View.VISIBLE);
                info.setVisibility(View.GONE);
                toggleTools.setImageResource(R.drawable.minus);
                toggleTools.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red)));
            }
        });

        infoButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("About & Support");
            builder.setIcon(R.drawable.fieldmeshlogo);
            String githubLink = "https://github.com/field-mesh/FieldMesh/";
            String patreonLink = "https://patreon.com/FieldMesh";

            String message = "<p><b>For more information access our repository:</b></p>" +
                    "<a href=\"" + githubLink + "\">GitHub Repository</a><br>" +
                    "<p><b>If you enjoy the project please support my work:</b></p>" +
                    "<a href=\"" + patreonLink + "\">Patreon Page</a><br><br>" +
                    "<p><b>Version: 1.0</b></p> <p>(Expect Bugs, if found report on github)</p>" +
                    "<b>Thank you for using FieldMesh!</b>";

            TextView messageView = new TextView(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                messageView.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
            } else {
                messageView.setText(Html.fromHtml(message));
            }
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            messageView.setPadding(50, 30, 50, 30);

            builder.setView(messageView);
            builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();
        });

        addPinButton.setOnClickListener(v -> {
            clearAllTemporaryDrawingStates();
            IS_EDIT_MODE = EDIT_MODE_PIN;
            toolMenu.setVisibility(View.GONE);
            toggleTools.setVisibility(View.GONE);
            editingTools.setVisibility(View.GONE);
            info.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.pinEditingMode, Toast.LENGTH_SHORT).show();
        });
        addLineButton.setOnClickListener(v -> {
            clearAllTemporaryDrawingStates();
            IS_EDIT_MODE = EDIT_MODE_LINE;
            currentLinePoints.clear();
            if (temporaryLineOverlay != null) mapView.getOverlays().remove(temporaryLineOverlay);
            temporaryLineOverlay = null;
            editingTools.setVisibility(View.VISIBLE);
            info.setVisibility(View.GONE);
            toolMenu.setVisibility(View.GONE);
            toggleTools.setVisibility(View.GONE);
            Toast.makeText(this, R.string.lineEditingMode, Toast.LENGTH_SHORT).show();
        });

        addPolyButton.setOnClickListener(v -> {
            clearAllTemporaryDrawingStates();
            IS_EDIT_MODE = EDIT_MODE_POLY;
            currentPolygonPoints.clear();
            if (temporaryPolygonOverlay != null) mapView.getOverlays().remove(temporaryPolygonOverlay);
            temporaryPolygonOverlay = null;
            editingTools.setVisibility(View.VISIBLE);
            info.setVisibility(View.GONE);
            toolMenu.setVisibility(View.GONE);
            toggleTools.setVisibility(View.GONE);
            Toast.makeText(this, R.string.polyEditingMode, Toast.LENGTH_SHORT).show();
        });

        addCircleButton.setOnClickListener(v -> {
            clearAllTemporaryDrawingStates();
            IS_EDIT_MODE = EDIT_MODE_CIRCLE;
            circleCenter = null;
            pendingShapeCenter = null;
            pendingShapeRadius = 0.0;
            if (temporaryCircleCenterMarker != null) {
                mapView.getOverlays().remove(temporaryCircleCenterMarker);
                temporaryCircleCenterMarker = null;
            }
            editingTools.setVisibility(View.VISIBLE);
            info.setVisibility(View.GONE);
            toolMenu.setVisibility(View.GONE);
            toggleTools.setVisibility(View.GONE);
            Toast.makeText(this, R.string.circleEditingMode, Toast.LENGTH_SHORT).show();
        });

        undoButton.setOnClickListener(v -> {
            switch (IS_EDIT_MODE) {
                case EDIT_MODE_LINE:
                    if (!currentLinePoints.isEmpty()) {
                        currentLinePoints.remove(currentLinePoints.size() - 1);
                        updateTemporaryLineOverlay();
                        Toast.makeText(this, "Last line point removed.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No line points to remove.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case EDIT_MODE_POLY:
                    if (!currentPolygonPoints.isEmpty()) {
                        currentPolygonPoints.remove(currentPolygonPoints.size() - 1);
                        updateTemporaryPolygonOverlay();
                        Toast.makeText(this, "Last polygon point removed.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No polygon points to remove.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case EDIT_MODE_CIRCLE:
                    if (pendingShapeCenter == null && circleCenter != null) {
                        if (temporaryCircleCenterMarker != null) {
                            mapView.getOverlays().remove(temporaryCircleCenterMarker);
                            temporaryCircleCenterMarker = null;
                        }
                        circleCenter = null;
                        mapView.invalidate();
                        Toast.makeText(this, "Circle center removed.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Cannot undo circle radius definition. Press 'Close' to cancel.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    Toast.makeText(this, "Nothing specific to undo in this mode.", Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        closeButton.setOnClickListener(v -> {
            clearAllTemporaryDrawingStates();
            resetEditingMode();
            Toast.makeText(this, R.string.closedEditing, Toast.LENGTH_SHORT).show();
        });

        doneButton.setOnClickListener(v -> {
            switch (IS_EDIT_MODE) {
                case EDIT_MODE_LINE:
                    if (currentLinePoints.size() >= 2 && currentLinePoints.size() < 16) {
                        showShapeColorSelectionDialog();
                    } else if (currentLinePoints.size() < 2) {
                        Toast.makeText(this, "A line needs at least 2 points.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lines cannot have more than 15 points.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case EDIT_MODE_POLY:
                    if (currentPolygonPoints.size() >= 3) {
                        showShapeColorSelectionDialog();
                    } else {
                        Toast.makeText(this, "A polygon needs at least 3 points.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case EDIT_MODE_CIRCLE:
                    if (pendingShapeCenter != null && pendingShapeRadius > 0.0) {
                        showShapeColorSelectionDialog();
                    } else if (circleCenter != null && pendingShapeRadius == 0.0) {
                        Toast.makeText(this, "Tap on the map to set the circle's radius.", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Toast.makeText(this, "Circle not fully defined. Tap center then radius, or 'Close'.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case EDIT_MODE_PIN:
                    Toast.makeText(this, "Pin placement is via map tap. 'Close' to exit Pin Mode.", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    resetEditingMode();
                    break;
            }
        });


        setTileSourceInternal(TILE_SOURCE_OSM, null);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        GeoPoint startPoint = new GeoPoint(0, 0);
        mapView.getController().setCenter(startPoint);
        mapController = mapView.getController();

        myLocationMarker = new Marker(mapView);
        myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Drawable markerIconDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.position_icon, getTheme());
        if (markerIconDrawable != null) {
            int desiredWidth = 100; int desiredHeight = 90;
            Bitmap bitmap = Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            markerIconDrawable.setBounds(0, 0, desiredWidth, desiredHeight);
            markerIconDrawable.draw(canvas);
            myLocationMarker.setIcon(new BitmapDrawable(getResources(), bitmap));
        }
        myLocationMarker.setInfoWindow(null);
        mapView.getOverlays().add(myLocationMarker);


        attributionOverlay = new AttributionOverlay(this);
        mapView.getOverlays().add(attributionOverlay);


        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(this);
        mapView.getOverlays().add(0, mapEventsOverlay);
        mapView.addMapListener(this);

        updateTileToggleButton();
        btnTileToggle.setOnClickListener(v -> showTileSourceSelectionDialog());

        updateFollowToggleButton();
        btnFollowToggle.setOnClickListener(v -> {
            isFollowing = !isFollowing; updateFollowToggleButton();
            if (isFollowing && myLocationMarker.getPosition() != null && myLocationMarker.getPosition().getLatitude() != 0.0) {
                mapController.animateTo(myLocationMarker.getPosition());
            }
        });
        updateRotateToggleButton();
        btnRotateToggle.setOnClickListener(v -> {
            isRotationEnabled = !isRotationEnabled; updateRotateToggleButton();
            if (isRotationEnabled) registerOrientationSensor();
            else { unregisterOrientationSensor(); mapView.setMapOrientation(0); btnRotateToggle.setRotation(0); }
        });

        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
        });

        launchMeshtasticButton = findViewById(R.id.btn_open_mesh);
        launchMeshtasticButton.setOnClickListener(v -> {
            String packageName = "com.geeksville.mesh";
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int screenHeight = displayMetrics.heightPixels;
                int screenWidth = displayMetrics.widthPixels;
                Rect bounds = new Rect();
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                    bounds.set(0, 0, screenWidth, screenHeight / 2);
                } else {
                    bounds.set(screenWidth / 2, 0, screenWidth, screenHeight);
                }
                ActivityOptions options = ActivityOptions.makeBasic();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    options.setLaunchBounds(bounds);
                }
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                startActivity(launchIntent, options.toBundle());
            } else {
                Toast.makeText(this, "Meshtastic app not found.", Toast.LENGTH_SHORT).show();
            }
        });

        periodicNodeUpdateHandler = new Handler(Looper.getMainLooper());
        nodeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                    updateNodeLocationsOnMap();
                }
                periodicNodeUpdateHandler.postDelayed(this, NODE_UPDATE_INTERVAL_MS);
            }
        };

        mapDataRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MeshReceiverService.ACTION_MAP_DATA_REFRESH.equals(intent.getAction())) {
                    Log.d(TAG, "Activity: Received map data refresh broadcast. Reloading map data.");
                    clearAndReloadMapData();
                }
            }
        };
        syncStatusUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MeshReceiverService.ACTION_SYNC_STATUS_UPDATE.equals(intent.getAction())) {
                    String status = intent.getStringExtra(MeshReceiverService.EXTRA_SYNC_STATUS_MESSAGE);
                    if (status != null && tvSyncStatus != null) {
                        tvSyncStatus.setText("Sync: " + status);
                        Log.d(TAG, "Activity: Sync status update: " + status);
                    }
                }
            }
        };

        loadAllMapData();
        Log.d(TAG, "onCreate: Activity creation finished.");
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, android.content.res.Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        mIsInMultiWindowMode = isInMultiWindowMode;
        Log.d(TAG, "onMultiWindowModeChanged: isInMultiWindowMode = " + isInMultiWindowMode);
    }

    void updateNodeLocationsOnMap() {
        try {
            if (geeksvilleMeshServiceActivity == null || !isGeeksvilleMeshServiceActivityBound) {
                return;
            }
            List<NodeInfo> nodes = geeksvilleMeshServiceActivity.getNodes();
            if (nodes == null) {
                if (meshNodesTextView != null) meshNodesTextView.setText("Nodes: 0");
                return;
            }
            if (meshNodesTextView != null) meshNodesTextView.setText("Nodes: " + nodes.size());
            if (myNodeIdTextView != null) myNodeIdTextView.setText("My ID: " + formatNodeIdForDisplay(geeksvilleMeshServiceActivity.getMyId()));


            long currentTimeSeconds = System.currentTimeMillis() / 1000L;
            long fiveMinutesInSeconds = TimeUnit.MINUTES.toSeconds(5);
            List<String> currentDisplayedNodeIds = new ArrayList<>();

            for (NodeInfo node : nodes) {
                if (node == null || node.getUser() == null || node.getPosition() == null ||
                        Objects.equals(geeksvilleMeshServiceActivity.getMyId(), node.getUser().getId())) {
                    continue;
                }

                String nodeId = node.getUser().getId();
                currentDisplayedNodeIds.add(nodeId);
                long nodePositionTimeSeconds = node.getPosition().getTime();
                GeoPoint nodePosition = new GeoPoint(node.getPosition().getLatitude(), node.getPosition().getLongitude());

                boolean isPositionOld = (currentTimeSeconds - nodePositionTimeSeconds) > fiveMinutesInSeconds;
                int targetColorResId = isPositionOld ? ColorIndex.getColorByIndex(2) : ColorIndex.getColorByIndex(0);

                Marker existingMarker = findMarkerById(mapView, nodeId);
                if (existingMarker != null) {
                    existingMarker.setPosition(nodePosition);
                }
                addCustomPin(nodePosition, R.drawable.radio, targetColorResId, node.getUser().getShortName(), nodeId);
            }

            List<Marker> pinsToRemove = new ArrayList<>();
            for(Marker pin : customPins){
                if(pin.getId() != null) {
                    Drawable icon = pin.getIcon();
                    Drawable radioIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.radio, getTheme());
                    if (pin.getId().startsWith("!") && !currentDisplayedNodeIds.contains(pin.getId())) {
                        pinsToRemove.add(pin);
                    }
                }
            }
            for(Marker pinToRemove : pinsToRemove){
                mapView.getOverlays().remove(pinToRemove);
                customPins.remove(pinToRemove);
            }

            mapView.invalidate();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in updateNodeLocationsOnMap: " + e.toString());
        } catch (Exception e) {
            Log.e(TAG, "Exception in updateNodeLocationsOnMap: " + e.toString(), e);
        }
    }


    public Marker findMarkerById(MapView mv, String targetId) {
        if (mv == null || targetId == null) return null;
        for (Marker pin : customPins) {
            if (targetId.equals(pin.getId())) {
                return pin;
            }
        }
        return null;
    }

    void startUpdatingNodeLocationsOnMap() {
        if (nodeUpdateRunnable != null && periodicNodeUpdateHandler != null) {
            periodicNodeUpdateHandler.removeCallbacks(nodeUpdateRunnable);
            periodicNodeUpdateHandler.post(nodeUpdateRunnable);
            Log.d(TAG, "Activity: Started updating node locations on map.");
        }
    }

    void stopUpdatingNodeLocationsOnMap() {
        if (periodicNodeUpdateHandler != null && nodeUpdateRunnable != null) {
            periodicNodeUpdateHandler.removeCallbacks(nodeUpdateRunnable);
            Log.d(TAG, "Activity: Stopped updating node locations on map.");
        }
    }

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint point) {
        double lat = point.getLatitude();
        double lon = point.getLongitude();
        int elevation = (int) point.getAltitude();

        DecimalFormat df = new DecimalFormat("#.#######", new DecimalFormatSymbols(Locale.US));
        GeoPoint p = new GeoPoint(Double.parseDouble(df.format(lat)), Double.parseDouble(df.format(lon)), elevation);

        boolean handled = false;
        if (IS_EDIT_MODE != 0) {
            switch (IS_EDIT_MODE) {
                case EDIT_MODE_PIN:
                    showIconSelectionDialog(p);
                    handled = true;
                    break;
                case EDIT_MODE_LINE:
                    if (currentLinePoints.size() < 15) {
                        currentLinePoints.add(p);
                        updateTemporaryLineOverlay();
                        Toast.makeText(this, "Point " + currentLinePoints.size() + " added.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Max 15 points for a line.", Toast.LENGTH_SHORT).show();
                    }
                    handled = true;
                    break;
                case EDIT_MODE_POLY:
                    if (currentPolygonPoints.size() < 15) {
                        currentPolygonPoints.add(p);
                        updateTemporaryPolygonOverlay();
                        Toast.makeText(this, "Point " + currentPolygonPoints.size() + " added.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Max 15 points for a polygon.", Toast.LENGTH_SHORT).show();
                    }
                    handled = true;
                    break;
                case EDIT_MODE_CIRCLE:
                    if (circleCenter == null) {
                        circleCenter = p;
                        pendingShapeCenter = p;
                        Toast.makeText(this, "Circle center set. Tap again for radius.", Toast.LENGTH_SHORT).show();
                        if (temporaryCircleCenterMarker == null) {
                            temporaryCircleCenterMarker = new Marker(mapView);
                            temporaryCircleCenterMarker.setInfoWindow(null);
                            Drawable tempIcon = ContextCompat.getDrawable(this, R.drawable.ic_appintro_indicator);
                            if (tempIcon != null) {
                                temporaryCircleCenterMarker.setIcon(tempIcon);
                            } else {
                                temporaryCircleCenterMarker.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.pin, getTheme()));
                            }
                            temporaryCircleCenterMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                        }
                        temporaryCircleCenterMarker.setPosition(circleCenter);
                        if (!mapView.getOverlays().contains(temporaryCircleCenterMarker)) {
                            mapView.getOverlays().add(temporaryCircleCenterMarker);
                        }
                    } else {
                        double radius = circleCenter.distanceToAsDouble(p);
                        if (radius > 1) {
                            pendingShapeRadius = radius;
                            if (temporaryCircleCenterMarker != null) {
                                mapView.getOverlays().remove(temporaryCircleCenterMarker);
                                temporaryCircleCenterMarker = null;
                            }
                            showShapeColorSelectionDialog();
                        } else {
                            Toast.makeText(this, "Radius too small. Tap further from center.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    mapView.invalidate();
                    handled = true;
                    break;
            }
        }
        return handled;
    }

    @Override
    public boolean longPressHelper(GeoPoint p) { return false; }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mapController == null) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:  mapController.zoomIn(); return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN: mapController.zoomOut(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateTemporaryLineOverlay() {
        if (mapView == null) return;
        if (temporaryLineOverlay != null) {
            mapView.getOverlays().remove(temporaryLineOverlay);
        }

        if (currentLinePoints.isEmpty()) {
            temporaryLineOverlay = null;
            mapView.invalidate();
            return;
        }

        temporaryLineOverlay = new Polyline(mapView);
        temporaryLineOverlay.setInfoWindow(null);
        temporaryLineOverlay.setColor(Color.argb(255,0,255,0));
        temporaryLineOverlay.getOutlinePaint().setStrokeWidth(8f * getResources().getDisplayMetrics().density);
        temporaryLineOverlay.setPoints(new ArrayList<>(currentLinePoints));
        mapView.getOverlays().add(temporaryLineOverlay);
        mapView.invalidate();
    }

    private void updateTemporaryPolygonOverlay() {
        if (mapView == null) return;
        if (temporaryPolygonOverlay != null) {
            mapView.getOverlays().remove(temporaryPolygonOverlay);
        }

        if (currentPolygonPoints.isEmpty()) {
            temporaryPolygonOverlay = null;
            mapView.invalidate();
            return;
        }
        if (currentPolygonPoints.size() < 2 && temporaryPolygonOverlay != null) {
            mapView.getOverlays().remove(temporaryPolygonOverlay);
            temporaryPolygonOverlay = null;
        }


        temporaryPolygonOverlay = new Polygon(mapView);
        temporaryPolygonOverlay.setInfoWindow(null);
        temporaryPolygonOverlay.getFillPaint().setColor(Color.argb(155, 0, 255, 0));
        temporaryPolygonOverlay.getOutlinePaint().setColor(Color.rgb(0,255,0));
        temporaryPolygonOverlay.getOutlinePaint().setStrokeWidth(6f * getResources().getDisplayMetrics().density);
        temporaryPolygonOverlay.setPoints(new ArrayList<>(currentPolygonPoints));
        mapView.getOverlays().add(temporaryPolygonOverlay);
        mapView.invalidate();
    }

    private void showShapeColorSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Shape Color");

        List<ColorItem> colorItems = ColorIndex.getColorItemsList();
        ColorArrayAdapter adapter = new ColorArrayAdapter(this, colorItems);

        builder.setAdapter(adapter, (dialog, which) -> {
            ColorItem selectedColorItem = colorItems.get(which);
            int actualColorValue = ContextCompat.getColor(this, selectedColorItem.getColorResourceId());
            int colorIndex = ColorIndex.getIndexByColorId(selectedColorItem.getColorResourceId());
            boolean dataChangedForWear = false;

            if (IS_EDIT_MODE == EDIT_MODE_CIRCLE && pendingShapeCenter != null && pendingShapeRadius > 0) {
                CircleInfo circleInfo = new CircleInfo();
                drawCircleOnMap(pendingShapeCenter, pendingShapeRadius, actualColorValue, circleInfo.getUniqueId());
                circleInfo.setLatitude(pendingShapeCenter.getLatitude());
                circleInfo.setLongitude(pendingShapeCenter.getLongitude());
                circleInfo.setRadius(pendingShapeRadius);
                circleInfo.setColor(colorIndex);
                if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                    MeshtasticConnector.sendData(geeksvilleMeshServiceActivity, circleInfo.encode(), "CIRCLE", DataPacket.ID_BROADCAST);
                }
                mapDataDbHelper.addCircle(circleInfo);
                Toast.makeText(this, "Circle added.", Toast.LENGTH_SHORT).show();
                pendingShapeCenter = null;
                pendingShapeRadius = 0.0;
                circleCenter = null;
                if (temporaryCircleCenterMarker != null) {
                    mapView.getOverlays().remove(temporaryCircleCenterMarker);
                    temporaryCircleCenterMarker = null;
                }
                dataChangedForWear = true;
            } else if (IS_EDIT_MODE == EDIT_MODE_LINE && currentLinePoints.size() >= 2) {
                LineInfo lineInfo = new LineInfo();
                drawFinalLine(new ArrayList<>(currentLinePoints), actualColorValue, lineInfo.getUniqueId());
                lineInfo.setPoints(currentLinePoints);
                lineInfo.setColor(colorIndex);
                if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                    MeshtasticConnector.sendData(geeksvilleMeshServiceActivity, lineInfo.encode(), "LINE", DataPacket.ID_BROADCAST);
                }
                mapDataDbHelper.addLine(lineInfo);
                Toast.makeText(this, "Line added.", Toast.LENGTH_SHORT).show();
                clearTemporaryLineState();
                dataChangedForWear = true;
            } else if (IS_EDIT_MODE == EDIT_MODE_POLY && currentPolygonPoints.size() >= 3) {
                PolygonInfo polyInfo = new PolygonInfo();
                drawFinalPolygon(new ArrayList<>(currentPolygonPoints), actualColorValue, polyInfo.getUniqueId());
                polyInfo.setPoints(currentPolygonPoints);
                polyInfo.setColor(colorIndex);
                if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                    MeshtasticConnector.sendData(geeksvilleMeshServiceActivity, polyInfo.encode(), "POLY", DataPacket.ID_BROADCAST);
                }
                mapDataDbHelper.addPolygon(polyInfo);
                Toast.makeText(this, "Polygon added.", Toast.LENGTH_SHORT).show();
                clearTemporaryPolygonState();
                dataChangedForWear = true;
            }
            resetEditingMode();

            if (dataChangedForWear) {
                Intent syncIntent = new Intent(this, MeshReceiverService.class);
                syncIntent.setAction(MeshReceiverService.ACTION_TRIGGER_WEAR_MAP_SYNC);
                startService(syncIntent);
                Log.d(TAG, "Sent ACTION_TRIGGER_WEAR_MAP_SYNC to MeshReceiverService after shape add/modify.");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(this, "Shape creation cancelled.", Toast.LENGTH_LONG).show();
            if (IS_EDIT_MODE == EDIT_MODE_CIRCLE) {
                if (temporaryCircleCenterMarker != null && pendingShapeRadius == 0.0) {
                    mapView.getOverlays().remove(temporaryCircleCenterMarker);
                    temporaryCircleCenterMarker = null;
                }
                circleCenter = null;
                pendingShapeCenter = null;
                pendingShapeRadius = 0.0;
            }
        });
        builder.create().show();
    }

    private void clearTemporaryLineState() {
        currentLinePoints.clear();
        if (temporaryLineOverlay != null && mapView != null) {
            mapView.getOverlays().remove(temporaryLineOverlay);
            temporaryLineOverlay = null;
        }
        if (mapView != null) mapView.invalidate();
    }

    private void clearTemporaryPolygonState() {
        currentPolygonPoints.clear();
        if (temporaryPolygonOverlay != null && mapView != null) {
            mapView.getOverlays().remove(temporaryPolygonOverlay);
            temporaryPolygonOverlay = null;
        }
        if (mapView != null) mapView.invalidate();
    }

    private void clearAllTemporaryDrawingStates() {
        circleCenter = null;
        pendingShapeCenter = null;
        pendingShapeRadius = 0.0;
        if (temporaryCircleCenterMarker != null && mapView != null) {
            mapView.getOverlays().remove(temporaryCircleCenterMarker);
            temporaryCircleCenterMarker = null;
        }
        clearTemporaryLineState();
        clearTemporaryPolygonState();

        if (mapView != null) mapView.invalidate();
    }

    private void drawCircleOnMap(GeoPoint center, double radiusInMeters, int baseColor, String uuid) {
        for (Polygon existingPoly : drawnPolygons) {
            if (uuid.equals(existingPoly.getId())) {
                mapView.getOverlays().remove(existingPoly);
                drawnPolygons.remove(existingPoly);
                break;
            }
        }

        Polygon circlePolygon = new Polygon(mapView);
        ArrayList<GeoPoint> circlePoints = Polygon.pointsAsCircle(center, radiusInMeters);
        circlePolygon.setPoints(circlePoints);
        circlePolygon.setId(uuid);

        int outlineColor = baseColor;
        int red = Color.red(baseColor);
        int green = Color.green(baseColor);
        int blue = Color.blue(baseColor);
        int fillColor = Color.argb(80, red, green, blue);
        float outlineWidth = 3.0f * getResources().getDisplayMetrics().density;

        circlePolygon.getFillPaint().setColor(fillColor);
        circlePolygon.getOutlinePaint().setColor(outlineColor);
        circlePolygon.getOutlinePaint().setStrokeWidth(outlineWidth);

        final String circleInfoTitle = "Circle - Radius: " + String.format(Locale.US, "%.1f", radiusInMeters) + "m";
        circlePolygon.setTitle(circleInfoTitle);

        circlePolygon.setOnClickListener((polygon, mv, eventPos) -> {
            if (IS_EDIT_MODE == 0) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Circle Options")
                        .setMessage(polygon.getTitle() + "\nID: " + polygon.getId() + "\nRemove this circle?")
                        .setPositiveButton("Remove", (d, w) -> {
                            mv.getOverlays().remove(polygon);
                            drawnPolygons.remove(polygon);
                            if (polygon.getId() != null) {
                                mapDataDbHelper.softDeleteMapObject(polygon.getId());
                                if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                                    MeshtasticConnector.sendDeleteCommand(geeksvilleMeshServiceActivity, polygon.getId());
                                }
                                Intent syncIntent = new Intent(this, MeshReceiverService.class);
                                syncIntent.setAction(MeshReceiverService.ACTION_TRIGGER_WEAR_MAP_SYNC);
                                startService(syncIntent);
                                Log.d(TAG, "Sent ACTION_TRIGGER_WEAR_MAP_SYNC to MeshReceiverService after circle removal.");
                            }
                            mv.invalidate();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            return true;
        });

        mapView.getOverlays().add(0, circlePolygon);
        drawnPolygons.add(circlePolygon);
        mapView.invalidate();
    }

    private void drawFinalLine(List<GeoPoint> points, int color, String uuid) {
        if (mapView == null || points == null || points.size() < 2) return;

        for (Polyline existingLine : drawnLines) {
            if (uuid.equals(existingLine.getId())) {
                mapView.getOverlays().remove(existingLine);
                drawnLines.remove(existingLine);
                break;
            }
        }

        Polyline line = new Polyline(mapView);
        line.setPoints(points);
        line.setColor(color);
        line.getOutlinePaint().setStrokeWidth(7f * getResources().getDisplayMetrics().density);
        line.setId(uuid);

        line.setOnClickListener((polyline, mv, eventPos) -> {
            if (IS_EDIT_MODE == 0) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Line Options")
                        .setMessage("ID: " + polyline.getId() + "\nRemove this line?")
                        .setPositiveButton("Remove", (d, w) -> {
                            mv.getOverlays().remove(polyline);
                            drawnLines.remove(polyline);
                            if (polyline.getId() != null) {
                                mapDataDbHelper.softDeleteMapObject(polyline.getId());
                                if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                                    MeshtasticConnector.sendDeleteCommand(geeksvilleMeshServiceActivity, polyline.getId());
                                }
                                Intent syncIntent = new Intent(this, MeshReceiverService.class);
                                syncIntent.setAction(MeshReceiverService.ACTION_TRIGGER_WEAR_MAP_SYNC);
                                startService(syncIntent);
                                Log.d(TAG, "Sent ACTION_TRIGGER_WEAR_MAP_SYNC to MeshReceiverService after line removal.");
                            }
                            mv.invalidate();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            return true;
        });
        mapView.getOverlays().add(0, line);
        drawnLines.add(line);
        mapView.invalidate();
    }

    private void drawFinalPolygon(List<GeoPoint> points, int baseColor, String uuid) {
        if (mapView == null || points == null || points.size() < 3) return;

        for (Polygon existingPoly : drawnPolygons) {
            if (uuid.equals(existingPoly.getId())) {
                mapView.getOverlays().remove(existingPoly);
                drawnPolygons.remove(existingPoly);
                break;
            }
        }

        Polygon polygon = new Polygon(mapView);
        polygon.setPoints(points);
        polygon.setId(uuid);

        int outlineColor = baseColor;
        int red = Color.red(baseColor); int green = Color.green(baseColor); int blue = Color.blue(baseColor);
        int fillColor = Color.argb(100, red, green, blue);

        polygon.getFillPaint().setColor(fillColor);
        polygon.getOutlinePaint().setColor(outlineColor);
        polygon.getOutlinePaint().setStrokeWidth(7f * getResources().getDisplayMetrics().density);

        polygon.setOnClickListener((poly, mv, eventPos) -> {
            if (IS_EDIT_MODE == 0) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Polygon Options")
                        .setMessage("ID: " + poly.getId() + "\nRemove this polygon?")
                        .setPositiveButton("Remove", (d, w) -> {
                            mv.getOverlays().remove(poly);
                            drawnPolygons.remove(poly);
                            if (poly.getId() != null) {
                                mapDataDbHelper.softDeleteMapObject(poly.getId());
                                if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                                    MeshtasticConnector.sendDeleteCommand(geeksvilleMeshServiceActivity, poly.getId());
                                }
                                Intent syncIntent = new Intent(this, MeshReceiverService.class);
                                syncIntent.setAction(MeshReceiverService.ACTION_TRIGGER_WEAR_MAP_SYNC);
                                startService(syncIntent);
                                Log.d(TAG, "Sent ACTION_TRIGGER_WEAR_MAP_SYNC to MeshReceiverService after polygon removal.");
                            }
                            mv.invalidate();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            return true;
        });
        mapView.getOverlays().add(0, polygon);
        drawnPolygons.add(polygon);
        mapView.invalidate();
    }

    private void showIconSelectionDialog(GeoPoint p) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Pin Icon");
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_icon_grid_view, null);
        builder.setView(dialogView);
        RecyclerView iconRecyclerView = dialogView.findViewById(R.id.icon_recycler_view);

        IconResIndex iconResIndex = new IconResIndex();
        List<IconResIndex.IconItem> iconItems = iconResIndex.getIconItemsList();
        final AlertDialog[] dialogHolder = new AlertDialog[1];

        IconGridAdapter.OnIconClickListener iconClickListener = selectedIconItem -> {
            if (dialogHolder[0] != null && dialogHolder[0].isShowing()) {
                dialogHolder[0].dismiss();
            }
            showColorSelectionDialog(p, selectedIconItem.getIconResourceId());
        };

        IconGridAdapter adapter = new IconGridAdapter(this, iconItems, iconClickListener);
        iconRecyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        iconRecyclerView.setAdapter(adapter);

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(this, "Pin creation cancelled.", Toast.LENGTH_SHORT).show();
            resetEditingMode();
        });
        dialogHolder[0] = builder.create();
        dialogHolder[0].show();
    }

    private void showColorSelectionDialog(GeoPoint p, int selectedIconResourceId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Pin Color");
        List<ColorItem> colorItems = ColorIndex.getColorItemsList();
        ColorArrayAdapter adapter = new ColorArrayAdapter(this, colorItems);

        builder.setAdapter(adapter, (dialog, which) -> {
            ColorItem selectedColorItem = colorItems.get(which);
            showPinLabelDialog(p, selectedIconResourceId, selectedColorItem.getColorResourceId());
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(this, "Pin creation cancelled.", Toast.LENGTH_SHORT).show();
            resetEditingMode();
        });
        builder.create().show();
    }

    private void showPinLabelDialog(GeoPoint p, int iconResourceId, int colorResId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Pin Label (Max 10 chars)");
        final EditText input = new EditText(this);
        InputFilter lengthFilter = new InputFilter.LengthFilter(10);
        InputFilter uppercaseAlnumFilter = (source, start, end, dest, dstart, dend) -> {
            StringBuilder filtered = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (Character.isLetterOrDigit(c) || c == ' ') {
                    filtered.append(Character.toUpperCase(c));
                }
            }
            return filtered.toString();
        };
        input.setFilters(new InputFilter[]{lengthFilter, uppercaseAlnumFilter});
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("e.g., TARGET1 (Optional)");
        builder.setView(input);

        builder.setPositiveButton("Add Pin", (dialog, which) -> {
            String labelText = input.getText().toString().trim();
            if (labelText.isEmpty()) labelText = "PIN";

            PinInfo pinInfo = new PinInfo();
            addCustomPin(p, iconResourceId, colorResId, labelText, pinInfo.getUniqueId());

            pinInfo.setLatitude(p.getLatitude());
            pinInfo.setLongitude(p.getLongitude());
            pinInfo.setLabel(labelText);
            pinInfo.setIconResourceId(IconResIndex.getIndexByIconResId(iconResourceId));
            pinInfo.setColor(ColorIndex.getIndexByColorId(colorResId));
            pinInfo.setElevation((int) p.getAltitude());
            pinInfo.setRotation(0);

            mapDataDbHelper.addPin(pinInfo);
            if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                MeshtasticConnector.sendData(geeksvilleMeshServiceActivity, pinInfo.encode(), "PIN", DataPacket.ID_BROADCAST);
            }
            Toast.makeText(this, "Pin added and sent.", Toast.LENGTH_SHORT).show();
            resetEditingMode();

            Intent syncIntent = new Intent(this, MeshReceiverService.class);
            syncIntent.setAction(MeshReceiverService.ACTION_TRIGGER_WEAR_MAP_SYNC);
            startService(syncIntent);
            Log.d(TAG, "Sent ACTION_TRIGGER_WEAR_MAP_SYNC to MeshReceiverService after pin add.");
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(MainActivity.this, "Pin creation cancelled.", Toast.LENGTH_SHORT).show();
            resetEditingMode();
        });
        builder.show();
    }

    private void resetEditingMode() {
        IS_EDIT_MODE = 0;
        ImageButton toggleToolsBtn = findViewById(R.id.btn_Toggle_Tools);
        LinearLayout editingToolsLayout = findViewById(R.id.editingTools);
        LinearLayout infoLayout = findViewById(R.id.info);
        LinearLayout toolMenuLayout = findViewById(R.id.tools_layout);

        if (toggleToolsBtn != null) toggleToolsBtn.setVisibility(View.VISIBLE);
        if (editingToolsLayout != null) editingToolsLayout.setVisibility(View.GONE);
        if (infoLayout != null) infoLayout.setVisibility(View.VISIBLE);

        if (toggleToolsBtn != null && toolMenuLayout != null) {
            if (toolMenuLayout.getVisibility() == View.GONE) {
                toggleToolsBtn.setImageResource(R.drawable.plus);
                toggleToolsBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,R.color.green)));
            } else {
                toggleToolsBtn.setImageResource(R.drawable.minus);
                toggleToolsBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,R.color.red)));
            }
        }
        clearAllTemporaryDrawingStates();
        Log.d(TAG, "Editing mode reset.");
    }


    Marker addCustomPin(GeoPoint position, int iconResourceId, int colorResId, String labelText, String uuid) {
        if (mapView == null) {
            Log.e(TAG, "mapView is null in addCustomPin.");
            return null;
        }

        Marker existingMarker = findMarkerById(mapView, uuid);
        if (existingMarker != null) {
            mapView.getOverlays().remove(existingMarker);
            customPins.remove(existingMarker);
        }

        Marker customMarker = new Marker(mapView);
        customMarker.setPosition(position);
        customMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        customMarker.setId(uuid);
        customMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        customMarker.setTitle(labelText);
        customMarker.setInfoWindow(null);

        Drawable outlineDrawable = ContextCompat.getDrawable(this, iconResourceId);
        Drawable coloredDrawable = ContextCompat.getDrawable(this, iconResourceId);
        int actualColor = ContextCompat.getColor(this, colorResId);

        if (outlineDrawable != null && coloredDrawable != null) {
            outlineDrawable = DrawableCompat.wrap(outlineDrawable.mutate());
            coloredDrawable = DrawableCompat.wrap(coloredDrawable.mutate());

            DrawableCompat.setTint(outlineDrawable, Color.BLACK);
            DrawableCompat.setTint(coloredDrawable, actualColor);

            float density = getResources().getDisplayMetrics().density;
            int baseIconSize = (int) (32 * density);
            int outlineOffset = (int) (2 * density);
            int textPadding = (int) (3 * density);
            float textSize = 11 * density;

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

            int finalWidth = Math.max(baseIconSize + outlineOffset * 2, labelBgWidth);
            int finalHeight = (baseIconSize + outlineOffset * 2) + (int)(1 * density) + labelBgHeight;

            Bitmap finalBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(finalBitmap);

            int iconLeft = (finalWidth - baseIconSize) / 2;
            int iconTop = outlineOffset;

            int[] dx = {-outlineOffset, 0, outlineOffset, -outlineOffset, outlineOffset, -outlineOffset, 0, outlineOffset};
            int[] dy = {-outlineOffset, -outlineOffset, -outlineOffset, 0, 0, outlineOffset, outlineOffset, outlineOffset};

            for(int i=0; i < dx.length; i++){
                outlineDrawable.setBounds(
                        iconLeft + dx[i],
                        iconTop + dy[i],
                        iconLeft + dx[i] + baseIconSize,
                        iconTop + dy[i] + baseIconSize
                );
                outlineDrawable.draw(canvas);
            }

            coloredDrawable.setBounds(
                    iconLeft,
                    iconTop,
                    iconLeft + baseIconSize,
                    iconTop + baseIconSize
            );
            coloredDrawable.draw(canvas);

            Paint labelBgPaint = new Paint();
            labelBgPaint.setColor(Color.argb(180, 0, 0, 0));
            labelBgPaint.setAntiAlias(true);

            int labelBgTop = iconTop + baseIconSize + outlineOffset + (int)(1*density);
            int labelBgLeft = (finalWidth - labelBgWidth) / 2;

            RectF labelBgRect = new RectF(labelBgLeft, labelBgTop, labelBgLeft + labelBgWidth, labelBgTop + labelBgHeight);
            canvas.drawRoundRect(labelBgRect, 6 * density, 6 * density, labelBgPaint);

            float textX = finalWidth / 2f;
            float textY = labelBgTop + (labelBgHeight / 2f) - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(labelText, textX, textY, textPaint);

            customMarker.setIcon(new BitmapDrawable(getResources(), finalBitmap));
        } else {
            Log.w(TAG, "Icon drawable was null for resource ID: " + iconResourceId + ". Using default.");
            customMarker.setIcon(ResourcesCompat.getDrawable(getResources(), org.osmdroid.library.R.drawable.marker_default, getTheme()));
        }

        if (iconResourceId != R.drawable.radio) {
            customMarker.setOnMarkerClickListener((marker, mv) -> {
                if (IS_EDIT_MODE == 0) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Pin Options")
                            .setMessage("Label: " + marker.getTitle() + "\nID: " + marker.getId() + "\nRemove this pin?")
                            .setPositiveButton("Remove", (dialog, which) -> {
                                if (marker.getId() != null) {
                                    removeCustomPinAndNotify(marker, marker.getId());
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
                return true;
            });
        }

        mapView.getOverlays().add(customMarker);
        if (!customPins.contains(customMarker)) {
            customPins.add(customMarker);
        }
        mapView.invalidate();
        return customMarker;
    }

    private void removeCustomPinAndNotify(Marker markerToRemove, String uuid) {
        if (mapView.getOverlays().remove(markerToRemove)) {
            customPins.remove(markerToRemove);
            mapView.invalidate();
            mapDataDbHelper.softDeleteMapObject(uuid);
            if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
                MeshtasticConnector.sendDeleteCommand(geeksvilleMeshServiceActivity, uuid);
            }
            Toast.makeText(this, "Pin '" + markerToRemove.getTitle() + "' removed.", Toast.LENGTH_SHORT).show();

            Intent syncIntent = new Intent(this, MeshReceiverService.class);
            syncIntent.setAction(MeshReceiverService.ACTION_TRIGGER_WEAR_MAP_SYNC);
            startService(syncIntent);
            Log.d(TAG, "Sent ACTION_TRIGGER_WEAR_MAP_SYNC to MeshReceiverService after pin removal.");
        } else {
            Log.w(TAG, "Attempted to remove a pin that was not on the map's overlay list: " + uuid);
        }
    }

    private void loadSavedPins() {
        if (mapDataDbHelper == null) return;
        List<PinInfo> savedPins = mapDataDbHelper.getAllPins();
        Log.i(TAG, "Loading " + savedPins.size() + " saved pins...");
        for (PinInfo pinInfo : savedPins) {
            GeoPoint position = new GeoPoint(pinInfo.getLatitude(), pinInfo.getLongitude(), pinInfo.getElevation());
            int colorResId = ColorIndex.getColorByIndex(pinInfo.getColor());
            int iconResId = IconResIndex.getIconResIdbyIndex(pinInfo.getIconResourceId());
            if (iconResId == -1) iconResId = R.drawable.pin;
            addCustomPin(position, iconResId, colorResId, pinInfo.getLabel(), pinInfo.getUniqueId());
        }
    }

    private void loadSavedPolygons() {
        if (mapDataDbHelper == null) return;
        List<PolygonInfo> savedPolygons = mapDataDbHelper.getAllPolygons();
        Log.i(TAG, "Loading " + savedPolygons.size() + " saved polygons...");
        for (PolygonInfo polygonInfo : savedPolygons) {
            List<GeoPoint> points = polygonInfo.getPoints();
            if (points != null && points.size() >=3) {
                int colorResId = ColorIndex.getColorByIndex(polygonInfo.getColor());
                int actualColorValue = ContextCompat.getColor(this, colorResId);
                drawFinalPolygon(points, actualColorValue, polygonInfo.getUniqueId());
            }
        }
    }

    private void loadSavedLines() {
        if (mapDataDbHelper == null) return;
        List<LineInfo> savedLines = mapDataDbHelper.getAllLines();
        Log.i(TAG, "Loading " + savedLines.size() + " saved lines...");
        for (LineInfo lineInfo : savedLines) {
            List<GeoPoint> points = lineInfo.getPoints();
            if (points != null && points.size() >= 2) {
                int colorResId = ColorIndex.getColorByIndex(lineInfo.getColor());
                int actualColorValue = ContextCompat.getColor(this, colorResId);
                drawFinalLine(points, actualColorValue, lineInfo.getUniqueId());
            }
        }
    }

    private void loadSavedCircles() {
        if (mapDataDbHelper == null) return;
        List<CircleInfo> savedCircles = mapDataDbHelper.getAllCircles();
        Log.i(TAG, "Loading " + savedCircles.size() + " saved circles...");
        for (CircleInfo circleInfo : savedCircles) {
            GeoPoint center = new GeoPoint(circleInfo.getLatitude(), circleInfo.getLongitude());
            double radius = circleInfo.getRadius();
            int colorResId = ColorIndex.getColorByIndex(circleInfo.getColor());
            int actualColorValue = ContextCompat.getColor(this, colorResId);
            drawCircleOnMap(center, radius, actualColorValue, circleInfo.getUniqueId());
        }
    }


    public void loadAllMapData() {
        Log.i(TAG, "Activity: Loading all saved map data...");
        clearVisualMapObjects();

        loadSavedPins();
        loadSavedPolygons();
        loadSavedLines();
        loadSavedCircles();
        Log.i(TAG, "Activity: Finished loading all saved map data.");
        if (mapView != null) mapView.invalidate();

        Intent syncIntent = new Intent(this, MeshReceiverService.class);
        syncIntent.setAction(MeshReceiverService.ACTION_TRIGGER_WEAR_MAP_SYNC);
        startService(syncIntent);
        Log.d(TAG, "Sent ACTION_TRIGGER_WEAR_MAP_SYNC to MeshReceiverService after loadAllMapData.");
    }

    private void clearVisualMapObjects() {
        if (mapView == null) return;
        Log.d(TAG, "Clearing visual map objects (pins, lines, polygons/circles)...");

        List<Marker> pinsToRemove = new ArrayList<>(customPins);
        for (Marker pin : pinsToRemove) {
            mapView.getOverlays().remove(pin);
        }
        customPins.clear();

        List<Polyline> linesToRemove = new ArrayList<>(drawnLines);
        for (Polyline line : linesToRemove) {
            mapView.getOverlays().remove(line);
        }
        drawnLines.clear();

        List<Polygon> polygonsToRemove = new ArrayList<>(drawnPolygons);
        for (Polygon polygon : polygonsToRemove) {
            mapView.getOverlays().remove(polygon);
        }
        drawnPolygons.clear();
        mapView.invalidate();
    }



    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateTopBarInfoDisplay() throws RemoteException {
        if (gnssStatus != null) {
            int numSatellitesTotal = 0;
            int numFixed = 0;
            for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
                numSatellitesTotal++;
                if (gnssStatus.usedInFix(i)) numFixed++;
            }
            tvSatelliteStatus.setText("GPS: OK");
            tvNumSatellites.setText("Sats: " + numFixed + "/" + numSatellitesTotal);
        } else {
            tvSatelliteStatus.setText("GPS: Searching...");
            tvNumSatellites.setText("Sats: 0/0");
        }

        Location lastKnownLocation = null;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation == null) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        }
        if (lastKnownLocation != null && lastKnownLocation.hasAccuracy()) {
            tvAccuracy.setText("Acc: ±" + String.format(Locale.US, "%.0f", lastKnownLocation.getAccuracy()) + "m");
        } else {
            tvAccuracy.setText("Acc: N/A");
        }

        if (isGeeksvilleMeshServiceActivityBound && geeksvilleMeshServiceActivity != null) {
            try {
                if (geeksvilleMeshServiceActivity.getNodes() != null) {
                    meshNodesTextView.setText("Nodes: " + geeksvilleMeshServiceActivity.getNodes().size());
                } else {
                    meshNodesTextView.setText("Nodes: 0");
                }
                myNodeIdTextView.setText("My ID: " + formatNodeIdForDisplay(geeksvilleMeshServiceActivity.getMyId()));
                meshStatusTextView.setText("Mesh: OK");
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in updateTopBarInfoDisplay for mesh info", e);
                meshStatusTextView.setText("Mesh: Error");
            }
        } else {
            meshNodesTextView.setText("Nodes: N/A");
            myNodeIdTextView.setText("My ID: N/A");
            meshStatusTextView.setText("Mesh: OFFLINE");
        }
    }

    private String formatNodeIdForDisplay(String nodeId) {
        if (nodeId == null) return "N/A";
        if (nodeId.startsWith("!")) {
            return nodeId.length() > 9 ? "!" + nodeId.substring(nodeId.length() - 8) : nodeId;
        }
        return nodeId.length() > 8 ? "!" + nodeId.substring(nodeId.length() - 8) : "!" + nodeId;
    }

    private List<File> getMbtilesFiles() {
        List<File> mbtilesFilesList = new ArrayList<>();
        File mbtilesDir = new File(getExternalFilesDir(null), MBTILES_SUBDIRECTORY);

        if (!mbtilesDir.exists()) {
            if (!mbtilesDir.mkdirs()) {
                Log.e(TAG, "Failed to create mbtiles directory: " + mbtilesDir.getAbsolutePath());
                Toast.makeText(this, "Could not create mbtiles directory. Please create it manually: " + mbtilesDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return mbtilesFilesList;
            }
        }

        if (mbtilesDir.exists() && mbtilesDir.isDirectory()) {
            File[] files = mbtilesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mbtiles"));
            if (files != null) {
                Collections.addAll(mbtilesFilesList, files);
                Collections.sort(mbtilesFilesList, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            }
        } else {
            Log.e(TAG, "MBTiles directory is not a directory or does not exist: " + mbtilesDir.getAbsolutePath());
        }
        return mbtilesFilesList;
    }


    private void showTileSourceSelectionDialog() {
        List<String> dialogDisplayItems = new ArrayList<>();
        List<Object> sourceDataObject = new ArrayList<>();

        dialogDisplayItems.add("--- Online Sources ---");
        sourceDataObject.add("HEADER_ONLINE");

        dialogDisplayItems.add(TILE_SOURCE_OSM);
        sourceDataObject.add(TILE_SOURCE_OSM);

        dialogDisplayItems.add(TILE_SOURCE_ESRI);
        sourceDataObject.add(TILE_SOURCE_ESRI);

        List<File> mbtilesDiskFiles = getMbtilesFiles();
        if (!mbtilesDiskFiles.isEmpty()) {
            dialogDisplayItems.add("--- Offline Sources ---");
            sourceDataObject.add("HEADER_OFFLINE");

            for (File file : mbtilesDiskFiles) {
                dialogDisplayItems.add(file.getName());
                sourceDataObject.add(file);
            }
        } else {
            dialogDisplayItems.add("(No offline .mbtiles files found in /" + MBTILES_SUBDIRECTORY + " folder)");
            sourceDataObject.add(null);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Tile Source");

        builder.setItems(dialogDisplayItems.toArray(new String[0]), (dialog, which) -> {
            Object selectedItemData = sourceDataObject.get(which);

            if (selectedItemData instanceof String) {
                String selectedName = (String) selectedItemData;
                if (TILE_SOURCE_OSM.equals(selectedName)) {
                    setTileSourceInternal(TILE_SOURCE_OSM, null);
                } else if (TILE_SOURCE_ESRI.equals(selectedName)) {
                    setTileSourceInternal(TILE_SOURCE_ESRI, null);
                } else if (selectedName.startsWith("HEADER")) {
                    return;
                }
            } else if (selectedItemData instanceof File) {
                setTileSourceInternal(null, (File) selectedItemData);
            }

            dialog.dismiss();
        });

        builder.create().show();
    }



    private void setTileSourceInternal(@Nullable String onlineSourceName, @Nullable File mbtilesFile) {
        if (mapView == null) return;

        try {
            if (mbtilesFile != null) {
                this.currentTileSourceName = mbtilesFile.getName();
                this.currentMbtilesFile = mbtilesFile;
                this.isSatellite = false;

                String mbtilesName = mbtilesFile.getName().replace(".mbtiles", "");

                int minZoom = 0;
                int maxZoom = 25;
                String tileExtension = ".png";

                ITileSource mbTileSource = new XYTileSource(
                        mbtilesName,
                        minZoom,
                        maxZoom,
                        256,
                        tileExtension,
                        new String[]{},
                        "Offline Map: " + mbtilesName
                );
                IArchiveFile mbArchive = MBTilesFileArchive.getDatabaseFileArchive(mbtilesFile);
                IRegisterReceiver registerReceiver = new SimpleRegisterReceiver(this);
                MapTileFileArchiveProvider archiveProvider = new MapTileFileArchiveProvider(
                        registerReceiver,
                        mbTileSource,
                        new IArchiveFile[]{mbArchive}
                );
                MapTileApproximater approximater = new MapTileApproximater();
                approximater.addProvider(archiveProvider);
                MapTileModuleProviderBase[] modules = new MapTileModuleProviderBase[]{
                        archiveProvider,
                        approximater
                };

                MapTileProviderArray tileProviderArray = new MapTileProviderArray(
                        mbTileSource,
                        registerReceiver,
                        modules
                );

                mapView.setTileProvider(tileProviderArray);
                mapView.setTileSource(mbTileSource);
                mapView.setTilesScaledToDpi(false);
                mapView.setMinZoomLevel((double) minZoom);
                mapView.setMaxZoomLevel((double) maxZoom);

                if (attributionOverlay != null) {
                    attributionOverlay.setAttribution("Offline Map: " + mbtilesName);
                }

            } else if (TILE_SOURCE_ESRI.equals(onlineSourceName)) {
                this.currentTileSourceName = TILE_SOURCE_ESRI;
                this.currentMbtilesFile = null;
                this.isSatellite = true;

                OnlineTileSourceBase esriTileSource = new OnlineTileSourceBase(
                        "ESRI WorldImagery", 0, 19, 256, ".jpeg",
                        new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"},
                        "Esri, Maxar, Earthstar Geographics, and the GIS User Community") {
                    @Override
                    public String getTileURLString(long pMapTileIndex) {
                        return getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + mImageFilenameEnding;
                    }
                };
                mapView.setTileProvider(new MapTileProviderBasic(getApplicationContext()));
                mapView.setTileSource(esriTileSource);
                mapView.setTilesScaledToDpi(true);
                if (attributionOverlay != null) attributionOverlay.setAttribution(esriTileSource.getCopyrightNotice());
                mapView.setMinZoomLevel(0.0);
                mapView.setMaxZoomLevel(19.0);
                if (mapController != null && mapView.getZoomLevelDouble() > 19) mapController.setZoom(19.0);

            } else {
                this.currentTileSourceName = TILE_SOURCE_OSM;
                this.currentMbtilesFile = null;
                this.isSatellite = false;

                mapView.setTileProvider(new MapTileProviderBasic(getApplicationContext()));
                mapView.setTileSource(TileSourceFactory.MAPNIK);
                mapView.setTilesScaledToDpi(false);
                if (attributionOverlay != null) attributionOverlay.setAttribution(TileSourceFactory.MAPNIK.getCopyrightNotice());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in setTileSourceInternal: ", e);
            Toast.makeText(this, "Failed to set tile source. Defaulting to OSM.", Toast.LENGTH_LONG).show();
            if (!TILE_SOURCE_OSM.equals(this.currentTileSourceName) || this.currentMbtilesFile != null) {
                setTileSourceInternal(TILE_SOURCE_OSM, null);
            }
        }

        mapView.invalidate();
        updateTileToggleButton();
    }



    private void updateTileToggleButton() { btnTileToggle.setImageResource(isSatellite ? R.drawable.layers_icon : R.drawable.layers_icon); }
    private void updateFollowToggleButton() { btnFollowToggle.setImageResource(isFollowing ? R.drawable.current_location_icon : R.drawable.free_location_icon); }
    private void updateRotateToggleButton() { btnRotateToggle.setImageResource(R.drawable.compass_icon); }

    @SuppressLint("MissingPermission")
    private void enableLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    locationManager.registerGnssStatusCallback(gnssStatusCallback, new Handler(Looper.getMainLooper()));
                }
            } catch (SecurityException e) { Log.e(TAG, "SecurityException in enableLocationUpdates", e); }
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_PERMISSIONS_REQUEST_CODE);
        } else {
            enableLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            List<String> deniedPermissions = new ArrayList<>();

            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    deniedPermissions.add(permissions[i]);
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                }
            }

            if (allGranted) {
                enableLocationUpdates();
            } else {

                StringBuilder deniedPermissionsMessage = new StringBuilder("The following permissions are required for full functionality but were denied: ");
                for (int i = 0; i < deniedPermissions.size(); i++) {
                    String permissionName = deniedPermissions.get(i);
                    if (permissionName.contains(".")) {
                        permissionName = permissionName.substring(permissionName.lastIndexOf(".") + 1);
                    }
                    deniedPermissionsMessage.append(permissionName);
                    if (i < deniedPermissions.size() - 1) {
                        deniedPermissionsMessage.append(", ");
                    }
                }
                deniedPermissionsMessage.append(". Some features may not work as expected.");
                Toast.makeText(this, deniedPermissionsMessage.toString(), Toast.LENGTH_LONG).show();
                Log.w(TAG, "Not all permissions granted. Denied: " + deniedPermissions.toString());
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity resuming...");
        if (mapView != null) mapView.onResume();

        Intent serviceIntent = new Intent(this, MeshReceiverService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        if (!isGeeksvilleMeshServiceActivityBound) {
            bindToGeeksvilleMeshServiceForActivity();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableLocationUpdates();
        }
        if (isRotationEnabled) registerOrientationSensor();
        else { registerOrientationSensor(); if (myLocationMarker != null) myLocationMarker.setRotation(-kalmanAngle); }

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mapDataRefreshReceiver, new IntentFilter(MeshReceiverService.ACTION_MAP_DATA_REFRESH));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                syncStatusUpdateReceiver, new IntentFilter(MeshReceiverService.ACTION_SYNC_STATUS_UPDATE));

        clearAndReloadMapData();
        startUpdatingNodeLocationsOnMap();
        if (currentMbtilesFile != null) {
            setTileSourceInternal(null, currentMbtilesFile);
        } else {
            setTileSourceInternal(currentTileSourceName, null);
        }
        Log.d(TAG, "onResume: Activity resumed.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Activity pausing...");
        if (mapView != null) mapView.onPause();

        if (locationManager != null) {
            locationManager.removeUpdates(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
                try {
                    locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering GNSS callback", e);
                }
            }
        }
        unregisterOrientationSensor();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mapDataRefreshReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStatusUpdateReceiver);

        stopUpdatingNodeLocationsOnMap();
        Log.d(TAG, "onPause: Activity paused.");
    }


    private void unregisterOrientationSensor() { if (sensorManager != null) sensorManager.unregisterListener(this); }
    private void registerOrientationSensor() {
        boolean sensorRegistered = false;
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
            sensorRegistered = true;
        } else if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
            sensorRegistered = true;
        }
        if (!sensorRegistered) {
            Toast.makeText(this, "Orientation sensors not available.", Toast.LENGTH_SHORT).show();
            isRotationEnabled = false; updateRotateToggleButton();
            if(mapView != null) mapView.setMapOrientation(0);
            if(btnRotateToggle != null) btnRotateToggle.setRotation(0);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        GeoPoint myPosition = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (myLocationMarker != null) {
            myLocationMarker.setPosition(myPosition);
            if (!isRotationEnabled) {
                if (location.hasBearing()) myLocationMarker.setRotation(location.getBearing());
                else myLocationMarker.setRotation(-kalmanAngle);
            } else {
                myLocationMarker.setRotation(0);
            }
        }
        if (isFollowing && mapController != null) mapController.animateTo(myPosition);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { updateTopBarInfoDisplay(); } catch (RemoteException e) { Log.e(TAG, "RemoteEx onLocationChanged", e); }
        }
        if(mapView != null) mapView.invalidate();
        Intent locationIntent = new Intent(this, MeshReceiverService.class);
        locationIntent.setAction(MeshReceiverService.ACTION_FORWARD_PHONE_LOCATION);
        locationIntent.putExtra(MeshReceiverService.EXTRA_LOCATION, location);
        startService(locationIntent);
        Log.d(TAG, "Sent ACTION_FORWARD_PHONE_LOCATION to MeshReceiverService.");
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensorType = event.sensor.getType();
        if (sensorType == Sensor.TYPE_ACCELEROMETER) System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
        else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
        else if (sensorType == Sensor.TYPE_ROTATION_VECTOR) System.arraycopy(event.values, 0, rotationVectorReading, 0, rotationVectorReading.length);

        if (sensorType == Sensor.TYPE_ACCELEROMETER || sensorType == Sensor.TYPE_MAGNETIC_FIELD || sensorType == Sensor.TYPE_ROTATION_VECTOR) {
            float azimuth = calculateOrientation();
            kalmanFilter(azimuth);
            if (isRotationEnabled) {
                if(mapView != null) mapView.setMapOrientation(-kalmanAngle);
                if(btnRotateToggle != null) btnRotateToggle.setRotation(-kalmanAngle);
                if (myLocationMarker != null) myLocationMarker.setRotation(0);
            } else {
                if (myLocationMarker != null) myLocationMarker.setRotation(-kalmanAngle);
            }
            if(mapView != null) mapView.invalidate();
        }
    }

    private float calculateOrientation() {
        float[] rotationMatrixRaw = new float[9];
        float[] rotationMatrixAdjusted = new float[9];
        float[] orientationValues = new float[3];
        boolean success = false;

        if (rotationVectorSensor != null && (rotationVectorReading[0] != 0 || rotationVectorReading[1] != 0 || rotationVectorReading[2] != 0) ) {
            SensorManager.getRotationMatrixFromVector(rotationMatrixRaw, rotationVectorReading);
            success = true;
        } else if (accelerometer != null && magnetometer != null && (accelerometerReading[0] != 0 || magnetometerReading[0] != 0)) {
            if (SensorManager.getRotationMatrix(rotationMatrixRaw, null, accelerometerReading, magnetometerReading)) {
                success = true;
            }
        }
        if (success) {
            int worldAxisX = SensorManager.AXIS_X, worldAxisY = SensorManager.AXIS_Y;
            if (getWindowManager() == null || getWindowManager().getDefaultDisplay() == null) return kalmanAngle;
            int screenRotation = getWindowManager().getDefaultDisplay().getRotation();
            switch (screenRotation) {
                case Surface.ROTATION_0:  worldAxisX = SensorManager.AXIS_X; worldAxisY = SensorManager.AXIS_Y; break;
                case Surface.ROTATION_90: worldAxisX = SensorManager.AXIS_Y; worldAxisY = SensorManager.AXIS_MINUS_X; break;
                case Surface.ROTATION_180: worldAxisX = SensorManager.AXIS_MINUS_X; worldAxisY = SensorManager.AXIS_MINUS_Y; break;
                case Surface.ROTATION_270: worldAxisX = SensorManager.AXIS_MINUS_Y; worldAxisY = SensorManager.AXIS_X; break;
            }
            SensorManager.remapCoordinateSystem(rotationMatrixRaw, worldAxisX, worldAxisY, rotationMatrixAdjusted);
            SensorManager.getOrientation(rotationMatrixAdjusted, orientationValues);
            return (float) Math.toDegrees(orientationValues[0]);
        }
        return kalmanAngle;
    }

    private void clearAndReloadMapData() {
        if (mapView == null) {
            Log.e(TAG, "MapView is null, cannot clear and reload map data.");
            return;
        }
        Log.i(TAG, "Clearing and reloading all map data from database...");

        List<Marker> pinsToRemove = new ArrayList<>(customPins);
        for (Marker pin : pinsToRemove) {
            mapView.getOverlays().remove(pin);
        }
        customPins.clear();
        Log.d(TAG, "Cleared " + pinsToRemove.size() + " custom pins.");

        List<Polyline> linesToRemove = new ArrayList<>(drawnLines);
        for (Polyline line : linesToRemove) {
            mapView.getOverlays().remove(line);
        }
        drawnLines.clear();
        Log.d(TAG, "Cleared " + linesToRemove.size() + " lines.");

        List<Polygon> polygonsToRemove = new ArrayList<>(drawnPolygons);
        for (Polygon polygon : polygonsToRemove) {
            mapView.getOverlays().remove(polygon);
        }
        drawnPolygons.clear();
        Log.d(TAG, "Cleared " + polygonsToRemove.size() + " polygons/circles.");

        loadAllMapData();
        mapView.invalidate();
        Log.i(TAG, "Finished clearing and reloading map data.");
    }

    private void kalmanFilter(float measurement) {
        kalmanErrorEstimate += kalmanProcessNoise;
        float kalmanGain = kalmanErrorEstimate / (kalmanErrorEstimate + kalmanMeasurementNoise);
        kalmanAngle += kalmanGain * (measurement - kalmanAngle);
        kalmanErrorEstimate *= (1 - kalmanGain);
        kalmanAngle = (kalmanAngle + 540) % 360 - 180;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* Not used */ }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { /* Deprecated */ }
    @Override public void onProviderEnabled(@NonNull String provider) { Toast.makeText(this, provider.toUpperCase() + " enabled", Toast.LENGTH_SHORT).show(); }
    @Override public void onProviderDisabled(@NonNull String provider) {
        Toast.makeText(this, provider.toUpperCase() + " disabled", Toast.LENGTH_SHORT).show();
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            tvSatelliteStatus.setText("GPS: Disabled");
            tvNumSatellites.setText("Sats: N/A");
            tvAccuracy.setText("Acc: N/A");
        }
    }

    @Override public boolean onScroll(ScrollEvent event) { return false; }
    @Override public boolean onZoom(ZoomEvent event) { return false; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Activity destroying...");
        unbindFromGeeksvilleMeshServiceForActivity();
        if (mapView != null) {
            mapView.onDetach();
        }
        mapView = null;
        if (periodicNodeUpdateHandler != null && nodeUpdateRunnable != null) {
            periodicNodeUpdateHandler.removeCallbacks(nodeUpdateRunnable);
        }
        if (mServiceStartHandler != null && mServiceStartRunnable != null) {
            mServiceStartHandler.removeCallbacks(mServiceStartRunnable);
        }
        Log.d(TAG, "onDestroy: Activity destroyed.");
    }
}
