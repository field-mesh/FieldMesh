package io.github.fieldmesh;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import io.github.fieldmesh.ui.UuidData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.osmdroid.util.GeoPoint;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.security.NoSuchAlgorithmException;

public class MapDataDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "MapDataDatabaseHelper";
    private static final String DATABASE_NAME = "map_data.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_MAP_OBJECTS = "map_objects";
    private static final String COLUMN_MO_ID = "_id";
    private static final String COLUMN_MO_UNIQUE_ID = "unique_id";
    private static final String COLUMN_MO_OBJECT_TYPE = "object_type";
    private static final String COLUMN_MO_LABEL = "label";
    private static final String COLUMN_MO_COLOR = "color";
    private static final String COLUMN_MO_IS_DELETED = "is_deleted";
    private static final String OBJECT_TYPE_PIN = "PIN";
    private static final String OBJECT_TYPE_CIRCLE = "CIRCLE";
    private static final String OBJECT_TYPE_LINE = "LINE";
    private static final String OBJECT_TYPE_POLYGON = "POLYGON";
    private static final String TABLE_PIN_DETAILS = "pin_details";
    private static final String COLUMN_PD_OBJECT_ID = "object_id";
    private static final String COLUMN_PD_LATITUDE = "latitude";
    private static final String COLUMN_PD_LONGITUDE = "longitude";
    private static final String COLUMN_PD_ICON_RESOURCE_ID = "icon_resource_id";
    private static final String COLUMN_PD_ELEVATION = "elevation";
    private static final String COLUMN_PD_ROTATION = "rotation";

    private static final String TABLE_CIRCLE_DETAILS = "circle_details";
    private static final String COLUMN_CD_OBJECT_ID = "object_id";
    private static final String COLUMN_CD_LATITUDE = "latitude";
    private static final String COLUMN_CD_LONGITUDE = "longitude";
    private static final String COLUMN_CD_RADIUS = "radius";
    private static final String COLUMN_CD_LINE_TYPE = "line_type";
    private static final String COLUMN_CD_ELEVATION = "elevation";
    private static final String TABLE_LINE_DETAILS = "line_details";
    private static final String COLUMN_LD_OBJECT_ID = "object_id";
    private static final String TABLE_POLYGON_DETAILS = "polygon_details";
    private static final String COLUMN_POLY_OBJECT_ID = "object_id";
    private static final String TABLE_OBJECT_COORDINATES = "object_coordinates";
    private static final String COLUMN_OC_ID = "_id";
    private static final String COLUMN_OC_OBJECT_ID = "object_id";
    private static final String COLUMN_OC_LATITUDE = "latitude";
    private static final String COLUMN_OC_LONGITUDE = "longitude";
    private static final String COLUMN_OC_ELEVATION = "elevation";
    private static final String COLUMN_OC_POINT_ORDER = "point_order";

    private static final String CREATE_TABLE_MAP_OBJECTS =
            "CREATE TABLE " + TABLE_MAP_OBJECTS + "(" +
                    COLUMN_MO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_MO_UNIQUE_ID + " TEXT UNIQUE NOT NULL, " +
                    COLUMN_MO_OBJECT_TYPE + " TEXT NOT NULL, " +
                    COLUMN_MO_LABEL + " TEXT, " +
                    COLUMN_MO_COLOR + " INTEGER NOT NULL, " +
                    COLUMN_MO_IS_DELETED + " INTEGER NOT NULL DEFAULT 0);";

    private static final String CREATE_TABLE_PIN_DETAILS =
            "CREATE TABLE " + TABLE_PIN_DETAILS + "(" +
                    COLUMN_PD_OBJECT_ID + " INTEGER PRIMARY KEY REFERENCES " + TABLE_MAP_OBJECTS + "(" + COLUMN_MO_ID + ") ON DELETE CASCADE, " +
                    COLUMN_PD_LATITUDE + " REAL NOT NULL, " +
                    COLUMN_PD_LONGITUDE + " REAL NOT NULL, " +
                    COLUMN_PD_ICON_RESOURCE_ID + " INTEGER NOT NULL, " +
                    COLUMN_PD_ELEVATION + " INTEGER NOT NULL, " +
                    COLUMN_PD_ROTATION + " INTEGER NOT NULL);";

    private static final String CREATE_TABLE_CIRCLE_DETAILS =
            "CREATE TABLE " + TABLE_CIRCLE_DETAILS + "(" +
                    COLUMN_CD_OBJECT_ID + " INTEGER PRIMARY KEY REFERENCES " + TABLE_MAP_OBJECTS + "(" + COLUMN_MO_ID + ") ON DELETE CASCADE, " +
                    COLUMN_CD_LATITUDE + " REAL NOT NULL, " +
                    COLUMN_CD_LONGITUDE + " REAL NOT NULL, " +
                    COLUMN_CD_RADIUS + " REAL NOT NULL, " +
                    COLUMN_CD_LINE_TYPE + " INTEGER NOT NULL, " +
                    COLUMN_CD_ELEVATION + " INTEGER NOT NULL);";

    private static final String CREATE_TABLE_LINE_DETAILS =
            "CREATE TABLE " + TABLE_LINE_DETAILS + "(" +
                    COLUMN_LD_OBJECT_ID + " INTEGER PRIMARY KEY REFERENCES " + TABLE_MAP_OBJECTS + "(" + COLUMN_MO_ID + ") ON DELETE CASCADE);";

    private static final String CREATE_TABLE_POLYGON_DETAILS =
            "CREATE TABLE " + TABLE_POLYGON_DETAILS + "(" +
                    COLUMN_POLY_OBJECT_ID + " INTEGER PRIMARY KEY REFERENCES " + TABLE_MAP_OBJECTS + "(" + COLUMN_MO_ID + ") ON DELETE CASCADE);";

    private static final String CREATE_TABLE_OBJECT_COORDINATES =
            "CREATE TABLE " + TABLE_OBJECT_COORDINATES + "(" +
                    COLUMN_OC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_OC_OBJECT_ID + " INTEGER NOT NULL REFERENCES " + TABLE_MAP_OBJECTS + "(" + COLUMN_MO_ID + ") ON DELETE CASCADE, " +
                    COLUMN_OC_LATITUDE + " REAL NOT NULL, " +
                    COLUMN_OC_LONGITUDE + " REAL NOT NULL, " +
                    COLUMN_OC_ELEVATION + " INTEGER NOT NULL, " +
                    COLUMN_OC_POINT_ORDER + " INTEGER NOT NULL, " +
                    "UNIQUE (" + COLUMN_OC_OBJECT_ID + ", " + COLUMN_OC_POINT_ORDER + "));";


    public MapDataDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating database tables...");
        db.execSQL(CREATE_TABLE_MAP_OBJECTS);
        db.execSQL(CREATE_TABLE_PIN_DETAILS);
        db.execSQL(CREATE_TABLE_CIRCLE_DETAILS);
        db.execSQL(CREATE_TABLE_LINE_DETAILS);
        db.execSQL(CREATE_TABLE_POLYGON_DETAILS);
        db.execSQL(CREATE_TABLE_OBJECT_COORDINATES);
        Log.i(TAG, "Database tables created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data if not migrating carefully.");
        if (oldVersion < 2 && newVersion >= 2) {
        }
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_OBJECT_COORDINATES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POLYGON_DETAILS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LINE_DETAILS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CIRCLE_DETAILS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PIN_DETAILS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MAP_OBJECTS);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    private long addMapObjectEntry(SQLiteDatabase db, String uniqueId, String objectType, String label, int color) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.e(TAG, "Unique ID cannot be null or empty for MapObject entry.");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_MO_UNIQUE_ID, uniqueId);
        values.put(COLUMN_MO_OBJECT_TYPE, objectType);
        values.put(COLUMN_MO_LABEL, label);
        values.put(COLUMN_MO_COLOR, color);
        values.put(COLUMN_MO_IS_DELETED, 0);
        return db.insert(TABLE_MAP_OBJECTS, null, values);
    }

    public long addPin(PinInfo pin) {
        if (pin == null || pin.getUniqueId() == null || pin.getUniqueId().isEmpty()) {
            Log.e(TAG, "PinInfo object or its UniqueId is null/empty. Cannot add to DB.");
            return -1;
        }
        if (getMapObjectId(getReadableDatabase(), pin.getUniqueId(), OBJECT_TYPE_PIN, false, true) != -1) {
            Log.w(TAG, "A non-deleted Pin with uniqueId: " + pin.getUniqueId() + " already exists. Use updatePin instead or ensure unique IDs are truly unique.");
        }

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        long objectId = -1;
        try {
            objectId = addMapObjectEntry(db, pin.getUniqueId(), OBJECT_TYPE_PIN, pin.getLabel(), pin.getColor());
            if (objectId == -1) {
                Log.e(TAG, "Failed to add Pin to MapObjects table for uniqueId: " + pin.getUniqueId() + ". It might already exist.");
                long existingId = getMapObjectId(db, pin.getUniqueId(), OBJECT_TYPE_PIN, true, false);
                boolean wasSoftDeleted = false;
                if (existingId != -1) {
                    Cursor tempCursor = db.query(TABLE_MAP_OBJECTS, new String[]{COLUMN_MO_IS_DELETED}, COLUMN_MO_ID + " = ?", new String[]{String.valueOf(existingId)}, null, null, null);
                    if (tempCursor != null && tempCursor.moveToFirst()) {
                        wasSoftDeleted = tempCursor.getInt(tempCursor.getColumnIndexOrThrow(COLUMN_MO_IS_DELETED)) == 1;
                    }
                    if (tempCursor != null) tempCursor.close();
                }

                if (wasSoftDeleted) {
                    Log.i(TAG, "Pin with uniqueId " + pin.getUniqueId() + " was soft-deleted. Consider an undelete operation or use update.");
                }
                return -1;
            }

            ContentValues detailValues = new ContentValues();
            detailValues.put(COLUMN_PD_OBJECT_ID, objectId);
            detailValues.put(COLUMN_PD_LATITUDE, pin.getLatitude());
            detailValues.put(COLUMN_PD_LONGITUDE, pin.getLongitude());
            detailValues.put(COLUMN_PD_ICON_RESOURCE_ID, pin.getIconResourceId());
            detailValues.put(COLUMN_PD_ELEVATION, pin.getElevation());
            detailValues.put(COLUMN_PD_ROTATION, pin.getRotation());

            if (db.insert(TABLE_PIN_DETAILS, null, detailValues) == -1) {
                Log.e(TAG, "Failed to add Pin details for objectId: " + objectId);
                objectId = -1;
            } else {
                db.setTransactionSuccessful();
                Log.i(TAG, "Pin added successfully: " + pin.getUniqueId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding Pin: " + e.getMessage(), e);
            objectId = -1;
        } finally {
            db.endTransaction();
        }
        if (db.isOpen()) {
        }
        return objectId;
    }

    public long addCircle(CircleInfo circle) {
        if (circle == null || circle.getUniqueId() == null || circle.getUniqueId().isEmpty()) {
            Log.e(TAG, "CircleInfo object or its UniqueId is null/empty. Cannot add to DB.");
            return -1;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        long objectId = -1;
        try {
            objectId = addMapObjectEntry(db, circle.getUniqueId(), OBJECT_TYPE_CIRCLE, circle.getLabel(), circle.getColor());
            if (objectId == -1) {
                Log.e(TAG, "Failed to add Circle to MapObjects table for uniqueId: " + circle.getUniqueId() + ". It might already exist.");
                return -1;
            }

            ContentValues detailValues = new ContentValues();
            detailValues.put(COLUMN_CD_OBJECT_ID, objectId);
            detailValues.put(COLUMN_CD_LATITUDE, circle.getLatitude());
            detailValues.put(COLUMN_CD_LONGITUDE, circle.getLongitude());
            detailValues.put(COLUMN_CD_RADIUS, circle.getRadius());
            detailValues.put(COLUMN_CD_LINE_TYPE, circle.getLineType());
            detailValues.put(COLUMN_CD_ELEVATION, circle.getElevation());

            if (db.insert(TABLE_CIRCLE_DETAILS, null, detailValues) == -1) {
                Log.e(TAG, "Failed to add Circle details for objectId: " + objectId);
                objectId = -1;
            } else {
                db.setTransactionSuccessful();
                Log.i(TAG, "Circle added successfully: " + circle.getUniqueId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding Circle: " + e.getMessage(), e);
            objectId = -1;
        } finally {
            db.endTransaction();
        }
        return objectId;
    }

    private long addLineOrPolygon(String uniqueId, String label, int color, List<LineInfo.Coordinate> points, String objectType, String detailTable) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.e(TAG, objectType + " UniqueId is null/empty. Cannot add to DB.");
            return -1;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        long objectId = -1;
        try {
            objectId = addMapObjectEntry(db, uniqueId, objectType, label, color);
            if (objectId == -1) {
                Log.e(TAG, "Failed to add " + objectType + " to MapObjects table for uniqueId: " + uniqueId + ". It might already exist.");
                return -1;
            }

            ContentValues detailTableValues = new ContentValues();
            detailTableValues.put( objectType.equals(OBJECT_TYPE_LINE) ? COLUMN_LD_OBJECT_ID : COLUMN_POLY_OBJECT_ID, objectId);
            if (db.insert(detailTable, null, detailTableValues) == -1) {
                Log.e(TAG, "Failed to add to " + detailTable + " for objectId: " + objectId);
                objectId = -1;
            } else {
                if (points != null) {
                    for (int i = 0; i < points.size(); i++) {
                        LineInfo.Coordinate point = points.get(i);
                        ContentValues pointValues = new ContentValues();
                        pointValues.put(COLUMN_OC_OBJECT_ID, objectId);
                        pointValues.put(COLUMN_OC_LATITUDE, point.getLatitude());
                        pointValues.put(COLUMN_OC_LONGITUDE, point.getLongitude());
                        pointValues.put(COLUMN_OC_ELEVATION, point.getElevation());
                        pointValues.put(COLUMN_OC_POINT_ORDER, i);
                        if (db.insert(TABLE_OBJECT_COORDINATES, null, pointValues) == -1) {
                            Log.e(TAG, "Failed to add point for " + objectType + " objectId: " + objectId);
                            objectId = -1;
                            break;
                        }
                    }
                }
            }

            if (objectId != -1) {
                db.setTransactionSuccessful();
                Log.i(TAG, objectType + " added successfully: " + uniqueId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error adding " + objectType + ": " + e.getMessage(), e);
            objectId = -1;
        } finally {
            db.endTransaction();
        }
        return objectId;
    }
    public long addLine(LineInfo line) {
        if (line == null) return -1;
        List<LineInfo.Coordinate> coordsForDb = new ArrayList<>();
        if (line.getPoints() != null) {
            for(GeoPoint gp : line.getPoints()) {
                coordsForDb.add(new LineInfo.Coordinate(gp.getLatitude(), gp.getLongitude(), (int)gp.getAltitude()));
            }
        }
        return addLineOrPolygon(line.getUniqueId(), line.getLabel(), line.getColor(), coordsForDb, OBJECT_TYPE_LINE, TABLE_LINE_DETAILS);
    }


    public long addPolygon(PolygonInfo polygon) {
        if (polygon == null) return -1;
        List<LineInfo.Coordinate> coordsForDb = new ArrayList<>();
        if (polygon.getPoints() != null) {
            for(GeoPoint gp : polygon.getPoints()) {
                coordsForDb.add(new LineInfo.Coordinate(gp.getLatitude(), gp.getLongitude(), (int)gp.getAltitude()));
            }
        }
        return addLineOrPolygon(polygon.getUniqueId(), polygon.getLabel(), polygon.getColor(), coordsForDb, OBJECT_TYPE_POLYGON, TABLE_POLYGON_DETAILS);
    }

    private Cursor getMapObjectCursor(SQLiteDatabase readableDb, String uniqueId, String objectType) {
        String selection = COLUMN_MO_UNIQUE_ID + " = ? AND " +
                COLUMN_MO_OBJECT_TYPE + " = ? AND " +
                COLUMN_MO_IS_DELETED + " = 0";
        String[] selectionArgs = {uniqueId, objectType};
        return readableDb.query(TABLE_MAP_OBJECTS, null, selection, selectionArgs, null, null, null);
    }

    private List<LineInfo.Coordinate> getCoordinatesForObject(SQLiteDatabase db, long objectId) {
        List<LineInfo.Coordinate> points = new ArrayList<>();
        Cursor cursor = db.query(TABLE_OBJECT_COORDINATES,
                new String[]{COLUMN_OC_LATITUDE, COLUMN_OC_LONGITUDE, COLUMN_OC_ELEVATION},
                COLUMN_OC_OBJECT_ID + " = ?",
                new String[]{String.valueOf(objectId)},
                null, null, COLUMN_OC_POINT_ORDER + " ASC");

        if (cursor.moveToFirst()) {
            do {
                double lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_OC_LATITUDE));
                double lon = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_OC_LONGITUDE));
                int elev = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OC_ELEVATION));
                points.add(new LineInfo.Coordinate(lat, lon, elev));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return points;
    }

    public PinInfo getPin(String uniqueId) {
        PinInfo pin = null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor mapObjectCursor = getMapObjectCursor(db, uniqueId, OBJECT_TYPE_PIN);

        if (mapObjectCursor.moveToFirst()) {
            long objectId = mapObjectCursor.getLong(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_ID));
            String label = mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_LABEL));
            int color = mapObjectCursor.getInt(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_COLOR));
            Cursor detailCursor = db.query(TABLE_PIN_DETAILS, null,
                    COLUMN_PD_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)},
                    null, null, null);

            if (detailCursor.moveToFirst()) {
                pin = new PinInfo();
                pin.setUniqueId(uniqueId);
                pin.setLabel(label);
                pin.setColor(color);
                pin.setLatitude(detailCursor.getDouble(detailCursor.getColumnIndexOrThrow(COLUMN_PD_LATITUDE)));
                pin.setLongitude(detailCursor.getDouble(detailCursor.getColumnIndexOrThrow(COLUMN_PD_LONGITUDE)));
                pin.setIconResourceId(detailCursor.getInt(detailCursor.getColumnIndexOrThrow(COLUMN_PD_ICON_RESOURCE_ID)));
                pin.setElevation(detailCursor.getInt(detailCursor.getColumnIndexOrThrow(COLUMN_PD_ELEVATION)));
                pin.setRotation(detailCursor.getInt(detailCursor.getColumnIndexOrThrow(COLUMN_PD_ROTATION)));
            }
            detailCursor.close();
        }
        mapObjectCursor.close();
        return pin;
    }

    public CircleInfo getCircle(String uniqueId) {
        CircleInfo circle = null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor mapObjectCursor = getMapObjectCursor(db, uniqueId, OBJECT_TYPE_CIRCLE);

        if (mapObjectCursor.moveToFirst()) {
            long objectId = mapObjectCursor.getLong(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_ID));
            String label = mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_LABEL));
            int color = mapObjectCursor.getInt(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_COLOR));

            Cursor detailCursor = db.query(TABLE_CIRCLE_DETAILS, null,
                    COLUMN_CD_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)},
                    null, null, null);

            if (detailCursor.moveToFirst()) {
                circle = new CircleInfo();
                circle.setUniqueId(uniqueId);
                circle.setLabel(label);
                circle.setColor(color);
                circle.setLatitude(detailCursor.getDouble(detailCursor.getColumnIndexOrThrow(COLUMN_CD_LATITUDE)));
                circle.setLongitude(detailCursor.getDouble(detailCursor.getColumnIndexOrThrow(COLUMN_CD_LONGITUDE)));
                circle.setRadius(detailCursor.getDouble(detailCursor.getColumnIndexOrThrow(COLUMN_CD_RADIUS)));
                circle.setLineType(detailCursor.getInt(detailCursor.getColumnIndexOrThrow(COLUMN_CD_LINE_TYPE)));
                circle.setElevation(detailCursor.getInt(detailCursor.getColumnIndexOrThrow(COLUMN_CD_ELEVATION)));
            }
            detailCursor.close();
        }
        mapObjectCursor.close();
        return circle;
    }

    public LineInfo getLine(String uniqueId) {
        LineInfo line = null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor mapObjectCursor = getMapObjectCursor(db, uniqueId, OBJECT_TYPE_LINE);

        if (mapObjectCursor.moveToFirst()) {
            long objectId = mapObjectCursor.getLong(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_ID));
            String label = mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_LABEL));
            int color = mapObjectCursor.getInt(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_COLOR));

            Cursor lineDetailCursor = db.query(TABLE_LINE_DETAILS, new String[]{COLUMN_LD_OBJECT_ID}, COLUMN_LD_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)}, null, null, null);
            boolean lineDetailExists = lineDetailCursor.moveToFirst();
            lineDetailCursor.close();

            if (lineDetailExists) {
                line = new LineInfo();
                line.setUniqueId(uniqueId);
                line.setLabel(label);
                line.setColor(color);
                List<LineInfo.Coordinate> points = getCoordinatesForObject(db, objectId);
                line.setPointsList(points);
            }
        }
        mapObjectCursor.close();
        return line;
    }

    public PolygonInfo getPolygon(String uniqueId) {
        PolygonInfo polygon = null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor mapObjectCursor = getMapObjectCursor(db, uniqueId, OBJECT_TYPE_POLYGON);

        if (mapObjectCursor.moveToFirst()) {
            long objectId = mapObjectCursor.getLong(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_ID));
            String label = mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_LABEL));
            int color = mapObjectCursor.getInt(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_COLOR));

            Cursor polyDetailCursor = db.query(TABLE_POLYGON_DETAILS, new String[]{COLUMN_POLY_OBJECT_ID}, COLUMN_POLY_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)}, null, null, null);
            boolean polyDetailExists = polyDetailCursor.moveToFirst();
            polyDetailCursor.close();

            if(polyDetailExists) {
                polygon = new PolygonInfo();
                polygon.setUniqueId(uniqueId);
                polygon.setLabel(label);
                polygon.setColor(color);
                List<LineInfo.Coordinate> points = getCoordinatesForObject(db, objectId);
                polygon.setPointsList(points);
            }
        }
        mapObjectCursor.close();
        return polygon;
    }

    public List<PinInfo> getAllPins() {
        List<PinInfo> pins = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT mo.*, pd.* FROM " + TABLE_MAP_OBJECTS + " mo JOIN " +
                TABLE_PIN_DETAILS + " pd ON mo." + COLUMN_MO_ID + " = pd." + COLUMN_PD_OBJECT_ID +
                " WHERE mo." + COLUMN_MO_OBJECT_TYPE + " = ? AND mo." + COLUMN_MO_IS_DELETED + " = 0";
        Cursor cursor = db.rawQuery(query, new String[]{OBJECT_TYPE_PIN});

        if (cursor.moveToFirst()) {
            do {
                PinInfo pin = new PinInfo();
                pin.setUniqueId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MO_UNIQUE_ID)));
                pin.setLabel(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MO_LABEL)));
                pin.setColor(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MO_COLOR)));
                pin.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PD_LATITUDE)));
                pin.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PD_LONGITUDE)));
                pin.setIconResourceId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PD_ICON_RESOURCE_ID)));
                pin.setElevation(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PD_ELEVATION)));
                pin.setRotation(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PD_ROTATION)));
                pins.add(pin);
            } while (cursor.moveToNext());
        }
        cursor.close();
        Log.i(TAG, "Loaded " + pins.size() + " non-deleted pins from database.");
        return pins;
    }

    public List<CircleInfo> getAllCircles() {
        List<CircleInfo> circles = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT mo.*, cd.* FROM " + TABLE_MAP_OBJECTS + " mo JOIN " +
                TABLE_CIRCLE_DETAILS + " cd ON mo." + COLUMN_MO_ID + " = cd." + COLUMN_CD_OBJECT_ID +
                " WHERE mo." + COLUMN_MO_OBJECT_TYPE + " = ? AND mo." + COLUMN_MO_IS_DELETED + " = 0";
        Cursor cursor = db.rawQuery(query, new String[]{OBJECT_TYPE_CIRCLE});

        if (cursor.moveToFirst()) {
            do {
                CircleInfo circle = new CircleInfo();
                circle.setUniqueId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MO_UNIQUE_ID)));
                circle.setLabel(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MO_LABEL)));
                circle.setColor(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MO_COLOR)));
                circle.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CD_LATITUDE)));
                circle.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CD_LONGITUDE)));
                circle.setRadius(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CD_RADIUS)));
                circle.setLineType(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CD_LINE_TYPE)));
                circle.setElevation(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CD_ELEVATION)));
                circles.add(circle);
            } while (cursor.moveToNext());
        }
        cursor.close();
        Log.i(TAG, "Loaded " + circles.size() + " non-deleted circles from database.");
        return circles;
    }

    public List<LineInfo> getAllLines() {
        List<LineInfo> lines = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String mapObjectsQuery = "SELECT * FROM " + TABLE_MAP_OBJECTS +
                " WHERE " + COLUMN_MO_OBJECT_TYPE + " = ? AND " + COLUMN_MO_IS_DELETED + " = 0";
        Cursor mapObjectCursor = db.rawQuery(mapObjectsQuery, new String[]{OBJECT_TYPE_LINE});

        if (mapObjectCursor.moveToFirst()) {
            do {
                long objectId = mapObjectCursor.getLong(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_ID));
                Cursor lineDetailCursor = db.query(TABLE_LINE_DETAILS, new String[]{COLUMN_LD_OBJECT_ID}, COLUMN_LD_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)}, null, null, null);
                boolean existsInLineDetails = lineDetailCursor.moveToFirst();
                lineDetailCursor.close();

                if (existsInLineDetails) {
                    LineInfo line = new LineInfo();
                    line.setUniqueId(mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_UNIQUE_ID)));
                    line.setLabel(mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_LABEL)));
                    line.setColor(mapObjectCursor.getInt(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_COLOR)));
                    List<LineInfo.Coordinate> points = getCoordinatesForObject(db, objectId);
                    line.setPointsList(points);
                    lines.add(line);
                }
            } while (mapObjectCursor.moveToNext());
        }
        mapObjectCursor.close();
        Log.i(TAG, "Loaded " + lines.size() + " non-deleted lines from database.");
        return lines;
    }

    public String getType(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.w(TAG, "Cannot get type for null or empty uniqueId.");
            return null;
        }

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        String objectType = null;

        try {
            String[] columns = {COLUMN_MO_OBJECT_TYPE};
            String selection = COLUMN_MO_UNIQUE_ID + " = ?";
            String[] selectionArgs = {uniqueId};

            cursor = db.query(
                    TABLE_MAP_OBJECTS,
                    columns,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                objectType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MO_OBJECT_TYPE));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting object type for uniqueId '" + uniqueId + "': " + e.getMessage(), e);
            objectType = null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return objectType;
    }

    public List<PolygonInfo> getAllPolygons() {
        List<PolygonInfo> polygons = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String mapObjectsQuery = "SELECT * FROM " + TABLE_MAP_OBJECTS +
                " WHERE " + COLUMN_MO_OBJECT_TYPE + " = ? AND " + COLUMN_MO_IS_DELETED + " = 0";
        Cursor mapObjectCursor = db.rawQuery(mapObjectsQuery, new String[]{OBJECT_TYPE_POLYGON});

        if (mapObjectCursor.moveToFirst()) {
            do {
                long objectId = mapObjectCursor.getLong(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_ID));
                Cursor polyDetailCursor = db.query(TABLE_POLYGON_DETAILS, new String[]{COLUMN_POLY_OBJECT_ID}, COLUMN_POLY_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)}, null, null, null);
                boolean existsInPolyDetails = polyDetailCursor.moveToFirst();
                polyDetailCursor.close();

                if(existsInPolyDetails) {
                    PolygonInfo polygon = new PolygonInfo();
                    polygon.setUniqueId(mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_UNIQUE_ID)));
                    polygon.setLabel(mapObjectCursor.getString(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_LABEL)));
                    polygon.setColor(mapObjectCursor.getInt(mapObjectCursor.getColumnIndexOrThrow(COLUMN_MO_COLOR)));
                    List<LineInfo.Coordinate> points = getCoordinatesForObject(db, objectId);
                    polygon.setPointsList(points);
                    polygons.add(polygon);
                }
            } while (mapObjectCursor.moveToNext());
        }
        mapObjectCursor.close();

        Log.i(TAG, "Loaded " + polygons.size() + " non-deleted polygons from database.");
        return polygons;
    }

    public int updatePin(PinInfo pin) {
        if (pin == null || pin.getUniqueId() == null || pin.getUniqueId().isEmpty()) return 0;

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        int rowsAffectedTotal = 0;
        try {
            long objectId = getMapObjectId(db, pin.getUniqueId(), OBJECT_TYPE_PIN, false, false);
            if (objectId == -1) {
                Log.e(TAG, "Cannot update Pin. Non-deleted UniqueId not found or not a PIN: " + pin.getUniqueId());
                return 0;
            }

            ContentValues moValues = new ContentValues();
            moValues.put(COLUMN_MO_LABEL, pin.getLabel());
            moValues.put(COLUMN_MO_COLOR, pin.getColor());
            int moRowsAffected = db.update(TABLE_MAP_OBJECTS, moValues, COLUMN_MO_ID + " = ?", new String[]{String.valueOf(objectId)});

            ContentValues pdValues = new ContentValues();
            pdValues.put(COLUMN_PD_LATITUDE, pin.getLatitude());
            pdValues.put(COLUMN_PD_LONGITUDE, pin.getLongitude());
            pdValues.put(COLUMN_PD_ICON_RESOURCE_ID, pin.getIconResourceId());
            pdValues.put(COLUMN_PD_ELEVATION, pin.getElevation());
            pdValues.put(COLUMN_PD_ROTATION, pin.getRotation());
            int pdRowsAffected = db.update(TABLE_PIN_DETAILS, pdValues, COLUMN_PD_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)});

            if (moRowsAffected > 0 || pdRowsAffected > 0) {
                rowsAffectedTotal = 1;
                db.setTransactionSuccessful();
                Log.i(TAG, "Pin updated successfully: " + pin.getUniqueId());
            } else {
                Log.i(TAG, "Pin update called, but no data changed for: " + pin.getUniqueId());

                db.setTransactionSuccessful();
                rowsAffectedTotal = 0;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating Pin: " + e.getMessage(), e);
            rowsAffectedTotal = 0;
        } finally {
            db.endTransaction();
        }
        return rowsAffectedTotal;
    }


    public int updateLine(LineInfo line) {
        if (line == null || line.getUniqueId() == null || line.getUniqueId().isEmpty()) return 0;

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        int success = 0;
        try {
            long objectId = getMapObjectId(db, line.getUniqueId(), OBJECT_TYPE_LINE, false, false);
            if (objectId == -1) {
                Log.e(TAG, "Cannot update Line. Non-deleted UniqueId not found or not a LINE: " + line.getUniqueId());
                return 0;
            }

            ContentValues moValues = new ContentValues();
            moValues.put(COLUMN_MO_LABEL, line.getLabel());
            moValues.put(COLUMN_MO_COLOR, line.getColor());
            db.update(TABLE_MAP_OBJECTS, moValues, COLUMN_MO_ID + " = ?", new String[]{String.valueOf(objectId)});

            db.delete(TABLE_OBJECT_COORDINATES, COLUMN_OC_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)});

            List<LineInfo.Coordinate> points = new ArrayList<>();
            if (line.getPoints() != null) {
                for(GeoPoint gp : line.getPoints()) {
                    points.add(new LineInfo.Coordinate(gp.getLatitude(), gp.getLongitude(), (int)gp.getAltitude()));
                }
            }

            if (points != null) {
                for (int i = 0; i < points.size(); i++) {
                    LineInfo.Coordinate point = points.get(i);
                    ContentValues pointValues = new ContentValues();
                    pointValues.put(COLUMN_OC_OBJECT_ID, objectId);
                    pointValues.put(COLUMN_OC_LATITUDE, point.getLatitude());
                    pointValues.put(COLUMN_OC_LONGITUDE, point.getLongitude());
                    pointValues.put(COLUMN_OC_ELEVATION, point.getElevation());
                    pointValues.put(COLUMN_OC_POINT_ORDER, i);
                    if (db.insert(TABLE_OBJECT_COORDINATES, null, pointValues) == -1) {
                        throw new Exception("Failed to insert new coordinate point during update.");
                    }
                }
            }
            db.setTransactionSuccessful();
            success = 1;
            Log.i(TAG, "Line updated successfully: " + line.getUniqueId());
        } catch (Exception e) {
            Log.e(TAG, "Error updating Line: " + e.getMessage(), e);
            success = 0;
        } finally {
            db.endTransaction();
        }
        return success;
    }

    private long getMapObjectId(SQLiteDatabase dbInstance, String uniqueId, String objectType, boolean includeDeleted, boolean closeDbInside) {
        SQLiteDatabase db = dbInstance;
        if (db == null) {
            Log.w(TAG, "getMapObjectId called with null dbInstance. Acquiring new one.");
            db = this.getReadableDatabase();
            closeDbInside = true;
        }

        String selection = COLUMN_MO_UNIQUE_ID + " = ? AND " + COLUMN_MO_OBJECT_TYPE + " = ?";
        if (!includeDeleted) {
            selection += " AND " + COLUMN_MO_IS_DELETED + " = 0";
        }
        Cursor cursor = db.query(TABLE_MAP_OBJECTS, new String[]{COLUMN_MO_ID},
                selection,
                new String[]{uniqueId, objectType}, null, null, null);
        long objectId = -1;
        if (cursor.moveToFirst()) {
            objectId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MO_ID));
        }
        cursor.close();
        if (closeDbInside) {
            db.close();
        }
        return objectId;
    }

    public void softDeleteMapObject(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.w(TAG, "Cannot soft delete: uniqueId is null or empty.");
            return;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MO_IS_DELETED, 1);

        String selection = COLUMN_MO_UNIQUE_ID + " = ?";
        int rowsAffected = db.update(TABLE_MAP_OBJECTS, values, selection, new String[]{uniqueId});

        if (rowsAffected > 0) {
            Log.i(TAG, "MapObject soft deleted successfully: " + uniqueId);
        } else {
            Log.w(TAG, "MapObject not found for soft delete or no change needed: " + uniqueId);
        }
    }

    public int hardDeleteMapObject(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.w(TAG, "Cannot hard delete: uniqueId is null or empty.");
            return 0;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_MAP_OBJECTS, COLUMN_MO_UNIQUE_ID + " = ?", new String[]{uniqueId});

        if (rowsDeleted > 0) {
            Log.i(TAG, "MapObject permanently deleted successfully: " + uniqueId);
        } else {
            Log.w(TAG, "MapObject not found for permanent deletion: " + uniqueId);
        }
        return rowsDeleted;
    }


    public String getDatabaseHash() {
        SQLiteDatabase db = null;
        StringBuilder sb = new StringBuilder();
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
            db = this.getReadableDatabase();
            String query = "SELECT " + COLUMN_MO_UNIQUE_ID +
                    " FROM " + TABLE_MAP_OBJECTS +
                    " WHERE " + COLUMN_MO_IS_DELETED + " = 0" +
                    " ORDER BY " + COLUMN_MO_UNIQUE_ID + " ASC";

            Cursor cursor = db.rawQuery(query, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    sb.append(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MO_UNIQUE_ID)));
                    sb.append("|");
                } while (cursor.moveToNext());
            }

            if (cursor != null) {
                cursor.close();
            }

            byte[] hashBytes = digest.digest(sb.toString().getBytes("UTF-8"));
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 Hashing algorithm not found", e);
            return null;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UTF-8 encoding not supported for hashing", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating database hash of unique IDs: " + e.getMessage(), e);
            return null;
        }
    }
    private void appendColumnDataFromCursor(Cursor cursor, StringBuilder sb, String[] columns) {
        for (String column : columns) {
            int columnIndex = cursor.getColumnIndexOrThrow(column);
            sb.append(column).append("=");
            if (cursor.isNull(columnIndex)) {
                sb.append("NULL");
            } else {
                int type = cursor.getType(columnIndex);
                if (type == Cursor.FIELD_TYPE_FLOAT) {
                    sb.append(Double.toString(cursor.getDouble(columnIndex)));
                } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                    sb.append(cursor.getLong(columnIndex));
                } else if (type == Cursor.FIELD_TYPE_STRING) {
                    sb.append(cursor.getString(columnIndex));
                } else {
                    sb.append(cursor.getString(columnIndex));
                }
            }
            sb.append(";");
        }
    }

    public long getDatabaseFileSize(Context context) {
        SQLiteDatabase db = null;
        long count = 0L;

        try {
            db = this.getReadableDatabase();
            if (db != null) {
                count = DatabaseUtils.queryNumEntries(db, TABLE_MAP_OBJECTS, COLUMN_MO_IS_DELETED + " = 0");
            } else {
                Log.e(TAG, "Failed to get a readable database instance for counting entries.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error counting non-deleted entries in " + TABLE_MAP_OBJECTS, e);
            count = 0L;
        }

        Log.i(TAG, "Total count of non-deleted unique_ids in " + TABLE_MAP_OBJECTS + ": " + count);
        return count;
    }

    private void appendDetailTableDataForObject(SQLiteDatabase db, StringBuilder sb, String tableName,
                                                String[] columnsToAppend, String objectIdColumnName, long objectId) {
        sb.append("DETAIL_TABLE_START:").append(tableName).append("|");
        Cursor detailCursor = null;
        try {
            detailCursor = db.query(tableName, columnsToAppend, objectIdColumnName + " = ?",
                    new String[]{String.valueOf(objectId)}, null, null, null);

            if (detailCursor != null && detailCursor.moveToFirst()) {
                sb.append("ROW_START:");
                appendColumnDataFromCursor(detailCursor, sb, columnsToAppend);
                sb.append("ROW_END|");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error appending detail table data for " + tableName + ", objectId: " + objectId, e);
            throw e;
        } finally {
            if (detailCursor != null) {
                detailCursor.close();
            }
        }
        sb.append("DETAIL_TABLE_END#");
    }

    private void appendObjectCoordinatesData(SQLiteDatabase db, StringBuilder sb, long objectId) {
        String tableName = TABLE_OBJECT_COORDINATES;
        sb.append("COORDS_TABLE_START:").append(tableName).append("|");
        Cursor coordCursor = null;
        try {
            String[] coordinateColumnsToHash = new String[]{
                    COLUMN_OC_LATITUDE, COLUMN_OC_LONGITUDE,
                    COLUMN_OC_ELEVATION, COLUMN_OC_POINT_ORDER
            };
            coordCursor = db.query(tableName, coordinateColumnsToHash,
                    COLUMN_OC_OBJECT_ID + " = ?", new String[]{String.valueOf(objectId)},
                    null, null, COLUMN_OC_POINT_ORDER + " ASC");
            if (coordCursor != null && coordCursor.moveToFirst()) {
                do {
                    sb.append("ROW_START:");
                    appendColumnDataFromCursor(coordCursor, sb, coordinateColumnsToHash);
                    sb.append("ROW_END|");
                } while (coordCursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error appending object coordinates for objectId: " + objectId, e);
            throw e;
        } finally {
            if (coordCursor != null) {
                coordCursor.close();
            }
        }
        sb.append("COORDS_TABLE_END#");
    }

    private void appendTableData(SQLiteDatabase db, StringBuilder sb, String tableName, String[] columns, String orderBy) {
        sb.append("TABLE_START:").append(tableName).append("|");
        Cursor cursor = null;
        try {
            cursor = db.query(tableName, columns, null, null, null, null, orderBy);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    sb.append("ROW_START:");
                    for (String column : columns) {
                        int columnIndex = cursor.getColumnIndex(column);
                        if (columnIndex != -1) {
                            sb.append(column).append("=");
                            if (cursor.isNull(columnIndex)) {
                                sb.append("NULL");
                            } else {
                                int type = cursor.getType(columnIndex);
                                if (type == Cursor.FIELD_TYPE_FLOAT) {
                                    sb.append(Double.toString(cursor.getDouble(columnIndex)));
                                } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                                    sb.append(cursor.getLong(columnIndex));
                                } else if (type == Cursor.FIELD_TYPE_STRING) {
                                    sb.append(cursor.getString(columnIndex));
                                } else {
                                    sb.append(cursor.getString(columnIndex));
                                }
                            }
                            sb.append(";");
                        }
                    }
                    sb.append("ROW_END|");
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error appending table data for table: " + tableName, e);
            throw e;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        sb.append("TABLE_END#");
    }


    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return null;
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public long getDatabaseLastModifiedTimestamp(Context mApplicationContext) {
        try {
            if (mApplicationContext == null) {
                Log.e(TAG, "Context is null, cannot get database path.");
                return 0;
            }
            File dbFile = mApplicationContext.getDatabasePath(DATABASE_NAME);
            if (dbFile != null && dbFile.exists()) {
                return dbFile.lastModified();
            } else {
                Log.w(TAG, "Database file '" + DATABASE_NAME + "' not found or path is null.");
                return 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting database last modified timestamp", e);
            return 0;
        }
    }

    public int undeleteMapObject(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.w(TAG, "Cannot undelete: uniqueId is null or empty.");
            return 0;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MO_IS_DELETED, 0);

        String selection = COLUMN_MO_UNIQUE_ID + " = ? AND " + COLUMN_MO_IS_DELETED + " = 1";
        int rowsAffected = db.update(TABLE_MAP_OBJECTS, values, selection, new String[]{uniqueId});

        if (rowsAffected > 0) {
            Log.i(TAG, "MapObject undeleted successfully: " + uniqueId);
        } else {
            Log.w(TAG, "MapObject not found for undelete, not soft-deleted, or error: " + uniqueId);
        }
        return rowsAffected;
    }

    public List<UuidData> getAllUuidData() {
        List<UuidData> uuidDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        String[] columns = {
                COLUMN_MO_UNIQUE_ID,
                COLUMN_MO_IS_DELETED
        };

        try {
            cursor = db.query(
                    TABLE_MAP_OBJECTS,
                    columns,
                    null,
                    null,
                    null,
                    null,
                    COLUMN_MO_UNIQUE_ID + " ASC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String uuidFromDb = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MO_UNIQUE_ID));
                    int isDeletedFromDb = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MO_IS_DELETED));
                    uuidDataList.add(new UuidData(uuidFromDb, isDeletedFromDb));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving all UUIDs with deletion status: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return uuidDataList;
    }
    public List<byte[]> encodeUuidListToChunkedByteArrays(List<UuidData> uuidDataList) {
        List<byte[]> allChunks = new ArrayList<>();

        if (uuidDataList == null || uuidDataList.isEmpty()) {
            return allChunks;
        }

        final int MAX_ITEMS_PER_CHUNK = 20;
        List<byte[]> currentChunkItemDataBuffer = new ArrayList<>(MAX_ITEMS_PER_CHUNK);

        for (UuidData item : uuidDataList) {
            if (item == null) {
                Log.w(TAG, "Skipping null UuidData item in the list.");
                continue;
            }

            String uuidStr = item.getUuid();
            if (uuidStr == null) {
                Log.w(TAG, "Skipping UuidData item with null UUID. Item: " + item.toString());
                continue;
            }

            if (uuidStr.length() != 8) {
                Log.w(TAG, "UUID string '" + uuidStr + "' is not 8 characters long as expected. Skipping this entry.");
                continue;
            }

            try {
                byte[] uuidStringBytes = uuidStr.getBytes(StandardCharsets.UTF_8);
                if (uuidStringBytes.length != 8) {
                    Log.w(TAG, "UUID string '" + uuidStr + "' (length " + uuidStr.length() +
                            ") did not convert to 8 bytes as UTF-8 (got " + uuidStringBytes.length +
                            " bytes). Skipping this entry.");
                    continue;
                }

                ByteBuffer itemByteBuffer = ByteBuffer.allocate(9);
                itemByteBuffer.put(uuidStringBytes);
                itemByteBuffer.put((byte) item.getIsDeleted());

                currentChunkItemDataBuffer.add(itemByteBuffer.array());

                if (currentChunkItemDataBuffer.size() == MAX_ITEMS_PER_CHUNK) {
                    byte[] chunk = finalizeChunkNoCount(currentChunkItemDataBuffer);
                    if (chunk.length > 0) {
                        allChunks.add(chunk);
                    }
                    currentChunkItemDataBuffer.clear();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing UUID string '" + uuidStr + "' for encoding: " + e.getMessage(), e);
            }
        }

        if (!currentChunkItemDataBuffer.isEmpty()) {
            byte[] chunk = finalizeChunkNoCount(currentChunkItemDataBuffer);
            if (chunk.length > 0) {
                allChunks.add(chunk);
            }
        }

        if (allChunks.isEmpty() && !uuidDataList.isEmpty()) {
            Log.w(TAG, "Input list was not empty, but no valid items were processed into chunks.");
        }

        return allChunks;
    }

    private byte[] finalizeChunkNoCount(List<byte[]> itemDataListInChunk) {
        if (itemDataListInChunk == null || itemDataListInChunk.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream chunkBos = new ByteArrayOutputStream();
        try {
            for (byte[] itemData : itemDataListInChunk) {
                chunkBos.write(itemData);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException during chunk finalization (should not occur): " + e.getMessage(), e);
            return new byte[0];
        }
        return chunkBos.toByteArray();
    }

    public List<UuidData> decodeSingleChunkToUuidList(byte[] chunkData) {
        List<UuidData> decodedItems = new ArrayList<>();

        if (chunkData == null || chunkData.length == 0) {
            return decodedItems;
        }

        if (chunkData.length % 9 != 0) {
            Log.e(TAG, "Malformed chunk (no count byte format): Length " + chunkData.length +
                    " is not a multiple of 9 (bytes per item).");
            return decodedItems;
        }

        int itemsInChunk = chunkData.length / 9;
        if (itemsInChunk == 0) {
            return decodedItems;
        }

        int currentPosition = 0;

        for (int i = 0; i < itemsInChunk; i++) {
            try {
                byte[] uuidBytes = new byte[8];
                System.arraycopy(chunkData, currentPosition, uuidBytes, 0, 8);
                String uuidStr = new String(uuidBytes, StandardCharsets.UTF_8);
                currentPosition += 8;

                int isDeletedFlag = chunkData[currentPosition] & 0xFF;
                currentPosition += 1;

                if (isDeletedFlag != 0 && isDeletedFlag != 1) {
                    Log.w(TAG, "Invalid isDeleted flag (" + isDeletedFlag + ") for UUID " + uuidStr +
                            " in chunk (no count byte format). Interpreting as 0 (not deleted).");
                }
                decodedItems.add(new UuidData(uuidStr, isDeletedFlag));
            } catch (Exception e) {
                Log.e(TAG, "Error decoding item " + (i + 1) + " in chunk (no count byte format): " + e.getMessage(), e);
                decodedItems.clear();
                return decodedItems;
            }
        }
        return decodedItems;
    }

    public boolean objectExists(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.w(TAG, "Cannot check existence: uniqueId is null or empty.");
            return false;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        boolean exists = false;
        try {
            String query = "SELECT 1 FROM " + TABLE_MAP_OBJECTS +
                    " WHERE " + COLUMN_MO_UNIQUE_ID + " = ? LIMIT 1";
            cursor = db.rawQuery(query, new String[]{uniqueId});
            if (cursor != null && cursor.moveToFirst()) {
                exists = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if object exists for uniqueId: " + uniqueId, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }

        }
        return exists;
    }

    public boolean isObjectDeleted(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            Log.w(TAG, "Cannot check deletion status: uniqueId is null or empty.");
            return false;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        boolean isDeleted = false;
        try {
            String[] columns = {COLUMN_MO_IS_DELETED};
            String selection = COLUMN_MO_UNIQUE_ID + " = ?";
            String[] selectionArgs = {uniqueId};
            cursor = db.query(TABLE_MAP_OBJECTS, columns, selection, selectionArgs, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MO_IS_DELETED)) == 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking deletion status for uniqueId: " + uniqueId, e);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return isDeleted;
    }
}