package net.imatruck.weatherwatchface;

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
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import net.imatruck.weatherwatchface.lib.WeatherWatchFaceConstants;

public class WeatherWatchFaceService extends CanvasWatchFaceService {

    private static final String TAG = WeatherWatchFaceService.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks,
            DataApi.DataListener, GoogleApiClient.OnConnectionFailedListener {

        private static final int MSG_LOAD_WEATHER = 0;
        private static final int MSG_LOAD_WEATHER_FORCE = 1;

        private static final int COLOR_CLEAR_DAY = 0xFF0288D1;
        private static final int COLOR_CLEAR_NIGHT = 0xFF1A237E;
        private static final int COLOR_PARTLY_CLOUDY_DAY = 0xFF78909C;
        private static final int COLOR_PARTLY_CLOUDY_NIGHT = 0xFF263238;
        private static final int COLOR_CLOUDY_DAY = 0xFF78909C;
        private static final int COLOR_CLOUDY_NIGHT = 0xFF455A64;
        private static final int COLOR_RAIN_DAY = 0xFF757575;
        private static final int COLOR_RAIN_NIGHT = 0xFF424242;
        private static final int COLOR_SNOW_SLEET_FOG_DAY = 0xFFBDBDBD;
        private static final int COLOR_SNOW_SLEET_FOG_NIGHT = 0xFF757575;

        private static final int TIME_INTERVAL_15_MIN = 900000;

        private static final String CONDITION_CLEAR_DAY = "clear-day";
        private static final String CONDITION_CLEAR_NIGHT = "clear-night";
        private static final String CONDITION_PARTLY_CLOUDY_DAY = "partly-cloudy-day";
        private static final String CONDITION_PARTLY_CLOUDY_NIGHT = "partly-cloudy-night";
        private static final String CONDITION_CLOUDY = "cloudy";
        private static final String CONDITION_RAIN = "rain";
        private static final String CONDITION_SNOW = "snow";
        private static final String CONDITION_SLEET = "sleet";
        private static final String CONDITION_WIND = "wind";
        private static final String CONDITION_FOG = "fog";

        Time mTime;
        Time mLastRefreshTime;

        boolean mLowBitAmbient;
        boolean mBurnInProtection;
        boolean mIsRound;
        int mChinSize;

        Paint mTempPaint;
        Paint mFeelsLikePaint;
        Paint mDegreePaint;
        Paint mHourPaint;
        Paint mColonPaint;
        Paint mMinutePaint;
        Paint mWeekDayPaint;
        Paint mDatePaint;
        Paint mMonthPaint;

        String mWeatherCondition = "N/A";
        String mTemperature = "?";
        String mFeelsLikeTemp = "?";
        boolean mIsNight = false;

        Bitmap mWeatherIcon;

        private AsyncTask<Void, Void, Void> mLoadWeatherInfoTask;

        final Handler mLoadWeatherInfoTaskHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "mLoadWeatherInfoTaskHandler");
                switch (msg.what){
                    case MSG_LOAD_WEATHER:
                        cancelLoadWeatherInfoTask();
                        mLoadWeatherInfoTask = new LoadWeatherInfoTask();
                        mLoadWeatherInfoTask.execute();
                        break;
                    case MSG_LOAD_WEATHER_FORCE:
                        cancelLoadWeatherInfoTask();
                        mLoadWeatherInfoTask = new LoadWeatherInfoTask(true);
                        mLoadWeatherInfoTask.execute();
                        break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {

            Log.d(TAG, "onCreate");

            super.onCreate(holder);

            mTime = new Time();
            mTime.setToNow();
            mLastRefreshTime = new Time();

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setHotwordIndicatorGravity(Gravity.BOTTOM | Gravity.LEFT)
                    .setStatusBarGravity(Gravity.BOTTOM | Gravity.RIGHT)
                    .setShowUnreadCountIndicator(true)
                    .build());

            // Temp paints
            mTempPaint = new Paint();
            mTempPaint.setARGB(255, 255, 255, 255);
            mTempPaint.setStrokeWidth(15.0f);
            mTempPaint.setAntiAlias(true);
            mTempPaint.setTextSize(35f);
            mTempPaint.setStrokeCap(Paint.Cap.SQUARE);
            mTempPaint.setTextAlign(Paint.Align.RIGHT);

            mFeelsLikePaint = new Paint();
            mFeelsLikePaint.setARGB(255, 255, 255, 255);
            mFeelsLikePaint.setTextAlign(Paint.Align.RIGHT);
            mFeelsLikePaint.setAntiAlias(true);
            mFeelsLikePaint.setTextSize(20f);


            // Time paints
            mHourPaint = new Paint();
            mHourPaint.setARGB(255, 255, 255, 255);
            mHourPaint.setStrokeWidth(10.0f);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setTextSize(60f);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mHourPaint.setTextAlign(Paint.Align.RIGHT);

            mMinutePaint = new Paint(mHourPaint);
            mMinutePaint.setTextAlign(Paint.Align.LEFT);
            mMinutePaint.setTypeface(Typeface.DEFAULT);

            mColonPaint = new Paint(mMinutePaint);
            mColonPaint.setTextAlign(Paint.Align.CENTER);

            // Date paints
            mWeekDayPaint = new Paint();
            mWeekDayPaint.setARGB(255, 255, 255, 255);
            mWeekDayPaint.setStrokeWidth(15.0f);
            mWeekDayPaint.setAntiAlias(true);
            mWeekDayPaint.setTextSize(20f);
            mWeekDayPaint.setStrokeCap(Paint.Cap.ROUND);
            mWeekDayPaint.setTextAlign(Paint.Align.RIGHT);

            mMonthPaint = new Paint(mWeekDayPaint);
            mMonthPaint.setTextAlign(Paint.Align.LEFT);

            mDatePaint = new Paint(mWeekDayPaint);
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint.setTypeface(Typeface.DEFAULT_BOLD);


            mWeatherIcon = BitmapFactory.decodeResource(getResources(), R.drawable.clear_day);

            mLoadWeatherInfoTaskHandler.sendEmptyMessage(MSG_LOAD_WEATHER);
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy");
            mLoadWeatherInfoTaskHandler.removeMessages(MSG_LOAD_WEATHER);
            cancelLoadWeatherInfoTask();
            Wearable.DataApi.removeListener(mGoogleApiClient, Engine.this);
            mGoogleApiClient.disconnect();
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            Log.d(TAG, "onPropertiesChanged");
            /* get device features (burn-in, low-bit ambient) */
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets){
            super.onApplyWindowInsets(insets);
            mIsRound = insets.isRound();
            mChinSize = insets.getSystemWindowInsetBottom();
        }

        @Override
        public void onTimeTick() {
            Log.d(TAG, "onTimeTick");
            super.onTimeTick();

            mTime.setToNow();
            if (mTime.toMillis(true) - mLastRefreshTime.toMillis(true) >= TIME_INTERVAL_15_MIN) {
                mLoadWeatherInfoTaskHandler.removeMessages(MSG_LOAD_WEATHER);
                mLoadWeatherInfoTaskHandler.sendEmptyMessage(MSG_LOAD_WEATHER);
            }
            else if (mTemperature.equals("?") || mWeatherCondition.equals("N/A")) {
                mLoadWeatherInfoTaskHandler.removeMessages(MSG_LOAD_WEATHER_FORCE);
                mLoadWeatherInfoTaskHandler.sendEmptyMessage(MSG_LOAD_WEATHER_FORCE);
            }
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            Log.d(TAG, "onAmbientModeChanged");
            /* the wearable switched between modes */
            boolean wasInAmbientMode = isInAmbientMode();
            super.onAmbientModeChanged(inAmbientMode);

            if (inAmbientMode != wasInAmbientMode) {
                if (mLowBitAmbient) {
                    mTempPaint.setAntiAlias(!inAmbientMode);
                    mDegreePaint.setAntiAlias(!inAmbientMode);
                }
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            Log.d(TAG, "onDraw");

            final float center_x = canvas.getWidth() / 2;
            final int canvas_height = canvas.getHeight();

            // Draw Background
            if (isInAmbientMode()){
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(getBackgroundColor());
            }

            // Draw Time
            String hourString = mTime.format("%H");
            String colonString = ":";
            String minuteString = mTime.format("%M");

            float colonLength = mColonPaint.measureText(colonString);

            float time_y = canvas_height * 0.25f;

            canvas.drawText(colonString, center_x, time_y, mColonPaint);
            canvas.drawText(hourString, center_x - colonLength / 2, time_y, mHourPaint);
            canvas.drawText(minuteString, center_x + colonLength / 2, time_y, mMinutePaint);

            // Draw date
            String dayOfWeekString = mTime.format("%a ");
            String dateString = mTime.format("%d");
            String monthString = mTime.format(" %b");

            float dateLength = mDatePaint.measureText(dateString);

            float date_y = canvas_height * 0.35f;

            canvas.drawText(dateString, center_x, date_y, mDatePaint);
            canvas.drawText(dayOfWeekString, center_x - dateLength / 2, date_y, mWeekDayPaint);
            canvas.drawText(monthString, center_x + dateLength / 2, date_y, mMonthPaint);

            // Draw current weather
            float temp_y = canvas_height * 0.525f;
            float feelLike_y = temp_y + canvas_height * 0.075f;

            float spaceLength = mTempPaint.measureText(" ");

            canvas.drawBitmap(mWeatherIcon, center_x + spaceLength, temp_y - mWeatherIcon.getHeight(), mTempPaint);

            canvas.drawText(mTemperature + "° ", center_x, temp_y, mTempPaint);

            if (!mFeelsLikeTemp.equals(mTemperature)) {
                canvas.drawText(mFeelsLikeTemp + "°", center_x - spaceLength, feelLike_y, mFeelsLikePaint);
            }

            Log.d(TAG, "onDraw Done");

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d(TAG, "onVisibilityChanged");
            /* the watch face became visible or invisible */
            super.onVisibilityChanged(visible);
            if (visible) {
                mGoogleApiClient.connect();
                invalidate();
            }
            else {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, Engine.this);
                    mGoogleApiClient.disconnect();
                }
            }
        }

        private void cancelLoadWeatherInfoTask() {
            Log.d(TAG, "cancelLoadWeatherInfoTask");
            if (mLoadWeatherInfoTask != null) {
                mLoadWeatherInfoTask.cancel(true);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "Data has changed");
            DataMap dataMap;
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    String path = event.getDataItem().getUri().getPath();
                    if (path.equals(WeatherWatchFaceConstants.DATASYNC_URI_WEATHER_INFO)) {
                        dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                        String icon = dataMap.getString(WeatherWatchFaceConstants.KEY_WEATHER_ICON);
                        String temp = dataMap.getString(WeatherWatchFaceConstants.KEY_WEATHER_TEMP);
                        String feelsLike = dataMap.getString(WeatherWatchFaceConstants.KEY_WEATHER_FEELSLIKE, temp);
                        long sunsetTime = dataMap.getLong(WeatherWatchFaceConstants.KEY_WEATHER_SUNSET, -1);
                        long sunriseTime = dataMap.getLong(WeatherWatchFaceConstants.KEY_WEATHER_SUNRISE, -1);

                        Log.d(TAG, "Received weather from mobile: " + icon + ", " + temp);
                        if (!mWeatherCondition.equals(icon)) {
                            mWeatherCondition = icon;
                            mWeatherIcon = BitmapFactory.decodeResource(getResources(), getWeatherIconResourceId());
                        }
                        mTemperature = temp;
                        mFeelsLikeTemp = feelsLike;
                        long currentTime = mTime.toMillis(false) / 1000;
                        mIsNight = currentTime < sunriseTime || currentTime > sunsetTime;
                        Log.d(TAG, String.format("It is currently: %1s", (mIsNight) ? "Night" : "Day"));
                        invalidate();
                    }
                }
            }
        }

        private int getWeatherIconResourceId() {
            if (CONDITION_CLEAR_DAY.equals(mWeatherCondition)) {
                return R.drawable.clear_day;
            }
            if (CONDITION_CLEAR_NIGHT.equals(mWeatherCondition)) {
                return R.drawable.clear_night;
            }
            if (CONDITION_PARTLY_CLOUDY_DAY.equals(mWeatherCondition)) {
                return R.drawable.partly_cloudy_day;
            }
            if (CONDITION_PARTLY_CLOUDY_NIGHT.equals(mWeatherCondition)) {
                return R.drawable.partly_cloudy_night;
            }
            if (CONDITION_CLOUDY.equals(mWeatherCondition)) {
                return R.drawable.cloudy;
            }
            if (CONDITION_RAIN.equals(mWeatherCondition)) {
                return R.drawable.rain;
            }
            if (CONDITION_SNOW.equals(mWeatherCondition)) {
                return R.drawable.snow;
            }
            if (CONDITION_SLEET.equals(mWeatherCondition)) {
                return R.drawable.sleet;
            }
            if (CONDITION_WIND.equals(mWeatherCondition)) {
                return R.drawable.wind;
            }
            if (CONDITION_FOG.equals(mWeatherCondition)) {
                return R.drawable.fog;
            }

            return R.drawable.clear_day;
        }

        private int getBackgroundColor() {
            if (CONDITION_CLEAR_DAY.equals(mWeatherCondition)) {
                return COLOR_CLEAR_DAY;
            }
            if (CONDITION_CLEAR_NIGHT.equals(mWeatherCondition)) {
                return COLOR_CLEAR_NIGHT;
            }
            if (CONDITION_PARTLY_CLOUDY_DAY.equals(mWeatherCondition)) {
                return COLOR_PARTLY_CLOUDY_DAY;
            }
            if (CONDITION_PARTLY_CLOUDY_NIGHT.equals(mWeatherCondition)) {
                return COLOR_PARTLY_CLOUDY_NIGHT;
            }
            if (!mIsNight) {
                if (CONDITION_CLOUDY.equals(mWeatherCondition)) {
                    return COLOR_CLOUDY_DAY;
                }
                if (CONDITION_RAIN.equals(mWeatherCondition)) {
                    return COLOR_RAIN_DAY;
                }
                if (CONDITION_SNOW.equals(mWeatherCondition) || CONDITION_FOG.equals(mWeatherCondition)
                        || CONDITION_SLEET.equals(mWeatherCondition)) {
                    return COLOR_SNOW_SLEET_FOG_DAY;
                }
                if (CONDITION_WIND.equals(mWeatherCondition)) {
                    return COLOR_CLEAR_DAY;
                }
            } else {
                if (CONDITION_CLOUDY.equals(mWeatherCondition)) {
                    return COLOR_CLOUDY_NIGHT;
                }
                if (CONDITION_RAIN.equals(mWeatherCondition)) {
                    return COLOR_RAIN_NIGHT;
                }
                if (CONDITION_SNOW.equals(mWeatherCondition) || CONDITION_FOG.equals(mWeatherCondition)
                        || CONDITION_SLEET.equals(mWeatherCondition)) {
                    return COLOR_SNOW_SLEET_FOG_NIGHT;
                }
                if (CONDITION_WIND.equals(mWeatherCondition)) {
                    return COLOR_CLEAR_NIGHT;
                }
            }

            return COLOR_CLEAR_DAY;
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }

        private class LoadWeatherInfoTask extends AsyncTask<Void, Void, Void> {

            boolean mForceRefresh = false;

            public LoadWeatherInfoTask() {}

            public LoadWeatherInfoTask(boolean forceRefresh) {
                this.mForceRefresh = forceRefresh;
            }

            @Override
            protected Void doInBackground(Void... params) {
                Log.d(TAG, "LoadWeatherInfoTask");

                String messagePath = (mForceRefresh) ?
                        WeatherWatchFaceConstants.MESSAGE_URI_REQUEST_WEATHER_FORCE :
                        WeatherWatchFaceConstants.MESSAGE_URI_REQUEST_WEATHER;

                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            mGoogleApiClient, node.getId(), messagePath, "".getBytes()).await();
                    if (result.getStatus().isSuccess()) {
                        Log.d(TAG, "Request Weather message (" + messagePath + ") sent to: " + node.getDisplayName());
                        mLastRefreshTime.setToNow();
                    }
                    else {
                        Log.e(TAG, "ERROR: failed to send Message");
                    }
                }
                return null;
            }
        }
    }

}
