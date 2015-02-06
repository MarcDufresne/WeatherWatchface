# Weather Watch Face for Android Wear

This watch face displays weather info from ForecastIO on your Android Wear device.

This is an experiment with the Android Wear Watch Face API. Currently supports only the LG G Watch, will work on other watches but weather info might be out of place a bit.

This repo contains 3 projects:

- `mobile`: Weather service for the phone, the watch face will connect to this to get weather info through Play Services
- `wear`: Watch face service, contains refresh and draw logic for watch face as well as Play Services listener
- `weatherwatchfaceconstants`: Keys for data exchange between the two services

## How to build
I suggest using Android Studio for this project. Clone and import in Android Studio and it should work.
You'll need to create a `WeatherServiceApiKey.java` file in the mobile project, next to the `WeatherService.java` file with the following code inside:
    
    package net.imatruck.weatherwatchface;

    public class WeatherServiceApiKey {
        public static final String API_KEY = "YOUR_FORECAST_IO_API_KEY_HERE";
    }