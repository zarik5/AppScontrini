package com.ing.software.ticketapp;

import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import co.ceryle.radiorealbutton.RadioRealButtonGroup;
import database.DataManager;
import database.SettingsEntity;

public class ApplicationSettings extends AppCompatActivity {

    SettingsEntity thisSettings;
    DataManager DB;
    RadioRealButtonGroup quality_group;
    RadioRealButtonGroup total_group;
    RadioRealButtonGroup reverse_group;
    RadioRealButtonGroup currency_group;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setElevation(0);
        setTitle(getResources().getString(R.string.settings));
        setContentView(R.layout.activity_application_settings);
        initializeComponents();
        setSettingsParameters();
    }

    /** Dal Maso
     * Initialize all the components
     */
    private void initializeComponents(){
        DB = new DataManager(this.getApplicationContext());
        thisSettings = new SettingsEntity(2, false, false, "EUR");

        quality_group = (RadioRealButtonGroup)findViewById(R.id.quality_group);
        total_group = (RadioRealButtonGroup)findViewById(R.id.total_group);
        reverse_group = (RadioRealButtonGroup)findViewById(R.id.reverse_group);
        currency_group = (RadioRealButtonGroup)findViewById(R.id.currency_group);

        if(DB.getAllSettings().size() != 0) {
            thisSettings = DB.getAllSettings().get(0);
        }
        else{
            long settID = DB.addSettings(thisSettings);
            thisSettings = DB.getAllSettings().get(0);
        }

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setThisSettings();
            }
        });
    }

    /** Dal Maso
     * Set current settings on screen
     */
    private void setSettingsParameters(){

        quality_group.setPosition(thisSettings.getAccuracyOCR());

        if(thisSettings.isAutomaticCorrectionAmountOCR()){
            total_group.setPosition(0);
        }
        else {
            total_group.setPosition(1);
        }

        if(thisSettings.isSearchUpDownOCR()){
            reverse_group.setPosition(0);
        }
        else {
            reverse_group.setPosition(1);
        }

        switch (thisSettings.getCurrencyDefault()){
            case ("EUR"):
                currency_group.setPosition(0);
                break;
            case ("USD"):
                currency_group.setPosition(1);
                break;
            case ("GBP"):
                currency_group.setPosition(2);
                break;
        }
    }

    /** Dal Maso
     * Update the settings
     */
    public void setThisSettings(){
        int quality_res = 0;
        String currency_res = "";
        boolean total_res = false;
        boolean reverse_res = false;

        //Check quality
        quality_res = quality_group.getPosition();

        //Check currency
        switch (currency_group.getPosition()){
            case (0):
                currency_res = "EUR";
                break;
            case (1):
                currency_res = "USD";
                break;
            case (2):
                currency_res = "GBP";
                break;
        }

        //Check total
        switch (total_group.getPosition()){
            case (0):
                total_res = true;
                break;
            case (1):
                total_res = false;
                break;
        }

        //Check reverse
        switch (reverse_group.getPosition()){
            case (0):
                reverse_res = true;
                break;
            case (1):
                reverse_res = false;
                break;
        }

        //Set values
        thisSettings.setAccuracyOCR(quality_res);
        thisSettings.setCurrencyDefault(currency_res);
        thisSettings.setAutomaticCorrectionAmountOCR(total_res);
        thisSettings.setSearchUpDownOCR(reverse_res);

        //Update db
        DB.updateSettings(thisSettings);
        finish();
    }
}
