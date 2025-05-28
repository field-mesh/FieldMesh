package io.github.fieldmesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class CircleInfo {
    private double radius;
    private int color;
    private int lineType;
    private double latitude;
    private double longitude;
    private int elevation;
    private String label;
    private String uniqueId;
    private static final String LABEL_CHARS_LOOKUP = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";
    private static final int MAX_LABEL_LENGTH = 10;
    private static final int END_OF_LABEL_MARKER_VALUE = 37;
    private static final char DEFAULT_CHAR_FOR_INVALID_INPUT = 'A';

    private static final int ENCODED_BYTE_LENGTH = 31;

    public CircleInfo() {
        String fullUUID = UUID.randomUUID().toString();
        this.uniqueId = fullUUID.substring(0, 8);
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        if (color >= 0 && color <= 3) {
            this.color = color;
        } else {
            throw new IllegalArgumentException(String.valueOf(io.github.fieldmesh.R.string.intValueRangeWarning));
        }
    }

    public int getLineType() {
        return lineType;
    }

    public void setLineType(int lineType) {
        if (lineType >= 0 && lineType <= 1) {
            this.lineType = lineType;
        } else {
            throw new IllegalArgumentException(String.valueOf(io.github.fieldmesh.R.string.lineTypeWarning));
        }
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getElevation() {
        return elevation;
    }

    public void setElevation(int elevation) {
        this.elevation = elevation;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        if (label != null && label.length() > MAX_LABEL_LENGTH) {
            this.label = label.substring(0, MAX_LABEL_LENGTH);
        } else {
            this.label = label;
        }
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(ENCODED_BYTE_LENGTH);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt((int) (this.latitude * 1E7));
        buffer.putInt((int) (this.longitude * 1E7));
        buffer.putFloat((float) this.radius);

        byte[] labelBytes = new byte[8];
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
        labelBytes[7] = (byte) ((charVals[9] & 0x0F) << 4);

        buffer.put(labelBytes);

        buffer.putShort((short) this.elevation);

        byte packedColorLineType = (byte) (((this.color & 0x03) << 1) | (this.lineType & 0x01));
        buffer.put(packedColorLineType);

        byte[] uidBytes = new byte[8];
        if (this.uniqueId != null && !this.uniqueId.isEmpty()) {
            byte[] actualUidBytes = this.uniqueId.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(actualUidBytes, 0, uidBytes, 0, Math.min(actualUidBytes.length, 8));
        }
        buffer.put(uidBytes);

        return buffer.array();
    }

    public void decode(byte[] data) {
        if (data == null || data.length < ENCODED_BYTE_LENGTH) {
            throw new IllegalArgumentException("Data array too short for CircleInfo. Expected " +
                    ENCODED_BYTE_LENGTH + " bytes, got " +
                    (data == null ? "null" : data.length));
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        this.latitude = buffer.getInt() / 1E7;
        this.longitude = buffer.getInt() / 1E7;
        this.radius = buffer.getFloat();

        byte[] labelBytes = new byte[8];
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

        this.elevation = buffer.getShort();

        byte packedColorLineType = buffer.get();
        this.color = (packedColorLineType >> 1) & 0x03;
        this.lineType = packedColorLineType & 0x01;
        byte[] uidBytes = new byte[8];
        buffer.get(uidBytes);
        int uidLength = 0;
        while (uidLength < uidBytes.length && uidBytes[uidLength] != 0) {
            uidLength++;
        }
        this.uniqueId = new String(uidBytes, 0, uidLength, StandardCharsets.UTF_8);
    }
}