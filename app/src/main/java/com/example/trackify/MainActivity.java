package com.example.trackify;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.views.MapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

public class MainActivity extends AppCompatActivity {

    Button btnStart, btnPause, btnResume, btnStop;
    MapView map;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MUST come after super but before map usage
        Configuration.getInstance().load(
                getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE)
        );

        setContentView(R.layout.activity_main);

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

        // START
        btnStart.setOnClickListener(v -> {
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
        });

        // PAUSE
        btnPause.setOnClickListener(v -> {
            btnPause.setVisibility(View.GONE);
            btnResume.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.VISIBLE);
        });

        // RESUME
        btnResume.setOnClickListener(v -> {
            btnResume.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
        });

        // STOP
        btnStop.setOnClickListener(v -> {
            btnResume.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            btnPause.setVisibility(View.GONE);
            btnStart.setVisibility(View.VISIBLE);
        });
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