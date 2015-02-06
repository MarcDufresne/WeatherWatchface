package net.imatruck.weatherwatchface;

import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.eclipsesource.json.JsonObject;
import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import net.imatruck.weatherwatchface.lib.WeatherWatchFaceConstants;

import java.util.Random;

public class WeatherService extends WearableListenerService implements
        MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = WeatherService.class.getSimpleName();
    private static final String API_KEY = WeatherServiceApiKey.API_KEY;
    private static final String EXCLUDE_URL = "hourly,minutely";

    ForecastIO mFio;

    String mLat, mLong;

    GoogleApiClient mGoogleApiClient;

    private static final int MSG_GET_WEATHER = 0;
    private static final int MSG_GET_WEATHER_FORCE = 1;

    private AsyncTask<Void, Void, Void> mGetWeatherTask;

    Handler mGetWeatherDataHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_WEATHER:
                    cancelGetWeatherTask();
                    mGetWeatherTask = new GetWeatherTask();
                    mGetWeatherTask.execute();
                    break;
                case MSG_GET_WEATHER_FORCE:
                    cancelGetWeatherTask();
                    mGetWeatherTask = new GetWeatherTask(true);
                    mGetWeatherTask.execute();
                    break;
            }
        }
    };

    private class GetWeatherTask extends AsyncTask<Void, Void, Void> {

        boolean mForceRefresh = false;

        private static final String FORECAST_KEY_ICON = "icon";
        private static final String FORECAST_KEY_TEMPERATURE = "temperature";
        private static final String FORECAST_KEY_APPARENT_TEMPERATURE = "apparentTemperature";
        private static final String FORECAST_KEY_SUNRISE_TIME = "sunriseTime";
        private static final String FORECAST_KEY_SUNSET_TIME = "sunsetTime";

        public GetWeatherTask() {}

        public GetWeatherTask(boolean forceRefresh) {
            this.mForceRefresh = forceRefresh;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mGoogleApiClient.blockingConnect();
            boolean gotForecast = mFio.getForecast(mLat, mLong);
            if (gotForecast) {
                JsonObject currently = mFio.getCurrently();
                JsonObject daily = mFio.getDaily().get("data").asArray().get(0).asObject();
                Log.d(TAG, "Got weather for location: " + mFio.getLatitude() + ", " + mFio.getLongitude());
                Log.d(TAG, "Current weather: " + currently.get(FORECAST_KEY_ICON) + ", " +
                        currently.get(FORECAST_KEY_TEMPERATURE) + " (" + currently.get(FORECAST_KEY_APPARENT_TEMPERATURE) + ")");
                sendWeatherData(currently, daily);
            }
            else {
                Log.e(TAG, "Couldn't get weather for last location");
            }
            return null;
        }

        private void sendWeatherData(JsonObject currently, JsonObject daily) {
            if (currently != null) {
                String temp = String.valueOf(Math.round(currently.get(FORECAST_KEY_TEMPERATURE).asFloat()));
                String feelsLike = String.valueOf(Math.round(currently.get(FORECAST_KEY_APPARENT_TEMPERATURE).asFloat()));

                PutDataMapRequest putDMR = PutDataMapRequest.create(WeatherWatchFaceConstants.DATASYNC_URI_WEATHER_INFO);
                putDMR.getDataMap().putString(WeatherWatchFaceConstants.KEY_WEATHER_ICON, currently.get(FORECAST_KEY_ICON).asString());
                putDMR.getDataMap().putString(WeatherWatchFaceConstants.KEY_WEATHER_TEMP, temp);

                if (daily != null) {
                    long sunrise = daily.get(FORECAST_KEY_SUNRISE_TIME).asLong();
                    long sunset = daily.get(FORECAST_KEY_SUNSET_TIME).asLong();
                    putDMR.getDataMap().putLong(WeatherWatchFaceConstants.KEY_WEATHER_SUNRISE, sunrise);
                    putDMR.getDataMap().putLong(WeatherWatchFaceConstants.KEY_WEATHER_SUNSET, sunset);
                }

                if (!temp.equals(feelsLike)) {
                    putDMR.getDataMap().putString(WeatherWatchFaceConstants.KEY_WEATHER_FEELSLIKE, feelsLike);
                }

                if (mForceRefresh) {
                    putDMR.getDataMap().putInt(WeatherWatchFaceConstants.KEY_WEATHER_FORCE_REFRESH,
                            new Random().nextInt(100));
                }

                PutDataRequest request = putDMR.asPutDataRequest();

                DataApi.DataItemResult result = Wearable.DataApi.putDataItem(mGoogleApiClient, request).await();

                if (result.getStatus().isSuccess()){
                    Log.d(TAG, String.format("Sent data to watch; Icon: %1s, Temp: %2s",
                            currently.get(FORECAST_KEY_ICON).asString(), temp));
                }
                else {
                    Log.e(TAG, "Couldn't send data to watch");
                }
            }
        }
    }

    private void cancelGetWeatherTask() {
        Log.d(TAG, "cancelGetWeatherTask");
        if (mGetWeatherTask != null) {
            mGetWeatherTask.cancel(true);
        }
    }

    protected synchronized void buildGoogleAPIClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "I'm running!");

        buildGoogleAPIClient();

        mFio = new ForecastIO(API_KEY);
        mFio.setUnits(ForecastIO.UNITS_CA);
        mFio.setExcludeURL(EXCLUDE_URL);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        cancelGetWeatherTask();
        mGetWeatherDataHandler.removeMessages(MSG_GET_WEATHER);
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(WeatherWatchFaceConstants.MESSAGE_URI_REQUEST_WEATHER)) {
            Log.d(TAG, "Message path received is: " + messageEvent.getPath());
            mGetWeatherDataHandler.sendEmptyMessage(MSG_GET_WEATHER);
        }
        else if (messageEvent.getPath().equals(WeatherWatchFaceConstants.MESSAGE_URI_REQUEST_WEATHER_FORCE)) {
            Log.d(TAG, "Message path received is: " + messageEvent.getPath());
            mGetWeatherDataHandler.sendEmptyMessage(MSG_GET_WEATHER_FORCE);
        }
        else {
            Log.d(TAG, "Wrong message path received: " + messageEvent.getPath());
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLat = String.valueOf(mLastLocation.getLatitude());
            mLong = String.valueOf(mLastLocation.getLongitude());
            Log.d(TAG, "Last known location: " + mLat + ", " + mLong);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {}
}
