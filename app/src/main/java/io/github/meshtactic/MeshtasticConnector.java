package io.github.meshtactic;

import android.os.RemoteException;
import android.util.Log;

import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;
import com.geeksville.mesh.MessageStatus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class MeshtasticConnector {
    private static final int MESHTASTIC_PORTNUM_PINS = 300;
    static String TAG = "MeshtasticConnector";
    private static List<Byte> dataTypes = Arrays.asList(
            (byte) 0x01,
            (byte) 0x02,
            (byte) 0x03,
            (byte) 0x04,
            (byte) 0x05,
            (byte) 0x06,
            (byte) 0x07,
            (byte) 0x08,
            (byte) 0x09,
            (byte) 0x0A,
            (byte) 0x0B

    );

    private static List<String> dataTypesNames = Arrays.asList(
            "PIN",
            "LINE",
            "POLY",
            "CIRCLE",
            "DELETE",
            "DB_HASH",
            "REQUEST_UUID_LIST",
            "SEND_UUID_LIST",
            "REQUEST_UUID_DATA",
            "SEND_UUID_DATA",
            "FINISH_SYNC"
    );


    public static void sendData(IMeshService meshService, byte[] encodedData, String type, String destination) {
        int dataTypeIndex = getDataTypeIndexByName(type);
        byte[] typedData;
        DataPacket dataPacket;
        final boolean WANT_ACK = false;
        typedData = new byte[encodedData.length + 1];
        typedData[0] = dataTypes.get(dataTypeIndex);
        System.arraycopy(encodedData, 0, typedData, 1, encodedData.length);
        dataPacket = new DataPacket(
                destination,
                typedData,
                MESHTASTIC_PORTNUM_PINS,
                DataPacket.ID_LOCAL,
                System.currentTimeMillis(),
                0,
                MessageStatus.QUEUED,
                0,
                0,
                WANT_ACK
        );
        try {
            meshService.send(dataPacket);
            MessageStatus status = dataPacket.getStatus();
            if (status == MessageStatus.ENROUTE || status == MessageStatus.DELIVERED || status == MessageStatus.QUEUED) {
            } else if (status == MessageStatus.ERROR) {
                Log.w(TAG, "Meshtastic service reported an error for the DataPacket. Status: " + status +
                        (dataPacket.getErrorMessage() != null ? ", Error: " + dataPacket.getErrorMessage() : ""));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send DataPacket due to RemoteException.", e);
        } catch (Exception e) {
            Log.e(TAG, "An unexpected error occurred while sending DataPacket.", e);
        }
    }

    public static int getDataTypeIndexByName(String dataTypeName) {
        int dataTypeIndex = 0;
        dataTypeIndex = dataTypesNames.indexOf(dataTypeName);
        return dataTypeIndex;
    }

    public static String getDataTypeNameByIndex(int dataTypeIndex) {
        return dataTypesNames.get(dataTypeIndex);
    }

    public static String receivedDataTypeName(byte[] receivedEncodedData) {
        String receivedDataTypeName = "";
        if (receivedEncodedData.length > 0) {
            byte receivedDataType = receivedEncodedData[0];
            int dataTypeIndex = dataTypes.indexOf(receivedDataType);
            if (dataTypeIndex >= 0 && dataTypeIndex < dataTypesNames.size()) {
                receivedDataTypeName = dataTypesNames.get(dataTypeIndex);
            } else {
                receivedDataTypeName = "UNKNOWN";
            }
        }
        return receivedDataTypeName;
    }

    public static void sendDeleteCommand(IMeshService meshService, String uuid) {
        byte[] encodedUUID = encodeUUID(uuid);
        sendData(meshService, encodedUUID, "DELETE", DataPacket.ID_BROADCAST);
    }

    public static String decodeUUID(byte[] encodedUUID) {
        if (encodedUUID == null) {
            return "";
        }
        int stringLength = 0;
        while (stringLength < encodedUUID.length && encodedUUID[stringLength] != 0) {
            stringLength++;
        }
        return new String(encodedUUID, 0, stringLength, StandardCharsets.UTF_8);
    }

    public static byte[] encodeUUID(String uuidString) {
        byte[] encodedUUID = new byte[8];

        if (uuidString != null && !uuidString.isEmpty()) {
            byte[] stringBytes = uuidString.getBytes(StandardCharsets.UTF_8);

            int lengthToCopy = Math.min(stringBytes.length, encodedUUID.length);

            System.arraycopy(stringBytes, 0, encodedUUID, 0, lengthToCopy);
        }
        return encodedUUID;
    }

    public static byte[] cleanReceivedData(byte[] receivedEncodedData) {
        byte[] cleanedData = new byte[receivedEncodedData.length - 1];
        System.arraycopy(receivedEncodedData, 1, cleanedData, 0, receivedEncodedData.length - 1);
        return cleanedData;
    }

    public static byte[] encodeString(String inputString) {
        return inputString.getBytes(StandardCharsets.UTF_8);
    }

    public static String decodeString(byte[] encodedString) {
        return new String(encodedString, StandardCharsets.UTF_8);
    }

    public static byte[] encodeTimestamp(long timestamp) {
        byte[] timestampBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            timestampBytes[i] = (byte) ((timestamp >> (8 * (7 - i))) & 0xFF);
        }
        return timestampBytes;
    }

    public static long decodeTimestamp(byte[] timestampBytes) {
        long timestamp = 0;
        for (int i = 0; i < 8; i++) {
            timestamp |= (long) (timestampBytes[i] & 0xFF) << (8 * (7 - i));
        }
        return timestamp;
    }

    public static byte[] encodeLong(long value) {
     ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
     buffer.putLong(value);
     return buffer.array();
 }

    public static long decodeLong(byte[] bytes) {
     if (bytes == null || bytes.length != Long.BYTES) {
         throw new IllegalArgumentException("Byte array must be non-null and have length " + Long.BYTES);
     }
     ByteBuffer buffer = ByteBuffer.wrap(bytes);
     return buffer.getLong();
 }

    public static void sendDBHash(IMeshService meshService, String dbHashString, long databaseSize, long timestamp) {
        byte[] dbHashBytes = encodeString(dbHashString);
        byte[] dbSizeBytes = encodeLong(databaseSize);
        byte[] timestampBytes = encodeTimestamp(timestamp);

        int totalLength = dbHashBytes.length + dbSizeBytes.length + timestampBytes.length;
        byte[] combinedPayload = new byte[totalLength];

        int currentIndex = 0;

        System.arraycopy(dbHashBytes, 0, combinedPayload, currentIndex, dbHashBytes.length);
        currentIndex += dbHashBytes.length;

        System.arraycopy(dbSizeBytes, 0, combinedPayload, currentIndex, dbSizeBytes.length);
        currentIndex += dbSizeBytes.length;

        System.arraycopy(timestampBytes, 0, combinedPayload, currentIndex, timestampBytes.length);

        sendData(meshService, combinedPayload, "DB_HASH", DataPacket.ID_BROADCAST);
    }

    public static DecodedDBHash decodeDBHashPayload(byte[] cleanedPayload) {
        if (cleanedPayload == null) {
            Log.e(TAG, "Cannot decode DB_HASH: cleaned payload is null.");
            return null;
        }

        final int TIMESTAMP_LENGTH = 8;
        final int DB_SIZE_LENGTH = 8;
        final int MIN_PAYLOAD_LENGTH = TIMESTAMP_LENGTH + DB_SIZE_LENGTH;

        if (cleanedPayload.length < MIN_PAYLOAD_LENGTH) {
            Log.e(TAG, "Cannot decode DB_HASH: cleaned payload is too short (" + cleanedPayload.length +
                    " bytes) to contain a " + DB_SIZE_LENGTH + "-byte database size and a " +
                    TIMESTAMP_LENGTH + "-byte timestamp.");
            return null;
        }

        byte[] timestampBytes = new byte[TIMESTAMP_LENGTH];
        int timestampStartIndex = cleanedPayload.length - TIMESTAMP_LENGTH;
        System.arraycopy(cleanedPayload, timestampStartIndex, timestampBytes, 0, TIMESTAMP_LENGTH);

        long timestamp;
        try {
            timestamp = decodeTimestamp(timestampBytes);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to decode timestamp from DB_HASH payload.", e);
            return null;
        }

        byte[] dbSizeBytes = new byte[DB_SIZE_LENGTH];
        int dbSizeStartIndex = cleanedPayload.length - TIMESTAMP_LENGTH - DB_SIZE_LENGTH;
        System.arraycopy(cleanedPayload, dbSizeStartIndex, dbSizeBytes, 0, DB_SIZE_LENGTH);

        long databaseSize;
        try {
            databaseSize = decodeLong(dbSizeBytes);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to decode database size from DB_HASH payload.", e);
            return null;
        }

        int dbHashBytesLength = cleanedPayload.length - TIMESTAMP_LENGTH - DB_SIZE_LENGTH;
        String dbHashString;

        if (dbHashBytesLength > 0) {
            byte[] dbHashBytes = new byte[dbHashBytesLength];
            System.arraycopy(cleanedPayload, 0, dbHashBytes, 0, dbHashBytesLength);
            dbHashString = decodeString(dbHashBytes);
            if (dbHashString == null) {
                Log.e(TAG, "Failed to decode DB hash string from DB_HASH payload.");
                return null;
            }
        } else if (dbHashBytesLength == 0) {
            dbHashString = "";
        } else {
            Log.e(TAG, "Error in decoding logic: dbHashBytesLength is negative ("+ dbHashBytesLength +"), which should not happen here.");
            return null;
        }

        return new DecodedDBHash(timestamp, dbHashString, databaseSize);
    }
}
