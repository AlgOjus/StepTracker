package com.example.steptracker;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;


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
public class MainActivity extends AppCompatActivity {

    Button btnStart, btnPause, btnResume, btnStop;
    MapView map;
    FusedLocationProviderClient fusedLocationClient;
    LocationCallback locationCallback;
    TextView tvDistance;
    Location previousLocation = null;
    double totalDistance = 0;
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
        // Now safe to access views
        map = findViewById(R.id.map);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        GeoPoint startPoint = new GeoPoint(25.4358, 81.8463);
        map.getController().setZoom(15.0);
        map.getController().setCenter(startPoint);

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
        });

        // PAUSE
        btnPause.setOnClickListener(v -> {
            btnPause.setVisibility(View.GONE);
            btnResume.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.VISIBLE);
            stopLocationUpdates();
        });

        // RESUME
        btnResume.setOnClickListener(v -> {
            btnResume.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            startLocationUpdates();
        });

        // STOP
        btnStop.setOnClickListener(v -> {
            btnResume.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            btnPause.setVisibility(View.GONE);
            btnStart.setVisibility(View.VISIBLE);
            stopLocationUpdates();
        });
    }
    private void startLocationUpdates() {

        LocationRequest request = LocationRequest.create();

        request.setInterval(1000);
        request.setFastestInterval(1000);

        request.setPriority(
                LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        );

        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult result) {

                if (result == null) return;

                for (Location location : result.getLocations()) {

                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    GeoPoint point = new GeoPoint(lat, lon);

                    // Move map to user
                    map.getController().setCenter(point);

                    // DISTANCE CALCULATION
                    if (previousLocation != null) {

                        totalDistance +=
                                previousLocation.distanceTo(location);

                        tvDistance.setText(
                                "Distance: " +
                                        String.format("%.2f", totalDistance) +
                                        " m"
                        );
                    }

                    previousLocation = location;
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