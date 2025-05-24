package io.github.meshtactic;

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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.github.meshtactic.ui.UuidData;
import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MeshReceiverService extends Service {
    private static final String TAG = "MeshReceiverService";
    public static final String ACTION_MAP_DATA_REFRESH = "com.clustra.meshtactic.ACTION_MAP_DATA_REFRESH";
    public static final String ACTION_SYNC_STATUS_UPDATE = "com.clustra.meshtactic.ACTION_SYNC_STATUS_UPDATE";
    public static final String EXTRA_SYNC_STATUS_MESSAGE = "com.clustra.meshtactic.EXTRA_SYNC_STATUS_MESSAGE";

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


    private final ServiceConnection geeksvilleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Geeksville MeshService connected to MeshReceiverService");
            geeksvilleMeshService = IMeshService.Stub.asInterface(service);
            isGeeksvilleMeshServiceBound = true;
            startPeriodicDBHashUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Geeksville MeshService disconnected from MeshReceiverService");
            geeksvilleMeshService = null;
            isGeeksvilleMeshServiceBound = false;
            stopPeriodicDBHashUpdate();
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
                                        case "POLYGON": PolygonInfo polygon = mapDataDbHelper.getPolygon(uuidToSend); if (polygon != null) MeshtasticConnector.sendData(geeksvilleMeshService, polygon.encode(), "POLY", syncingWithNodeId); break;
                                        default: Log.w(TAG, "ServiceReceiver: Unknown type '" + type + "' for UUID " + uuidToSend + " to send."); break;
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
        currentlyAccumulatingPeerUuidList.clear();
        expectedTotalChunksForPeerUuidList = 0;
        receivedChunkCountForPeerUuidList = 0;
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
                .setContentTitle("MeshTactic Sync")
                .setContentText("Listening for mesh data.")
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
        bindToGeeksvilleMeshService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MeshReceiverService onStartCommand");
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Meshtactic Sync Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Channel for Meshtactic background data sync.");
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


    @Override
    public void onDestroy() {
        Log.d(TAG, "MeshReceiverService onDestroy");
        try {
            unregisterReceiver(meshtasticPacketReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "meshtasticPacketReceiver not registered or already unregistered.");
        }
        unbindFromGeeksvilleMeshService();
        stopPeriodicDBHashUpdate();
        cancelDataRequestTimeout();

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
