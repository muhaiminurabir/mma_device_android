package com.geniatech.hdmiin;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class HdmiInPIP extends Service {
    private static final String TAG = "BluetoothGuide";
    private WindowManager mWindowManager = null;
    private WindowManager.LayoutParams mLayoutParams = null;
    private RelativeLayout mLayout;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences sharedPreferences;
    private HdmiInCamera hdmiInCamera;
    private final Handler handler = new Handler();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "---onCreate---");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "---onStartCommand---");
        if (intent.getBooleanExtra("isShow", true)) {
            sharedPreferences = getSharedPreferences("config", Context.MODE_PRIVATE);
            showPipWindow();
        } else {
            hidePipWindow();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setSize() {
        DisplayMetrics outMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(outMetrics);
        int widthPixels = outMetrics.widthPixels;
        int heightPixels = outMetrics.heightPixels;
        int size = sharedPreferences.getInt("size", 0);
        Log.i(TAG, "size==" + size);
        switch (size) {
            case 0:
                mLayoutParams.width = (int) (widthPixels * 0.2);
                mLayoutParams.height = (int) (heightPixels * 0.2);
                break;
            case 1:
                mLayoutParams.width = (int) (widthPixels * 0.3);
                mLayoutParams.height = (int) (heightPixels * 0.3);
                break;
            case 2:
                mLayoutParams.width = (int) (widthPixels * 0.4);
                mLayoutParams.height = (int) (heightPixels * 0.4);
                break;
            case 3:
                mLayoutParams.width = (int) (widthPixels * 0.5);
                mLayoutParams.height = (int) (heightPixels * 0.5);
                break;
        }
    }

    @SuppressLint("RtlHardcoded")
    private void setLocation() {
        int location = sharedPreferences.getInt("location", 0);
        Log.i(TAG, "location==" + location);
        switch (location) {
            case 0:
                mLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
                break;
            case 1:
                mLayoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
                break;
            case 2:
                mLayoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
                break;
            case 3:
                mLayoutParams.gravity = Gravity.RIGHT | Gravity.BOTTOM;
                break;
        }
    }

    private void hidePipWindow() {
        Log.i(TAG, "---hidePipWindow---");
        if (mLayout != null) {
            mWindowManager.removeView(mLayout);
            mLayout = null;
            releaseWakeLock();
        }
        if (hdmiInCamera != null) {
            hdmiInCamera.release();
        }

    }

    @SuppressLint({"InflateParams", "RtlHardcoded"})
    private void showPipWindow() {
        Log.i(TAG, "---showPipWindow---");
        keepScreenOn();
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mLayoutParams = new WindowManager.LayoutParams();
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        mLayoutParams.format = PixelFormat.RGBA_8888;
        //mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        setLocation();
        setSize();
        if (mLayout == null) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mLayout = (RelativeLayout) inflater.inflate(R.layout.pip_layout, null);
            TextureView camera_view = mLayout.findViewById(R.id.camera_view);
            TextView no_signal = mLayout.findViewById(R.id.no_signal);
            mWindowManager.addView(mLayout, mLayoutParams);
            mLayout.setFocusableInTouchMode(true);
            hdmiInCamera = new HdmiInCamera(this, camera_view, no_signal, handler);
        }
        hdmiInCamera.init();
    }

    private void keepScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        wakeLock.acquire(24 * 60 * 60 * 1000L /*10 minutes*/);
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}
