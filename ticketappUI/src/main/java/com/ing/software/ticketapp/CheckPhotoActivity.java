package com.ing.software.ticketapp;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
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

import com.bumptech.glide.Glide;
import com.ing.software.common.Ref;
import com.ing.software.ocr.IPError;
import com.ing.software.ocr.ImageProcessor;
import com.ing.software.ocr.OcrError;
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
import java.util.Set;

import database.DataManager;
import database.MissionEntity;
import database.SettingsEntity;
import database.TicketEntity;

import static java.util.Arrays.asList;
import static com.ing.software.ocr.OcrOptions.*;

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
    Button btnRedo;
    ImageView checkPhotoView;
    MissionEntity ticketMission;
    SettingsEntity settings;
    Button currencyBtn;

    TicketEntity thisTicket = new TicketEntity();
    String currency;

    String[] choices = new String[] {"EUR", "GBP", "USD"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_check_photo);
        //Activity operations
        initializeComponents();
        new Thread(() -> {
            setFinalBitmap();
            runOCRProcess();
        }).start();
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
        currencyBtn = findViewById(R.id.currency_btn);
        long missionId = getIntent().getExtras().getLong(IntentCodes.INTENT_MISSION_ID);
        ticketMission = DB.getMission(missionId);


        settings = DB.getAllSettings().get(0);

        currency = settings.getCurrencyDefault();
        currencyBtn.setText(currency);

        currencyBtn.setOnClickListener(view -> {
            Ref<Integer> currentChoice = new Ref<>(asList(choices).indexOf(currency));
            new AlertDialog.Builder(CheckPhotoActivity.this)
                    .setTitle("Scegli la valuta del totale")
                    .setSingleChoiceItems(choices, currentChoice.val, (dialog, which) -> {
                        currency = choices[which];
                        currencyBtn.setText(currency);
                        dialog.dismiss();
                    })
                    .show();
        });



        checkPrice.setOnFocusChangeListener((view, b) -> checkPrice.setTextColor(Color.WHITE));


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
                saveThisTicket();
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

//        Glide.with(this)
//                .load(Singleton.getInstance().getTakenPicture())
//                .skipMemoryCache(true)
//                .into(checkPhotoView);
        checkPhotoView.setImageBitmap(Bitmap.createScaledBitmap(finalBitmap, display.getWidth(), display.getHeight(), true));
    }

    private void setFinalBitmap(){
        finalBitmap = AppUtilities.fromByteArrayToBitmap(Singleton.getInstance().getTakenPicture());
    }

    /** Dal Maso
     * rotate the bitmap of 90 degrees
     */
    private void rotateBitmap(boolean upsideDown) {
        Matrix matrix = new Matrix();
        matrix.postRotate(upsideDown ? -90 : 90);
        /*
        Glide.with(this)
                .load(Singleton.getInstance().getTakenPicture())
                .asBitmap()
                .transform(new MyTransformation(this, 90))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(checkPhotoView);
                */
        finalBitmap = Bitmap.createBitmap(finalBitmap, 0, 0, finalBitmap.getWidth(), finalBitmap.getHeight(), matrix, false);

    }

    private OcrOptions getOcrOptions() {
        OcrOptions options = OcrOptions.getDefault()
                .total(TotalSearch.DEEP)
                .date(DateSearch.NORMAL)
                .products(ProductsSearch.SKIP);

        if (settings.isAutomaticCorrectionAmountOCR())
            options.priceEditing(PriceEditing.ALLOW_STRICT);
        else
            options.priceEditing(PriceEditing.SKIP);

        if (settings.isSearchUpDownOCR())
            options = options.orientation(Orientation.ALLOW_UPSIDE_DOWN);
        switch (settings.getAccuracyOCR()) {
            case (0) : options.resolution(Resolution.THIRD);
            case (1) : options.resolution(Resolution.HALF);
            case (2) : options.resolution(Resolution.NORMAL);
            default: Log.d("Options Resolution", "Not set");
        }
        options.suggestedCountry(AppUtilities.currencyAbbrevToLocale(settings.getCurrencyDefault()));
        return options;
    }

    private void drawTotalRectangle(RectF rect) {

    }

    /** Dal Maso
     * OCR photo analyzing and values set
     */
    private void runOCRProcess(){
        // OCR asynchronous implementation:
        ImageProcessor imgProc = new ImageProcessor(finalBitmap);
        imgProc.rotateImage(1);
        List<IPError> procErrors = imgProc.findTicket(false);

        double marginMul = 0.1;
        Bitmap bm = imgProc.undistort(marginMul, getWindowManager().getDefaultDisplay().getWidth());
        runOnUiThread(() -> {
            checkPhotoView.setImageBitmap(bm);
            if (procErrors.contains(IPError.OUT_OF_FOCUS)) {
                Toast.makeText(getApplicationContext(), "Lo scontrino e' sfuocato", Toast.LENGTH_SHORT).show();
            }
            if (procErrors.contains(IPError.OVEREXPOSED)) {
                Toast.makeText(getApplicationContext(), "Lo scontrino e' sovraesposto", Toast.LENGTH_SHORT).show();
            }
            if (procErrors.contains(IPError.UNDEREXPOSED)) {
                Toast.makeText(getApplicationContext(), "Lo scontrino e' troppo scuro", Toast.LENGTH_SHORT).show();
            }
            if (procErrors.contains(IPError.POOR_BG_CONTRAST)) {
                Toast.makeText(getApplicationContext(), "Contrasto con lo sfondo troppo basso", Toast.LENGTH_SHORT).show();
            }
        });
        OcrTicket result = ocrManager.getTicket(imgProc, getOcrOptions());
        OCR_result = result;
        Set<OcrError> errors = result.errors;
        //Thread UI control reservation
        runOnUiThread(() -> {
//            if(!result.errors.isEmpty()) {
//                Toast.makeText(getApplicationContext(), result.errors.toString().replace("_", " ").replace("[", "").replace("]", ""), Toast.LENGTH_SHORT).show();
//            }
            if(result.total != null) {
                checkPrice.setText(result.total.toString());

                if(errors.contains(OcrError.UNCERTAIN_TOTAL) || errors.contains(OcrError.TOTAL_EDITED)) {
                    checkPrice.setTextColor(Color.RED);
                    Toast.makeText(getApplicationContext(), "Il totale potrebbe essere sbagliato", Toast.LENGTH_SHORT).show();
                }

                if (result.totalRect != null) {
                    drawTotalRectangle(result.totalRect);
                }

            }
            if(result.date != null) {
                //Ticket date < Mission date start, it advises the user
                if(result.date.before(ticketMission.getStartDate())){
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.dateTicketMin), Toast.LENGTH_SHORT).show();
                }
                //Ticket date > Mission date finish, it advises the user
                if(result.date.after(ticketMission.getEndDate())){
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.dateTicketMax), Toast.LENGTH_SHORT).show();
                }

            }
            if (result.containsCover) {
                Toast.makeText(getApplicationContext(), "E' presente il coperto, specificare il numero di persone", Toast.LENGTH_SHORT).show();
            }

            if (result.currency != null) {

                currency = result.currency.getCurrencyCode();
                currencyBtn.setText(currency);
            }

            waitOCR.setVisibility(View.INVISIBLE);
        });
        boolean upDown = result.errors.contains(OcrError.UPSIDE_DOWN);
        if (upDown) {
            imgProc.rotate(2);
            runOnUiThread(() -> checkPhotoView.setImageBitmap(bm));
        }

        //enable save button
        btnOK.setClickable(true);
    }

    /** Dal Maso
     * Save the image and one original copy
     */
    public void saveThisTicket(){
        //avoid multiple saves
        btnOK.setClickable(false);

        new Thread(() -> {
            Set<OcrError> errors = OCR_result.errors;
            rotateBitmap(errors.contains(OcrError.UPSIDE_DOWN));

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

                thisTicket.setCurrency(currency);
                if (OCR_result.date != null) {
                    thisTicket.setDate(OCR_result.date);
                } else {
                    thisTicket.setDate(ticketMission.getStartDate());
                }

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

                thisTicket.setMissionID(ticketMission.getID());
                long id = DB.addTicket(thisTicket);

                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();

                finalBitmap.compress(Bitmap.CompressFormat.JPEG,100, outOriginal);
                outOriginal.flush();
                outOriginal.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }
}
