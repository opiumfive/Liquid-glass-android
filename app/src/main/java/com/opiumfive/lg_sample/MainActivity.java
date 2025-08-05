package com.opiumfive.lg_sample;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.lglass).setOnClickListener((v) -> {
            startActivity(new Intent(this, LiquidGlassActivity.class).putExtra("t", 1));
        });
        findViewById(R.id.lglass2).setOnClickListener((v) -> {
            startActivity(new Intent(this, LiquidGlassActivity.class).putExtra("t", 2));
        });
        findViewById(R.id.lglass3).setOnClickListener((v) -> {
            startActivity(new Intent(this, LiquidGlassActivity.class).putExtra("t", 3));
        });
        findViewById(R.id.lglass4).setOnClickListener((v) -> {
            startActivity(new Intent(this, LiquidGlassActivity.class).putExtra("t", 4));
        });
        findViewById(R.id.lglass5).setOnClickListener((v) -> {
            startActivity(new Intent(this, LiquidGlassActivity.class).putExtra("t", 5));
        });
        findViewById(R.id.lglass6).setOnClickListener((v) -> {
            startActivity(new Intent(this, LiquidGlassActivity.class).putExtra("t", 6));
        });
    }
}