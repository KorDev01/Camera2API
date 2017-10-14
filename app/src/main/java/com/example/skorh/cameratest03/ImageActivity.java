package com.example.skorh.cameratest03;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/*
 * This activity shows the image after taking it
 * By pressing the save button the image could be saved
 */
public class ImageActivity extends AppCompatActivity {
    private static final String TAG = "CameraTest01";
    private ImageView imageView;
    private byte[] mBytesPicture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_image);
        getSupportActionBar().hide();

        imageView = (ImageView) findViewById(R.id.imageView);

        if(getIntent() != null){
            Log.i(TAG, "intent is null");
        }

        /*
         * Getting image from MainActivity
         * Not through intent, because the
         * image size is too big
         */
        if (MainActivity.mTakenImageBytes != null){
            byte[] bytes = MainActivity.mTakenImageBytes;
            mBytesPicture = bytes;
            Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
            imageView.setImageBitmap(bitmapImage);
        }

        Button buttonSave = (Button) findViewById(R.id.btn_savepic);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBytesPicture != null) {
                    try {
                        save(mBytesPicture);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });


    }

    /*
     * Returning to MainActivity
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(TAG, "On back clicked");
            finish();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /*
     * Saving the image
     */
    private void save(byte[] bytes) throws IOException {
        File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(bytes);
        } finally {
            if (null != output) {
                output.close();
                Toast.makeText(ImageActivity.this, "Image saved!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
