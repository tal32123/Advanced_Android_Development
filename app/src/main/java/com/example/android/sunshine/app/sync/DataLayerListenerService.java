package com.example.android.sunshine.app.sync;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Created by Tal on 11/24/2016.
 * Credit to https://developer.android.com/training/wearables/data-layer/events.html#Listen
 */
public class DataLayerListenerService extends WearableListenerService {

    private static final String LOG_TAG = "DataListener";
    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";
    GoogleApiClient mGoogleApiClient;

    public DataLayerListenerService(){
        Log.d(LOG_TAG, "DataLayerListenerService Initialized");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "Wearable Listener Connected");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals("/path/update")){
            Log.d(LOG_TAG, "Message received. Syncing now." +  messageEvent.getData().toString());
            SunshineSyncAdapter.syncImmediately(this);
        }
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);
        Log.d(LOG_TAG, "capability changed");
    }

    @Override
    public void onConnectedNodes(List<Node> connectedNodes) {
        if (!connectedNodes.isEmpty()) {
            Log.d(LOG_TAG, "syncing now");
            SunshineSyncAdapter.syncImmediately(this);
        }
    }

    @Override
    public void onPeerConnected(Node node) {
        super.onPeerConnected(node);
        Log.d(LOG_TAG, "sync through onpeerconnected");
        SunshineSyncAdapter.syncImmediately(this);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onDataChanged: " + dataEvents);
        }

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(LOG_TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : dataEvents) {
            Uri uri = event.getDataItem().getUri();

            // Get the node id from the host value of the URI
            String nodeId = uri.getHost();

            // Send the RPC

            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                Log.d(LOG_TAG, "path = " + path);
                if (path.equals("/path/update")) {
                    SunshineSyncAdapter.syncImmediately(this);
                }
            }

        }
    }

}