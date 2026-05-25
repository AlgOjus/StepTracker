package com.example.steptracker;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import org.osmdroid.views.overlay.Polyline;
import java.util.Locale;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.views.MapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import android.location.Location;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;

import java.util.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    Button btnStart, btnPause, btnResume, btnStop;
    MapView map;
    FusedLocationProviderClient fusedLocationClient;
    LocationCallback locationCallback;
    TextView tvDistance;
    Location previousLocation = null;
    double totalDistance = 0;
    private Polyline routeLine;
    private ArrayList<GeoPoint> routePoints = new ArrayList<>();
    TextView tvTimer, tvSpeed;

    Handler timerHandler = new Handler();

    long startTime = 0L;
    long timeInMilliseconds = 0L;
    long timeSwap = 0L;
    long updatedTime = 0L;
    private SensorManager sensorManager;
    private Sensor stepSensor;

    private int stepCount = 0;

    private float lastMagnitude = 0;

    private long lastStepTime = 0;
    private TextView tvSteps;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MUST come after super but before map usage
        Configuration.getInstance().load(
                getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE)
        );

        setContentView(R.layout.activity_main);
        tvDistance = findViewById(R.id.tvDistance);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    1
            );
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACTIVITY_RECOGNITION
                    },
                    2
            );
        }

        // Now safe to access views
        map = findViewById(R.id.map);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        GeoPoint startPoint = new GeoPoint(25.4358, 81.8463);
        map.getController().setZoom(15.0);
        map.getController().setCenter(startPoint);

        routeLine = new Polyline();
        routeLine.setWidth(8f);
        routeLine.setColor(Color.BLUE);

        map.getOverlays().add(routeLine);
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnStop = findViewById(R.id.btnStop);

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        // START
        btnStart.setOnClickListener(v -> {

            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);

            startLocationUpdates();
            map.getController().setZoom(18.0);
            startTime = SystemClock.uptimeMillis();
            timerHandler.postDelayed(updateTimerThread, 0);
            if (stepSensor != null) {

                sensorManager.registerListener(
                        this,
                        stepSensor,
                        SensorManager.SENSOR_DELAY_GAME
                );

            }
            Intent serviceIntent =
                    new Intent(this, TrackingService.class);

            ContextCompat.startForegroundService(
                    this,
                    serviceIntent
            );
        });

        // PAUSE
        btnPause.setOnClickListener(v -> {
            btnPause.setVisibility(View.GONE);
            btnResume.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.VISIBLE);
            stopLocationUpdates();
            timeSwap += timeInMilliseconds;
            timerHandler.removeCallbacks(updateTimerThread);
            sensorManager.unregisterListener(this);
        });

        // RESUME
        btnResume.setOnClickListener(v -> {
            btnResume.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            startLocationUpdates();
            startTime = SystemClock.uptimeMillis();
            timerHandler.postDelayed(updateTimerThread, 0);
            if (stepSensor != null) {

                sensorManager.registerListener(
                        this,
                        stepSensor,
                        SensorManager.SENSOR_DELAY_GAME                );
            }
        });

        // STOP
        btnStop.setOnClickListener(v -> {
            btnResume.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            btnPause.setVisibility(View.GONE);
            btnStart.setVisibility(View.VISIBLE);
            stopLocationUpdates();
            routePoints.clear();
            routeLine.setPoints(routePoints);
            map.invalidate();
            totalDistance = 0;
            tvDistance.setText("Distance: 0 m");
            previousLocation = null;
            timerHandler.removeCallbacks(updateTimerThread);

            startTime = 0L;
            timeInMilliseconds = 0L;
            timeSwap = 0L;
            updatedTime = 0L;

            tvTimer.setText("Time: 00:00:00");
            sensorManager.unregisterListener(this);

            stepCount = 0;

            tvSteps.setText("Steps: 0");
            Intent serviceIntent =
                    new Intent(this, TrackingService.class);
            tvSpeed.setText("Speed: 0 km/hr");
            stopService(serviceIntent);
        });

        tvTimer = findViewById(R.id.tvTimer);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvSteps = findViewById(R.id.tvSteps);

        sensorManager =
                (SensorManager) getSystemService(SENSOR_SERVICE);

        stepSensor =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_ACCELEROMETER
                );
        if (stepSensor == null) {

            Toast.makeText(
                    this,
                    "Accelerometer Not Available",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
    Runnable updateTimerThread = new Runnable() {
        @Override
        public void run() {

            timeInMilliseconds =
                    SystemClock.uptimeMillis() - startTime;

            updatedTime = timeSwap + timeInMilliseconds;

            int secs = (int) (updatedTime / 1000);

            int mins = secs / 60;
            int hours = mins / 60;

            secs = secs % 60;
            mins = mins % 60;

            tvTimer.setText(
                    String.format(
                            Locale.getDefault(),
                            "Time: %02d:%02d:%02d",
                            hours,
                            mins,
                            secs
                    )
            );

            timerHandler.postDelayed(this, 1000);
        }
    };
    private void startLocationUpdates() {

        LocationRequest request = LocationRequest.create();

        request.setInterval(1000);
        request.setFastestInterval(1000);

        request.setPriority(
                LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        );
        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                Location location = locationResult.getLastLocation();

                if (location != null) {

                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    GeoPoint newPoint = new GeoPoint(lat, lon);

                    routePoints.add(newPoint);

                    routeLine.setPoints(routePoints);

                    map.invalidate();

                    map.getController().animateTo(newPoint);

                    if (previousLocation != null) {
                        totalDistance += previousLocation.distanceTo(location);

                        tvDistance.setText(
                                String.format(
                                        Locale.getDefault(),
                                        "Distance: %.2f m",
                                        totalDistance
                                )
                        );
                    }

                    previousLocation = location;
                    float speed = location.getSpeed();
                    float speedKm = speed * 3.6f;

                    tvSpeed.setText(
                            String.format(
                                    Locale.getDefault(),
                                    "Speed: %.2f km/h",
                                    speedKm
                            )
                    );
                }
            }
        };
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                null
        );
    }

    private void stopLocationUpdates() {

        if (locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(
                    locationCallback
            );
        }
    }
    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType()
                == Sensor.TYPE_ACCELEROMETER) {

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float magnitude =
                    (float) Math.sqrt(
                            x * x +
                                    y * y +
                                    z * z
                    );

            float delta =
                    Math.abs(magnitude - lastMagnitude);

            lastMagnitude = magnitude;

            long currentTime =
                    System.currentTimeMillis();

            // Better walking threshold
            if (delta > 8) {

                // Human walking timing filter
                if (currentTime - lastStepTime > 400) {

                    lastStepTime = currentTime;

                    stepCount++;

                    tvSteps.setText(
                            "Steps: " + stepCount
                    );
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy
    ) {

    }
    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}