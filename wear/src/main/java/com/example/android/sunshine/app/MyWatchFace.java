/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService{

    private static final String LOG_TAG = MyWatchFace.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }




    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Bitmap mIcon;
        Paint mIconPaint;
        Paint mTextPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        boolean mAmbient;
        Calendar mCalendar;
        float highTempYOffset;
        float lowTempYOffset;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };


        float mXOffset;
        float mYOffset;

        String mLowTemp;
        String mHighTemp;
        Long mTime;
        String makeWeatherUnique;

        private boolean mResolvingError;

        GoogleApiClient mGoogleApiClient;

        DataApi.DataListener dataListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEventBuffer) {
                Log.d(LOG_TAG, "Data Changed");
//                this number is removed from the weather data. In the following methods. It was added in order to make data
//                unique so that it is sent by Wearable Api

                for (DataEvent event : dataEventBuffer) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        DataItem item = event.getDataItem();
                        Log.d(LOG_TAG, "onDataChanged path = " + item.getUri().getPath());
                        if (item.getUri().getPath().equals("/watchface-temp-update")) {

                            DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                            if(dataMap.containsKey("makeWeatherUnique")) {
                                Log.d(LOG_TAG, "makeWeatherUnique = " + makeWeatherUnique);
                                makeWeatherUnique = dataMap.getInt("makeWeatherUnique") + "";
                                Log.d(LOG_TAG, "makeWeatherUnique = " + makeWeatherUnique);
                            }

                            if(dataMap.containsKey("high-temp")) {
                                String tempTemp = dataMap.getString("high-temp");
                                Log.d(LOG_TAG, "tempTemp 1 = " + tempTemp);
                                if (tempTemp != null) {
                                    mHighTemp = tempTemp;
                                    mHighTemp = mHighTemp.replace((makeWeatherUnique), "");
                                    Log.d(LOG_TAG, "mHighTemp = " + mHighTemp);
                                }
                            }
                            if(dataMap.containsKey("low-temp")) {
                                mLowTemp = dataMap.getString("low-temp");
                                mLowTemp = mLowTemp.replace((makeWeatherUnique),"");
                                Log.d(LOG_TAG, "mLowTemp = " + mLowTemp);
                            }
                            if (dataMap.containsKey("time")) {
                                mTime = dataMap.getLong("time");
                            }

                            if (dataMap.containsKey("icon")){
                                Asset iconAsset = dataMap.getAsset("icon");
                                if (iconAsset != null) {
                                    new GetBitmap().execute(iconAsset);

                                }
                            }

                            Log.d(LOG_TAG, "mHighTemp = " + mHighTemp);
                            Log.d(LOG_TAG, "mLowTemp = " + mLowTemp);
                            Log.d(LOG_TAG, "makeWeatherUnique " + makeWeatherUnique);
                            invalidate();
                        }
                        if (item.getUri().getPath().equals("/path/update")) {
                            DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                            if(dataMap.containsKey("makeWeatherUnique")) {
                                Log.d(LOG_TAG, "makeWeatherUnique = " + makeWeatherUnique);
                                makeWeatherUnique = dataMap.getInt("makeWeatherUnique") + "";
                                Log.d(LOG_TAG, "makeWeatherUnique = " + makeWeatherUnique);
                            }

                            if(dataMap.containsKey("high-temp")) {
                                String tempTemp = dataMap.getString("high-temp");
                                Log.d(LOG_TAG, "tempTemp 2 = " + tempTemp);
                                if (tempTemp != null) {
                                    mHighTemp = tempTemp;
                                    mHighTemp = mHighTemp.replace((makeWeatherUnique), "");
                                    Log.d(LOG_TAG, "mHighTemp = " + mHighTemp);
                                }
                            }
                            if(dataMap.containsKey("low-temp")) {
                                mLowTemp = dataMap.getString("low-temp");
                                mLowTemp = mLowTemp.replace((makeWeatherUnique),"");
                                Log.d(LOG_TAG, "mLowTemp = " + mLowTemp);
                            }
                            if (dataMap.containsKey("time")) {
                                mTime = dataMap.getLong("time");
                            }
                            if (dataMap.containsKey("icon")){
                                Asset iconAsset = dataMap.getAsset("icon");
                                if (iconAsset != null) {
                                    new GetBitmap().execute(iconAsset);

                                }
                            }
                            Log.d(LOG_TAG, "mHighTemp = " + mHighTemp);
                            Log.d(LOG_TAG, "mLowTemp = " + mLowTemp);
                            Log.d(LOG_TAG, "makeWeatherUnique " + makeWeatherUnique);
                            invalidate();
                        }

                    }

                }
            }
        };

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mHighTempPaint = new Paint();
            mHighTempPaint.setColor(resources.getColor(R.color.digital_text));

            mLowTempPaint = new Paint();
            mLowTempPaint.setColor(resources.getColor(R.color.light_grey));


            mCalendar = Calendar.getInstance();

            highTempYOffset = getResources().getDimension(R.dimen.digital_temp_high_offset);
            lowTempYOffset = getResources().getDimension(R.dimen.digital_temp_low_offset);

            mResolvingError = false;
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(@Nullable Bundle bundle) {
                            Log.d(LOG_TAG, "Wearable connected");
                            Wearable.DataApi.addListener(mGoogleApiClient, dataListener);
                            sendMessage("/path/update", "connected to watchface");


                            syncImmediately();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.d(LOG_TAG, "Wearable Connection Suspended");
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                            Log.d(LOG_TAG, "Connection failed");
                        }
                    })
                    .build();
            mGoogleApiClient.connect();


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            mTextPaint.setTextSize(textSize);
            mHighTempPaint.setTextSize(tempTextSize);
            mLowTempPaint.setTextSize(tempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // this is where the watchface touch code is inserted

                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);


            float centerX = bounds.centerX();
            float centerY = bounds.centerY();

            if (mHighTemp != null && mLowTemp != null) {
                if (mIcon != null){// && !mLowBitAmbient){
                    canvas.drawBitmap(mIcon,
                           centerX - 100 ,// centerX - mIcon.getWidth() - mIcon.getWidth() / 4,
                            lowTempYOffset - mIcon.getHeight(),
                            mIconPaint);}
                //High temp
                canvas.drawText(mHighTemp,
                        centerX + 25,
                        highTempYOffset,
                        mHighTempPaint);
                //Low temp
                canvas.drawText(mLowTemp,
                        centerX + 25,//+ highTempSize + highTempRightMargin,
                        lowTempYOffset,
                        mLowTempPaint);
            }
            else {
                Log.d("mHighTemp = " + mHighTemp, "mLowTemp = " + mLowTemp + " time = " + mTime + " makeWeatherUnique " + makeWeatherUnique);
                float tempYOffset = getResources().getDimension(R.dimen.digital_y_offset);
                canvas.drawText(getString(R.string.No_temperature_data_string), 30, tempYOffset + 35, mHighTempPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
        private void sendMessage( final String path, final String text ) {
            new Thread( new Runnable() {
                @Override
                public void run() {
                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( mGoogleApiClient ).await();
                    for(Node node : nodes.getNodes()) {
                        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                                mGoogleApiClient, node.getId(), path, text.getBytes() ).await();
                    }
                }
            }).start();
        }



        public void
        syncImmediately() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/path/update");
            putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
            PutDataRequest request = putDataMapRequest.asPutDataRequest();
            request.setUrgent();


            Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if (!dataItemResult.getStatus().isSuccess()) {
                        Log.d(LOG_TAG, "Failed to sync immediately");
                    } else {
                        Log.d(LOG_TAG, "Successfully synced immediately");
                    }
                }
            });
        }

        public Bitmap loadBitmapFromAsset(Asset asset) {
            final int TIMEOUT_MS = 500;
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(LOG_TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
        public class GetBitmap extends AsyncTask<Asset, Void, Void> {

            @Override
            protected Void doInBackground(Asset... assets) {
                Asset asset = assets[0];
                mIcon = loadBitmapFromAsset(asset);
                mIcon = Bitmap.createScaledBitmap(mIcon, 75, 75, false);
                postInvalidate();

                return null;
            }
        }
    }
}