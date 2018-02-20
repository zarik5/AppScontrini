package com.ing.software.ticketapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.ing.software.ocr.OcrManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import database.DataManager;
import database.MissionEntity;
import database.PersonEntity;
import database.TicketEntity;
import export.ExportManager;
import export.ExportTypeNotSupportedException;
import export.ExportedFile;

public class BillActivity extends AppCompatActivity {
    public FloatingActionButton fab, fab1, fab2;
    public Animation fab_open, fab_close, rotate_forward, rotate_backward;
    public List<TicketEntity> list = new LinkedList<TicketEntity>();
    public Uri photoURI;
    public boolean isFabOpen = false;
    String tempPhotoPath;
    Integer missionID;
    MissionEntity thisMission;
    Context context;
    String root;
    public DataManager DB;
    OcrManager ocrManager;
    CustomAdapter adapter;
    Camera mCamera;
    ListView listView;
    ExportManager manager;
    TextView title;

    static final int REQUEST_TAKE_PHOTO = 1;

    int textSize = 23;
    int paddingLeft = 10;
    int paddingTop = 40;
    int paddingRight = 10;
    int paddingBottom = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill);

        root = getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString();
        DB = new DataManager(this.getApplicationContext());
        context = this.getApplicationContext();
        PersonEntity thisPerson = DB.getPerson(Singleton.getInstance().getPersonID());


        missionID = Singleton.getInstance().getMissionID();
        thisMission = DB.getMission(missionID);
        //lazzarin
        Singleton.getInstance().setStartDate(thisMission.getStartDate());
        Singleton.getInstance().setEndDate(thisMission.getEndDate());

        ActionBar ab = getSupportActionBar();
        ab.setTitle(thisMission.getName());
        ab.setSubtitle(getResources().getString(R.string.missionOf)+" "+thisPerson.getName()+" "+thisPerson.getLastName());

        ocrManager = new OcrManager();
        while (ocrManager.initialize(this) != 0) { // 'this' is the context
            try {
                //On first run vision library will be downloaded
                Toast.makeText(this, getResources().getString(R.string.downLibrary), Toast.LENGTH_LONG).show();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        initializeComponents();
    }

    /** Dal Maso, Piccolo, Mantovan
     * Catch events on toolbar
     * @param item object on the toolbar
     * @return flag of success
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        Intent intent = new Intent();
        switch (item.getItemId()) {
            case R.id.action_export:

                //Piccolo
                manager = new ExportManager(DB, getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).getPath());
                listView = new ListView(this);
                ArrayList<String> formats = manager.exportTags();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.dialog_export, R.id.txt, formats);
                listView.setAdapter(adapter);
                builder.setView(listView);

                //Mantovan, Custom title
                title = new TextView(this);
                title.setText(R.string.text_Export);
                title.setGravity(Gravity.CENTER_HORIZONTAL);
                title.setTextSize(textSize);
                title.setBackgroundResource(R.color.colorPrimary);
                title.setTextColor(getResources().getColor(R.color.white));
                title.setPadding(paddingLeft, paddingTop,paddingRight,paddingBottom);
                builder.setCustomTitle(title);


                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int which, long l) {
                        try{
                            ExportedFile exported = manager.exportMission(missionID,formats.get(which));
                            Uri toExport = Uri.fromFile(exported.file);
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.putExtra(Intent.EXTRA_STREAM, toExport);
                            shareIntent.setType("file/"+formats.get(which));
                            startActivity(Intent.createChooser(shareIntent,getResources().getString(R.string.text_ExportMissionTo)));
                        }
                        catch (ExportTypeNotSupportedException e){
                            e.printStackTrace();
                        }
                }});

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                break;

            default:
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
        return true;
    }

    /** Dal Maso
     * Setting toolbar buttons and style from /res/menu
     * @param menu
     * @return success flag
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.export_menu, menu);
        return true;
    }

    /** Dal Maso
     * Se non sono presenti ticket mostra come aggiungerli
     */
    public void showGuide(){
        if(!thisMission.isClosed()) {
            TapTargetView.showFor(this, TapTarget.forView(findViewById(R.id.fab), getResources().getString(R.string.ticketAddTitle), getResources().getString(R.string.ticketAddDesc))
                            .targetCircleColor(R.color.white)
                            .titleTextSize(21)
                            .titleTextColor(R.color.white)
                            .descriptionTextSize(13)
                            .descriptionTextColor(R.color.white)
                            .textColor(R.color.white)
                            .icon(getResources().getDrawable(R.mipmap.ic_camera_white_24dp)),
                    new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
                        @Override
                        public void onTargetClick(TapTargetView view) {
                            super.onTargetClick(view);      // This call is optional
                            takePhotoIntent();
                        }
                    }
            );
        }
    }

    /** Dal Maso
     *  Manage all animations and catch onclick events about FloatingActionButtons
     */
    public void initializeComponents(){
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                1);
        listView = (ListView)findViewById(R.id.list1);
        printAllTickets();
        fab = (FloatingActionButton)findViewById(R.id.fab);
        fab_open = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.fab_close);
        rotate_forward = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.rotate_forward);
        rotate_backward = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.rotate_backward);

        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                takePhotoIntent();
            }
        });

        if(thisMission.isClosed()) {
            fab.setVisibility(View.INVISIBLE);
        }
        else{
            fab.setVisibility(View.VISIBLE);
        }

        if(DB.getAllTickets().size() == 0){
            showGuide();
        }
    }

    /** Dal Maso
     * Add new ticket to the list
     */
    public void addToList(){
        adapter = new CustomAdapter(this, R.layout.cardview, list, missionID, DB);
        listView.setAdapter(adapter);
    }


    /**Dal Maso
     * It opens the custom camera, take the photo, analize it and save it
     */
    private void takePhotoIntent() {
        if(checkCameraHardware(context)){
            mCamera = getCameraInstance();
            Intent cameraActivity = new Intent(context, CameraActivity.class);
            startActivityForResult(cameraActivity, REQUEST_TAKE_PHOTO);
        }
    }

    //Dal Maso
    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    //Dal Maso
    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }


    /** Dal Maso
     * Catch intent results
     * @param requestCode action number
     * @param resultCode intent result code
     * @param data intent data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {

                case(REQUEST_TAKE_PHOTO):
                    printAllTickets();
                    break;


                default:
                    printAllTickets();
                    break;
            }
        }
        if (resultCode == Activity.RESULT_CANCELED) {
            printAllTickets();
        }
    }

    /** Dal Maso
     *  Print all tickets, get it from DB
     */
    public void printAllTickets(){
        list.clear();
        List<TicketEntity> ticketList = DB.getTicketsForMission(missionID);
        TicketEntity t;
        int count = 0;
        for(int i = 0; i < ticketList.size(); i++){
            list.add(ticketList.get(i));
            count++;
        }
        addToList();
        //If there aren't tickets show message
        TextView noBills = (TextView)findViewById(R.id.noBills);
        String noBillsError=getResources().getString(R.string.noBills);
        if(!thisMission.isClosed())
            noBillsError+=getResources().getString(R.string.noBillsOpen);
        noBills.setText(noBillsError);
        if(count == 0){
            noBills.setVisibility(View.VISIBLE);
        }
        else{
            noBills.setVisibility(View.INVISIBLE);
        }
    }

    /** DAL MASO
     * Rotate the image
     * @param source photo bitmap
     * @param angle angle of rotation
     * @return rotated bitmap
     */
    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    /**PICCOLO
     * Method that is run when the activity is resumed.
     * it hides the button for adding tickets if the mission is closed, else it shows it.
     */
    public void onResume(){
        super.onResume();
        if(thisMission.isClosed()) {
            fab.setVisibility(View.INVISIBLE);
        }
        else{
            fab.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent();
        setResult(RESULT_CANCELED, intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ocrManager.release();
    }
}
