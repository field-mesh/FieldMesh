package io.github.fieldmesh;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

import io.github.fieldmesh.CircleInfo;
import io.github.fieldmesh.LineInfo;
import io.github.fieldmesh.MapDataDatabaseHelper;
import io.github.fieldmesh.PinInfo;
import io.github.fieldmesh.PolygonInfo;

import earth.worldwind.WorldWindow;
import earth.worldwind.geom.Position;
import earth.worldwind.layer.BackgroundLayer;
import earth.worldwind.layer.BlueMarbleLayer;
import earth.worldwind.layer.RenderableLayer;
import earth.worldwind.render.Color;
import earth.worldwind.shape.Path;
import earth.worldwind.shape.Placemark;
import earth.worldwind.shape.Polygon;
import earth.worldwind.shape.ShapeAttributes;
import earth.worldwind.shape.SurfaceCircle;

/**
 * Activity displaying map objects using NASA WorldWind instead of osmdroid.
 */

public class WorldWindActivity extends AppCompatActivity {
    private WorldWindow worldWindow;
    private RenderableLayer objectLayer;
    private MapDataDatabaseHelper mapDbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worldwind);

        FrameLayout globe = findViewById(R.id.globe);
        worldWindow = new WorldWindow(this);
        globe.addView(worldWindow);

        worldWindow.getLayers().addLayer(new BackgroundLayer());
        worldWindow.getLayers().addLayer(new BlueMarbleLayer());

        objectLayer = new RenderableLayer();
        worldWindow.getLayers().addLayer(objectLayer);

        mapDbHelper = new MapDataDatabaseHelper(this);
        populateMapObjects();
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

    private void populateMapObjects() {
        objectLayer.removeAllRenderables();

        List<PinInfo> pins = mapDbHelper.getAllPins(null);
        for (PinInfo pin : pins) {
            Position pos = Position.fromDegrees(pin.getLatitude(), pin.getLongitude(), 0);
            Placemark placemark = new Placemark(pos);
            placemark.setLabelText(pin.getLabel());
            objectLayer.addRenderable(placemark);
        }

        List<LineInfo> lines = mapDbHelper.getAllLines(null);
        for (LineInfo line : lines) {
            List<Position> positions = new ArrayList<>();
            for (GeoPoint gp : line.getPoints()) {
                positions.add(Position.fromDegrees(gp.getLatitude(), gp.getLongitude(), 0));
            }
            Path path = new Path(positions);
            path.setAttributes(buildShapeAttributes(line.getColor()));
            objectLayer.addRenderable(path);
        }

        List<PolygonInfo> polygons = mapDbHelper.getAllPolygons(null);
        for (PolygonInfo polygon : polygons) {
            List<Position> positions = new ArrayList<>();
            for (GeoPoint gp : polygon.getPoints()) {
                positions.add(Position.fromDegrees(gp.getLatitude(), gp.getLongitude(), 0));
            }
            Polygon poly = new Polygon(positions);
            poly.setAttributes(buildShapeAttributes(polygon.getColor()));
            objectLayer.addRenderable(poly);
        }

        List<CircleInfo> circles = mapDbHelper.getAllCircles(null);
        for (CircleInfo circle : circles) {
            SurfaceCircle sc = new SurfaceCircle(
                    Position.fromDegrees(circle.getLatitude(), circle.getLongitude(), 0),
                    circle.getRadius()
            );
            sc.setAttributes(buildShapeAttributes(circle.getColor()));
            objectLayer.addRenderable(sc);
        }
    }

    private ShapeAttributes buildShapeAttributes(int colorIndex) {
        ShapeAttributes attrs = new ShapeAttributes();
        Color color = colorFromIndex(colorIndex);
        attrs.setOutlineColor(color);
        Color fill = new Color(color);
        fill.setAlpha(0.3f);
        attrs.setInteriorColor(fill);
        return attrs;
    }

    private Color colorFromIndex(int index) {
        switch (index) {
            case 1:
                return new Color(0f, 0f, 1f, 1f);
            case 2:
                return new Color(0f, 1f, 0f, 1f);
            case 3:
                return new Color(1f, 1f, 0f, 1f);
            default:
                return new Color(1f, 0f, 0f, 1f);
        }
    }
}