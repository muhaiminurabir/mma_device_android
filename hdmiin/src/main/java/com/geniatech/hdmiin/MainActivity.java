package com.geniatech.hdmiin;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    public static final String[] REQUEST_PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
    };
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private SharedPreferences sharedPreferences;
    private HdmiInCamera hdmiInCamera;
    private final Handler handler = new Handler();

    public static boolean hasUnauthorizedPermission(Activity activity) {
        for (String permission : REQUEST_PERMISSIONS) {
            if (PackageManager.PERMISSION_GRANTED != activity.checkSelfPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startService(false);

        sharedPreferences = getSharedPreferences("config", Context.MODE_PRIVATE);
        CheckBox mute_checkbox = findViewById(R.id.mute_checkbox);
        Spinner select_location_spinner = findViewById(R.id.select_location_spinner);
        Spinner choose_size_spinner = findViewById(R.id.choose_size_spinner);
        Button pip_button = findViewById(R.id.pip_button);
        Button take_picture = findViewById(R.id.take_picture);
        TextView no_signal = findViewById(R.id.no_signal);
        TextureView camera_view = findViewById(R.id.camera_view);

        mute_checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                sharedPreferences.edit().putBoolean("isMute", isChecked).apply();
                hdmiInCamera.EnableHDMIInAudio(!isChecked);
            }
        });
        select_location_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "position==" + position);
                sharedPreferences.edit().putInt("location", position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        choose_size_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sharedPreferences.edit().putInt("size", position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        pip_button.setOnClickListener(v -> {
            startService(true);
            finish();
        });
        take_picture.setOnClickListener(v -> hdmiInCamera.takePicture());
        mute_checkbox.setChecked(sharedPreferences.getBoolean("isMute", false));
        select_location_spinner.setSelection(sharedPreferences.getInt("location", 0));
        choose_size_spinner.setSelection(sharedPreferences.getInt("size", 0));

        hdmiInCamera = new HdmiInCamera(this, camera_view, no_signal, handler);
        if (hasUnauthorizedPermission(this)) {
            requestPermissions(REQUEST_PERMISSIONS, REQUEST_CAMERA_PERMISSION);
        } else {
            hdmiInCamera.init();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hdmiInCamera.release();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getResources().getString(R.string.err_permission), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            hdmiInCamera.init();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void startService(boolean isShow) {
        Intent intent = new Intent(this, HdmiInPIP.class);
        intent.putExtra("isShow", isShow);
        startService(intent);
    }

}