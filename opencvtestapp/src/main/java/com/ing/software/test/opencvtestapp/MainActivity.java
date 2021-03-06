package com.ing.software.test.opencvtestapp;

import java.io.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.*;
import android.widget.*;

import com.annimon.stream.function.ThrowableConsumer;

import com.ing.software.common.*;
import com.ing.software.ocr.*;
import com.ing.software.ocr.OcrObjects.*;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.core.Point;

import static android.Manifest.permission.*;
import static android.content.pm.PackageManager.*;
import static android.os.Environment.getExternalStorageDirectory;
import static com.ing.software.common.CommonUtils.*;
import static com.ing.software.common.Reflect.*;
import static org.opencv.core.Core.FONT_HERSHEY_SIMPLEX;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.imgproc.Imgproc.*;
import static java.util.Collections.*;

/**
 * This app is used to test and diagnose the class ImageProcessor and some functions relative to the ocr.
 * The tests are executed against the dataset and photos shot with the app itself.
 * @author Riccardo Zaglia
 */
public class MainActivity extends AppCompatActivity {

    // aliases
    private static final Class<?> IP = ImageProcessor.class;
    private static final Class<?> TF = TestFunctions.class;
    private static final Class<?> OA = OcrAnalyzer.class;
    private static final Class<?> DA = DataAnalyzer.class;

    private static final DecimalFormat NUM_FMT = new DecimalFormat("#.##");


    private static final String FOLDER = "/TestOCR";
    private static final int CAMERA_REQUEST = 110;

    private static final int DEF_THICKNESS = 6;
    private static final int LINE_THICKNESS = 6;
    private static final int FONT_THICKNESS = 2;
    private static final double FONT_SIZE_DEF = 0.6;
    private static final double FONT_SIZE_STRIP = 2.;

    private static final Scalar WHITE = new Scalar(255,255,255, 255);
    private static final Scalar RED = new Scalar(255,0,0, 255);
    private static final Scalar DARK_RED = new Scalar(127,0, 0, 255);
    private static final Scalar ORANGE = new Scalar(255,127, 0, 255);
    private static final Scalar YELLOW = new Scalar(255,255, 0, 255);
    private static final Scalar GREEN = new Scalar(0,255,0, 255);
    private static final Scalar DARK_GREEN = new Scalar(0,127,0, 255);
    private static final Scalar BLUE = new Scalar(0,0,255, 255);
    private static final Scalar PURPLE = new Scalar(255,0,255, 255);
    private static final Scalar BLACK = new Scalar(0,0,0, 255);


    private static final int MSG_TITLE = 0;
    private static final int MSG_IMAGE = 1;
    private static final int MSG_EXCEPTION = 2;

    // bit flags
    private static final int SHOW_ORIGINAL = 1;
    private static final int SHOW_OPENCV = 2;
    private static final int SHOW_OCR = 4;

    private int showFlags = SHOW_ORIGINAL | SHOW_OCR;
    private Semaphore cameraSem = new Semaphore(0);
    private Semaphore datasetSem = new Semaphore(0);
    private int cameraThreads = 0;
    private int imgsTot;
    private int imgIdx = 0;
    private OcrAnalyzer analyzer = null;
    private File tempPhoto = null;

    /**
     * This object is used to execute code on the main thread, from another thread.
     */
    private Handler hdl = new Handler(Looper.getMainLooper(), msg -> {
        try {
            switch (msg.what) {
                case MSG_TITLE:
                    this.setTitle((String) msg.obj);
                    break;
                case MSG_IMAGE:
                    ((ImageView) findViewById(R.id.imageView)).setImageBitmap((Bitmap) msg.obj);
                    break;
                case MSG_EXCEPTION:
                    Toast.makeText(this, "Exception: " + msg.obj, Toast.LENGTH_LONG).show();
                    Log.e("Exception:", (String)msg.obj);
                    break;
                default:
                    return false;
            }
            return true;
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    });

    /**
     * Object used as a replacement for the try-catch-finally construct to reuse the same catch logic.
     */
    ExceptionHandler errHdlr = new ExceptionHandler(e ->
            hdl.obtainMessage(MSG_EXCEPTION, e.toString() + "\n" + e.getMessage()).sendToTarget()
    );

    /**
     * Change appbar text
     * @param str title string
     */
    private void asyncSetTitle(String str) { hdl.obtainMessage(MSG_TITLE, str).sendToTarget(); }

    /**
     * Convert a Bitmap to an OpenCV Mat.
     * @param bm bitmap
     * @return mat
     */
    private static Mat bitmapToMat(Bitmap bm) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bm, mat);
        return mat;
    }

    /**
     * Draw on contour inside Mat
     * @param rgba input RGBA Mat
     * @param contour MatOfPoint
     * @param color Scalar with 4 channels
     * @return output RGBA Mat
     */
    static void drawContour(Mat rgba, MatOfPoint contour, Scalar color, int thickness) {
        List<MatOfPoint> ctrList = Collections.singletonList(contour);
        drawContours(rgba, ctrList, 0, color, thickness);
    }


    /**
     * Draw polygon lines on a Bitmap.
     * @param bm input bitmap
     * @param corns list of corners
     * @param color integer containing channel values in the order: A R G B
     * @return output bitmap
     */
    static Bitmap drawPoly(Bitmap bm, List<PointF> corns, int color, float thickness) {
        Bitmap copy = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(copy);
        c.drawBitmap(bm, 0, 0, null);

        Paint p = new Paint();
        p.setColor(color);
        p.setStrokeWidth(thickness);
        int cornsTot = corns.size();
        for (int i = 0; i < cornsTot; i++) {
            PointF start = corns.get(i), end = corns.get((i + 1) % cornsTot);
            c.drawLine(start.x, start.y, end.x, end.y, p);
        }
        return copy;
    }

    /**
     * Draw a polygon on a mat image
     * @param img in-out RGBA Mat. Not null.
     * @param pts MatOfPoint2f containing the vertices of a polygon
     * @param color line color
     * @param thick line thickness
     */
    static void drawPoly(Mat img, MatOfPoint2f pts, Scalar color, int thick) {
        polylines(img, singletonList(new MatOfPoint(pts.toArray())), true, color, thick);
    }

    /**
     * Convert a list of points to a list containing one MatOfPoint
     * @param pts list of PointF. Not null.
     * @return List of MatOfPoint
     * @throws Exception
     */
    static List<MatOfPoint> pts2matArr(List<PointF> pts) throws Exception {
        List<Point> cvPts = invoke(IP, "androidPtsToCV", pts);
        return singletonList(new MatOfPoint(cvPts.toArray(new Point[4])));
    }

    /**
     * Draw the text of OcrTexts on an image, filling the area containing words and outlining the rows of texts.
     * @param img in-out RGBA Mat. Not null.
     * @param texts list of OcrText. Can be empty. Not null.
     * @param backColor word background color. Not null.
     * @param fontSize font size
     * @throws Exception
     */
    static void drawTextLines(Mat img, List<OcrText> texts, Scalar backColor, double fontSize) throws Exception {
        for (OcrText line : texts) {
            polylines(img, pts2matArr(line.corners()), true, RED, LINE_THICKNESS);
            for (OcrText w : line.children()) {
                fillPoly(img, pts2matArr(w.corners()), backColor);
            }
        }
        for (OcrText line : texts) {
            for (OcrText w : line.children()) {
                PointF blPt = w.corners().get(3); // bottom-right point (clockwise from top-left)
                putText(img, w.textUppercase(), new Point(blPt.x + 3, blPt.y - 3), // add some padding (3, -3)
                        FONT_HERSHEY_SIMPLEX, fontSize, WHITE, FONT_THICKNESS);
            }
        }
    }

    /**
     * Draw lines contained in a MatOfInt4
     * @param rgba in-out RGBA Mat. Not null.
     * @param lines MatOfInt4 containing the lines. Not null.
     * @param color Scalar with 4 channels. Not null.
     */
    static void drawLines(Mat rgba, MatOfInt4 lines, Scalar color) {
        if (lines.rows() > 0) {
            int[] coords = lines.toArray();
            for (int i = 0; i < lines.rows(); i++) {
                line(rgba, new Point(coords[i * 4], coords[i * 4 + 1]),
                        new Point(coords[i * 4 + 2], coords[i * 4 + 3]), color, 3);
            }
        }
    }

    /**
     * Classify a and draw a list of OcrTexts. Class is shown with background color:
     * BLUE: unclassified
     * PURPLE: dates
     * DARK GREEN: total string
     * DARK RED: prices
     * ORANGE: potential or corrupted prices
     * RED: upside down prices
     *
     * @param bm Bitmap. Not null.
     * @param texts list of OcrText. Can be empty. Not null.
     * @param fontSize font size
     * @return OpenCV Mat filled with the bitmap image and the drawn texts
     * @throws Exception
     */
    private Mat classifyAndDrawTexts(Bitmap bm, List<OcrText> texts, double fontSize) throws Exception {
        Mat img = bitmapToMat(bm);

        // first of all, find best amount string and draw strip rect
        List<Scored<Pair<OcrText, Locale>>> amountStrs = invoke(TF, "findAllScoredAmountStrings",
                texts, size(bm));
        if (amountStrs.size() != 0) { //Note: size != 0 is more readable than !...isEmpty()
            OcrText amountStr = max(amountStrs).obj().first;
            RectF amountSripRect = invoke(OA, "getAmountExtendedBox", amountStr, (float)bm.getWidth());
            List<Point> cvPts = invoke(IP, "androidPtsToCV", rectToPts(amountSripRect));
            MatOfPoint2f ptsMat = invoke(IP, "ptsToMat", cvPts);
            drawPoly(img, ptsMat, GREEN, DEF_THICKNESS);
        }

        //draw elements in ascending order of importance

        // draw all texts
        drawTextLines(img, texts, BLUE, fontSize);

        // draw potential prices
        List<OcrText> potPrices = invoke(TF, "findAllPotentialPrices", texts);
        drawTextLines(img, potPrices, ORANGE, fontSize);

        // draw all dates
        List<Triple<OcrText, Date, Locale>> dates = invoke(DA, "findAllDatesRegex", texts, null);
        drawTextLines(img, Stream.of(dates).map(p -> p.first).toList(), PURPLE, fontSize);

        // draw all amount strings
        drawTextLines(img, Stream.of(amountStrs).map(s -> s.obj().first).toList(), DARK_GREEN, fontSize);

        // draw certain prices
        List<Pair<OcrText, BigDecimal>> prices = invoke(DA, "findAllPricesRegex", texts, true);
        drawTextLines(img, Stream.of(prices).map(p -> p.first).toList(), DARK_RED, fontSize);

        // draw upside down prices
        List<OcrText> udPrices = invoke(TF, "findAllUpsideDownPrices", texts);
        drawTextLines(img, udPrices, RED, fontSize);

        return img;
    }

    /**
     * Get number of images in the dataset
     * @return number of images
     */
    private static int getImgsTot() {
        File[] files = new File(getExternalStorageDirectory().toString() + FOLDER).listFiles();
        return files.length;
    }

    /**
     * Get i-th image in the dataset
     * @param idx index
     * @return bitmap
     * @throws FileNotFoundException bitmap not found
     */
    private static Bitmap getBitmap(int idx) throws FileNotFoundException {
        return BitmapFactory.decodeStream(new FileInputStream(
                new File(getExternalStorageDirectory().toString()
                        + FOLDER + "/" + String.valueOf(idx) + ".jpg")));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        analyzer = new OcrAnalyzer();
        errHdlr.tryRun(() ->
            invoke(analyzer, "initialize", this)
        );
        imgsTot = getImgsTot();
        tempPhoto = new File(getExternalStorageDirectory().toString() + "/temp.jpg");

        Consumer<Integer> skip = idxOffset -> {
            imgIdx += idxOffset;
            // fix index bounds
            if (imgIdx < 0) {
                imgIdx = 0;
            } else if(imgIdx >= imgsTot) {
                imgIdx = imgsTot - 1;
            }
            this.setTitle(String.valueOf(imgIdx));
        };
        findViewById(R.id.prev_btn).setOnClickListener(v -> skip.accept(-1));
        findViewById(R.id.next_btn).setOnClickListener(v -> skip.accept(+1));
        findViewById(R.id.prev_fast_btn).setOnClickListener(v -> skip.accept(-10));
        findViewById(R.id.next_fast_btn).setOnClickListener(v -> skip.accept(+10));

        findViewById(R.id.imageView).setOnClickListener(v -> {
            if (cameraThreads > 0) {
                cameraSem.release();
            } else {
                datasetSem.release();
            }
        });
        findViewById(R.id.shot_btn).setOnClickListener(v -> takePicture());

        if (savedInstanceState == null) {
            // request permissions
            if (checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED
                    || checkSelfPermission(CAMERA) != PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] {WRITE_EXTERNAL_STORAGE, CAMERA}, 1);
            } else {
                startDatasetLoop();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = true;
        for (int result : grantResults) {
            allGranted &= (result == PERMISSION_GRANTED);
        }
        if (allGranted)
            startDatasetLoop();
    }

    /**
     * Execute processImage() for the images in the dataset.
     */
    private void startDatasetLoop() {
        new Thread(() -> errHdlr.tryRun(() -> {
            while(true) {
                if(imgIdx >= imgsTot) {
                    imgIdx = imgsTot - 1;
                }
                asyncSetTitle(String.valueOf(imgIdx));
                processImage(getBitmap(imgIdx), bm -> {
                    hdl.obtainMessage(MSG_IMAGE, bm).sendToTarget();
                    datasetSem.acquire();
                });
                imgIdx++;
            }
        })).start();
    }

    /**
     * Process a single image showing every step of the pipeline.
     * show() should stop the method execution until the screen is tapped.
     * This method was build as a monolith to edit the pipeline more easily, but once this method is
     * called, it cannot be canceled halfway in.
     */
    @SuppressWarnings("unchecked")
    private void processImage(Bitmap bm, ThrowableConsumer<Bitmap, Exception> show) throws Exception {
        ThrowableConsumer<Mat, Exception> showMat = img ->
                show.accept(invoke(IP, "matToBitmap", img));

        ImageProcessor imgProc = new ImageProcessor(bm);

        if (check(showFlags, SHOW_ORIGINAL))
            show.accept(bm);

        if (check(showFlags, SHOW_OPENCV)) {
            Mat srcImg = getField(imgProc, "srcImg");
            double shortSide = getField(IP, "SHORT_SIDE");
            Mat grayResized = invoke(IP, "toGrayResized", srcImg, shortSide);
            Swap<Mat> graySwap = new Swap<>(grayResized.clone(), new Mat());
            Mat binary = ((Mat)invoke(IP, "prepareBinaryImg", graySwap)).clone();
            showMat.accept(binary);

            int openingIters = getField(IP, "OPEN_ITERS");
            invoke(IP, "opening", graySwap, openingIters);
            List<Scored<MatOfPoint>> contours =
                    invoke(IP, "findBiggestContours", graySwap, 2);
            int closingIters = getField(IP, "CLOSE_ITERS");
            MatOfPoint contour = ((Scored<MatOfPoint>)invoke(IP, "contourClosing",
                    contours.get(0).obj(), graySwap, closingIters)).obj();

            graySwap.first = binary;
            invoke(IP, "toEdges", graySwap);
            invoke(IP, "removeBackground", graySwap, contour);
            showMat.accept(graySwap.first);

            MatOfInt4 lines = invoke(IP, "houghLines", graySwap);
            MatOfPoint2f corners = invoke(IP, "findPolySimple", contour);

            double angle = invoke(IP, "predominantAngle", lines, new Ref<Double>());

            MatOfPoint2f rect = new MatOfPoint2f();
            if (corners.rows() == 4) {
                corners.copyTo(rect);
            } else {
                rect = invoke(IP, "rotatedBoundingBox", contour, angle, grayResized.size());
            }

            Mat mask = new Mat(grayResized.rows(), grayResized.cols(), CV_8UC1);
            invoke(IP, "maskFromContour", mask, contour);
            double exposureUpThresh = getField(IP, "EXPOSURE_UPPER_THRESH");
            double exposureLowThresh = getField(IP, "EXPOSURE_LOWER_THRESH");
            double exposure = invoke(IP, "getExposure", grayResized, mask);
            double focusThresh = getField(IP, "FOCUS_THRESH");
            double focus = invoke(IP, "getFocus", grayResized, mask);
            double bgThresh = getField(IP, "BG_CONTRAST_THRESH");
            double bgContrast = invoke(IP, "getBackgroundContrast", rect, contour);

            StringBuilder titleStr = new StringBuilder();
            titleStr.append(imgIdx).append(" BGC:").append(NUM_FMT.format(bgContrast));
            if (bgContrast < bgThresh) {
                titleStr.append(" BAD");
            }
            titleStr.append(" F:").append((int)focus);
            if (focus < focusThresh) {
                titleStr.append(" BAD");
            }
            titleStr.append(" E:").append((int)exposure);
            if (exposure < exposureLowThresh) {
                titleStr.append(" UNDER");
            } else if (exposure > exposureUpThresh) {
                titleStr.append(" OVER");
            }
            asyncSetTitle(titleStr.toString());

            // show both contours and hough lines
            // polygon: GREEN if accepted, RED if rejected
            // contours: YELLOW accepted, ORANGE rejected
            // optimized contour: BLUE
            // hough lines: PURPLE
            // final image rectangle: BLACK
            Mat rgbaResized = new Mat();
            cvtColor(grayResized, rgbaResized, COLOR_GRAY2RGBA);
            drawPoly(rgbaResized, rect, BLACK, DEF_THICKNESS);
            drawContour(rgbaResized, contours.get(1).obj(), ORANGE, DEF_THICKNESS);
            drawContour(rgbaResized, contours.get(0).obj(), YELLOW, DEF_THICKNESS);
            drawContour(rgbaResized, contour, BLUE, DEF_THICKNESS);
            drawLines(rgbaResized, lines, PURPLE);
            drawPoly(rgbaResized, corners, corners.rows() == 4 ? GREEN : RED, DEF_THICKNESS);
            showMat.accept(rgbaResized);
        }

        if (check(showFlags, SHOW_OCR)) {
            Bitmap ocrBm = invoke(imgProc, "undistortForOCR", 1. / 3.);
            List<OcrText> texts = invoke(analyzer, "analyze", ocrBm);

            //find amount strings
            List<Scored<Pair<OcrText, Locale>>> amountStrs =
                    invoke(TF, "findAllScoredAmountStrings", texts, size(ocrBm));

            // find dates
            Pair<OcrText, Date> date = invoke(DA, "findDate", texts, null, null);

            // draw
            StringBuilder titleStr = new StringBuilder();
            titleStr.append(imgIdx);
            if (date != null) {
                titleStr.append(" - ")
                        .append(new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(date.second));
            } else {
                titleStr.append(" - date not found or multiple");
            }
            asyncSetTitle(titleStr.toString());
            showMat.accept(classifyAndDrawTexts(ocrBm, texts, FONT_SIZE_DEF));

            // find amount price
            if (amountStrs.size() != 0) {
                OcrText amountStr = max(amountStrs).obj().first;
                RectF srcStripRect =
                        invoke(OA, "getAmountExtendedBox", amountStr, (float)ocrBm.getWidth());
                Bitmap amountStrip =
                        invoke(OA, "getStrip", imgProc, size(ocrBm), amountStr, srcStripRect);
                RectF dstStripRect = rectFromSize(size(amountStrip));
                List<OcrText> stripTextsStripSpace = invoke(analyzer, "analyze", amountStrip);
                List<OcrText> stripTextsBmSpace = Stream.of(stripTextsStripSpace)
                        .map(line -> new OcrText(line, dstStripRect, srcStripRect))
                        .toList();
                BigDecimal price =
                        invoke(TF, "findAmountPrice", stripTextsBmSpace, amountStr, srcStripRect);

                // draw strip
                titleStr = new StringBuilder();
                titleStr.append(imgIdx);
                if (price != null)
                    titleStr.append(" -  ").append(price);
                asyncSetTitle(titleStr.toString());
                showMat.accept(classifyAndDrawTexts(amountStrip, stripTextsStripSpace, FONT_SIZE_STRIP));
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        item.setChecked(!item.isChecked());

        switch (item.getItemId()) {
            case R.id.show_original:
                showFlags = overwriteBits(showFlags, SHOW_ORIGINAL, item.isChecked());
                break;
            case R.id.show_opencv:
                showFlags = overwriteBits(showFlags, SHOW_OPENCV, item.isChecked());
                break;
            case R.id.show_OCR:
                showFlags = overwriteBits(showFlags, SHOW_OCR, item.isChecked());
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Request a photo captured with native camera.
     */
    public void takePicture() {
        errHdlr.tryRun(() -> {
            invoke(StrictMode.class, "disableDeathOnFileUriExposure"); // hack to avoid using FileProvider
            if (!tempPhoto.exists())
                tempPhoto.createNewFile();
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tempPhoto));
            startActivityForResult(cameraIntent, CAMERA_REQUEST);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST && resultCode != 0) {
            new Thread(() -> errHdlr.tryRun(() -> {
                cameraThreads++; // disable unlocking dataset thread

                processImage(BitmapFactory.decodeStream(new FileInputStream(tempPhoto)), bm -> {
                    hdl.obtainMessage(MSG_IMAGE, bm).sendToTarget();
                    cameraSem.acquire();
                });

                tempPhoto.delete();
                cameraThreads--; // enable unlocking dataset thread
            })).start();
        }
    }
}
