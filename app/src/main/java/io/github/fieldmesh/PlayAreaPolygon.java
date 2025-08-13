package io.github.fieldmesh;


import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Shader;
import android.view.MotionEvent;


import androidx.annotation.NonNull;


import org.osmdroid.api.IGeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Polygon;


/**
 * Polygon overlay that fills the area outside of the polygon with a repeating pattern and a background color.
 */
public class PlayAreaPolygon extends Polygon {


    private BitmapShader bitmapShader;
    private Bitmap patternBitmap;
    private IGeoPoint lastCenterGeoPoint;
    private int xOffset = 0;
    private int yOffset = 0;
    private final Paint mOutsideFillPaint; // Use a dedicated Paint for the outside pattern
    private final Paint mOutsideBackgroundPaint; // Dedicated Paint for the outside background
    private final float outlineStrokeWidth; // Added outline stroke width
    private int outsideBackgroundColor = Color.argb(150, 0, 0, 0); // Default dark semi-transparent background


    public PlayAreaPolygon(MapView mapView) {
        this(mapView, 5.0f); // Default outline thickness of 5 pixels
    }


    public PlayAreaPolygon(MapView mapView, float outlineThickness) {
        super(mapView);
        this.outlineStrokeWidth = outlineThickness;
        // Initialize the dedicated paint object for the outside pattern
        mOutsideFillPaint = new Paint();
        mOutsideFillPaint.setStyle(Paint.Style.FILL);
        // Initialize the dedicated paint object for the outside background color
        mOutsideBackgroundPaint = new Paint();
        mOutsideBackgroundPaint.setStyle(Paint.Style.FILL);
        mOutsideBackgroundPaint.setColor(outsideBackgroundColor);
        // Ensure the base polygon's paint is set to draw an outline
        getOutlinePaint().setStyle(Paint.Style.STROKE);
        getOutlinePaint().setStrokeWidth(outlineThickness);
    }


    public void setOutsideBackgroundColor(int color) {
        this.outsideBackgroundColor = color;
        mOutsideBackgroundPaint.setColor(color);
    }


    public void setPatternBitmap(@NonNull Bitmap bmp) {
        this.patternBitmap = bmp;
        // Create the shader and apply it to our dedicated outside paint
        this.bitmapShader = new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        mOutsideFillPaint.setShader(bitmapShader);
    }


    private void recalculateMatrix(@NonNull final MapView mapView) {
        if (patternBitmap == null || bitmapShader == null) return;
        final Projection projection = mapView.getProjection();
        final IGeoPoint geoPoint = mapView.getMapCenter();
        if (lastCenterGeoPoint == null) lastCenterGeoPoint = geoPoint;


        final Point point = projection.toPixels(geoPoint, null);
        final Point lastCenterPoint = projection.toPixels(lastCenterGeoPoint, null);


        xOffset += lastCenterPoint.x - point.x;
        yOffset += lastCenterPoint.y - point.y;


        xOffset %= patternBitmap.getWidth();
        yOffset %= patternBitmap.getHeight();


        final Matrix matrix = new Matrix();
        matrix.reset();
        matrix.postTranslate(xOffset, yOffset);
        bitmapShader.setLocalMatrix(matrix);


        lastCenterGeoPoint = geoPoint;
    }


    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) {
            return;
        }
        recalculateMatrix(mapView);


        // 1. Let the parent class draw the OUTLINE ONLY with the desired thickness.
        // The parent's draw method is needed to calculate the screen path (mPath).
        Paint originalFill = this.mFillPaint;
        this.mFillPaint = null; // Disable internal fill for this draw call
        Paint originalStrokeWidthPaint = getOutlinePaint();
        float originalStrokeWidth = originalStrokeWidthPaint.getStrokeWidth();
        originalStrokeWidthPaint.setStrokeWidth(outlineStrokeWidth); // Set the desired stroke width
        super.draw(canvas, mapView, false); // Draw outline and calculate mPath
        this.mFillPaint = originalFill; // Restore the fill paint
        originalStrokeWidthPaint.setStrokeWidth(originalStrokeWidth); // Restore the original stroke width


        // 2. Create a path for the area OUTSIDE the polygon.
        Path outsidePath = new Path();
        // Start with a path representing the entire screen
        outsidePath.addRect(0, 0, canvas.getWidth(), canvas.getHeight(), Path.Direction.CW);


        // 3. "Cut out" the polygon's path from the screen-sized path.
        // The mPath variable is calculated by the super.draw() call above.
        if (mPath != null) {
            outsidePath.op(mPath, Path.Op.DIFFERENCE);
        }


        // 4. Draw the background color on the outside area.
        canvas.drawPath(outsidePath, mOutsideBackgroundPaint);


        // 5. Draw the repeating pattern on the outside area.
        canvas.drawPath(outsidePath, mOutsideFillPaint);
    }


    /**
     * An event is considered "contained" if it's OUTSIDE the polygon,
     * which is the defined "play area".
     */
    @Override
    public boolean contains(final MotionEvent pEvent) {
        return !super.contains(pEvent);
    }
}