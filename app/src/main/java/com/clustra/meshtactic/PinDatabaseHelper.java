package com.clustra.meshtactic;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class PinDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "map_pins.db";
    private static final int DATABASE_VERSION = 1;

    private static final String COLUMN_ELEVATION = "elevation";
    private static final String TABLE_PINS = "pins";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_LATITUDE = "latitude";
    private static final String COLUMN_LONGITUDE = "longitude";
    private static final String COLUMN_LABEL = "label";
    private static final String COLUMN_ICON_RESOURCE_ID = "icon_resource_id";
    private static final String COLUMN_COLOR = "color";

    private static final String CREATE_TABLE_PINS =
            "CREATE TABLE " + TABLE_PINS + "(" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_LATITUDE + " REAL NOT NULL, " +
                    COLUMN_LONGITUDE + " REAL NOT NULL, " +
                    COLUMN_LABEL + " TEXT, " +
                    COLUMN_ICON_RESOURCE_ID + " INTEGER NOT NULL, " +
                    COLUMN_COLOR + " INTEGER NOT NULL, " +
                    COLUMN_ELEVATION + " INTEGER NOT NULL DEFAULT 0);";


    public PinDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_PINS);
        Log.i("PinDatabaseHelper", "Database table created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PINS);
        onCreate(db);
        Log.w("PinDatabaseHelper", "Database upgraded from version " + oldVersion + " to " + newVersion);
    }

    public long addPin(GeoPoint position, int iconResourceId, int color, String label) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LATITUDE, position.getLatitude());
        values.put(COLUMN_LONGITUDE, position.getLongitude());
        values.put(COLUMN_LABEL, label);
        values.put(COLUMN_ICON_RESOURCE_ID, iconResourceId);
        values.put(COLUMN_COLOR, color);
        values.put(COLUMN_ELEVATION, 0);
        long id = db.insert(TABLE_PINS, null, values);
        db.close();
        Log.i("PinDatabaseHelper", "Pin added to database: " + label + " at " + position.getLatitude() + ", " + position.getLongitude() + ", elevation: 0");
        return id;
    }

    public List<PinInfo> getAllPins() {
        List<PinInfo> pinList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_PINS;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                PinInfo pin = new PinInfo();
                pin.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)));
                pin.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE)));
                pin.setLabel(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABEL)));
                pin.setIconResourceId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ICON_RESOURCE_ID)));
                pin.setColor(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR)));
                pin.setElevation(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ELEVATION)));
                pinList.add(pin);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        Log.i("PinDatabaseHelper", "Loaded " + pinList.size() + " pins from database.");
        return pinList;
    }

    public void removePin(GeoPoint position) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_PINS,
                COLUMN_LATITUDE + " = ? AND " + COLUMN_LONGITUDE + " = ?",
                new String[]{String.valueOf(position.getLatitude()), String.valueOf(position.getLongitude())});
        db.close();
        Log.i("PinDatabaseHelper", "Removed " + rowsDeleted + " pin(s) at " + position.getLatitude() + ", " + position.getLongitude());
    }
}