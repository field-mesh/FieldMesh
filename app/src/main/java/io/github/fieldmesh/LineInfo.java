package io.github.fieldmesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class LineInfo {
    private List<Coordinate> points;
    private int color;
    private String label;
    private String uniqueId;
    // FIX: Changed to byte for efficiency. 0 = Global.
    private byte squadId;

    private static final String LABEL_CHARS_LOOKUP = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";
    private static final int MAX_LABEL_LENGTH = 10;
    private static final int END_OF_LABEL_MARKER_VALUE = 37;
    private static final char DEFAULT_CHAR_FOR_INVALID_INPUT = 'A';
    private static final int MAX_POINTS = 15;

    // --- Fixed Constants ---
    private static final int UNIQUE_ID_LENGTH_BYTES = 8;
    private static final int LABEL_ENCODED_LENGTH_BYTES = 8;
    // FIX: Added constant for the new squadId field
    private static final int SQUAD_ID_LENGTH_BYTES = 1;
    // FIX: Updated the base length to include the new squadId byte
    private static final int BASE_ENCODED_LENGTH = UNIQUE_ID_LENGTH_BYTES + LABEL_ENCODED_LENGTH_BYTES + SQUAD_ID_LENGTH_BYTES + 1;
    private static final int BYTES_PER_POINT = 4 + 4 + 2;

    public LineInfo() {
        this.points = new ArrayList<>();
        String fullUUID = UUID.randomUUID().toString();
        this.uniqueId = fullUUID.substring(0, 8);
        this.squadId = 0; // Default to Global
    }

    // --- Getters ---
    public String getLabel() { return label; }
    public byte getSquadId() { return squadId; }
    public String getUniqueId() { return uniqueId; }
    public int getColor() { return color; }
    public List<org.osmdroid.util.GeoPoint> getPoints() {
        List<org.osmdroid.util.GeoPoint> geoPoints = new ArrayList<>();
        for (Coordinate point : points) {
            geoPoints.add(new org.osmdroid.util.GeoPoint(point.getLatitude(), point.getLongitude()));
        }
        return geoPoints;
    }

    // --- Setters ---
    public void setLabel(String label) {
        if (label != null && label.length() > MAX_LABEL_LENGTH) {
            this.label = label.substring(0, MAX_LABEL_LENGTH).toUpperCase();
        } else if (label != null) {
            this.label = label.toUpperCase();
        } else {
            this.label = null;
        }
    }
    public void setSquadId(byte squadId) { this.squadId = squadId; }
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }
    public void setPoints(List<org.osmdroid.util.GeoPoint> geoPoints) {
        if (geoPoints != null && geoPoints.size() <= MAX_POINTS) {
            this.points = new ArrayList<>();
            for (org.osmdroid.util.GeoPoint point : geoPoints) {
                this.points.add(new Coordinate(point.getLatitude(), point.getLongitude(), 0));
            }
        } else if (geoPoints != null) {
            throw new IllegalArgumentException("Maximum " + MAX_POINTS + " points allowed for a line.");
        } else {
            this.points = new ArrayList<>();
        }
    }
    public void setPointsList(List<Coordinate> points) {
        if (points != null && points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("Maximum " + MAX_POINTS + " points allowed for a line.");
        }
        this.points = points != null ? new ArrayList<>(points) : new ArrayList<>();
    }
    public void setColor(int color) {
        int maxColor = ColorIndex.colors.size() - 1;
        if (color >= 0 && color <= maxColor) {
            this.color = color;
        } else {
            throw new IllegalArgumentException("Color value must be between 0 and " + maxColor + ".");
        }
    }

    public byte[] encode() {
        int numPoints = (this.points != null) ? this.points.size() : 0;
        if (numPoints > MAX_POINTS) {
            throw new IllegalStateException("Number of points exceeds maximum allowed (" + MAX_POINTS + ")");
        }

        int totalSize = BASE_ENCODED_LENGTH + (numPoints * BYTES_PER_POINT);
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte[] uidBytes = new byte[UNIQUE_ID_LENGTH_BYTES];
        if (this.uniqueId != null && !this.uniqueId.isEmpty()) {
            byte[] actualUidBytes = this.uniqueId.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(actualUidBytes, 0, uidBytes, 0, Math.min(actualUidBytes.length, UNIQUE_ID_LENGTH_BYTES));
        }
        buffer.put(uidBytes);

        // FIX: Encode the squadId byte
        buffer.put(this.squadId);

        byte[] labelBytes = new byte[LABEL_ENCODED_LENGTH_BYTES];
        String currentLabel = this.label;
        int[] charVals = new int[MAX_LABEL_LENGTH];
        for (int i = 0; i < MAX_LABEL_LENGTH; i++) {
            if (currentLabel != null && i < currentLabel.length()) {
                char c = currentLabel.charAt(i);
                int val = LABEL_CHARS_LOOKUP.indexOf(c);
                if (val != -1) {
                    charVals[i] = val;
                } else {
                    charVals[i] = LABEL_CHARS_LOOKUP.indexOf(DEFAULT_CHAR_FOR_INVALID_INPUT);
                }
            } else {
                charVals[i] = END_OF_LABEL_MARKER_VALUE;
            }
        }
        labelBytes[0] = (byte) ((charVals[0] << 2) | (charVals[1] >> 4));
        labelBytes[1] = (byte) (((charVals[1] & 0x0F) << 4) | (charVals[2] >> 2));
        labelBytes[2] = (byte) (((charVals[2] & 0x03) << 6) | charVals[3]);
        labelBytes[3] = (byte) ((charVals[4] << 2) | (charVals[5] >> 4));
        labelBytes[4] = (byte) (((charVals[5] & 0x0F) << 4) | (charVals[6] >> 2));
        labelBytes[5] = (byte) (((charVals[6] & 0x03) << 6) | charVals[7]);
        labelBytes[6] = (byte) ((charVals[8] << 2) | (charVals[9] >> 4));
        labelBytes[7] = (byte) (((charVals[9] & 0x0F) << 4));
        buffer.put(labelBytes);

        int lowTwo = this.color & 0x03;
        int highBit = (this.color & 0x04) >> 2;
        byte metadataByte = (byte) (((numPoints & 0x0F) << 4) | (lowTwo << 2) | (highBit << 1));
        buffer.put(metadataByte);

        if (this.points != null) {
            for (Coordinate point : this.points) {
                buffer.putInt((int) (point.getLatitude() * 1E7));
                buffer.putInt((int) (point.getLongitude() * 1E7));
                buffer.putShort((short) point.getElevation());
            }
        }
        return buffer.array();
    }

    public void decode(byte[] data) {
        // Use the original base length for the initial check to support old packets
        final int ORIGINAL_BASE_LENGTH = UNIQUE_ID_LENGTH_BYTES + LABEL_ENCODED_LENGTH_BYTES + 1;
        if (data == null || data.length < ORIGINAL_BASE_LENGTH) {
            throw new IllegalArgumentException("Data array too short for LineInfo base. Expected at least " +
                    ORIGINAL_BASE_LENGTH + " bytes, got " + (data == null ? "null" : data.length));
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte[] uidBytes = new byte[UNIQUE_ID_LENGTH_BYTES];
        buffer.get(uidBytes);
        int uidLength = 0;
        while (uidLength < uidBytes.length && uidBytes[uidLength] != 0) {
            uidLength++;
        }
        this.uniqueId = new String(uidBytes, 0, uidLength, StandardCharsets.UTF_8);

        // FIX: Decode squadId, but check remaining bytes to see if it's an old packet
        // (8 bytes for label + 1 for metadata = 9)
        if (buffer.remaining() > (LABEL_ENCODED_LENGTH_BYTES + 1)) {
            this.squadId = buffer.get();
        } else {
            this.squadId = 0; // Default to Global for old packets
        }

        byte[] labelBytes = new byte[LABEL_ENCODED_LENGTH_BYTES];
        buffer.get(labelBytes);
        int[] charVals = new int[MAX_LABEL_LENGTH];
        charVals[0] = (labelBytes[0] >> 2) & 0x3F;
        charVals[1] = ((labelBytes[0] & 0x03) << 4) | ((labelBytes[1] >> 4) & 0x0F);
        charVals[2] = ((labelBytes[1] & 0x0F) << 2) | ((labelBytes[2] >> 6) & 0x03);
        charVals[3] = labelBytes[2] & 0x3F;
        charVals[4] = (labelBytes[3] >> 2) & 0x3F;
        charVals[5] = ((labelBytes[3] & 0x03) << 4) | ((labelBytes[4] >> 4) & 0x0F);
        charVals[6] = ((labelBytes[4] & 0x0F) << 2) | ((labelBytes[5] >> 6) & 0x03);
        charVals[7] = labelBytes[5] & 0x3F;
        charVals[8] = (labelBytes[6] >> 2) & 0x3F;
        charVals[9] = ((labelBytes[6] & 0x03) << 4) | ((labelBytes[7] >> 4) & 0x0F);

        StringBuilder labelBuilder = new StringBuilder(MAX_LABEL_LENGTH);
        for (int i = 0; i < MAX_LABEL_LENGTH; i++) {
            int val = charVals[i];
            if (val == END_OF_LABEL_MARKER_VALUE) {
                break;
            }
            if (val >= 0 && val < LABEL_CHARS_LOOKUP.length()) {
                labelBuilder.append(LABEL_CHARS_LOOKUP.charAt(val));
            } else {
                labelBuilder.append(DEFAULT_CHAR_FOR_INVALID_INPUT);
            }
        }
        this.label = labelBuilder.toString();

        byte metadataByte = buffer.get();
        int numPoints = (metadataByte >> 4) & 0x0F;
        int lowTwo = (metadataByte >> 2) & 0x03;
        int highBit = (metadataByte >> 1) & 0x01;
        this.color = (highBit << 2) | lowTwo;

        int expectedBaseLength = (this.squadId == 0 && buffer.position() <= ORIGINAL_BASE_LENGTH) ? ORIGINAL_BASE_LENGTH : BASE_ENCODED_LENGTH;
        int expectedMinDataLength = expectedBaseLength + (numPoints * BYTES_PER_POINT);
        if (data.length < expectedMinDataLength) {
            throw new IllegalArgumentException("Data array too short for the number of points indicated. Expected " +
                    expectedMinDataLength + " bytes, got " + data.length);
        }

        this.points = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; i++) {
            if (buffer.remaining() < BYTES_PER_POINT) {
                throw new IllegalArgumentException("Insufficient data remaining in buffer for point " + (i + 1) +
                        ". Required " + BYTES_PER_POINT + ", available " + buffer.remaining());
            }
            double lat = buffer.getInt() / 1E7;
            double lon = buffer.getInt() / 1E7;
            int elev = buffer.getShort();
            this.points.add(new Coordinate(lat, lon, elev));
        }
    }

    public static class Coordinate {
        private double latitude;
        private double longitude;
        private int elevation;

        public Coordinate(double latitude, double longitude, int elevation) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.elevation = elevation;
        }

        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public int getElevation() { return elevation; }
        public void setElevation(int elevation) { this.elevation = elevation; }

        @Override
        public String toString() {
            return "Coordinate{" +
                    "lat=" + latitude +
                    ", lon=" + longitude +
                    ", elev=" + elevation +
                    '}';
        }
    }
}