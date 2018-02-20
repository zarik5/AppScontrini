package com.ing.software.ticketapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;
import static com.ing.software.ticketapp.BillActivity.getCameraInstance;

/**
 * This class is fully developed by Nicola Dal Maso
 */

public class CameraActivity extends Activity {

    private Camera mCamera;
    private SurfaceView mPreview;
    private MediaRecorder mMediaRecorder;
    private byte[] imageData;
    private static  final int FOCUS_AREA_SIZE= 300;
    public static final int MEDIA_TYPE_IMAGE = 1;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);

        initializeComponents();
    }

    /** Dal Maso
     * Initialize all the components
     */
    public void initializeComponents(){
        mCamera = getCameraInstance();
        mCamera.setDisplayOrientation(90);

        //Camera parameters
        Camera.Parameters p = mCamera.getParameters();
        p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        p.setJpegQuality(100);
        List<Camera.Size> sizes = p.getSupportedPictureSizes();
        Camera.Size size = sizes.get(0);
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i).width > size.width)
                size = sizes.get(i);
        }
        p.setPictureSize(size.width, size.height);

        mCamera.setParameters(p);

        final ImageButton flashButton = (ImageButton)findViewById(R.id.flashBtn);
        flashButton.setTag(0);
        flashButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_off));

        //Flash button managment
        flashButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                final int status =(Integer) v.getTag();
                switch (status){
                    //Flash on
                    case 0:
                        v.setTag(1);
                        flashButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_on));
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                        mCamera.setParameters(p);
                        break;
                    //Flash auto
                    case 1:
                        v.setTag(2);
                        flashButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_auto));
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                        mCamera.setParameters(p);
                        break;
                    //Flash off
                    case 2:
                        v.setTag(0);
                        flashButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_off));
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        mCamera.setParameters(p);
                        break;
                }
            }
        });


        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);


        //Camera focus on touch
        mPreview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    focusOnTouch(event);
                }
                return true;
            }
        });

        //Start camera real-time preview
        preview.addView(mPreview);

        //Image capture button
        ImageButton captureButton = (ImageButton) findViewById(R.id.takePhoto_button);
        captureButton.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                     // get an image from the camera
                     mCamera.takePicture(null, null, mPicture);
             }
        });

        //Finish taking photo
        Button finishButton = (Button)findViewById(R.id.btnCheck_goBack);
        finishButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                finish();
            }
        });

    }

    //Focus manage method
    private void focusOnTouch(MotionEvent event) {
        if (mCamera != null ) {

            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.getMaxNumMeteringAreas() > 0){
                Log.i(TAG,"fancy !");
                Rect rect = calculateFocusArea(event.getX(), event.getY());

                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                meteringAreas.add(new Camera.Area(rect, 800));
                parameters.setFocusAreas(meteringAreas);

                mCamera.setParameters(parameters);
                mCamera.autoFocus(mAutoFocusTakePictureCallback);
            }else {
                mCamera.autoFocus(mAutoFocusTakePictureCallback);
            }
        }
    }

    //It calculates the focus area
    private Rect calculateFocusArea(float x, float y) {
        int left = clamp(Float.valueOf((x / mPreview.getWidth()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);
        int top = clamp(Float.valueOf((y / mPreview.getHeight()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);

        return new Rect(left, top, left + FOCUS_AREA_SIZE, top + FOCUS_AREA_SIZE);
    }

    private int clamp(int touchCoordinateInCameraReper, int focusAreaSize) {
        int result;
        if (Math.abs(touchCoordinateInCameraReper)+focusAreaSize/2>1000){
            if (touchCoordinateInCameraReper>0){
                result = 1000 - focusAreaSize/2;
            } else {
                result = -1000 + focusAreaSize/2;
            }
        } else{
            result = touchCoordinateInCameraReper - focusAreaSize/2;
        }
        return result;
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();
        releaseCamera();
        // two new lines for removing the old CameraPreview
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.removeView(mPreview);
    }
    @Override
    public void onResume() {
        super.onResume();
        if (mCamera == null)
        {
            initializeComponents();
        }
    }

    private Camera.AutoFocusCallback mAutoFocusTakePictureCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (success) {
                // do something...
                Log.i("tap_to_focus","success!");
            } else {
                // do something...
                Log.i("tap_to_focus","fail!");
            }
        }
    };

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Singleton.getInstance().setTakenPicure(data);
            Intent intent = new Intent(getApplicationContext(), CheckPhotoActivity.class);
            startActivity(intent);
        }
    };
}
