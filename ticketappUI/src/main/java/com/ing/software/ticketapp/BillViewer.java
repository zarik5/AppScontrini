package com.ing.software.ticketapp;

import android.support.v7.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;

import database.DataManager;
import database.TicketEntity;

public class BillViewer extends AppCompatActivity {
    public FloatingActionButton fabEdit, fabDelete, fabCrop, fabConfirmEdit;
    public DataManager DB;
    int ticketId;
    int missionID;
    Context context;
    final int TICKET_MOD = 1;
    TicketEntity thisTicket;
    String ticketTitle = "", ticketAmount = "", ticketPeople = "", ticketAmountUn = "", ticketShop = "", ticketPath = ""; // ticketDate = ""
    Date ticketDate;
    ImageView imgView;

    //Dal Maso
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        setContentView(R.layout.activity_bill_viewer);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#66000000")));
        DB = new DataManager(this.getApplicationContext());
        context = this.getApplicationContext();

        initialize();
    }

    /** Dal Maso
     * Initialize components
     */
    public void initialize(){
        //Get data from parent view
        Intent intent = getIntent();
        ticketId = Singleton.getInstance().getTicketID();
        thisTicket = DB.getTicket(ticketId);
        ticketPath = thisTicket.getFileUri().toString().substring(7);
        ticketPeople = ""+thisTicket.getTagPlaces();
        if(thisTicket.getTitle().replace(" ", "").length() == 0){
            ticketTitle = getResources().getString(R.string.title_Ticket);
        } else {
            ticketTitle = thisTicket.getTitle();
        }
        ticketDate = thisTicket.getDate();
        if(thisTicket.getShop() == null || thisTicket.getShop().trim().compareTo("") == 0){
            ticketShop = getString(R.string.string_NoShop);
        } else {
            ticketShop = thisTicket.getShop();
        }
        if(thisTicket.getAmount() == null || thisTicket.getAmount().compareTo(new BigDecimal(0.00, MathContext.DECIMAL64)) <= 0){
            ticketAmount = getString(R.string.string_NoAmountFull);
            ticketAmountUn = getString(R.string.string_NoAmountFull);
        } else {
            ticketAmount = thisTicket.getAmount().setScale(2, RoundingMode.HALF_EVEN).toString() +  Singleton.getInstance().getCurrency();
            ticketAmountUn = thisTicket.getPricePerson().setScale(2, RoundingMode.HALF_EVEN).toString() + " " + Singleton.getInstance().getCurrency();
        }

        //Title
        setTitle(ticketTitle);
        TextView billLastMod = (TextView)findViewById(R.id.billDate);
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        billLastMod.setText(formatter.format(ticketDate));

        //ImageName
        TextView billName = (TextView)findViewById(R.id.billName);
        billName.setText(ticketTitle);

        //Total price
        TextView billPrice = (TextView)findViewById(R.id.billTotal);
        billPrice.setText(ticketAmount);

        //Total per person
        TextView billPriceUn = (TextView)findViewById(R.id.billTotalUn);
        billPriceUn.setText(ticketAmountUn);

        //Number of people
        TextView billPeople = (TextView)findViewById(R.id.billPeople);
        billPeople.setText(ticketPeople);

        //Shop
        TextView billShop = (TextView)findViewById(R.id.billShop);
        billShop.setText(ticketShop);

        //Full image view
        imgView = (ImageView)findViewById(R.id.billImage);

        Glide.with(context)
                .load(ticketPath)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(imgView);

        fabCrop=(FloatingActionButton)findViewById(R.id.fabCrop);
        fabDelete=(FloatingActionButton)findViewById(R.id.fabDelete);
        fabCrop.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                cropPhoto(ticketId);
            }//onClick
        });
        fabDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteTicket(ticketId);
            }//onClick
        });
    }

    /** Dal Maso
     * Setting toolbar buttons and style from /res/menu
     * @param menu
     * @return success flag
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editticket_menu, menu);
        return true;
    }

    /** Dal Maso
     * Catch events on toolbar
     * @param item object on the toolbar
     * @return flag of success
     *
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {

            case R.id.action_editTicket:
                //Open Edit Ticket Activity
                Intent editTicket = new Intent(context, EditTicket.class);
                startActivityForResult(editTicket, TICKET_MOD);
                break;

            default:
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
        return true;
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
                case(TICKET_MOD):
                    initialize();
                    break;
                case (CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE):
                    DB.updateTicket(thisTicket);
                    initialize();
                    break;
            }
        }
    }


    /**PICCOLO, DAL MASO
     * Method that deletes a ticket from the db
     * @param id the id of the TicketEntity in the db
     */
    private void deleteTicket(long id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(this.getString(R.string.deleteTicketToast))
                .setTitle(this.getString(R.string.deleteTitle));
        // Add the buttons
        builder.setPositiveButton(this.getString(R.string.buttonDelete), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                TicketEntity thisTicket = DB.getTicket(ticketId);
                String toDelete = "";
                //Get the ticket to delete
                toDelete = thisTicket.getFileUri().toString().substring(7);
                final File ticketDelete = new File(toDelete);
                final File ticketDeleteOriginal = new File (toDelete+"orig");
                if(DB.deleteTicket(ticketId) && ticketDelete.delete() && ticketDeleteOriginal.delete()){
                    Intent intent = new Intent();
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
        builder.setNegativeButton(this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //Nothing
            }
        });
        AlertDialog alert = builder.show();
        Button nbutton = alert.getButton(DialogInterface.BUTTON_POSITIVE);
        nbutton.setTextColor(Color.parseColor("#2196F3"));
    }//deleteTicket

    /**PICCOLO, problems fixes and edit by Dal Maso
     * Method that lets the user crop and/or rotate the original photo, once the crop is confirmed,
     * the ocr is run to get data that it couldn't have red the first time
     * @param id the id of the TicketEntity in the db
     */
    private void cropPhoto(int id) {
        Uri toCropUri = thisTicket.getFileUri();
        File originalFile = new File(toCropUri.toString().substring(7)+"orig");
        Uri originalUri=Uri.fromFile(originalFile);
        CropImage.activity(originalUri)
                .setOutputUri(toCropUri).start(this);
    }//cropPhoto

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
    }
}

