package com.sh1r0.caffe_android_demo2;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Color;
import android.media.ExifInterface;
import android.graphics.Matrix;

import com.sh1r0.caffe_android_lib.CaffeMobile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.io.FileOutputStream;


public class MainActivity extends Activity implements CNNListener, PreprocessListener {
    private static final String LOG_TAG = "MainActivityMNIST";
    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_IMAGE_SELECT = 200;
    public static final int MEDIA_TYPE_IMAGE = 1;
    private static String[] IMAGENET_CLASSES;

    private Button btnCamera;
    private Button btnSelect;
    private ImageView ivCaptured;
    private TextView tvLabel;
    private Uri fileUri;
    private ProgressDialog dialogPreprocess;
    private ProgressDialog dialog;
    private float[] theta;
    private Bitmap bmp;
    private Bitmap bmpTransformation;
    private CaffeMobile caffeMobile;
    File sdcard = Environment.getExternalStorageDirectory();
    // String modelDir = sdcard.getAbsolutePath() + "/caffe_mobile/bvlc_reference_caffenet";
    // String modelProto = modelDir + "/deploy.prototxt";
    // String modelBinary = modelDir + "/bvlc_reference_caffenet.caffemodel";
    String modelDir = sdcard.getAbsolutePath() + "/caffe_mobile/stcnn_mnist";
    String modelProto = modelDir + "/deploy_not4layers.prototxt";
    String modelBinary = modelDir + "/ST_CNN_vs4layers_iter_100000.caffemodel";
    // String modelProto = modelDir + "/deploy.prototxt";
    // String modelBinary = modelDir + "/ST_CNN_iter_100000.caffemodel";

    static {
        System.loadLibrary("caffe");
        System.loadLibrary("caffe_jni");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivCaptured = (ImageView) findViewById(R.id.ivCaptured);
        tvLabel = (TextView) findViewById(R.id.tvLabel);

        btnCamera = (Button) findViewById(R.id.btnCamera);
        btnCamera.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                initPrediction();
                fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);
                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(i, REQUEST_IMAGE_CAPTURE);
            }
        });

        btnSelect = (Button) findViewById(R.id.btnSelect);
        btnSelect.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                initPrediction();
                Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, REQUEST_IMAGE_SELECT);
            }
        });

        // TODO: implement a splash screen(?
        caffeMobile = new CaffeMobile();
        caffeMobile.setNumThreads(4);
        caffeMobile.loadModel(modelProto, modelBinary);
        caffeMobile.setScale(0.00390625f);

        // float[] meanValues = {104, 117, 123};
        // caffeMobile.setMean(meanValues);

        // AssetManager am = this.getAssets();
        // try {
        //     InputStream is = am.open("synset_words.txt");
        //     Scanner sc = new Scanner(is);
        //     List<String> lines = new ArrayList<String>();
        //     while (sc.hasNextLine()) {
        //         final String temp = sc.nextLine();
        //         lines.add(temp.substring(temp.indexOf(" ") + 1));
        //     }
        //     IMAGENET_CLASSES = lines.toArray(new String[0]);
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }


        initPrediction();
        predictImage("/sdcard/Pictures/MNIST/IMG_20160612_184402.jpg.gray.cropped.darkened.png");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_IMAGE_CAPTURE || requestCode == REQUEST_IMAGE_SELECT) && resultCode == RESULT_OK) {
            String imgPath;

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                imgPath = fileUri.getPath();
            } else {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = MainActivity.this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgPath = cursor.getString(columnIndex);
                cursor.close();
            }

            predictImage(imgPath);
        } else {
            btnCamera.setEnabled(true);
            btnSelect.setEnabled(true);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void predictImage(String imgPath) {
        bmp = BitmapFactory.decodeFile(imgPath);
        Log.d(LOG_TAG, imgPath);
        Log.d(LOG_TAG, String.valueOf(bmp.getHeight()));
        Log.d(LOG_TAG, String.valueOf(bmp.getWidth()));

        dialogPreprocess = ProgressDialog.show(MainActivity.this, "Preprocessing...", "Wait for one sec...", true);
        // imgPath = pre_preprocess(imgPath);
        PreprocessTask preprocessTask = new PreprocessTask(MainActivity.this, MainActivity.this);
        preprocessTask.execute(imgPath);

        // dialog = ProgressDialog.show(MainActivity.this, "Predicting...", "Wait for one sec...", true);

        // CNNTask cnnTask = new CNNTask(MainActivity.this);
        // cnnTask.execute(imgPath);
    }

    private class PreprocessTask extends AsyncTask<String, Void, Integer> {
        private PreprocessListener listener;
        private long startTime;
        private String[] filePaths;
        private MainActivity worker;

        public PreprocessTask(PreprocessListener listener, MainActivity worker) {
            this.listener = listener;
            this.worker = worker;
        }

        @Override
        protected Integer doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            filePaths = worker.pre_preprocess(strings[0]);
            // return Int((int) startTime);
            return null;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            Log.i(LOG_TAG, String.format("elapsed wall time: %d ms", SystemClock.uptimeMillis() - startTime));
            listener.onPreprocessingTaskCompleted(filePaths);
            super.onPostExecute(integer);
        }
    }

    @Override
    public void onPreprocessingTaskCompleted(String[] filePaths) {
        // ivCaptured.setImageBitmap(bmp);
        // tvLabel.setText(IMAGENET_CLASSES[result]);
        // tvLabel.setText("Proprocessing: done");

        // if (dialogPreprocess != null) {
        //     dialogPreprocess.dismiss();
        // }


        // dialog = ProgressDialog.show(MainActivity.this, "Predicting...", "Wait for one sec...", true);

        CNNTask cnnTask = new CNNTask(MainActivity.this, bmp);
        cnnTask.execute(filePaths[filePaths.length - 1]);
    }

    public int[][][] bitmap2int(Bitmap bitmap) {
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        int[][][] res = new int[3][height][width];
        for (int line = 0; line < height; ++line) {
            for (int col = 0; col < width; ++col) {
                int pixel = bitmap.getPixel(col, line);
                res[0][line][col] = Color.red(pixel);
                res[1][line][col] = Color.green(pixel);
                res[2][line][col] = Color.blue(pixel);
            }
        }
        return res;
    }

    public Bitmap int2bitmap(int[][][] pixels) {
        int height = pixels[0].length;
        int width = pixels[0][0].length;
        Bitmap res = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int line = 0; line < height; ++line) {
            for (int col = 0; col < width; ++col) {
                int pixel = Color.rgb(
                    pixels[0][line][col],
                    pixels[1][line][col],
                    pixels[2][line][col]
                );
                res.setPixel(col, line, pixel);
            }
        }
        return res;
    }

    public String[] pre_preprocess(String imgPath) {
        // load from file
        bmp = BitmapFactory.decodeFile(imgPath);

        // rotate if need be
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(imgPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                               ExifInterface.ORIENTATION_UNDEFINED);
        bmp = rotateBitmap(bmp, orientation);

        // Bitmap test = int2bitmap(bitmap2int(bmp));

        // convert to gray
        bmp = toGrayscale(bmp);
        String newPath = "";
        // newPath = saveToFile(bmp, imgPath + ".gray.png");

        // scale down:  4000x3000 => ~90*60
        int height = bmp.getHeight();
        int width = bmp.getWidth();
        if (height != 60 || width != 60) {
            if (height > width) {
                bmp = Bitmap.createScaledBitmap(bmp,
                    // Math.round(60.f * ((float)width / (float)height)),
                    60,
                    Math.round(60.f * ((float)height/(float)width)),
                    true);
            } else if (height < width){
                bmp = Bitmap.createScaledBitmap(bmp,
                    Math.round(60.f * ((float)width / (float)height)),
                    60,
                    // Math.round(60.f * ((float)height/(float)width)),
                    true);
            } else {
                bmp = Bitmap.createScaledBitmap(bmp, 60, 60, true);
            }
        }

        // bmp = toGrayscale(bmp);
        // newPath = saveToFile(bmp, imgPath + ".gray.png");

        // convert to array of pixels
        int[][][] pixels = bitmap2int(bmp);
        height = pixels[0].length;
        width = pixels[0][0].length;

        // crop to square the image
        // make it faster ?: static Bitmap    createBitmap(Bitmap source, int x, int y, int width, int height)
        if (height != width) {
            int newHeight = height;
            int newWidth  = width;
            int fromWidth = 0;
            // int toWidth = width;
            int fromHeight = 0;
            // int toHeight = 0;
            if (height > width) {
                newHeight = width;
                fromHeight = (height - newHeight) / 2;
                // toHeight = height - fromHeight;
            } else {
                newWidth = height;
                fromWidth = (width - newWidth) / 2;
                // toWidth = width - fromWidth;
            }
            Log.i(LOG_TAG, String.format("crop %dx%d to %dx%d", width, height, newWidth, newHeight));

            int[][][] newPixels = new int[3][newHeight][newWidth];
            for (int line = 0; line < newHeight; ++line) {
                for (int col = 0; col < newWidth; ++col) {
                    newPixels[0][line][col] = pixels[0][line + fromHeight][col + fromWidth];
                    newPixels[1][line][col] = pixels[1][line + fromHeight][col + fromWidth];
                    newPixels[2][line][col] = pixels[2][line + fromHeight][col + fromWidth];
                }
            }
            pixels = newPixels;
            height = newHeight;
            width = newWidth;
        }
        // bmp = int2bitmap(pixels);
        // newPath = saveToFile(bmp, imgPath + ".gray.cropped.png");

        // set small values to 0
        final int limit2 = 40;
        final int limit3 = 55;
        for (int line = 0; line < height; ++line) {
            for (int col = 0; col < width; ++col) {
                if (pixels[0][line][col] < 20) {
                    pixels[0][line][col] = 0;
                    pixels[1][line][col] = 0;
                    pixels[2][line][col] = 0;
                } else if (pixels[0][line][col] < limit2) { // if medium low
                    if (line == 0 || line == height-1 || col == 0 || col == width-1) {
                        // if on the border, remove
                        pixels[0][line][col] = 0;
                        pixels[1][line][col] = 0;
                        pixels[2][line][col] = 0;
                    } else if ( pixels[0][line+1][col] < limit3 ||
                                pixels[0][line-1][col] < limit3 ||
                                pixels[0][line][col+1] < limit3 ||
                                pixels[0][line][col-1] < limit3) {
                        // if its neighboors are small as well, then remove it
                        pixels[0][line][col] = 0;
                        pixels[1][line][col] = 0;
                        pixels[2][line][col] = 0;
                    }
                }
            }
        }
        bmp = int2bitmap(pixels);
        newPath = saveToFile(bmp, imgPath + ".gray.cropped.darkened.png");

        return new String[] {newPath};
    }

    protected String saveToFile(Bitmap bmp, String filename) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(filename);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return filename;
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    private void initPrediction() {
        btnCamera.setEnabled(false);
        btnSelect.setEnabled(false);
        tvLabel.setText("");
    }

    private class CNNTask extends AsyncTask<String, Void, Integer> {
        private CNNListener listener;
        private long startTime;
        private Bitmap inputBitmap;

        public CNNTask(CNNListener listener, Bitmap inputBitmap) {
            this.listener = listener;
            this.inputBitmap = inputBitmap;
        }

        @Override
        protected Integer doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            float[] theta = caffeMobile.predictTheta(strings[0], 6);
            Bitmap bmpTransformation = int2bitmap(visualize(bitmap2int(bmp), theta));
            listener.onTaskCompletedTheta(theta, bmpTransformation);
            // listener.onTaskCompletedTheta(theta, inputBitmap);
            return caffeMobile.predictImage(strings[0])[0];
        }

        @Override
        protected void onPostExecute(Integer integer) {
            Log.i(LOG_TAG, String.format("elapsed wall time: %d ms", SystemClock.uptimeMillis() - startTime));
            listener.onTaskCompleted(integer);
            super.onPostExecute(integer);
        }
    }

    @Override
    public void onTaskCompleted(int result) {
        // ivCaptured.setImageBitmap(bmp);
        ivCaptured.setImageBitmap(bmpTransformation);

        // tvLabel.setText(IMAGENET_CLASSES[result]);
        tvLabel.setText(String.valueOf(result));
        btnCamera.setEnabled(true);
        btnSelect.setEnabled(true);

        if (dialogPreprocess != null) {
            dialogPreprocess.dismiss();
        }

        // if (dialog != null) {
        //     dialog.dismiss();
        // }
    }

    @Override
    public void onTaskCompletedTheta(float[] theta, Bitmap bmpTransformation) {
        this.theta = theta;
        this.bmpTransformation = bmpTransformation;
    }

    /**
     * Create a file Uri for saving an image or video
     */
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MNIST");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(LOG_TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
           case ExifInterface.ORIENTATION_ROTATE_90:
               matrix.setRotate(90);
               break;
           case ExifInterface.ORIENTATION_TRANSVERSE:
               matrix.setRotate(-90);
               matrix.postScale(-1, 1);
               break;
           case ExifInterface.ORIENTATION_ROTATE_270:
               matrix.setRotate(-90);
               break;
           default:
               return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        }
        catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    // public void visualize(int[][][] in_img, float[] in_theta, int[][][] out_img) {
    public int[][][] visualize(int[][][] in_img, float[] in_theta) {
        // int[][][] res = new int[3][height][width];
        // int[][][] out_img = new int[in_img.length][in_img[0].length][in_img[0][0].length];
        int[][][] out_img = in_img;
        // final int imgW = 60;
        int imgW = Math.min(in_img[0].length, in_img[0][0].length);
        final int thetaL = 6;
        // float temp1[6];
        float[][] in_coor = new float[40][2];
        float[][] out_coor = new float[40][2];


        char i, j, k;
        Log.d(LOG_TAG, "dimensions " + String.valueOf(in_img.length) + "x" + String.valueOf(in_img[0].length) + "x" + String.valueOf(in_img[0][0].length));
        //out_img = in_img;
        //x = col                      //y = row / line
        // in_coor[0][0] = 0       ; in_coor[0][1] = 0;
        // in_coor[1][0] = 0       ; in_coor[1][1] = imgW - 1;
        // in_coor[2][0] = imgW - 1; in_coor[2][1] = imgW - 1;
        // in_coor[3][0] = imgW - 1; in_coor[3][1] = 0;

        for (i = 0; i < 10; i++){
            in_coor[i     ][0] = 0            ; in_coor[i     ][1] = i * imgW / 10;
            in_coor[i + 10][0] = imgW - 1     ; in_coor[i + 10][1] = i * imgW / 10;
            in_coor[i + 20][0] = i * imgW / 10; in_coor[i + 20][1] = 0            ;
            in_coor[i + 30][0] = i * imgW / 10; in_coor[i + 30][1] = imgW - 1     ;

        }
        // for (i = 0; i < 40; i++) printf("%dth : x : %f, y : %f\n",i, in_coor[i][0], in_coor[i][1]);
        // printf("\n");
        /////////////////////////////////////////////////////////////////////////////////
        for (i = 0; i < thetaL; i++) in_theta[i] /= 2;

        in_theta[2] *= imgW;
        in_theta[5] *= imgW;
        /////////////////////////////////////////////////////////////////////////////////

        for (i = 0; i < 40; i++){
            in_coor[i][0] -= (imgW + 1) / 2;
            in_coor[i][1] -= (imgW + 1) / 2;
            out_coor[i][0] = in_theta[0]*in_coor[i][0] + in_theta[1]*in_coor[i][1] + in_theta[2];
            out_coor[i][1] = in_theta[3]*in_coor[i][0] + in_theta[4]*in_coor[i][1] + in_theta[5];
            out_coor[i][0] += (imgW + 1) / 2;
            out_coor[i][1] += (imgW + 1) / 2;
            out_coor[i][0] = Math.round(out_coor[i][0]);
            out_coor[i][1] = Math.round(out_coor[i][1]);
            if (out_coor[i][0] < 0) out_coor[i][0] = 0;
            if (out_coor[i][1] < 0) out_coor[i][1] = 0;
            if (out_coor[i][0] > imgW-1) out_coor[i][0] = imgW-1;
            if (out_coor[i][1] > imgW-1) out_coor[i][1] = imgW-1;
        }
        /////////////////////////////////////////////////////////////////////////////////

        for (i = 0; i < 40; i++){
            Log.d(LOG_TAG, "out_coor " + String.valueOf(out_coor[i][0]) + " " + String.valueOf(out_coor[i][1]));
            // Log.d(LOG_TAG, "" + String.valueOf());
            // out_img[0][ (int)out_coor[i][1] ][ (int)out_coor[i][0] ] = 255;
            // out_img[1][ (int)out_coor[i][1] ][ (int)out_coor[i][0] ] = 0;
            // out_img[2][ (int)out_coor[i][1] ][ (int)out_coor[i][0] ] = 0;
            out_img[0][ (int)out_coor[i][0] ][ (int)out_coor[i][1] ] = 255;
            out_img[1][ (int)out_coor[i][0] ][ (int)out_coor[i][1] ] = 0;
            out_img[2][ (int)out_coor[i][0] ][ (int)out_coor[i][1] ] = 0;
        }




        // char i, j;

        // //out_img = in_img;
        // //x                       //y
        // in_coor[0][0] = 0       ; in_coor[0][1] = 0;
        // in_coor[1][0] = 0       ; in_coor[1][1] = imgW - 1;
        // in_coor[2][0] = imgW - 1; in_coor[2][1] = imgW - 1;
        // in_coor[3][0] = imgW - 1; in_coor[3][1] = 0;
        // /////////////////////////////////////////////////////////////////////////////////
        // for (i = 0; i < thetaL; i++) in_theta[i] /= 2;

        // in_theta[2] *= imgW;
        // in_theta[5] *= imgW;
        // /////////////////////////////////////////////////////////////////////////////////

        // for (i = 0; i < 4; i++) {
        //     in_coor[i][0] -= (imgW + 1) / 2;
        //     in_coor[i][1] -= (imgW + 1) / 2;
        //     out_coor[i][0] = in_theta[0]*in_coor[i][0] + in_theta[1]*in_coor[i][1] + in_theta[2];
        //     out_coor[i][1] = in_theta[3]*in_coor[i][0] + in_theta[4]*in_coor[i][1] + in_theta[5];
        //     out_coor[i][0] += (imgW + 1) / 2;
        //     out_coor[i][1] += (imgW + 1) / 2;
        //     if (out_coor[i][0] < 0) out_coor[i][0] = 0;
        //     if (out_coor[i][1] < 0) out_coor[i][1] = 0;
        // }
        // /////////////////////////////////////////////////////////////////////////////////

        // for (i = 0; i < 4; i++) {
        //     out_img[0][(int)out_coor[i][1]][(int)out_coor[i][0]] = 255;
        //     out_img[1][(int)out_coor[i][1]][(int)out_coor[i][0]] = 0;
        //     out_img[2][(int)out_coor[i][1]][(int)out_coor[i][0]] = 0;
        // }

        /////////////////////////////////////////////////////////////////////////////////
        //printf("theta : \n");
        //printf("%f %f %f\n", in_theta[0], in_theta[1], in_theta[2]);
        //printf("%f %f %f\n", in_theta[3], in_theta[4], in_theta[5]);
        //printf("\n");

        //printf("x : %f, y : %f\n", out_coor[0][0], out_coor[0][1]);
        return out_img;
    }

}
