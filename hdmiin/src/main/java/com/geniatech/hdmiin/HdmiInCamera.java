package com.geniatech.hdmiin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.rockchip.camera2.util.JniCameraCall;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HdmiInCamera {
    private static final String TAG = "HdmiInCamera";
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private final Context context;
    private final TextureView textureView;
    private final TextView noSignalView;
    private final Handler handler;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private boolean isDisplay = true;
    private Size imageDimension;
    private ImageReader imageReader;
    private Handler mBackgroundHandler;
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        final int openTime = 3;
        int tryOpenCurrent = 0;

        @Override
        public void onOpened(CameraDevice camera) {
            // This is called when the camera is open
            Log.d(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
            tryOpenCurrent = 0;
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.i(TAG, "onError");
            cameraDevice.close();
            cameraDevice = null;
            if (tryOpenCurrent < openTime) {
                openCamera();
                Log.d(TAG, String.format("----try open camera on %d times", tryOpenCurrent));
                tryOpenCurrent++;
            }
        }
    };
    private HandlerThread mBackgroundThread;
    private AudioStream mAudioStream;
    private Thread CameraFormatThread;
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // open your camera here
            Log.d(TAG, "onSurfaceTextureAvailable");
            // openCamera();
            startDisplay();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    public HdmiInCamera(Context context, TextureView textureView, TextView no_signal, Handler handler) {
        this.context = context;
        this.textureView = textureView;
        this.noSignalView = no_signal;
        this.handler = handler;
    }

    public void init() {
        mAudioStream = AudioStream.getInstance();
        JniCameraCall.openDevice();
        textureView.setSurfaceTextureListener(textureListener);
        startBackgroundThread();
    }

    public void EnableHDMIInAudio(boolean enable) {
        Log.i(TAG, "EnableHDMIInAudio " + enable);
        if (enable) {
            mAudioStream.start(2);// hdmi and speaker by default
        } else {
            mAudioStream.stop();
        }
    }

    public void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width;
            int height;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            width = imageDimension.getWidth();
            height = imageDimension.getHeight();
            Log.d(TAG, "pic size W=" + width + ",H=" + height);
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = context.getResources().getConfiguration().orientation;
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    try (Image image = reader.acquireLatestImage()) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    try (OutputStream output = new FileOutputStream(file)) {
                        output.write(bytes);
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(context, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void startBackgroundThread() {
        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("Camera Background");
            mBackgroundThread.setPriority(Thread.MAX_PRIORITY);
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            Log.d(TAG, "createCameraPreview");
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            Log.d(TAG, "imageDimension.getWidth()=" + imageDimension.getWidth() + ",imageDimension.getHeight()=" + imageDimension.getHeight());
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }
                    Log.d(TAG, "onConfigured");
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Log.i(TAG, "onConfigureFailed");
                    Toast.makeText(context, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        Log.i(TAG, "openCamera start");
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            for (Size size : map.getOutputSizes(SurfaceTexture.class)) {
                Log.d(TAG, "supported stream size: " + size.toString());
            }
            Log.d(TAG, "current hdmi input size:" + imageDimension.toString());
            manager.openCamera(cameraId, stateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "openCamera end");
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        Log.d(TAG, "updatePreview");
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera");
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    /*protected void onResume() {
        Log.d(TAG, "onResume");
        startBackgroundThread();
        EnableHDMIInAudio(true);
    }

    protected void pause() {
        stopDisplay();
        closeCamera();
        JniCameraCall.closeDevice();
        EnableHDMIInAudio(false);
    }*/

    public void release() {
        Log.i(TAG, "release");
        stopBackgroundThread();
        stopDisplay();
        EnableHDMIInAudio(false);
    }

    private void stopDisplay() {
        isDisplay = false;
        if (CameraFormatThread != null) {
            CameraFormatThread.interrupt();
        }
    }

    private void startDisplay() {
        CameraFormatThread = new Thread(() -> {
            boolean isHdmiIn = false;
            while (isDisplay) {
                int[] format = JniCameraCall.getFormat();
                if (format != null && format.length > 0) {
                    Size curDriverDimension = new Size(format[0], format[1]);
                    Log.i(TAG, "format != null format[2] = " + format[2]);
                    if (format[2] != 0 && !isHdmiIn) {
                        Log.i(TAG, "hdmi is plug");
                        isHdmiIn = true;
                        onHdmiStatusChange(isHdmiIn, curDriverDimension);
                    } else if (format[2] == 0 && isHdmiIn) {
                        Log.i(TAG, "hdmi is unplug");
                        isHdmiIn = false;
                        onHdmiStatusChange(isHdmiIn, curDriverDimension);
                    }
                }
                SystemClock.sleep(500);
            }
        });
        CameraFormatThread.start();
    }

    public void onHdmiStatusChange(boolean isHdmiIn, Size driverDimension) {
        Log.i(TAG, "onHdmiStatusChange isHdmiIn = " + isHdmiIn + " width:" + driverDimension.getWidth() + " height:" + driverDimension.getHeight());
        imageDimension = driverDimension;
        if (isHdmiIn) {
            openCamera();
            boolean isMute = context.getSharedPreferences("config", Context.MODE_PRIVATE).getBoolean("isMute", false);
            EnableHDMIInAudio(!isMute);
        } else {
            EnableHDMIInAudio(false);
            closeCamera();
        }
        handler.post(() -> noSignalView.setVisibility(isHdmiIn ? View.GONE : View.VISIBLE));
    }

}
