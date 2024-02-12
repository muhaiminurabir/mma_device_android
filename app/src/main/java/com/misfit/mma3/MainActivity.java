package com.misfit.mma3;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.geniatech.hdmiin.HdmiInCamera;
import com.misfit.mma3.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;

    private HdmiInCamera hdmiInCamera;
    private final Handler handler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        try {
            //binding.hdmiInput.setSurfaceTextureListener((TextureView.SurfaceTextureListener) this);
            //binding.hdmiFrame.addView(binding.hdmiText);
            hdmiInCamera = new HdmiInCamera(this, binding.hdmiInput, binding.noSignal, handler);
            CameraPermission();
            TextView title= new TextView(this);
            title.setText("From Misfit");
            title.setAllCaps(true);
            title.setTextSize(24);
            title.setTextColor(Color.parseColor("#FF0000"));
            title.setGravity(Gravity.CENTER);
            //title.setBackgroundColor(Color.parseColor("#ffffff"));
            title.setPadding(5,5,5,5);
            //binding.hdmiFrame.addView(title);
            //hdmiInCamera.init();
        } catch (Exception e) {
            Log.d("Error Line Number", Log.getStackTraceString(e));
        }
    }

    private void CameraPermission() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                // You can use the API that requires the permission.
                hdmiInCamera.init();
                hdmiInCamera.EnableHDMIInAudio(true);
                hdmiInCamera.takePicture();
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this, android.Manifest.permission.CAMERA)) {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
                //showInContextUI(...);
            } else {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                        android.Manifest.permission.CAMERA);
            }
        } catch (Exception e) {
            Log.d("Error Line Number", Log.getStackTraceString(e));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hdmiInCamera.release();
        finish();
    }

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    hdmiInCamera.init();
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });
}