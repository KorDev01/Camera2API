package com.example.skorh.cameratest03;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;
/*
 * This is the Main Activity of the application, which was
 * created to test different camera functions using camera 2 api
 * Should be started in portrait mode
 * Landscape mode works correctly when rotated anticlockwise
 * This application is device depending, so it could work correctly on some devices
 * but on others it could work incorrect!
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CameraTest03";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    public static byte[] mTakenImageBytes;

    private TextureView mTextureView;
    private TextureView mTextureViewForDrawing;
    private boolean mTextureForDrawingReady;
    private CameraCharacteristics mCameraInfo;
    private Size mViewDimension;
    private String mCameraId;
    protected CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    protected CaptureRequest.Builder mCaptureRequestBuilder;
    private Size mOutputStreamDimension;
    private ImageReader mImageReader;

    /*
     * Handling the textureView, which is responsible for drawing focus area and faces
     */
    private TextureView.SurfaceTextureListener textureForDrawingListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture _surfaceTexture, int _width, int _height) {
            mTextureForDrawingReady = true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture _surfaceTexture, int _width, int _height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture _surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture _surfaceTexture) {

        }
    };

    /*
     * Handling the textureView, which is responsible for showing the preview
     */
    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(final SurfaceTexture _surfaceTexture, int _width, int _height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture _surfaceTexture, int _width, int _height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture _surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture _surfaceTexture) {

        }
    };

    /*
     * Informs about CameraDevice status changes
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice _cameraDevice) {
            Log.i(TAG, "Camera onOpened");
            mCameraDevice = _cameraDevice;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice _cameraDevice) {
            Log.i(TAG, "Camera onDisconnected");
            _cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice _cameraDevice, int i) {
            Log.i(TAG, "Camera onError");
            _cameraDevice.close();
        }
    };

    /*
     * Informs us about status changes of CaptureSession
     */
    private CameraCaptureSession.CaptureCallback mPreviewCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession _session, @NonNull CaptureRequest _request, @NonNull TotalCaptureResult _result) {
            super.onCaptureCompleted(_session, _request, _result);
            Face[] faces = _result.get(CaptureResult.STATISTICS_FACES);
            drawFacesOnTextureForDraw(faces);
        }
    };


    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        //Setting the application to fullscreen mode
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        //Hiding the action bar
        getSupportActionBar().hide();
        //Initializing byte[] to pass image
        //between activities, not possible through
        //an intent because of the big size of the image
        mTakenImageBytes = null;
        //Getting the screen size
        final RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.layout);
        final ViewTreeObserver observer = relativeLayout.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mViewDimension = new Size(relativeLayout.getWidth(), relativeLayout.getHeight());
                    }
                });

        // TextureView is an UI Element, which can be used for representing images
        mTextureView = (TextureView) findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(textureListener);
        //Waiting when TextureView becomes ready
        mTextureForDrawingReady = false;
        //TextureView for drawing focus area and face rectangles
        mTextureViewForDrawing = (TextureView) findViewById(R.id.textureForDrawing);
        mTextureViewForDrawing.setSurfaceTextureListener(textureForDrawingListener);
        mTextureViewForDrawing.setOpaque(false);
        //Button for taking pictures
        Button takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View _view) {
                takePicture();
            }
        });
        //Listening to touches, for setting focus areas
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View _view, MotionEvent _motionEvent) {
                int actionMasked = _motionEvent.getActionMasked();
                if (actionMasked != MotionEvent.ACTION_DOWN) {
                    return false;
                }
                return setFocusArea((int) _motionEvent.getX(), (int) _motionEvent.getY(), _view.getWidth(), _view.getHeight());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        super.onPause();
    }

    /*
     * This method is needed for switching orientations
     * also you need to modify the manifest with this code:
     *  <activity
     *   android:name=".YourActivity"
     *   android:configChanges="orientation|screenSize">
     *  </activity>
     */
    @Override
    public void onConfigurationChanged(Configuration _newConfig) {
        super.onConfigurationChanged(_newConfig);
        Log.i(TAG, "onConfigurationChanged");
        // Checks the orientation of the screen
        setRightProportionToTextureByOrientation();
    }

    /*
     * Receiving the permissions
     */
    @Override
    public void onRequestPermissionsResult(int _requestCode, @NonNull String[] _permissions, @NonNull int[] _grantResults) {
        if (_requestCode == REQUEST_CAMERA_PERMISSION) {
            if (_grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "You need to accept the permission!", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /*
     * This method is responsible for taking a picture
     */
    private void takePicture() {
        Log.i(TAG, "takePicture");
        if (mCameraDevice == null) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            //Getting available sizes of the image
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
            }
            //Setting default values for the image
            int width = 640;
            int height = 480;
            //Choosing the biggest available size
            if (jpegSizes != null && jpegSizes.length > 0) {
                if (jpegSizes[jpegSizes.length - 1].getWidth() > jpegSizes[0].getWidth()){
                    width = jpegSizes[jpegSizes.length - 1].getWidth();
                    height = jpegSizes[jpegSizes.length - 1].getHeight();
                }else{
                    width = jpegSizes[0].getWidth();
                    height = jpegSizes[0].getHeight();
                }
            }
            //Obtaining an imageReader object, which will receive the image from camera
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);
            //Create a request for taking an image
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            //set effect here if needed
            //Checking rotation, to get the image in right rotation mode
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Log.i(TAG, "Rotation" + rotation);
            int[] orientations = {90, 0, 270, 180};

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientations[rotation]);
            //Listening for the image
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader _imageReader) {
                    Image image = null;
                    try {
                        //Getting image from reader
                        image = _imageReader.acquireLatestImage();
                        //Converting image to byte array
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        //Passing bytes to activity not through intent because of the picture size
                        buffer.get(bytes);
                        mTakenImageBytes = bytes;
                        Intent intent = new Intent(MainActivity.this, ImageActivity.class);
                        startActivity(intent);
                    } finally {
                        if (image != null) {
                            //Close image to free the image for reuse
                            image.close();
                        }
                    }
                }
            };
            //Set the listener to wait when the image is ready
            reader.setOnImageAvailableListener(readerListener, null);
            //Create a capture session to take an image
            mCameraDevice.createCaptureSession(Arrays.asList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession _session) {
                    try {
                        //Capture the image
                        //Callback is null, because we listen for the image through imageReader,
                        //which contains a surface, where the image will be send to
                        _session.capture(captureBuilder.build(), null, null);
                    } catch (CameraAccessException _e) {
                        _e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession _session) {
                }
            }, null);
        } catch (CameraAccessException _e) {
            _e.printStackTrace();
        }
    }

    /**
     * This method is used to open the camera
     * This allows us to interact with the cameraDevice
     */
    private void openCamera() {
        Log.i(TAG, "openCamera");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //Permissions
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            //Getting the cameraId of the back camera
            mCameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            mCameraInfo = characteristics;
            //Getting supported effect by the camera
            int[] effects = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
            for (int i : effects) {
                Log.i(TAG, "Effect supported: " + i);
            }
            //Getting the image dimensions
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
            int maxWidth = 0;
            int count = 0;
            int index = 0;
            for(Size size : outputSizes){
                if(maxWidth < size.getWidth()){
                    maxWidth = size.getWidth();
                    index = count;
                }
                count++;
            }
            mOutputStreamDimension = map.getOutputSizes(SurfaceTexture.class)[index];
            Log.i(TAG, "Output size: " + mOutputStreamDimension.getWidth() + "/" + mOutputStreamDimension.getHeight());
            setRightProportionToTexture();
            //Connecting to the camera
            manager.openCamera(mCameraId, stateCallback, null);
        } catch (CameraAccessException _e) {
            _e.printStackTrace();
        }
    }

    /*
     * Prepares a capture request for preview
     */
    private void createCameraPreview() {
        Log.i(TAG, "createCameraPreview");
        try {
            //Getting Texture of the TextureView
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            //Creating a Surface
            Surface surface = new Surface(texture);
            //Creating a request for the camera
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface);
            //Creating a capture session
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (mCameraDevice == null) {
                        return;
                    }
                    mCameraCaptureSession = cameraCaptureSession;
                    //Configuring the request
                    //Setting auto-exposure, auto-white-balance and auto-focus to automatic mode
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    //Setting the mode to recognize faces
                    mCaptureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                    //Setting filters
                    /*
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE);
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SEPIA);
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD);
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD);
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE);
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_AQUA);
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE);
                    */
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Config change", Toast.LENGTH_SHORT).show();
                }
            }, null);

        } catch (CameraAccessException _e) {
            _e.printStackTrace();
        }
    }

    /*
     * This method updates the capture request for the preview
     * and sets a repeating request
     */
    protected void updatePreview() {
        Log.i(TAG, "updatePreview");
        if (mCameraDevice == null) {
            Log.e(TAG, "Camera dev. is null, error");
            return;
        }
        try {
            //Stop the previous request
            mCameraCaptureSession.stopRepeating();
            //Set the new request
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), mPreviewCallback, null);
        } catch (CameraAccessException _e) {
            _e.printStackTrace();
        }
    }

    private void closeCamera() {
        Log.i(TAG, "closeCamera");
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    /*
     * This method is invoked when the orientation is changed
     * and sets new dimensions of the view
     */
    private void setRightProportionToTextureByOrientation() {
        Log.i(TAG, "setRightProportionToTextureByOrientation");
        //Changing width and height values
        int newViewWidth = mViewDimension.getHeight();
        int newViewHeight = mViewDimension.getWidth();
        mViewDimension = new Size(newViewWidth, newViewHeight);
        Size newSize = setRightProportionToTexture();
        transformImage(newSize.getWidth(), newSize.getHeight());
        //Delete focus area or faces from TextureView
        clearTextureForDraw();
        updatePreview();
    }

    /*
     * This method sets new dimensions to textureView
     */
    private Size setRightProportionToTexture() {
        Log.i(TAG, "setRightProportionToTexture");
        //Setting right proportion, works only for back camera
        //For front camera it may not work perfectly
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        Size[] sizes = new Size[0];
        try {
            sizes = manager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
        } catch (CameraAccessException _e) {
            _e.printStackTrace();
        }
        float maxCameraWidth = sizes[sizes.length - 1].getWidth();
        float maxCameraHeight = sizes[sizes.length - 1].getHeight();
        //Depending on device, in which order the values are sorted
        if (sizes[0].getWidth() > sizes[sizes.length - 1].getWidth()) {
            maxCameraWidth = sizes[0].getWidth();
            maxCameraHeight = sizes[0].getHeight();
        }

        //For portrait
        double scale = Math.min(((float) mViewDimension.getWidth() / maxCameraHeight), ((float) mViewDimension.getHeight() / maxCameraWidth));
        int viewWidth = (int) (maxCameraHeight * scale);
        int viewHeight = (int) (maxCameraWidth * scale);
        //For Landscape
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            scale = Math.min(((float) mViewDimension.getWidth() / maxCameraWidth), ((float) mViewDimension.getHeight() / maxCameraHeight));
            viewWidth = (int) (maxCameraWidth * scale);
            viewHeight = (int) (maxCameraHeight * scale);
        }
        //Setting calculated dimensions to TextureView
        mTextureView.setLayoutParams(new RelativeLayout.LayoutParams(viewWidth, viewHeight));
        return new Size(viewWidth, viewHeight);
    }

    /**
     * This method is needed to transform the image
     * to the sizes of the textureView. Without this method
     * the image on the textureView would be correct in portrait mode
     * but in landscape mode it would stretched and false oriented
     *
     * @param _width
     * @param _height
     */
    private void transformImage(int _width, int _height) {
        Log.i(TAG, "transform image");
        if (mOutputStreamDimension == null || mTextureView == null) {
            Log.i(TAG, "imgDim or textureView is null");
            return;
        }
        Matrix matrix = new Matrix();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, _width, _height);
        RectF previewRectF = new RectF(0, 0, mOutputStreamDimension.getHeight(), mOutputStreamDimension.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) _width / mOutputStreamDimension.getWidth(), (float) _height / mOutputStreamDimension.getHeight());
            matrix.postScale(1, 1, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /*
     * This focus sets the area where the camera should focus
     * int _touchX the point on the X axis where the user touched
     * int _touchY the point on the Y axis where the user touched
     * int _viewW the width of the TextureView, where the image from the camera is displayed
     * int _viewH the height of the TextureView, where the image from the camera is displayed
     */
    private boolean setFocusArea(int _touchX, int _touchY, int _viewW, int _viewH) {
        Log.i(TAG, "setFocusArea");
        //Get the sensor dimension from camera
        final Rect sensorArraySize = mCameraInfo.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        //Position for the focus rectangle the left upper corner
        int x = 0;
        int y = 0;
        //Define the area for the focus
        int halfTouchWidth = 150;
        int halfTouchHeight = halfTouchWidth;
        // Draw the area where the focus will be set
        drawFocusAreaOnTextureForDraw(new Point(_touchX, _touchY), halfTouchWidth);
        //Calculate the the position for the sensor depending on orientation
        //This is necessary because of different dimensions of the textureView
        //and camera sensor
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            x = (int) (_touchX * ((float) sensorArraySize.width() / (float) _viewW));
            y = (int) (_touchY * ((float) sensorArraySize.height() / (float) _viewH));
        } else {
            y = (int) ((_viewW - _touchX) * ((float) sensorArraySize.height() / (float) _viewW));
            x = (int) (_touchY * ((float) sensorArraySize.width() / (float) _viewH));
        }
        //Create a rectangle for the camera, which represents the focus area
        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth, 0),
                Math.max(y - halfTouchHeight, 0),
                halfTouchWidth * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX);

        //Create a callback
        CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                try {
                    //Set a request with selected focus area
                    mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.e(TAG, "Manual AF failure: " + failure);
            }
        };

        try {
            //Stop the previous repeating request
            mCameraCaptureSession.stopRepeating();
            //Check if device support auto focus, the amount of regions should be
            //at least one
            if (mCameraInfo.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1) {
                //Set the selected focus area to the request
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
            }
            //Set auto-exposure, auto white balance and auto focus, to automatic mode
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            //Sets focus only when triggered
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            //Triggers the auto focus and locks it after focusing
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            //Build the request
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), captureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return true;
    }

    /*
     * Drawing current focus area
     * The focus area is a rectangle, but is drawn as a circle
     * Point _point where the user has tapped
     * int _radius the radius of the focus area
     */
    private void drawFocusAreaOnTextureForDraw(Point _point, int _radius) {
        if (mTextureForDrawingReady) {
            Canvas canvas = mTextureViewForDrawing.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5.0f);
                canvas.drawCircle(_point.x, _point.y, _radius, paint);
            } else {
                Log.i(TAG, "Canvas null");
            }
            mTextureViewForDrawing.unlockCanvasAndPost(canvas);
        } else {
            Log.e(TAG, "TextureForDrawing not ready");
        }
    }

    /*
     * Drawing detected faces
     * _faces an array of Face with all faces, which should be drawn as
     *  a rect with points for eyes and mouth
     */
    private void drawFacesOnTextureForDraw(Face[] _faces) {
        if (mTextureForDrawingReady) {
            Canvas canvas = mTextureViewForDrawing.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                for (Face face : _faces) {
                    Paint paint = new Paint();
                    paint.setColor(Color.GREEN);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5.0f);
                    Rect rect = face.getBounds();
                    Point leftEye = face.getLeftEyePosition();
                    Point rightEye = face.getRightEyePosition();
                    Point mouth = face.getMouthPosition();

                    Rect sensor = mCameraInfo.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                    double scaleX = (double) mTextureView.getWidth() / (double) sensor.right;
                    double scaleY = (double) mTextureView.getHeight() / (double) sensor.bottom;
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        scaleX = (double) mTextureView.getWidth() / (double) sensor.bottom;
                        scaleY = (double) mTextureView.getHeight() / (double) sensor.right;
                    }
                    //In Landscape: calculating from sensor to texture dimensions
                    int x = (int) ((double) rect.left * scaleX);
                    int y = (int) ((double) rect.top * scaleY);
                    int width = (int) ((double) rect.width() * scaleX);
                    int height = (int) ((double) rect.height() * scaleY);
                    if (leftEye != null && rightEye != null && mouth != null) {
                        leftEye = new Point((int) (leftEye.x * scaleX), (int) (leftEye.y * scaleY));
                        rightEye = new Point((int) (rightEye.x * scaleX), (int) (rightEye.y * scaleY));
                        mouth = new Point((int) (mouth.x * scaleX), (int) (mouth.y * scaleY));
                    }
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        int swap = x;
                        x = mTextureView.getWidth() - y - width;
                        y = swap;
                        if (leftEye != null && rightEye != null && mouth != null) {
                            leftEye = new Point(mTextureView.getWidth() - leftEye.y, leftEye.x);
                            rightEye = new Point(mTextureView.getWidth() - rightEye.y, rightEye.x);
                            mouth = new Point(mTextureView.getWidth() - mouth.y, mouth.x);
                        }
                    }
                    canvas.drawRect(x, y, x + width, y + height, paint);
                    if (leftEye != null && rightEye != null && mouth != null) {
                        canvas.drawPoint((float) leftEye.x, (float) leftEye.y, paint);
                        canvas.drawPoint((float) rightEye.x, (float) rightEye.y, paint);
                        canvas.drawPoint((float) mouth.x, (float) mouth.y, paint);
                    }
                }
            } else {
                Log.i(TAG, "Canvas null");
            }
            mTextureViewForDrawing.unlockCanvasAndPost(canvas);
        } else {
            Log.e(TAG, "TextureForDrawing not ready");
        }
    }

    /*
     * Clears the TextureView, deletes focus areas and faces
     */
    private void clearTextureForDraw() {
        Canvas canvas = mTextureViewForDrawing.lockCanvas();
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        mTextureViewForDrawing.unlockCanvasAndPost(canvas);
    }
}
