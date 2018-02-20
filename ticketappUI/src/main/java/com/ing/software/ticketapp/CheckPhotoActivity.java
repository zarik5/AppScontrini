package com.ing.software.ticketapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.ing.software.ocr.ImageProcessor;
import com.ing.software.ocr.OcrManager;
import com.ing.software.ocr.OcrOptions;
import com.ing.software.ocr.OcrTicket;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import database.DataManager;
import database.MissionEntity;
import database.SettingsEntity;
import database.TicketEntity;

/**
 * This class is fully developed by Nicola Dal Maso
 */

public class CheckPhotoActivity extends Activity {

    OcrManager ocrManager;
    String root;
    DataManager DB;
    OcrTicket OCR_result;
    EditText checkName;
    EditText checkPrice;
    EditText checkPeople;
    CheckBox checkRefundable;
    Bitmap finalBitmap;
    ProgressBar waitOCR;
    Button btnOK;
    Date dateTicket;
    Button btnRedo;
    ImageView checkPhotoView;
    MissionEntity ticketMission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_check_photo);
        //Activity operations
        initializeComponents();
        setFinalBitmap();
        setPhotoTaken();
        startOCRProcess();
    }

    /** Dal Maso
     * Initalize all components
     */
    private void initializeComponents(){
        root = getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString();
        DB = new DataManager(this.getApplicationContext());
        //elements initialize
        checkPhotoView = (ImageView)findViewById(R.id.checkPhoto_image);
        checkPrice = (EditText)findViewById(R.id.input_checkTotal);
        checkPeople = (EditText)findViewById(R.id.input_numPeople);
        checkName = (EditText)findViewById(R.id.input_checkName);
        btnRedo = (Button)findViewById(R.id.btnCheck_retry);
        checkRefundable = (CheckBox)findViewById(R.id.check_Refundable);
        btnOK = (Button)findViewById(R.id.btnCheck_allow);
        waitOCR = (ProgressBar)findViewById(R.id.progressBarOCR);
        waitOCR.setVisibility(View.VISIBLE);
        ticketMission = DB.getMission(Singleton.getInstance().getMissionID());

        //OCR initialize
        ocrManager = new OcrManager();

        //addOCRSettings();

        while (ocrManager.initialize(this) != 0) { // 'this' is the context
            try {
                //On first run vision library will be downloaded
                Toast.makeText(this, getResources().getString(R.string.downLibrary), Toast.LENGTH_LONG).show();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //Redo photo button
        btnRedo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        //Save photo button
        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveThisTicket(finalBitmap);
                finish();
            }
        });
        //Waiting OCR
        btnOK.setClickable(false);
    }

    /** Dal Maso
     * set the bitmap to the imageview
     */
    private void setPhotoTaken(){
        Display display = getWindowManager().getDefaultDisplay();
        checkPhotoView.setImageBitmap(Bitmap.createScaledBitmap(finalBitmap, display.getWidth(), display.getHeight(), true));
    }

    /** Dal Maso
     * rotate the bitmap of 90 degrees
     */
    private void setFinalBitmap(){
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap btm = AppUtilities.fromByteArrayToBitmap(Singleton.getInstance().getTakenPicture());
        /*
        Glide.with(this)
                .load(Singleton.getInstance().getTakenPicture())
                .asBitmap()
                .transform(new MyTransformation(this, 90))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(checkPhotoView);
                */
        finalBitmap = Bitmap.createBitmap(btm, 0, 0, btm.getWidth(), btm.getHeight(), matrix, true);
    }

    private OcrOptions getOcrOptions() {
        List<SettingsEntity> settingsList = DB.getAllSettings();
        if (settingsList.isEmpty() || settingsList.get(0) == null)
            return OcrOptions.getDefault();
        SettingsEntity setting = settingsList.get(0);
        OcrOptions options = OcrOptions.getDefault();
        if (setting.isAutomaticCorrectionAmountOCR())
            options = options.priceEditing(OcrOptions.PriceEditing.ALLOW_VOID);
        if (setting.isSearchUpDownOCR())
            options = options.orientation(OcrOptions.Orientation.ALLOW_UPSIDE_DOWN);
        switch (setting.getAccuracyOCR()) {
            case (0) : options = options.resolution(OcrOptions.Resolution.THIRD);
            case (1) : options = options.resolution(OcrOptions.Resolution.HALF);
            case (2) : options = options.resolution(OcrOptions.Resolution.NORMAL);
            default: Log.d("Options Resolution", "Not set");
        }
        switch (setting.getCurrencyDefault()) {
            case ("EUR"):
                options = options.suggestedCountry(Locale.ITALY);
                break;
            case ("USD"):
                options = options.suggestedCountry(Locale.US);
                break;
            case ("GBP"):
                options = options.suggestedCountry(Locale.UK);
                break;
        }
        return options;
    }

    /** Dal Maso
     * OCR photo analyzing and values set
     */
    private void startOCRProcess(){
        // OCR asynchronous implementation:
        ImageProcessor imgProc = new ImageProcessor(finalBitmap);
        ocrManager.getTicket(imgProc, getOcrOptions(), result -> {
            //Thread UI control reservation
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(!result.errors.isEmpty()) {
                        Toast.makeText(getApplicationContext(), result.errors.toString().replace("_", " ").replace("[", "").replace("]", ""), Toast.LENGTH_SHORT).show();
                    }
                    if(result.total != null) {
                        checkPrice.setText(result.total.toString());
                    }
                    if(OCR_result.date != null) {
                        dateTicket = OCR_result.date;
                        //Ticket date < Mission date start, it advises the user
                        if(OCR_result.date.before(ticketMission.getStartDate())){
                            Toast.makeText(getApplicationContext(), getResources().getString(R.string.dateTicketMin), Toast.LENGTH_SHORT).show();
                        }
                        //Ticket date > Mission date finish, it advises the user
                        if(OCR_result.date.after(ticketMission.getEndDate())){
                            Toast.makeText(getApplicationContext(), getResources().getString(R.string.dateTicketMax), Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        dateTicket = ticketMission.getStartDate();
                    }
                    waitOCR.setVisibility(View.INVISIBLE);
                }
            });
            OCR_result = result;
            //enable save button
            btnOK.setClickable(true);
        });
    }

    /** Dal Maso
     * Save the image and one original copy
     * @param imageToSave image to save
     */
    public void saveThisTicket(Bitmap imageToSave){
        //avoid multiple saves
        btnOK.setClickable(false);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        String fname = imageFileName+".jpg";
        File file = new File(root, fname);
        File originalPhoto = new File(root,fname+"orig");
        final Uri uri=Uri.fromFile(file);
        if (file.exists())
            file.delete();
        if(originalPhoto.exists())
            originalPhoto.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            FileOutputStream outOriginal = new FileOutputStream(originalPhoto);
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");

            TicketEntity thisTicket = new TicketEntity();

            thisTicket.setDate(dateTicket);

            thisTicket.setTagPlaces(Short.parseShort(checkPeople.getText().toString()));

            if(checkRefundable.isChecked()){
                thisTicket.setRefundable(true);
            }
            else{
                thisTicket.setRefundable(false);
            }

            thisTicket.setFileUri(uri);
            try {
                thisTicket.setAmount(BigDecimal.valueOf(Double.parseDouble(checkPrice.getText().toString())));
            }
            catch (Exception e){
                thisTicket.setAmount(null);
            }
            thisTicket.setTitle(checkName.getText().toString());

            thisTicket.setMissionID(Singleton.getInstance().getMissionID());
            long id = DB.addTicket(thisTicket);

            imageToSave.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            imageToSave.compress(Bitmap.CompressFormat.JPEG,100, outOriginal);
            outOriginal.flush();
            outOriginal.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Dal Maso
     * Set all OCR settings from db
     */
    private void addOCRSettings(){
        if(DB.getAllSettings().size() != 0){
            SettingsEntity settings = DB.getAllSettings().get(0);

            switch (settings.getAccuracyOCR()){
                case (0):
                    OcrOptions.getDefault().resolution(OcrOptions.Resolution.THIRD);
                    break;
                case (1):
                    OcrOptions.getDefault().resolution(OcrOptions.Resolution.HALF);
                    break;
                case (2):
                    OcrOptions.getDefault().resolution(OcrOptions.Resolution.NORMAL);
                    break;
            }

            switch (settings.getCurrencyDefault()){
                case ("EUR"):
                    OcrOptions.getDefault().suggestedCountry(Locale.ITALY);
                    break;
                case ("USD"):
                    OcrOptions.getDefault().suggestedCountry(Locale.US);
                    break;
                case ("GBP"):
                    OcrOptions.getDefault().suggestedCountry(Locale.UK);
                    break;
            }

            if(settings.isAutomaticCorrectionAmountOCR()){
                OcrOptions.getDefault().priceEditing(OcrOptions.PriceEditing.ALLOW_STRICT);
            }
            else {
                OcrOptions.getDefault().priceEditing(OcrOptions.PriceEditing.SKIP);
            }

            if(settings.isSearchUpDownOCR()){
                OcrOptions.getDefault().orientation(OcrOptions.Orientation.ALLOW_UPSIDE_DOWN);
            }
            else{
                OcrOptions.getDefault().orientation(OcrOptions.Orientation.NORMAL);
            }
        }
    }

    /*
    public class MyTransformation extends BitmapTransformation

    {
        private int mOrientation;

        public MyTransformation(Context context, int orientation){
        super(context);
        mOrientation = orientation;
    }

        @Override
        protected Bitmap transform (BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight)
        {
            int exifOrientationDegrees = getExifOrientationDegrees(mOrientation);
            return TransformationUtils.rotateImageExif(toTransform, pool, exifOrientationDegrees);
        }

    private int getExifOrientationDegrees(int orientation) {
        int exifInt;
        switch (orientation) {
            case 90:
                exifInt = ExifInterface.ORIENTATION_ROTATE_90;
                break;
            default:
                exifInt = ExifInterface.ORIENTATION_NORMAL;
                break;
        }
        return exifInt;
    }

        @Override
        public String getId() {
            return mOrientation + "";
        }
    }
    */
}
