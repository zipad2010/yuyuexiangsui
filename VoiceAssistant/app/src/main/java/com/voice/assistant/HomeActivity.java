package com.voice.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeActivity extends WallpaperActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Runnable clockUpdater = this::updateClock;

    private TextView tvTime;
    private TextView tvDate;
    private TextView tvLocation;
    private TextView tvWeather;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvTime = findViewById(R.id.tv_home_time);
        tvDate = findViewById(R.id.tv_home_date);
        tvLocation = findViewById(R.id.tv_home_location);
        tvWeather = findViewById(R.id.tv_home_weather);
        findViewById(R.id.btn_enter_chat).setOnClickListener(view -> {
            startActivity(new Intent(this, MainActivity.class));
        });

        updateClock();
        requestLocation();
    }

    private void updateClock() {
        Date now = new Date();
        tvTime.setText(timeFormat.format(now));
        tvDate.setText(dateFormat.format(now));
        handler.removeCallbacks(clockUpdater);
        handler.postDelayed(clockUpdater, 1000L);
    }

    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
            return;
        }
        loadLocationAndWeather();
    }

    private void loadLocationAndWeather() {
        // 防御性检查：权限可能被用户拒绝或在回调前被撤销
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tvLocation.setText("暂未定位");
            tvWeather.setText("天气待更新");
            return;
        }
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location location;
        try {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (SecurityException e) {
            tvLocation.setText("暂未定位");
            tvWeather.setText("天气待更新");
            return;
        }
        if (location == null) {
            tvLocation.setText("暂未定位");
            tvWeather.setText("天气待更新");
            return;
        }
        Location finalLocation = location;
        new Thread(() -> {
            String place = resolvePlace(finalLocation);
            String weather = loadWeather(finalLocation);
            runOnUiThread(() -> {
                tvLocation.setText(place);
                tvWeather.setText(weather);
            });
        }).start();
    }

    private String resolvePlace(Location location) {
        if (!Geocoder.isPresent()) {
            return String.format(Locale.CHINA, "%.3f, %.3f", location.getLatitude(), location.getLongitude());
        }
        try {
            List<Address> addresses = new Geocoder(this, Locale.CHINA)
                    .getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String locality = address.getLocality();
                return locality == null || locality.isEmpty() ? address.getAdminArea() : locality;
            }
        } catch (IOException ignored) {
        }
        return "当前位置";
    }

    private String loadWeather(Location location) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                location.getLatitude(), location.getLongitude());
        try (Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return "天气待更新";
            }
            JSONObject current = new JSONObject(response.body().string()).optJSONObject("current");
            if (current == null) {
                return "天气待更新";
            }
            return current.optInt("temperature_2m") + "°C  " + weatherLabel(current.optInt("weather_code"));
        } catch (Exception ignored) {
            return "天气待更新";
        }
    }

    private String weatherLabel(int code) {
        if (code == 0) return "晴朗";
        if (code <= 3) return "多云";
        if (code <= 48) return "有雾";
        if (code <= 67) return "有雨";
        if (code <= 77) return "降雪";
        if (code <= 82) return "阵雨";
        return "雷雨";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            loadLocationAndWeather();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockUpdater);
        super.onDestroy();
    }
}