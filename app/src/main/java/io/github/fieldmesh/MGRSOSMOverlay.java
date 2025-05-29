package io.github.fieldmesh;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

import mil.nga.grid.features.Line;
import mil.nga.mgrs.features.GridLine;
import mil.nga.mgrs.grid.GridLabel;
import mil.nga.mgrs.grid.GridLabeler;
import mil.nga.mgrs.grid.style.ZoomGrids;
import mil.nga.mgrs.gzd.GridRange;
import mil.nga.mgrs.gzd.GridZone;
import mil.nga.mgrs.gzd.GridZones;
import mil.nga.grid.features.Point;

public class MGRSOSMOverlay extends Overlay {

    private final mil.nga.mgrs.grid.style.Grids grids;

    private final org.osmdroid.util.GeoPoint mReusableOsmPoint1 = new org.osmdroid.util.GeoPoint(0,0);
    private final org.osmdroid.util.GeoPoint mReusableOsmPoint2 = new org.osmdroid.util.GeoPoint(0,0);
    private final org.osmdroid.util.GeoPoint mReusableOsmCenter = new org.osmdroid.util.GeoPoint(0,0);

    private final android.graphics.Point mReusableScreenPoint1 = new android.graphics.Point();
    private final android.graphics.Point mReusableScreenPoint2 = new android.graphics.Point();
    private final android.graphics.Point mReusableScreenCenter = new android.graphics.Point();

    private final Path mLinePath = new Path();
    private final Rect mTextMeasureRect = new Rect();


    private final RectF mReusableClipScreenRect = new RectF();
    private final RectF mReusableLabelCellScreenBounds = new RectF();



    public MGRSOSMOverlay(mil.nga.mgrs.grid.style.Grids grids) {
        super();
        this.grids = grids;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) {
            return;
        }

        Projection projection = mapView.getProjection();
        BoundingBox osmVisibleBounds = mapView.getBoundingBox();


        mil.nga.grid.features.Bounds ngaVisibleBounds = new mil.nga.grid.features.Bounds(
                osmVisibleBounds.getLonWest(), osmVisibleBounds.getLatSouth(),
                osmVisibleBounds.getLonEast(), osmVisibleBounds.getLatNorth()
        );

        int zoom = (int) mapView.getZoomLevelDouble();

        ZoomGrids zoomGrids = this.grids.getGrids(zoom);
        if (zoomGrids == null || !zoomGrids.hasGrids()) {
            return;
        }


        GridRange gridRange = GridZones.getGridRange(ngaVisibleBounds);

        for (mil.nga.mgrs.grid.style.Grid gridStyle : zoomGrids.grids()) {

            Paint labelPaint = gridStyle.getLabelPaint();

            for (GridZone zone : gridRange) {

                mil.nga.grid.features.Bounds zoneGeoBounds = zone.getBounds();
                if (!zoneGeoBounds.intersects(ngaVisibleBounds, true)) {
                    continue;
                }


                mil.nga.grid.features.Bounds visiblePortionOfZoneGeo = zoneGeoBounds.overlap(ngaVisibleBounds);
                if (visiblePortionOfZoneGeo == null || visiblePortionOfZoneGeo.isEmpty()) {
                    continue;
                }


                projectNgaBoundsToScreenRect(visiblePortionOfZoneGeo, projection, mReusableClipScreenRect);
                if (mReusableClipScreenRect.width() <= 0 || mReusableClipScreenRect.height() <= 0) { // Check if the projected rect is valid/visible
                    continue;
                }


                List<GridLine> lines = zone.getLines(visiblePortionOfZoneGeo, gridStyle.getType());
                if (lines != null && !lines.isEmpty()) {

                    drawNgaLinesOnCanvas(canvas, projection, lines, gridStyle, mReusableClipScreenRect);
                }


                GridLabeler labeler = gridStyle.getLabeler();
                if (labeler != null && labeler.isEnabled() && labelPaint != null) {
                    List<GridLabel> labels = labeler.getLabels(visiblePortionOfZoneGeo, gridStyle.getType(), zone);
                    if (labels != null && !labels.isEmpty()) {

                        drawNgaLabelsOnCanvas(canvas, projection, labels, gridStyle.getLabelBuffer(), labelPaint, mReusableClipScreenRect);
                    }
                }
            }
        }
    }


    private void drawNgaLinesOnCanvas(Canvas canvas, Projection projection, List<GridLine> ngaLines,
                                      mil.nga.mgrs.grid.style.Grid gridStyle, RectF clipRectScreen) {
        canvas.save();
        canvas.clipRect(clipRectScreen);

        for (GridLine ngaLine : ngaLines) {
            Paint paint = gridStyle.getLinePaint(ngaLine.getGridType());
            if (paint == null) continue;

            Line metersLine = ngaLine.toMeters();
            Point point1Geo = metersLine.getPoint1().toDegrees();
            Point point2Geo = metersLine.getPoint2().toDegrees();

            mReusableOsmPoint1.setCoords(point1Geo.getLatitude(), point1Geo.getLongitude());
            mReusableOsmPoint2.setCoords(point2Geo.getLatitude(), point2Geo.getLongitude());

            projection.toPixels(mReusableOsmPoint1, mReusableScreenPoint1);
            projection.toPixels(mReusableOsmPoint2, mReusableScreenPoint2);

            mLinePath.reset(); // Reset for each line segment
            mLinePath.moveTo(mReusableScreenPoint1.x, mReusableScreenPoint1.y);
            mLinePath.lineTo(mReusableScreenPoint2.x, mReusableScreenPoint2.y);
            canvas.drawPath(mLinePath, paint);
        }
        canvas.restore();
    }


    private void drawNgaLabelsOnCanvas(Canvas canvas, Projection projection, List<GridLabel> ngaLabels,
                                       double buffer, Paint labelPaint, RectF clipRectScreen) {
        canvas.save();
        canvas.clipRect(clipRectScreen);

        for (GridLabel ngaLabel : ngaLabels) {
            String name = ngaLabel.getName();
            if (name == null || name.isEmpty()) continue; // Skip empty labels

            labelPaint.getTextBounds(name, 0, name.length(), mTextMeasureRect);


            projectNgaBoundsToScreenRect(ngaLabel.getBounds(), projection, mReusableLabelCellScreenBounds);
            if (mReusableLabelCellScreenBounds.width() <= 0 || mReusableLabelCellScreenBounds.height() <= 0) continue;

            double gridPercentage = 1.0 - (2 * buffer);
            double maxWidth = gridPercentage * mReusableLabelCellScreenBounds.width();
            double maxHeight = gridPercentage * mReusableLabelCellScreenBounds.height();

            if (mTextMeasureRect.width() > 0 && mTextMeasureRect.height() > 0 && // Ensure text has dimensions
                    mTextMeasureRect.width() <= maxWidth && mTextMeasureRect.height() <= maxHeight) {

                Point centerGeo = ngaLabel.getCenter().toDegrees();
                mReusableOsmCenter.setCoords(centerGeo.getLatitude(), centerGeo.getLongitude());
                projection.toPixels(mReusableOsmCenter, mReusableScreenCenter);

                if (clipRectScreen.contains(mReusableScreenCenter.x, mReusableScreenCenter.y)) {
                    canvas.drawText(name,
                            mReusableScreenCenter.x - mTextMeasureRect.exactCenterX(),
                            mReusableScreenCenter.y - mTextMeasureRect.exactCenterY(),
                            labelPaint);
                }
            }
        }
        canvas.restore();
    }


    private void projectNgaBoundsToScreenRect(mil.nga.grid.features.Bounds ngaBounds, Projection projection, RectF outRectScreen) {
        Point swGeo = ngaBounds.getSouthwest().toDegrees();
        Point neGeo = ngaBounds.getNortheast().toDegrees();


        mReusableOsmPoint1.setCoords(swGeo.getLatitude(), swGeo.getLongitude());
        mReusableOsmPoint2.setCoords(neGeo.getLatitude(), neGeo.getLongitude());

        projection.toPixels(mReusableOsmPoint1, mReusableScreenPoint1);
        projection.toPixels(mReusableOsmPoint2, mReusableScreenPoint2);

        outRectScreen.set(
                Math.min(mReusableScreenPoint1.x, mReusableScreenPoint2.x),
                Math.min(mReusableScreenPoint1.y, mReusableScreenPoint2.y),
                Math.max(mReusableScreenPoint1.x, mReusableScreenPoint2.x),
                Math.max(mReusableScreenPoint1.y, mReusableScreenPoint2.y)
        );
    }
}