package com.ing.software.ticketapp;

import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import database.DataManager;
import database.MissionEntity;
import database.TicketEntity;

public class EditTicket extends AppCompatActivity {

    public DataManager DB;
    int ticketID;
    int missionID;
    Context context;
    TicketEntity thisTicket;
    MissionEntity thisMission;
    String ticketTitle = "", ticketAmount = "", ticketShop = "", ticketPath = "", ticketDateString = "", ticketPeople = "";
    Date ticketDate;
    TextView txtTitle;
    TextView txtAmount;
    TextView txtShop;
    TextView txtDate;
    CheckBox checkRefund;
    TextView txtPeople;
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setElevation(0);
        context = this.getApplicationContext();
        setTitle(context.getString(R.string.title_EditTicket));
        setContentView(R.layout.activity_edit_ticket);

        DB = new DataManager(this.getApplicationContext());
        ticketID = Singleton.getInstance().getTicketID();
        missionID = Singleton.getInstance().getMissionID();
        thisTicket = DB.getTicket(ticketID);
        thisMission = DB.getMission(missionID);
        TextView editDate=(TextView)findViewById(R.id.input_ticketDateMod);
        LinearLayout bntMissionStart = (LinearLayout)findViewById(R.id.buttonEditTicketDate);
        bntMissionStart.setOnClickListener(new View.OnClickListener() {
            //lazzarin
            public void onClick(View v) {
                Singleton.getInstance().setStartFlag(2);
                DialogFragment newFragment = new DatePickerFragment().newInstance(editDate);
                newFragment.show(getFragmentManager(), "startDatePicker");
            }
        });


        //Get data from parent view
        setTicketValues();

    }

    /** Dal Maso
     * Setting toolbar buttons and style from /res/menu
     * @param menu
     * @return success flag
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.confirm_menu, menu);
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
            case R.id.action_confirm:
                //Salva i file nel DB

                thisTicket.setTitle(txtTitle.getText().toString());
                /**
                 * lazzarin (Cleaned by Dal Maso)
                 */
                SimpleDateFormat dateformat = new SimpleDateFormat("dd/MM/yyyy");
                try {
                    MissionEntity current = DB.getMission(missionID);
                    String  start=dateformat.format(current.getStartDate());

                    String finish=dateformat.format(current.getEndDate());
                    
                    if( AppUtilities.checkIntervalDate(start,finish,txtDate.getText().toString()))
                        thisTicket.setDate(dateformat.parse(txtDate.getText().toString()));
                    else {
                        Toast.makeText(context, getResources().getString(R.string.toast_errorIntervalDate), Toast.LENGTH_SHORT).show();
                        break;
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                thisTicket.setShop(txtShop.getText().toString());
                thisTicket.setTagPlaces(Short.parseShort(txtPeople.getText().toString()));
                thisTicket.setRefundable(checkRefund.isChecked());

                /**
                 * Mantovan Federico
                 * Check entry amount
                 */

                int count = 0;
                String amount = txtAmount.getText().toString();
                for(int i = 0; i < amount.length(); i++){
                    char character = amount.charAt(i);
                    String letter = character + "";
                    if(letter.equals(".")){
                        count++;
                    }
                }


                if(count > 0 && amount.length() - count == 0){ //Point (>1) and not number
                    Toast.makeText(context, getResources().getString(R.string.toast_multiPoint_noNumber), Toast.LENGTH_SHORT).show();
                    break;
                }
                if(count > 1){ //Point and number (>1)
                    Toast.makeText(context, getResources().getString(R.string.toast_multiPoint), Toast.LENGTH_SHORT).show();
                    break;
                }
                if (count <= 1){ //Zero or one point and number (>= 1)
                    if(amount.length() != 0){
                        if(Double.parseDouble(amount) > 9999){
                            Toast.makeText(context, getResources().getString(R.string.toast_tooHightTotal), Toast.LENGTH_SHORT).show();
                            break;
                        }
                        else
                            thisTicket.setAmount(BigDecimal.valueOf(Double.parseDouble(txtAmount.getText().toString())));
                    }
                }

                DB.updateTicket(thisTicket);
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
                break;

            default:
                finish();
                break;
        }
        return true;
    }

    /** Dal Maso
     * set the current values on the variables
     */
    private void setTicketValues(){

        ticketPath = thisTicket.getFileUri().toString().substring(7);
        ticketTitle = thisTicket.getTitle();
        ticketDate = thisTicket.getDate();
        ticketPeople = ""+thisTicket.getTagPlaces();
        if(thisTicket.getAmount() == null){
            ticketAmount = "";
        }
        else {
            ticketAmount = thisTicket.getAmount().setScale(2, RoundingMode.HALF_EVEN).toString();
        }
        if(thisTicket.getShop() == null){
            ticketShop = "";
        }
        else {
            ticketShop = thisTicket.getShop();
        }

        //set those values to the edittext
        setTicketValuesOnScreen();
    }

    /** Dal Maso
     * set the current values on the edittexts
     */
    private void setTicketValuesOnScreen(){
        txtTitle = (TextView)findViewById(R.id.input_ticketTitleMod);
        txtAmount = (TextView)findViewById(R.id.input_ticketAmountMod);
        txtShop = (TextView)findViewById(R.id.input_ticketShopMod);
        txtDate = (TextView)findViewById(R.id.input_ticketDateMod);
        checkRefund = (CheckBox)findViewById(R.id.check_isRepaidTicket);
        txtPeople = (TextView)findViewById(R.id.input_ticketPersonNumber);
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        txtDate.setText(formatter.format(ticketDate));
        txtTitle.setText(ticketTitle);
        txtShop.setText(ticketShop);
        txtAmount.setText(ticketAmount);
        txtPeople.setText(ticketPeople);
        if(thisTicket.isRefundable()){
            checkRefund.setChecked(true);
        }
    }
}
