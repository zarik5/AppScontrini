package com.ing.software.ocrtestapp;


import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.Stream;
import com.ing.software.common.Ref;
import com.ing.software.ocr.ImageProcessor;
import com.ing.software.ocr.OcrManager;
import com.ing.software.ocr.OcrOptions;
import com.ing.software.ocr.OcrTicket;
import com.ing.software.ocr.OcrUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import static com.ing.software.ocrtestapp.StatusVars.*;
import static com.ing.software.ocrtestapp.StatusVars.DATE_RECEIVED;

/**
 * This class analyze all pics in folder 'sdcard/TestOCR' for now it does not handle errors
 * (missing folder, invalid files, subdirectories etc).
 * When floating button is clicked, a new background service is created, with the path of a single
 * file.
 */
public class MainActivity extends AppCompatActivity implements OcrResultReceiver.Receiver {

    final OcrResultReceiver mReceiver = new OcrResultReceiver(new Handler());
    private static OcrManager ocrAnalyzer;
    static private final String testFolder = "/TestOCR";
    private int counter = 0;
    private final PermissionsHandler permissionsHandler = new PermissionsHandler(this);
    private static final Semaphore sem = new Semaphore(0);
    List<String> images = Stream.of(DataSet.dataSet)
            .map(string -> string.first).toList();
    List<BigDecimal> prices = Stream.of(DataSet.dataSet)
            .map(price -> price.second).toList();
    int correct = 0;
    int wrong = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!permissionsHandler.hasStoragePermission())
            permissionsHandler.requestStoragePermission();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ocrAnalyzer = new OcrManager();
        while (ocrAnalyzer.initialize(this) != 0) {
            try {
                Toast.makeText(this, "Downloading library...", Toast.LENGTH_LONG).show();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mReceiver.setReceiver(this);
        OcrUtils.log(1, "MAIN", "STARTING: " + getDate());
        List<File> listFile = loadImage(testFolder);
        final List<String> listNames = new ArrayList<>();
        for (File aFile : listFile)
            listNames.add(aFile.getAbsolutePath());

        findViewById(R.id.run_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (counter == listNames.size()) {
                    ocrAnalyzer.release();
                    ++counter;
                    Snackbar.make(view, "DataAnalyzer released", Snackbar.LENGTH_LONG)
                            .setAction("Service", null).show();
                } else if (counter < listNames.size()) {
                    Intent intent = new Intent(MainActivity.this, TestService.class);
                    intent.putExtra("receiver", mReceiver);
                    ++counter;
                    startService(intent);
                    Snackbar.make(view, "Service started", Snackbar.LENGTH_LONG)
                            .setAction("Service", null).show();
                } else {
                    Snackbar.make(view, "Nothing to analyze", Snackbar.LENGTH_LONG)
                            .setAction("Service", null).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!permissionsHandler.hasStoragePermission())
            permissionsHandler.requestStoragePermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!permissionsHandler.resultGranted(requestCode, permissions, grantResults))
        {
            if (requestCode == PermissionsHandler.REQUEST_STORAGE_CODE)
            {
                String text = "No storage permission" ;
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Shows results from service in a scrollview
     * @param resultCode code received
     * @param resultData bundle associated with code
     */
    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        //ScrollView scrollView = (ScrollView) findViewById(R.id.scroller);
        TextView tv = (TextView) findViewById(R.id.scrollerText);
        List<Pair<String, Boolean>> strings = new ArrayList<>(); // boolean -> true if log error
        switch (resultCode) {
            case STATUS_RUNNING:
                //Toast.makeText(this, "Starting img: " + resultData.getString(IMAGE_RECEIVED), Toast.LENGTH_LONG).show();
                tv.append("\n");
                strings.add(new Pair<>("\nStarting img: " + resultData.getString(IMAGE_RECEIVED) + "--------------------------", false));
                break;
            case STATUS_FINISHED:
                BigDecimal price = null;
                String result = "FAILURE";
                if (images.contains(resultData.getString(IMAGE_RECEIVED))) {
                    int index = images.indexOf(resultData.getString(IMAGE_RECEIVED));
                    price = prices.get(index).setScale(2, RoundingMode.HALF_UP);
                    try {
                        if (price.compareTo(new BigDecimal(resultData.getString(AMOUNT_RECEIVED))) == 0) {
                            result = "SUCCESS";
                            ++correct;
                        } else {
                            result = "WRONG should be: " + price.setScale(2, RoundingMode.HALF_UP);
                            ++wrong;
                        }
                    } catch (Exception e) {
                        //BAh
                    }
                }
                strings.add(new Pair<>("\nAmount is: " + resultData.getString(AMOUNT_RECEIVED), false));
                strings.add(new Pair<>("\nResult is: " + result, !result.equals("SUCCESS")));
                strings.add(new Pair<>("\nDate is: " + resultData.getString(DATE_RECEIVED), false));
                break;
            case STATUS_ERROR:
                /* Handle the error */
                String error = resultData.getString(ERROR_RECEIVED);
                //Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                strings.add(new Pair<>("\nError: " + error, false));
                break;
            case STATUS_AVERAGE:
                strings.add(new Pair<>("\n\nAVERAGE TIME: " + resultData.getString(DURATION_RECEIVED) + " seconds\n"
                    + "Correct = " + correct + "\nWrong = " + wrong, false));
                break;
        }
        for (Pair<String, Boolean> pair : strings) {
            if (pair.second) {
                Log.e("RESULT:", pair.first);
            } else {
                Log.d("RESULT:", pair.first);
            }
            tv.append(pair.first);
        }
        //scrollView.addView(tv);
    }

    /**
     * Get current date, for logging purposes
     * @return current date as string
     */
    private String getDate() {
        Calendar cal = Calendar.getInstance();
        return new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US).format(cal.getTime());
    }

    /**
     * Retrieves list of files in dir
     * @param dir directory containing files. Must be a non empty directory with only files
     * @return list of files in current dir
     */
    static private List<File> loadImage(String dir) {
        File appDir = new File(Environment.getExternalStorageDirectory().toString() + dir);
        if (appDir.isDirectory())
            Log.e("listFileInfolder", "File is directory. Path is: " + appDir.getPath());
        else
            Log.e("listFileInfolder", "File is not directory. Path is: " + appDir.getPath());
        return listFilesInFolder(appDir);
    }

    static public List<File> listFilesInFolder(File folder) {
        List<File> fileInFolder = new ArrayList<>();
        for (File fileEntry : folder.listFiles()) {
            fileInFolder.add(fileEntry);
        }
        Log.e("listFileInfolder", "Files in directory are: " + fileInFolder.size());
        return fileInFolder;
    }


    /**
     * Service to manage requests to analyze tickets
     * When ticket is ready, send message to the receiver.
     */
    public static class TestService extends IntentService {

        public TestService() {
            super("TestService");
        }

        public TestService(String name) {
            super(name);
        }

        @Override
        protected void onHandleIntent(final Intent workIntent) {
            OcrUtils.log(1, "TestService", "Entering service");
            final ResultReceiver receiver = workIntent.getParcelableExtra("receiver");
            final Semaphore sem = new Semaphore(0);
            final Ref<Double> durationSum = new Ref<>(0.0);
            int validBitmaps = 0;
            List<File> listFile = loadImage(MainActivity.testFolder);
            for (File aFile : listFile) {
                String name = aFile.getName();
                Bitmap testBmp = getBitmapFromFile(aFile);
                //test = OcrAnalyzer.getCroppedPhoto(test, this);
                final long startTime = System.nanoTime();
                final Bundle bundle = new Bundle();
                if (testBmp != null) {
                    validBitmaps++;
                    OcrUtils.log(1, "OcrHandler", "____________________________________________________");
                    OcrUtils.log(1, "OcrHandler", "");
                    OcrUtils.log(1, "OcrHandler", "ANALYZING: " + aFile.getName());
                    OcrUtils.log(1, "OcrHandler", "_____________________________________________________");
                    bundle.putString(IMAGE_RECEIVED, aFile.getName());
                    receiver.send(STATUS_RUNNING, bundle);

                    ImageProcessor preproc = new ImageProcessor(testBmp);
                    //String rectString = (err.isEmpty() ? "found" : "not found");
                    //OcrUtils.log(1, "OcrHandler", "Rectangle: " + rectString);
                    //bundle.putString(RECTANGLE_RECEIVED, rectString);
                    OcrOptions options = OcrOptions.getDefault()
                            .priceEditing(OcrOptions.PriceEditing.ALLOW_STRICT)
                            .products(OcrOptions.ProductsSearch.SKIP)
                            .resolution(OcrOptions.Resolution.THIRD)
                            .total(OcrOptions.TotalSearch.DEEP);
                    OcrTicket result = ocrAnalyzer.getTicket(preproc, options);
                    OcrUtils.log(1, "OcrHandler", "Detection complete");
                    long endTime = System.nanoTime();
                    double duration = ((double) (endTime - startTime)) / 1000000000;
                    durationSum.val += duration;
                    if (result.total != null) {
                        OcrUtils.log(1, "OcrHandler", "Amount: " + result.total);
                        bundle.putString(AMOUNT_RECEIVED, result.total.toString());
                        bundle.putString(DURATION_RECEIVED, duration + "");
                    } else {
                        OcrUtils.log(1, "OcrHandler", "No amount found");
                        bundle.putString(AMOUNT_RECEIVED, "Not found.");
                        bundle.putString(DURATION_RECEIVED, duration + "");
                    }
//                    if (result.restoredAmount != null) {
//                        OcrUtils.log(1, "OcrHandler", "Restored Amount: " + result.restoredAmount);
//                        bundle.putString(AMOUNT_RESTORED_RECEIVED, result.restoredAmount.toString());
//                        bundle.putString(DURATION_RECEIVED, duration + "");
//                    } else {
//                        OcrUtils.log(1, "OcrHandler", "No restored amount found");
//                        bundle.putString(AMOUNT_RESTORED_RECEIVED, "Not found.");
//                        bundle.putString(DURATION_RECEIVED, duration + "");
//                    }
                    if (result.date != null) {
                        OcrUtils.log(1, "OcrHandler", "Date: " + result.date.toString());
                        DateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                        String formattedDate = df.format(result.date);
                        OcrUtils.log(1, "OcrHandler", "Formatted Date: " + formattedDate);
                        bundle.putString(DATE_RECEIVED, formattedDate);
                    } else {
                        OcrUtils.log(1, "OcrHandler", "No date found");
                        bundle.putString(DATE_RECEIVED, "Not found.");
                    }
                    receiver.send(STATUS_FINISHED, bundle);
                } else {
                    bundle.putString("ErrorMessage", "Error null image");
                    receiver.send(STATUS_ERROR, bundle);
                }
            }
            final Bundle bundle = new Bundle();
            bundle.putString(DURATION_RECEIVED, String.valueOf(durationSum.val / validBitmaps));
            receiver.send(STATUS_AVERAGE, bundle);
            this.stopSelf();
        }

        private File getFileFromPath(String path) {
            return new File(path);
        }

        /**
         * Decode bitmap from file
         * @param file not null and must be an image
         * @return bitmap from file
         */
        private Bitmap getBitmapFromFile(File file) {
            FileInputStream fis = null;
            try {
                try {
                    fis = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                return BitmapFactory.decodeStream(fis);
            } finally {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
