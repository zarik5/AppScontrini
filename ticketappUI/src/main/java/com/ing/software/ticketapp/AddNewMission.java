package com.ing.software.ticketapp;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import database.DataManager;
import database.MissionEntity;

public class AddNewMission extends AppCompatActivity{

    Context context;
    public DataManager DB;
    TextView missionStart;
    TextView missionFinish;
    int personID = 1;
    long missionID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setElevation(0);
        setTitle(getString(R.string.newMission));
        setContentView(R.layout.activity_add_new_mission);

        DB = new DataManager(this.getApplicationContext());
        context = this.getApplicationContext();

        Intent intent = getIntent();
        personID = intent.getExtras().getInt("person");

        initializeComponents();
    }

    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(
                activity.getCurrentFocus().getWindowToken(), 0);
    }

    //DalMaso, edit by Lazzarin
    private void initializeComponents(){
        missionStart = (TextView)findViewById(R.id.input_missionStart);
        missionFinish = (TextView)findViewById(R.id.input_missionFinish);
        LinearLayout bntMissionStart = (LinearLayout)findViewById(R.id.button_missionStart);
        LinearLayout bntMissionFinish = (LinearLayout)findViewById(R.id.button_missionFinish);
        //clean Singleton Date
        Singleton.getInstance().setStartDate(null);
        bntMissionStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hideSoftKeyboard(AddNewMission.this);
                Singleton.getInstance().setStartFlag(0);
                DialogFragment newFragment = new DatePickerFragment().newInstance(missionStart);
                newFragment.show(getFragmentManager(), "startDatePicker");
            }
        });
        bntMissionFinish.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Singleton.getInstance().setStartFlag(1);
                hideSoftKeyboard(AddNewMission.this);
                DialogFragment newFragment = new DatePickerFragment().newInstance(missionFinish);
                newFragment.show(getFragmentManager(), "finishDatePicker");
            }
        });
        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(addMission()){
                    Intent intent = new Intent();
                    Intent startImageView = new Intent(context, BillActivity.class);
                    startImageView.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startImageView.putExtra(IntentCodes.INTENT_MISSION_ID,missionID);
                    context.startActivity(startImageView);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
    }

    /** Dal Maso
     * Catch events on toolbar
     * @param item object on the toolbar
     * @return flag of success
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        Intent intent = new Intent();
        switch (item.getItemId()) {
            default:
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
        return true;
    }

    /** Dal Maso
     * Add new mission to the db
     * @return add result
     */
    private boolean addMission(){
        EditText editName =(EditText)findViewById(R.id.input_missionName);
        EditText editLocation = (EditText)findViewById(R.id.input_missionLocation);
        String name = editName.getText().toString();
        String location = editLocation.getText().toString();
        String startDate = missionStart.getText().toString();
        String finishDate = missionFinish.getText().toString();

        if ((name == null) || name.replaceAll(" ","").equals("")) {
            Toast.makeText(context, getResources().getString(R.string.toast_missionNoName), Toast.LENGTH_SHORT).show();
            return false;
        }
        if((location==null) || location.replaceAll(" ","").equals("")) {
            Toast.makeText(context, getResources().getString(R.string.toast_missionNoLocation), Toast.LENGTH_SHORT).show();
            return false;
        }
        if((startDate==null) ||startDate.equals(getResources().getString(R.string.dateStart))) {
            Toast.makeText(context, getResources().getString(R.string.toast_noDataStart), Toast.LENGTH_SHORT).show();
            return false;
        }
        if((finishDate==null) || finishDate.equals(getResources().getString(R.string.dateFinish))) {
            Toast.makeText(context, getResources().getString(R.string.toast_noDataFinish), Toast.LENGTH_SHORT).show();
            return false;
        }

        MissionEntity miss = new MissionEntity();
        miss.setName(name);
        miss.setPersonID(personID);
        miss.setLocation(location);
        miss.setClosed(false);

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        try {
            String start = AppUtilities.addMonth(startDate);
            String finish = AppUtilities.addMonth(finishDate);
            if(!AppUtilities.checkDate(start,finish)) {
                Toast.makeText(context, getResources().getString(R.string.toast_errorDate), Toast.LENGTH_SHORT).show();
                return false;
            }
            miss.setStartDate(format.parse(start));
            miss.setEndDate(format.parse(finish));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        missionID = DB.addMission(miss);
        return true;
    }

}
