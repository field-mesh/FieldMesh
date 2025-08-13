package io.github.fieldmesh;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.Region;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Polygon;

/**
 * Polygon overlay that fills the area outside of the polygon with a repeating pattern.
 */
public class PlayAreaPolygon extends Polygon {

    private BitmapShader bitmapShader;
    private Bitmap patternBitmap;
    private IGeoPoint lastCenterGeoPoint;
    private int xOffset = 0;
    private int yOffset = 0;

    public PlayAreaPolygon(MapView mapView) {
        super(mapView);
    }

    public void setPatternBitmap(@NonNull Bitmap bmp) {
        this.patternBitmap = bmp;
        this.bitmapShader = new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        if (mFillPaint != null) {
            mFillPaint.setShader(bitmapShader);
        }
    }

    private void recalculateMatrix(@NonNull final MapView mapView) {
        if (patternBitmap == null) return;
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
        matrix.setScale(1, 1);
        matrix.preTranslate(xOffset, yOffset);
        bitmapShader.setLocalMatrix(matrix);

        lastCenterGeoPoint = geoPoint;
    }

    @Override
    protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;
        recalculateMatrix(mapView);

        Paint originalFill = mFillPaint;
        mFillPaint = null;
        super.draw(canvas, mapView, shadow);
        mFillPaint = originalFill;

        if (mPath != null && mFillPaint != null) {
            canvas.save();
            canvas.clipPath(mPath, Region.Op.DIFFERENCE);
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mFillPaint);
            canvas.restore();
        }
    }

    @Override
    public boolean contains(final MotionEvent pEvent) {
        return !super.contains(pEvent);
    }
}

