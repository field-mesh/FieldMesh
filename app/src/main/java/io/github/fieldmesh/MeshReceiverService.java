package io.github.fieldmesh;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;
import com.geeksville.mesh.NodeInfo;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.fieldmesh.ui.UuidData;

public class MeshReceiverService extends Service implements MessageClient.OnMessageReceivedListener {
    private static final String TAG = "MeshReceiverService";
    public static final String ACTION_MAP_DATA_REFRESH = "io.github.fieldmesh.ACTION_MAP_DATA_REFRESH";
    public static final String ACTION_SYNC_STATUS_UPDATE = "io.github.fieldmesh.ACTION_SYNC_STATUS_UPDATE";
    public static final String EXTRA_SYNC_STATUS_MESSAGE = "io.github.fieldmesh.EXTRA_SYNC_STATUS_MESSAGE";

    public static final String ACTION_FORWARD_PHONE_LOCATION = "io.github.fieldmesh.ACTION_FORWARD_PHONE_LOCATION";
    public static final String EXTRA_LOCATION = "io.github.fieldmeshc.EXTRA_LOCATION";
    public static final String ACTION_TRIGGER_WEAR_MAP_SYNC = "io.github.fieldmesh.ACTION_TRIGGER_WEAR_MAP_SYNC";


    private static final String CHANNEL_ID = "MeshReceiverServiceChannel";

    private MapDataDatabaseHelper mapDataDbHelper;
    private IMeshService geeksvilleMeshService;
    private boolean isGeeksvilleMeshServiceBound = false;
    private boolean isDBSyncing = false;
    private String syncingWithNodeId = null;
    private long syncProcessStartTime = 0;
    private List<UuidData> fullPeerUuidList = new ArrayList<>();
    private List<UuidData> itemsToRequestFromPeer = new ArrayList<>();

    private List<UuidData> currentlyAccumulatingPeerUuidList = new ArrayList<>();
    private byte expectedTotalChunksForPeerUuidList = 0;
    private int receivedChunkCountForPeerUuidList = 0;

    private List<UuidData> currentlyAccumulatingDataRequestsFromPeer = new ArrayList<>();
    private byte expectedTotalChunksForDataRequestsFromPeer = 0;
    private int receivedChunkCountForDataRequestsFromPeer = 0;
    private static final long DATA_REQUEST_TIMEOUT_MS = 45 * 1000;
    private static final int MAX_DATA_REQUEST_RETRIES = 2;
    private int currentDataRequestRetries = 0;
    private Handler dataRequestTimeoutHandler;
    private Runnable dataRequestTimeoutRunnable;
    private Handler periodicDBHashUpdateHandler;
    private Runnable dbHashUpdateRunnable;
    private static final long DB_HASH_SEND_INTERVAL_MS = 1 * 60 * 1000;

    private Handler periodicNodeLocationUpdateHandler;
    private Runnable nodeLocationUpdateRunnable;
    private static final long NODE_LOCATION_UPDATE_INTERVAL_MS = 30 * 1000;

    private MessageClient messageClientToWear;
    private String wearNodeId = null;
    private static final String PATH_REQUEST_FULL_SYNC_FROM_WEAR = "/fieldmesh/request_full_sync";
    private static final String PATH_MAP_DATA_BATCH_TO_WEAR = "/fieldmesh/map_data_batch";
    private static final String PATH_PHONE_LOCATION_UPDATE_TO_WEAR = "/fieldmesh/phone_location_update";
    private static final String PATH_NODE_LOCATIONS_UPDATE_TO_WEAR = "/fieldmesh/node_locations_update";


    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private static final long LOCATION_UPDATE_INTERVAL_MS = 30000;
    private static final long FASTEST_LOCATION_UPDATE_INTERVAL_MS = 15000;


    private final ServiceConnection geeksvilleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Geeksville MeshService connected to MeshReceiverService");
            geeksvilleMeshService = IMeshService.Stub.asInterface(service);
            isGeeksvilleMeshServiceBound = true;
            startPeriodicDBHashUpdate();
            startPeriodicNodeLocationUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Geeksville MeshService disconnected from MeshReceiverService");
            geeksvilleMeshService = null;
            isGeeksvilleMeshServiceBound = false;
            stopPeriodicDBHashUpdate();
            stopPeriodicNodeLocationUpdate();
        }
    };

    private void bindToGeeksvilleMeshService() {
        Intent intent = new Intent();
        intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService");
        try {
            boolean bindResult = bindService(intent, geeksvilleServiceConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "Geeksville MeshService binding attempt from MeshReceiverService: " + bindResult);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind to Geeksville MeshService from MeshReceiverService", e);
        }
    }

    private void unbindFromGeeksvilleMeshService() {
        if (isGeeksvilleMeshServiceBound) {
            try {
                unbindService(geeksvilleServiceConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Geeksville MeshService not registered or already unbound: " + e.getMessage());
            }
            isGeeksvilleMeshServiceBound = false;
            geeksvilleMeshService = null;
        }
    }

    private final BroadcastReceiver meshtasticPacketReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "ServiceReceiver: onReceive for action: " + intent.getAction());
            if (geeksvilleMeshService == null || !isGeeksvilleMeshServiceBound) {
                Log.w(TAG, "ServiceReceiver: Geeksville MeshService not bound. Cannot process or send sync messages. Attempting rebind.");
                bindToGeeksvilleMeshService();
                return;
            }

            Bundle extras = intent.getExtras();
            DataPacket packet = null;
            String packetKey = "com.geeksville.mesh.Payload";

            if (intent.hasExtra(packetKey)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packet = intent.getParcelableExtra(packetKey, DataPacket.class);
                } else {
                    @SuppressWarnings("deprecation")
                    Object tempPacketObj = intent.getParcelableExtra(packetKey);
                    if (tempPacketObj instanceof DataPacket) {
                        packet = (DataPacket) tempPacketObj;
                    }
                }

                if (packet == null || packet.getBytes() == null) {
                    Log.w(TAG, "ServiceReceiver: Received null packet or packet with null bytes.");
                    return;
                }

                String receivedDataTypeName = MeshtasticConnector.receivedDataTypeName(packet.getBytes());
                byte[] receivedEncodedData = MeshtasticConnector.cleanReceivedData(packet.getBytes());
                String fromNodeId = packet.getFrom();
                Log.d(TAG, "ServiceReceiver: Processing data type: '" + receivedDataTypeName + "' from " + fromNodeId);

                if (isDBSyncing && syncingWithNodeId != null && syncingWithNodeId.equals(fromNodeId)) {
                    syncProcessStartTime = System.currentTimeMillis();
                }

                boolean dataChanged = false;

                switch (receivedDataTypeName) {
                    case "DB_HASH":
                        DecodedDBHash decodedDBHash = MeshtasticConnector.decodeDBHashPayload(receivedEncodedData);
                        String myHash = mapDataDbHelper.getDatabaseHash();
                        long myDBSize = mapDataDbHelper.getDatabaseFileSize(getApplicationContext());
                        Log.i(TAG, "ServiceReceiver: Received DB_HASH from " + fromNodeId + ": hash=" + decodedDBHash.getDbHash() + ", size=" + decodedDBHash.getDatabaseSize() +
                                ". My hash=" + myHash + ", size=" + myDBSize);
                        sendSyncStatusUpdate("synced");

                        if (myHash.equals(decodedDBHash.getDbHash()) && myDBSize == decodedDBHash.getDatabaseSize()) {
                            Log.i(TAG, "ServiceReceiver: DB Hashes match with " + fromNodeId + ". Databases are in sync.");
                            if (isDBSyncing && syncingWithNodeId != null && syncingWithNodeId.equals(fromNodeId)) {
                                Log.i(TAG, "ServiceReceiver: Was syncing with " + fromNodeId + " and hashes now match. Finalizing sync.");
                                cancelDataRequestTimeout();
                                resetSyncState();
                            }
                        } else {
                            Log.i(TAG, "ServiceReceiver: DB Hash mismatch or size difference with " + fromNodeId + ".");
                            if (decodedDBHash.getDatabaseSize() > myDBSize || (decodedDBHash.getDatabaseSize() == myDBSize && !myHash.equals(decodedDBHash.getDbHash()))) {
                                if (!isDBSyncing) {
                                    Log.i(TAG, "ServiceReceiver: My DB is smaller or different. Initiating sync with " + fromNodeId);
                                    initiateSyncProcess(fromNodeId);
                                } else if (syncingWithNodeId != null && syncingWithNodeId.equals(fromNodeId)) {
                                    Log.i(TAG, "ServiceReceiver: Currently syncing with " + fromNodeId + " but received a new differing hash. Restarting UUID list request.");
                                    initiateSyncProcess(fromNodeId);
                                } else {
                                    Log.i(TAG, "ServiceReceiver: DB is smaller/different but already syncing with " + syncingWithNodeId + ". Ignoring hash from " + fromNodeId + " for now.");
                                }
                            } else {
                                Log.i(TAG, "ServiceReceiver: My DB is larger or same size but different hash. Peer " + fromNodeId + " might need to sync from me.");
                            }
                        }
                        break;

                    case "REQUEST_UUID_LIST":
                        Log.i(TAG, "ServiceReceiver: Received REQUEST_UUID_LIST from " + fromNodeId);
                        if (isDBSyncing && syncingWithNodeId != null && !syncingWithNodeId.equals(fromNodeId)) {
                            Log.w(TAG, "ServiceReceiver: Busy syncing with " + syncingWithNodeId + ". Ignoring REQUEST_UUID_LIST from " + fromNodeId);
                            return;
                        }
                        cancelDataRequestTimeout();
                        isDBSyncing = true;
                        syncingWithNodeId = fromNodeId;
                        syncProcessStartTime = System.currentTimeMillis();
                        currentDataRequestRetries = 0;
                        sendSyncStatusUpdate("syncing");

                        List<UuidData> localCompleteUuidList = mapDataDbHelper.getAllUuidData();
                        List<byte[]> encodedUUIDListChunks = mapDataDbHelper.encodeUuidListToChunkedByteArrays(localCompleteUuidList);

                        Log.i(TAG, "ServiceReceiver: Sending " + encodedUUIDListChunks.size() + " chunks of UUID list to " + syncingWithNodeId);
                        for (int i = 0; i < encodedUUIDListChunks.size(); i++) {
                            byte[] originalChunk = encodedUUIDListChunks.get(i);
                            if (i > 255) {
                                Log.e(TAG, "Too many chunks for UUID list, index exceeds byte size!");
                                break;
                            }
                            byte indexByte = (byte) i;
                            byte encodedListSizeByte = (byte) encodedUUIDListChunks.size();

                            byte[] chunkWithHeader = new byte[originalChunk.length + 2];
                            chunkWithHeader[0] = encodedListSizeByte;
                            chunkWithHeader[1] = indexByte;
                            System.arraycopy(originalChunk, 0, chunkWithHeader, 2, originalChunk.length);
                            MeshtasticConnector.sendData(geeksvilleMeshService, chunkWithHeader, "SEND_UUID_LIST", syncingWithNodeId);
                        }
                        break;

                    case "SEND_UUID_LIST":
                        if (!isDBSyncing || syncingWithNodeId == null || !syncingWithNodeId.equals(fromNodeId)) {
                            Log.w(TAG, "ServiceReceiver: Received SEND_UUID_LIST but not in correct sync state or from unexpected sender: " + fromNodeId);
                            return;
                        }
                        cancelDataRequestTimeout();

                        byte totalPeerUuidChunks = receivedEncodedData[0];
                        byte currentPeerUuidChunkIndex = receivedEncodedData[1];
                        byte[] peerUuidChunkData = new byte[receivedEncodedData.length - 2];
                        System.arraycopy(receivedEncodedData, 2, peerUuidChunkData, 0, peerUuidChunkData.length);

                        if (currentPeerUuidChunkIndex == 0) {
                            currentlyAccumulatingPeerUuidList.clear();
                            expectedTotalChunksForPeerUuidList = totalPeerUuidChunks;
                            receivedChunkCountForPeerUuidList = 0;
                            fullPeerUuidList.clear();
                            Log.d(TAG, "ServiceReceiver: Starting to receive " + totalPeerUuidChunks + " UUID list chunks from " + syncingWithNodeId);
                            sendSyncStatusUpdate("syncing (0/" + (totalPeerUuidChunks-1) + ")");
                        }

                        List<UuidData> decodedPeerChunkContent = mapDataDbHelper.decodeSingleChunkToUuidList(peerUuidChunkData);
                        currentlyAccumulatingPeerUuidList.addAll(decodedPeerChunkContent);
                        receivedChunkCountForPeerUuidList++;
                        Log.d(TAG, "ServiceReceiver: Received UUID list chunk " + currentPeerUuidChunkIndex + "/" + (expectedTotalChunksForPeerUuidList-1) + ". Accum: " + currentlyAccumulatingPeerUuidList.size());
                        sendSyncStatusUpdate("syncing (" + currentPeerUuidChunkIndex + "/" + (expectedTotalChunksForPeerUuidList-1) + ")");


                        if (receivedChunkCountForPeerUuidList == expectedTotalChunksForPeerUuidList) {
                            Log.i(TAG, "ServiceReceiver: All " + expectedTotalChunksForPeerUuidList + " UUID list chunks received from " + syncingWithNodeId);
                            fullPeerUuidList.addAll(currentlyAccumulatingPeerUuidList);
                            currentlyAccumulatingPeerUuidList.clear();

                            itemsToRequestFromPeer.clear();
                            List<UuidData> myLocalUuids = mapDataDbHelper.getAllUuidData();

                            for (UuidData peerItem : fullPeerUuidList) {
                                if (peerItem.isDeleted == 1) {
                                    if (mapDataDbHelper.objectExists(peerItem.getUuid()) && !mapDataDbHelper.isObjectDeleted(peerItem.getUuid())) {
                                        Log.d(TAG, "ServiceReceiver: Peer item " + peerItem.getUuid() + " is deleted. Marking deleted locally.");
                                        mapDataDbHelper.softDeleteMapObject(peerItem.getUuid());
                                        dataChanged = true;
                                    }
                                } else {
                                    if (!mapDataDbHelper.objectExists(peerItem.getUuid())) {
                                        itemsToRequestFromPeer.add(peerItem);
                                    }
                                }
                            }

                            if (itemsToRequestFromPeer.isEmpty()) {
                                Log.i(TAG, "ServiceReceiver: No missing items to request from " + syncingWithNodeId + ". DBs should be in sync.");
                                MeshtasticConnector.sendData(geeksvilleMeshService, new byte[0], "FINISH_SYNC", syncingWithNodeId);
                                resetSyncState();
                                if(dataChanged) sendMapDataRefreshBroadcast();
                            } else {
                                Log.i(TAG, "ServiceReceiver: Need to request " + itemsToRequestFromPeer.size() + " items from " + syncingWithNodeId);
                                sendSyncStatusUpdate("syncing (" + itemsToRequestFromPeer.size() + ")");
                                List<byte[]> missingItemsChunks = mapDataDbHelper.encodeUuidListToChunkedByteArrays(itemsToRequestFromPeer);
                                if (!missingItemsChunks.isEmpty()) {
                                    for (int i = 0; i < missingItemsChunks.size(); i++) {
                                        byte[] originalChunk = missingItemsChunks.get(i);
                                        byte indexByte = (byte) i;
                                        byte totalMissingChunksByte = (byte) missingItemsChunks.size();
                                        byte[] chunkWithHeader = new byte[originalChunk.length + 2];
                                        chunkWithHeader[0] = totalMissingChunksByte;
                                        chunkWithHeader[1] = indexByte;
                                        System.arraycopy(originalChunk, 0, chunkWithHeader, 2, originalChunk.length);
                                        MeshtasticConnector.sendData(geeksvilleMeshService, chunkWithHeader, "REQUEST_UUID_DATA", syncingWithNodeId);
                                    }
                                    currentDataRequestRetries = 0;
                                    startDataRequestTimeout();
                                } else if (!itemsToRequestFromPeer.isEmpty()){
                                    Log.e(TAG, "ServiceReceiver: itemsToRequestFromPeer has " + itemsToRequestFromPeer.size() + " items but encoded to 0 chunks. Cannot request.");
                                    sendSyncStatusUpdate("error");
                                    resetSyncState();
                                } else {
                                    Log.i(TAG, "ServiceReceiver: No missing items to request after encoding. DBs should be in sync.");
                                    MeshtasticConnector.sendData(geeksvilleMeshService, new byte[0], "FINISH_SYNC", syncingWithNodeId);
                                    resetSyncState();
                                }
                            }
                        }
                        break;

                    case "REQUEST_UUID_DATA":
                        if (!isDBSyncing || syncingWithNodeId == null || !syncingWithNodeId.equals(fromNodeId)) {
                            Log.w(TAG, "ServiceReceiver: Received REQUEST_UUID_DATA but not in correct sync state or from unexpected sender: " + fromNodeId);
                            return;
                        }

                        byte totalRequestedDataChunks = receivedEncodedData[0];
                        byte currentRequestedDataChunkIndex = receivedEncodedData[1];
                        byte[] requestedDataChunkPayload = new byte[receivedEncodedData.length - 2];
                        System.arraycopy(receivedEncodedData, 2, requestedDataChunkPayload, 0, requestedDataChunkPayload.length);

                        if (currentRequestedDataChunkIndex == 0) {
                            currentlyAccumulatingDataRequestsFromPeer.clear();
                            expectedTotalChunksForDataRequestsFromPeer = totalRequestedDataChunks;
                            receivedChunkCountForDataRequestsFromPeer = 0;
                            Log.d(TAG, "ServiceReceiver: Starting to receive " + totalRequestedDataChunks + " chunks of data requests from " + syncingWithNodeId);
                            sendSyncStatusUpdate("syncing (0/" + (expectedTotalChunksForDataRequestsFromPeer-1) + ")");
                        }

                        List<UuidData> decodedRequestedChunkUuids = mapDataDbHelper.decodeSingleChunkToUuidList(requestedDataChunkPayload);
                        currentlyAccumulatingDataRequestsFromPeer.addAll(decodedRequestedChunkUuids);
                        receivedChunkCountForDataRequestsFromPeer++;
                        Log.d(TAG, "ServiceReceiver: Received data request chunk " + currentRequestedDataChunkIndex + "/" + (expectedTotalChunksForDataRequestsFromPeer-1) + ". Accum: " + currentlyAccumulatingDataRequestsFromPeer.size());
                        sendSyncStatusUpdate("syncing ("+ currentRequestedDataChunkIndex + "/" + (expectedTotalChunksForDataRequestsFromPeer-1) + ")");

                        if (receivedChunkCountForDataRequestsFromPeer == expectedTotalChunksForDataRequestsFromPeer) {
                            Log.i(TAG, "ServiceReceiver: All " + expectedTotalChunksForDataRequestsFromPeer + " data request chunks received from " + syncingWithNodeId);
                            sendSyncStatusUpdate("syncing " + currentlyAccumulatingDataRequestsFromPeer.size());

                            for (UuidData requestedItem : currentlyAccumulatingDataRequestsFromPeer) {
                                String uuidToSend = requestedItem.getUuid();
                                String type = mapDataDbHelper.getType(uuidToSend);
                                Log.d(TAG, "ServiceReceiver: Peer " + syncingWithNodeId + " requested data for UUID: " + uuidToSend + " (Type: " + type + ")");
                                if (type != null && mapDataDbHelper.objectExists(uuidToSend) && !mapDataDbHelper.isObjectDeleted(uuidToSend)) {
                                    switch (type) {
                                        case "PIN": PinInfo pin = mapDataDbHelper.getPin(uuidToSend); if (pin != null) MeshtasticConnector.sendData(geeksvilleMeshService, pin.encode(), "PIN", syncingWithNodeId); break;
                                        case "CIRCLE": CircleInfo circle = mapDataDbHelper.getCircle(uuidToSend); if (circle != null) MeshtasticConnector.sendData(geeksvilleMeshService, circle.encode(), "CIRCLE", syncingWithNodeId); break;
                                        case "LINE": LineInfo line = mapDataDbHelper.getLine(uuidToSend); if (line != null) MeshtasticConnector.sendData(geeksvilleMeshService, line.encode(), "LINE", syncingWithNodeId); break;
                                        case "POLYGON":
                                            PolygonInfo polygon = mapDataDbHelper.getPolygon(uuidToSend);
                                            if (polygon != null)
                                                MeshtasticConnector.sendData(geeksvilleMeshService, polygon.encode(), "POLY", syncingWithNodeId);
                                            break;
                                        case "PLAY_AREA":
                                            PolygonInfo playArea = mapDataDbHelper.getPolygon(uuidToSend);
                                            if (playArea != null)
                                                MeshtasticConnector.sendData(geeksvilleMeshService, playArea.encode(), "PLAY_AREA", syncingWithNodeId);
                                            break;
                                        default:
                                            Log.w(TAG, "ServiceReceiver: Unknown type '" + type + "' for UUID " + uuidToSend + " to send.");
                                            break;
                                    }
                                } else {
                                    Log.w(TAG, "ServiceReceiver: Requested UUID " + uuidToSend + " not found or is already (soft)deleted locally, not sending.");
                                }
                            }
                            currentlyAccumulatingDataRequestsFromPeer.clear();
                            Log.i(TAG, "ServiceReceiver: Finished sending all requested data items to " + syncingWithNodeId);
                        }
                        break;

                    case "FINISH_SYNC":
                        Log.i(TAG, "ServiceReceiver: Received FINISH_SYNC from " + fromNodeId);
                        if (isDBSyncing && syncingWithNodeId != null && syncingWithNodeId.equals(fromNodeId)) {
                            Log.i(TAG, "ServiceReceiver: Sync process with " + syncingWithNodeId + " is now considered complete by both sides.");
                            resetSyncState();
                            dataChanged = true;
                        } else {
                            Log.i(TAG, "ServiceReceiver: Received FINISH_SYNC from " + fromNodeId + " but not actively syncing with them or not syncing at all. State remains.");
                        }
                        break;

                    case "PIN":
                    case "LINE":
                    case "POLY":
                    case "PLAY_AREA":
                    case "CIRCLE":
                    case "SEND_UUID_DATA":
                        String actualItemUuid = null;
                        String actualItemType = receivedDataTypeName;
                        byte[] actualItemData = receivedEncodedData;

                        if ("SEND_UUID_DATA".equals(receivedDataTypeName)) {
                            actualItemType = MeshtasticConnector.receivedDataTypeName(receivedEncodedData);
                            actualItemData = MeshtasticConnector.cleanReceivedData(receivedEncodedData);
                            Log.d(TAG, "ServiceReceiver: SEND_UUID_DATA contained inner type: " + actualItemType);
                        }

                        boolean itemAdded = false;
                        switch (actualItemType) {
                            case "PIN":
                                PinInfo pin = new PinInfo();
                                pin.decode(actualItemData);
                                actualItemUuid = pin.getUniqueId();
                                if (!mapDataDbHelper.objectExists(actualItemUuid)) {
                                    mapDataDbHelper.addPin(pin);
                                    Log.i(TAG, "ServiceReceiver: (Inner) PIN " + actualItemUuid + " ADDED NEW from " + fromNodeId);
                                    itemAdded = true;
                                } else {
                                    Log.i(TAG, "ServiceReceiver: (Inner) PIN " + actualItemUuid + " from " + fromNodeId + " already exists. IGNORED.");
                                }
                                break;
                            case "LINE":
                                LineInfo line = new LineInfo();
                                line.decode(actualItemData);
                                actualItemUuid = line.getUniqueId();
                                if (!mapDataDbHelper.objectExists(actualItemUuid)) {
                                    mapDataDbHelper.addLine(line);
                                    Log.i(TAG, "ServiceReceiver: (Inner) LINE " + actualItemUuid + " ADDED NEW from " + fromNodeId);
                                    itemAdded = true;
                                } else {
                                    Log.i(TAG, "ServiceReceiver: (Inner) LINE " + actualItemUuid + " from " + fromNodeId + " already exists. IGNORED.");
                                }
                                break;
                            case "POLY":
                                PolygonInfo poly = new PolygonInfo();
                                poly.decode(actualItemData);
                                actualItemUuid = poly.getUniqueId();
                                if (!mapDataDbHelper.objectExists(actualItemUuid)) {
                                    mapDataDbHelper.addPolygon(poly);
                                    Log.i(TAG, "ServiceReceiver: (Inner) POLYGON " + actualItemUuid + " ADDED NEW from " + fromNodeId);
                                    itemAdded = true;
                                } else {
                                    Log.i(TAG, "ServiceReceiver: (Inner) POLYGON " + actualItemUuid + " from " + fromNodeId + " already exists. IGNORED.");
                                }
                                break;
                            case "PLAY_AREA":
                                PolygonInfo playAreaPoly = new PolygonInfo();
                                playAreaPoly.decode(actualItemData);
                                actualItemUuid = playAreaPoly.getUniqueId();
                                mapDataDbHelper.removeAllPlayAreasExcept(actualItemUuid);
                                if (!mapDataDbHelper.objectExists(actualItemUuid)) {
                                    mapDataDbHelper.addPolygon(playAreaPoly);
                                    Log.i(TAG, "ServiceReceiver: (Inner) PLAY_AREA " + actualItemUuid + " ADDED NEW from " + fromNodeId);
                                    itemAdded = true;
                                } else {
                                    Log.i(TAG, "ServiceReceiver: (Inner) PLAY_AREA " + actualItemUuid + " from " + fromNodeId + " already exists. IGNORED.");
                                }
                                break;
                            case "CIRCLE":
                                CircleInfo circle = new CircleInfo();
                                circle.decode(actualItemData);
                                actualItemUuid = circle.getUniqueId();
                                if (!mapDataDbHelper.objectExists(actualItemUuid)) {
                                    mapDataDbHelper.addCircle(circle);
                                    Log.i(TAG, "ServiceReceiver: (Inner) CIRCLE " + actualItemUuid + " ADDED NEW from " + fromNodeId);
                                    itemAdded = true;
                                } else {
                                    Log.i(TAG, "ServiceReceiver: (Inner) CIRCLE " + actualItemUuid + " from " + fromNodeId + " already exists. IGNORED.");
                                }
                                break;
                            default:
                                if (!"SEND_UUID_DATA".equals(receivedDataTypeName)) {
                                    Log.w(TAG, "ServiceReceiver: Unhandled direct item type: " + actualItemType);
                                }
                                break;
                        }
                        if (itemAdded) {
                            dataChanged = true;
                        }
                        if (actualItemUuid != null && isDBSyncing && syncingWithNodeId != null && syncingWithNodeId.equals(fromNodeId)) {
                            if (checkAndRemoveFromItemsToRequest(actualItemUuid)) {
                                if (itemsToRequestFromPeer.isEmpty()) completeDataReceptionPhase();
                            }
                        }
                        break;

                    case "DELETE":
                        String uuidToDeleteCtrl = MeshtasticConnector.decodeUUID(receivedEncodedData);
                        if (mapDataDbHelper.objectExists(uuidToDeleteCtrl) && !mapDataDbHelper.isObjectDeleted(uuidToDeleteCtrl)) {
                            mapDataDbHelper.softDeleteMapObject(uuidToDeleteCtrl);
                            dataChanged = true;
                            Log.i(TAG, "ServiceReceiver: DELETE for " + uuidToDeleteCtrl + " (was active) processed from " + fromNodeId);
                        } else {
                            Log.i(TAG, "ServiceReceiver: Received DELETE for " + uuidToDeleteCtrl + " from " + fromNodeId + " but object not found or already deleted. No change.");
                        }
                        if (isDBSyncing && syncingWithNodeId != null && syncingWithNodeId.equals(fromNodeId)) {
                            if (checkAndRemoveFromItemsToRequest(uuidToDeleteCtrl)) {
                                if (itemsToRequestFromPeer.isEmpty()) completeDataReceptionPhase();
                            }
                        }
                        break;


                    default:
                        Log.w(TAG, "ServiceReceiver: Unknown or unhandled data type received at outer level: " + receivedDataTypeName);
                }

                if (dataChanged) {
                    sendMapDataRefreshBroadcast();
                    triggerWearMapSyncInternal();
                }
            }
        }
    };

    private void initiateSyncProcess(String targetNodeId) {
        cancelDataRequestTimeout();
        isDBSyncing = true;
        syncingWithNodeId = targetNodeId;
        syncProcessStartTime = System.currentTimeMillis();
        currentDataRequestRetries = 0;
        itemsToRequestFromPeer.clear();
        fullPeerUuidList.clear();
        currentlyAccumulatingPeerUuidList.clear(); expectedTotalChunksForPeerUuidList = 0; receivedChunkCountForPeerUuidList = 0;
        sendSyncStatusUpdate("syncing");
        MeshtasticConnector.sendData(geeksvilleMeshService, new byte[0], "REQUEST_UUID_LIST", syncingWithNodeId);
    }

    private void completeDataReceptionPhase() {
        Log.i(TAG, "ServiceReceiver: All requested items received from " + (syncingWithNodeId != null ? formatNodeId(syncingWithNodeId) : "peer") + " or otherwise resolved. Sync complete.");
        cancelDataRequestTimeout();
        if(syncingWithNodeId != null && geeksvilleMeshService != null && isGeeksvilleMeshServiceBound) {
            MeshtasticConnector.sendData(geeksvilleMeshService, new byte[0], "FINISH_SYNC", syncingWithNodeId);
        }
        resetSyncState();
        sendMapDataRefreshBroadcast();
        triggerWearMapSyncInternal();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MeshReceiverService onCreate");
        mapDataDbHelper = new MapDataDatabaseHelper(this);
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FieldMesh Sync")
                .setContentText("Listening for mesh data and tracking location.")
                .setSmallIcon(R.drawable.radio)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1, notification);

        IntentFilter filter = new IntentFilter("com.geeksville.mesh.RECEIVED.300");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(meshtasticPacketReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(meshtasticPacketReceiver, filter);
        }

        dataRequestTimeoutHandler = new Handler(Looper.getMainLooper());
        periodicDBHashUpdateHandler = new Handler(Looper.getMainLooper());
        periodicNodeLocationUpdateHandler = new Handler(Looper.getMainLooper());

        bindToGeeksvilleMeshService();

        messageClientToWear = Wearable.getMessageClient(this);
        messageClientToWear.addListener(this);
        findWearableNode();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();
        startInternalLocationUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MeshReceiverService onStartCommand, Action: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_FORWARD_PHONE_LOCATION.equals(action)) {
                Location location = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    location = intent.getParcelableExtra(EXTRA_LOCATION, Location.class);
                } else {
                    location = intent.getParcelableExtra(EXTRA_LOCATION);
                }
                if (location != null) {
                    sendLocationToWearInternal(location, this.wearNodeId);
                } else {
                    Log.w(TAG, "Received ACTION_FORWARD_PHONE_LOCATION but location extra was null.");
                }
            } else if (ACTION_TRIGGER_WEAR_MAP_SYNC.equals(action)) {
                Log.i(TAG, "Received ACTION_TRIGGER_WEAR_MAP_SYNC from MainActivity.");
                triggerWearMapSyncInternal();
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "FieldMesh Sync Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Channel for FieldMesh background data sync and location tracking.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startPeriodicDBHashUpdate() {
        if (dbHashUpdateRunnable == null) {
            dbHashUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isGeeksvilleMeshServiceBound && geeksvilleMeshService != null && !isDBSyncing) {
                        try {
                            String dbHash = mapDataDbHelper.getDatabaseHash();
                            long dbSize = mapDataDbHelper.getDatabaseFileSize(getApplicationContext());
                            Log.d(TAG, "Service: Broadcasting DB Hash: " + dbHash + " (Size: " + dbSize + ")");
                            MeshtasticConnector.sendDBHash(geeksvilleMeshService, dbHash, dbSize, dbSize);
                        } catch (Exception e) {
                            Log.e(TAG, "Service: Error sending DB Hash", e);
                        }
                    }
                    if (periodicDBHashUpdateHandler != null) {
                        periodicDBHashUpdateHandler.postDelayed(this, DB_HASH_SEND_INTERVAL_MS);
                    }
                }
            };
        }
        periodicDBHashUpdateHandler.removeCallbacks(dbHashUpdateRunnable);
        periodicDBHashUpdateHandler.post(dbHashUpdateRunnable);
        Log.d(TAG, "Service: Started periodic DB Hash update.");
    }

    private void stopPeriodicDBHashUpdate() {
        if (periodicDBHashUpdateHandler != null && dbHashUpdateRunnable != null) {
            periodicDBHashUpdateHandler.removeCallbacks(dbHashUpdateRunnable);
            Log.d(TAG, "Service: Stopped periodic DB Hash update.");
        }
    }

    private void startPeriodicNodeLocationUpdate() {
        if (nodeLocationUpdateRunnable == null) {
            nodeLocationUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isGeeksvilleMeshServiceBound && geeksvilleMeshService != null && wearNodeId != null) {
                        fetchAndSendNodeLocationsToWear();
                    } else {
                        if (!isGeeksvilleMeshServiceBound || geeksvilleMeshService == null) {
                            Log.w(TAG, "NodeLocationUpdate: Geeksville service not bound. Skipping update.");
                        }
                        if (wearNodeId == null) {
                            Log.w(TAG, "NodeLocationUpdate: Wear node ID is null. Skipping update, attempting to find node.");
                            findWearableNode();
                        }
                    }
                    if (periodicNodeLocationUpdateHandler != null) {
                        periodicNodeLocationUpdateHandler.postDelayed(this, NODE_LOCATION_UPDATE_INTERVAL_MS);
                    }
                }
            };
        }
        periodicNodeLocationUpdateHandler.removeCallbacks(nodeLocationUpdateRunnable);
        periodicNodeLocationUpdateHandler.post(nodeLocationUpdateRunnable);
        Log.d(TAG, "Service: Started periodic Node Location update for Wear.");
    }

    private void stopPeriodicNodeLocationUpdate() {
        if (periodicNodeLocationUpdateHandler != null && nodeLocationUpdateRunnable != null) {
            periodicNodeLocationUpdateHandler.removeCallbacks(nodeLocationUpdateRunnable);
            Log.d(TAG, "Service: Stopped periodic Node Location update for Wear.");
        }
    }


    private void fetchAndSendNodeLocationsToWear() {
        try {
            if (geeksvilleMeshService == null || !isGeeksvilleMeshServiceBound) {
                Log.w(TAG, "fetchAndSendNodeLocationsToWear: Geeksville service not available.");
                return;
            }
            if (wearNodeId == null) {
                Log.w(TAG, "fetchAndSendNodeLocationsToWear: Wear node ID is null.");
                findWearableNode();
                return;
            }

            List<NodeInfo> nodes = geeksvilleMeshService.getNodes();
            if (nodes == null || nodes.isEmpty()) {
                Log.d(TAG, "fetchAndSendNodeLocationsToWear: No nodes found or list is null.");
                messageClientToWear.sendMessage(wearNodeId, PATH_NODE_LOCATIONS_UPDATE_TO_WEAR, new JSONArray().toString().getBytes())
                        .addOnSuccessListener(result -> Log.d(TAG, "Service: Empty node locations list sent to Wear."))
                        .addOnFailureListener(e -> Log.w(TAG, "Service: Failed to send empty node locations list to Wear.", e));
                return;
            }

            String myNodeId = geeksvilleMeshService.getMyId();
            JSONArray nodesJsonArray = new JSONArray();
            long currentTimeSeconds = System.currentTimeMillis() / 1000L;
            long fiveMinutesInSeconds = TimeUnit.MINUTES.toSeconds(5);

            for (NodeInfo node : nodes) {
                if (node == null || node.getUser() == null || node.getPosition() == null ||
                        Objects.equals(myNodeId, node.getUser().getId())) {
                    continue;
                }

                JSONObject nodeJson = new JSONObject();
                nodeJson.put("id", node.getUser().getId());
                nodeJson.put("shortName", node.getUser().getShortName());
                nodeJson.put("longName", node.getUser().getLongName());
                nodeJson.put("lat", node.getPosition().getLatitude());
                nodeJson.put("lon", node.getPosition().getLongitude());
                long nodePositionTimeSeconds = node.getPosition().getTime();
                nodeJson.put("positionTime", nodePositionTimeSeconds);
                nodeJson.put("isOld", (currentTimeSeconds - nodePositionTimeSeconds) > fiveMinutesInSeconds);

                nodesJsonArray.put(nodeJson);
            }

            if (nodesJsonArray.length() > 0) {
                String jsonDataString = nodesJsonArray.toString();
                Log.d(TAG, "Service: Sending " + nodesJsonArray.length() + " node locations to Wear: " + wearNodeId);
                messageClientToWear.sendMessage(wearNodeId, PATH_NODE_LOCATIONS_UPDATE_TO_WEAR, jsonDataString.getBytes())
                        .addOnSuccessListener(result -> Log.d(TAG, "Service: Node locations sent to Wear successfully."))
                        .addOnFailureListener(e -> Log.e(TAG, "Service: Failed to send node locations to Wear.", e));
            } else {
                Log.d(TAG, "fetchAndSendNodeLocationsToWear: No eligible nodes to send after filtering.");
                messageClientToWear.sendMessage(wearNodeId, PATH_NODE_LOCATIONS_UPDATE_TO_WEAR, new JSONArray().toString().getBytes())
                        .addOnSuccessListener(result -> Log.d(TAG, "Service: Empty node locations list (post-filter) sent to Wear."))
                        .addOnFailureListener(e -> Log.w(TAG, "Service: Failed to send empty node locations list (post-filter) to Wear.", e));
            }

        } catch (RemoteException e) {
            Log.e(TAG, "fetchAndSendNodeLocationsToWear: RemoteException: " + e.toString());
        } catch (JSONException e) {
            Log.e(TAG, "fetchAndSendNodeLocationsToWear: JSONException: " + e.toString());
        } catch (Exception e) {
            Log.e(TAG, "fetchAndSendNodeLocationsToWear: Exception: " + e.toString(), e);
        }
    }


    private void resetSyncState() {
        Log.i(TAG, "Service: Resetting DB Sync state. Was syncing with: " + syncingWithNodeId);
        sendSyncStatusUpdate("synced");
        isDBSyncing = false;
        syncingWithNodeId = null;
        syncProcessStartTime = 0;
        fullPeerUuidList.clear();
        itemsToRequestFromPeer.clear();
        currentlyAccumulatingPeerUuidList.clear(); expectedTotalChunksForPeerUuidList = 0; receivedChunkCountForPeerUuidList = 0;
        currentlyAccumulatingDataRequestsFromPeer.clear(); expectedTotalChunksForDataRequestsFromPeer = 0; receivedChunkCountForDataRequestsFromPeer = 0;
        cancelDataRequestTimeout();
        currentDataRequestRetries = 0;
    }

    private boolean checkAndRemoveFromItemsToRequest(String uuid) {
        boolean removed = itemsToRequestFromPeer.removeIf(item -> item.getUuid().equals(uuid));
        if (removed) {
            Log.d(TAG, "Service: Removed " + uuid + " from itemsToRequestFromPeer. Remaining: " + itemsToRequestFromPeer.size());
        }
        return removed;
    }

    private void startDataRequestTimeout() {
        if (dataRequestTimeoutHandler == null) dataRequestTimeoutHandler = new Handler(Looper.getMainLooper());
        if (dataRequestTimeoutRunnable != null) dataRequestTimeoutHandler.removeCallbacks(dataRequestTimeoutRunnable);

        Log.d(TAG, "Service: Starting data request timeout (" + DATA_REQUEST_TIMEOUT_MS + "ms) for " + itemsToRequestFromPeer.size() + " items from " + syncingWithNodeId);
        sendSyncStatusUpdate("syncing");

        dataRequestTimeoutRunnable = () -> {
            if (isDBSyncing && syncingWithNodeId != null && !itemsToRequestFromPeer.isEmpty()) {
                if (currentDataRequestRetries < MAX_DATA_REQUEST_RETRIES) {
                    currentDataRequestRetries++;
                    Log.w(TAG, "Service: TIMEOUT for data from " + syncingWithNodeId + ". Missing: " + itemsToRequestFromPeer.size() + ". Re-requesting (Attempt " + currentDataRequestRetries + ").");
                    sendSyncStatusUpdate("retry" + " (" + currentDataRequestRetries + ")");
                    List<byte[]> missingChunks = mapDataDbHelper.encodeUuidListToChunkedByteArrays(itemsToRequestFromPeer);
                    if (!missingChunks.isEmpty()) {
                        for (int i = 0; i < missingChunks.size(); i++) {
                            byte[] oChunk = missingChunks.get(i); byte iByte = (byte)i; byte tByte = (byte)missingChunks.size();
                            byte[] headerChunk = new byte[oChunk.length + 2]; headerChunk[0] = tByte; headerChunk[1] = iByte;
                            System.arraycopy(oChunk, 0, headerChunk, 2, oChunk.length);
                            MeshtasticConnector.sendData(geeksvilleMeshService, headerChunk, "REQUEST_UUID_DATA", syncingWithNodeId);
                        }
                        dataRequestTimeoutHandler.postDelayed(dataRequestTimeoutRunnable, DATA_REQUEST_TIMEOUT_MS);
                    } else if(!itemsToRequestFromPeer.isEmpty()) {
                        Log.e(TAG, "Service: Timeout - itemsToRequestFromPeer not empty but 0 chunks to request.");
                        sendSyncStatusUpdate("retry (error)");
                        resetSyncState();
                    } else {
                        Log.d(TAG, "Service: Timeout runnable - itemsToRequestFromPeer is now empty.");
                        completeDataReceptionPhase();
                    }
                } else {
                    Log.e(TAG, "Service: Max retries for data from " + syncingWithNodeId + ". Aborting sync. Missing: " + itemsToRequestFromPeer.size());
                    sendSyncStatusUpdate("failed");
                    resetSyncState();
                }
            } else {
                Log.d(TAG, "Service: Data request timeout runnable executed, but conditions not met for re-request.");
            }
        };
        dataRequestTimeoutHandler.postDelayed(dataRequestTimeoutRunnable, DATA_REQUEST_TIMEOUT_MS);
    }

    private void cancelDataRequestTimeout() {
        if (dataRequestTimeoutHandler != null && dataRequestTimeoutRunnable != null) {
            dataRequestTimeoutHandler.removeCallbacks(dataRequestTimeoutRunnable);
            Log.d(TAG, "Service: Cancelled data request timeout.");
        }
    }

    private void sendMapDataRefreshBroadcast() {
        Intent intent = new Intent(ACTION_MAP_DATA_REFRESH);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Service: Sent ACTION_MAP_DATA_REFRESH broadcast.");
    }
    private void sendSyncStatusUpdate(String message) {
        Intent intent = new Intent(ACTION_SYNC_STATUS_UPDATE);
        intent.putExtra(EXTRA_SYNC_STATUS_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Service: Sent ACTION_SYNC_STATUS_UPDATE: " + message);
    }

    private String formatNodeId(String nodeId) {
        if (nodeId == null) return "Unknown";
        return nodeId.length() > 8 ? "!" + nodeId.substring(nodeId.length() - 4) : nodeId;
    }

    private void findWearableNode() {
        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
            if (nodes != null && !nodes.isEmpty()) {
                this.wearNodeId = nodes.get(0).getId();
                Log.i(TAG, "Service: Found connected Wear OS node: " + this.wearNodeId + " (" + nodes.get(0).getDisplayName() + ")");
                triggerWearMapSyncInternal();
            } else {
                Log.w(TAG, "Service: No Wear OS nodes connected.");
                this.wearNodeId = null;
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Service: Failed to get connected Wear OS nodes.", e);
            this.wearNodeId = null;
        });
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.i(TAG, "Service: Message received from Wear: " + messageEvent.getSourceNodeId() + ", Path: " + messageEvent.getPath());
        if (PATH_REQUEST_FULL_SYNC_FROM_WEAR.equals(messageEvent.getPath())) {
            Log.i(TAG, "Service: Wear device requested full map data sync.");
            this.wearNodeId = messageEvent.getSourceNodeId();
            sendAllMapDataToWearInternal(this.wearNodeId);
            if (isGeeksvilleMeshServiceBound && geeksvilleMeshService != null) {
                fetchAndSendNodeLocationsToWear();
            }
        }
    }

    private void triggerWearMapSyncInternal() {
        if (this.wearNodeId != null) {
            Log.d(TAG, "Service: Triggering map data sync to Wear node: " + this.wearNodeId);
            sendAllMapDataToWearInternal(this.wearNodeId);
            if (isGeeksvilleMeshServiceBound && geeksvilleMeshService != null) {
                fetchAndSendNodeLocationsToWear();
            }
        } else {
            Log.w(TAG, "Service: Cannot trigger Wear sync, wearNodeId is null. Attempting to find node.");
            findWearableNode();
        }
    }

    private void sendAllMapDataToWearInternal(String nodeId) {
        if (nodeId == null) {
            Log.w(TAG, "Service: Cannot send map data to Wear, node ID is null. Attempting to find node if not already trying.");
            if (this.wearNodeId == null) findWearableNode();
            return;
        }
        if (mapDataDbHelper == null) {
            Log.e(TAG, "Service: mapDataDbHelper is null, cannot send data to Wear.");
            return;
        }
        if (messageClientToWear == null) {
            Log.e(TAG, "Service: messageClientToWear is null, cannot send data to Wear.");
            return;
        }

        Log.i(TAG, "Service: Preparing to send all map data to Wear node: " + nodeId);
        JSONObject batchData = new JSONObject();
        try {
            JSONArray pinsJsonArray = new JSONArray();
            List<PinInfo> allPins = mapDataDbHelper.getAllPins("Global");
            for (PinInfo pin : allPins) {
                JSONObject pinJson = new JSONObject();
                pinJson.put("uuid", pin.getUniqueId());
                pinJson.put("lat", pin.getLatitude());
                pinJson.put("lon", pin.getLongitude());
                pinJson.put("color", ContextCompat.getColor(this, ColorIndex.getColorByIndex(pin.getColor())));
                pinJson.put("label", pin.getLabel());
                pinJson.put("iconIndex", pin.getIconResourceId());
                pinsJsonArray.put(pinJson);
            }
            batchData.put("pins", pinsJsonArray);

            JSONArray linesJsonArray = new JSONArray();
            List<LineInfo> allLines = mapDataDbHelper.getAllLines("Global");
            for (LineInfo line : allLines) {
                JSONObject lineJson = new JSONObject();
                lineJson.put("uuid", line.getUniqueId());
                JSONArray pointsArray = new JSONArray();
                if (line.getPoints() != null) {
                    for (GeoPoint gp : line.getPoints()) {
                        JSONObject pointJson = new JSONObject();
                        pointJson.put("lat", gp.getLatitude());
                        pointJson.put("lon", gp.getLongitude());
                        pointsArray.put(pointJson);
                    }
                }
                lineJson.put("points", pointsArray);
                lineJson.put("color", ContextCompat.getColor(this, ColorIndex.getColorByIndex(line.getColor())));
                linesJsonArray.put(lineJson);
            }
            batchData.put("lines", linesJsonArray);

            JSONArray polygonsJsonArray = new JSONArray();
            List<PolygonInfo> allPolygons = mapDataDbHelper.getAllPolygons("Global");
            for (PolygonInfo poly : allPolygons) {
                JSONObject polyJson = new JSONObject();
                polyJson.put("uuid", poly.getUniqueId());
                JSONArray pointsArray = new JSONArray();
                if (poly.getPoints() != null) {
                    for (GeoPoint gp : poly.getPoints()) {
                        JSONObject pointJson = new JSONObject();
                        pointJson.put("lat", gp.getLatitude());
                        pointJson.put("lon", gp.getLongitude());
                        pointsArray.put(pointJson);
                    }
                }
                polyJson.put("points", pointsArray);
                int baseColorPoly = ContextCompat.getColor(this, ColorIndex.getColorByIndex(poly.getColor()));
                polyJson.put("strokeColor", baseColorPoly);
                polyJson.put("fillColor", Color.argb(100, Color.red(baseColorPoly), Color.green(baseColorPoly), Color.blue(baseColorPoly)));
                polygonsJsonArray.put(polyJson);
            }
            batchData.put("polygons", polygonsJsonArray);

            JSONArray circlesJsonArray = new JSONArray();
            List<CircleInfo> allCircles = mapDataDbHelper.getAllCircles("Global");
            for (CircleInfo circle : allCircles) {
                JSONObject circleJson = new JSONObject();
                circleJson.put("uuid", circle.getUniqueId());
                circleJson.put("lat", circle.getLatitude());
                circleJson.put("lon", circle.getLongitude());
                circleJson.put("radius", circle.getRadius());
                int baseColorCircle = ContextCompat.getColor(this, ColorIndex.getColorByIndex(circle.getColor()));
                circleJson.put("strokeColor", baseColorCircle);
                circleJson.put("fillColor", Color.argb(80, Color.red(baseColorCircle), Color.green(baseColorCircle), Color.blue(baseColorCircle)));
                circlesJsonArray.put(circleJson);
            }
            batchData.put("circles", circlesJsonArray);

            String jsonDataString = batchData.toString();
            Log.d(TAG, "Service: Sending JSON to Wear (" + nodeId + "): " + jsonDataString.substring(0, Math.min(jsonDataString.length(), 300)) + "...");
            messageClientToWear.sendMessage(nodeId, PATH_MAP_DATA_BATCH_TO_WEAR, jsonDataString.getBytes())
                    .addOnSuccessListener(result -> Log.i(TAG, "Service: Map data batch sent to Wear successfully."))
                    .addOnFailureListener(e -> Log.e(TAG, "Service: Failed to send map data batch to Wear.", e));

        } catch (JSONException e) {
            Log.e(TAG, "Service: Error creating JSON for Wear data.", e);
        } catch (Exception e) {
            Log.e(TAG, "Service: Unexpected error preparing data for Wear.", e);
        }
    }

    private void sendLocationToWearInternal(Location location, String nodeId) {
        if (nodeId == null) {
            Log.w(TAG, "Service: Cannot send location to Wear, node ID is null. Attempting to find node if not already trying.");
            if (this.wearNodeId == null) findWearableNode();
            return;
        }
        if (messageClientToWear == null) {
            Log.e(TAG, "Service: messageClientToWear is null, cannot send location to Wear.");
            return;
        }
        if (location == null) {
            Log.w(TAG, "Service: Location to send is null.");
            return;
        }

        JSONObject locationJson = new JSONObject();
        try {
            locationJson.put("lat", location.getLatitude());
            locationJson.put("lon", location.getLongitude());
            if (location.hasBearing()) {
                locationJson.put("bearing", location.getBearing());
            }
            if (location.hasAccuracy()) {
                locationJson.put("accuracy", location.getAccuracy());
            }
            byte[] locationDataBytes = locationJson.toString().getBytes();
            messageClientToWear.sendMessage(nodeId, PATH_PHONE_LOCATION_UPDATE_TO_WEAR, locationDataBytes)
                    .addOnSuccessListener(result -> Log.d(TAG, "Service: Location update sent to Wear: " + locationJson.toString()))
                    .addOnFailureListener(e -> Log.w(TAG, "Service: Failed to send location update to Wear.", e));
        } catch (JSONException e) {
            Log.e(TAG, "Service: Error creating JSON for location update.", e);
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL_MS)
                .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location lastLocation = locationResult.getLastLocation();
                if (lastLocation != null) {
                    Log.d(TAG, "Service: New phone location received: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                    if (wearNodeId != null) {
                        sendLocationToWearInternal(lastLocation, wearNodeId);
                    } else {
                        Log.w(TAG, "Service: New phone location received, but no Wear node to send to. Attempting to find node.");
                        findWearableNode();
                    }
                }
            }
        };
    }

    private void startInternalLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Service: Location permission not granted. Cannot start phone location updates.");
            return;
        }
        if (fusedLocationClient != null && locationRequest != null && locationCallback != null) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                Log.i(TAG, "Service: Requested internal phone location updates.");
            } catch (SecurityException e) {
                Log.e(TAG, "Service: SecurityException while requesting phone location updates.", e);
            }
        } else {
            Log.e(TAG, "Service: FusedLocationClient, LocationRequest, or LocationCallback not initialized for phone location. Cannot start updates.");
        }
    }

    private void stopInternalLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.i(TAG, "Service: Stopped internal phone location updates.");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "MeshReceiverService onDestroy");
        stopInternalLocationUpdates();
        stopPeriodicNodeLocationUpdate();
        try {
            unregisterReceiver(meshtasticPacketReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "meshtasticPacketReceiver not registered or already unregistered.");
        }
        unbindFromGeeksvilleMeshService();
        stopPeriodicDBHashUpdate();
        cancelDataRequestTimeout();

        if (messageClientToWear != null) {
            messageClientToWear.removeListener(this);
        }

        if (mapDataDbHelper != null) {
            mapDataDbHelper.close();
            mapDataDbHelper = null;
        }
        Log.i(TAG, "MeshReceiverService destroyed.");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
