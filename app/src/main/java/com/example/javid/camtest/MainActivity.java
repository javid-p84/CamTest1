package com.example.javid.camtest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {

        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);

    }

    TextureView previewView;
    String cameraId;
    CameraDevice cameraDevice;
    CameraCaptureSession cameraCaptureSessions;
    CaptureRequest.Builder captureRequestBuilder;
    Size imageDimension;
    ImageReader imageReader;
    File file;
    boolean mflashSupported;
    Handler mBackgroundHandler;
    HandlerThread mBackgroundHandlerThread;

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = cameraDevice;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = (TextureView) findViewById(R.id.preview);
        assert previewView != null;

        previewView.setSurfaceTextureListener(textureListener);
        Button btn = (Button) findViewById(R.id.btnTake);
        btn.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this,"Taking pic",Toast.LENGTH_SHORT).show();
                //oast.makeText(this, "Your can use Camera without permission", Toast.LENGTH_SHORT).show();
                takePicture();
            }
        });
    }

    private void takePicture() {
        if (cameraDevice == null)
            return;

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            CameraCharacteristics chars = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpgSizes = null;

            if (chars != null)
                jpgSizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

            int width = 640;
            int height = 480;
            if (jpgSizes != null && jpgSizes.length > 0) {
                width = jpgSizes[0].getWidth();
                height = jpgSizes[0].getHeight();
            }

            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

            List<Surface> outputSurfaces = new ArrayList<>(2);

            outputSurfaces.add(reader.getSurface());

            outputSurfaces.add(new Surface(previewView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(cameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            file = new File(Environment.getExternalStorageDirectory() + UUID.randomUUID().toString());
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);


                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        if (image != null)
                            image.close();


                    }

                }

                private void save(byte[] bytes) throws Exception {
                    OutputStream outputStream = null;
                    try {

                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally {
                        if (outputStream != null)
                            outputStream.close();
                    }

                }

            };

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved " + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();

                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        cameraCaptureSession.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (Exception ex) {
                        ex.printStackTrace();

                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, mBackgroundHandler);

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }

    }

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = previewView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;

                    updatePreview();


                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT);

                }
            }, null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null)
            Toast.makeText(MainActivity.this, "Error in Preview", Toast.LENGTH_LONG);

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }


    private void openCamera() {
        Toast.makeText(MainActivity.this,"Opening the Cam",Toast.LENGTH_SHORT).show();
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            assert map != null;

            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_CAMERA_PERMISSION);

                return;
            }

            manager.openCamera(cameraId, stateCallback, null);


        } catch (Exception ex) {

            Toast.makeText(MainActivity.this,ex.toString(),Toast.LENGTH_SHORT).show();
            ex.printStackTrace();


        }


    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Your can use Camera without permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThreat();

        if (previewView.isAvailable())
            openCamera();
        else
            previewView.setSurfaceTextureListener(textureListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;


        } catch (Exception ex) {
            ex.printStackTrace();

        }
    }

    private void startBackgroundThreat() {

        mBackgroundHandlerThread = new HandlerThread("Camera Background");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());


    }

}
