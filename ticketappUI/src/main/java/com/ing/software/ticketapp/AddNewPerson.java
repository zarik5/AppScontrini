package com.ing.software.ticketapp;

import android.content.Context;
import android.content.Intent;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import database.DataManager;
import database.PersonEntity;

public class AddNewPerson extends AppCompatActivity {

    Context context;
    public DataManager DB;
    TextView missionStart;
    TextView missionFinish;
    int personID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setElevation(0);
        DB = new DataManager(this.getApplicationContext());
        context = this.getApplicationContext();
        setTitle(context.getString(R.string.newPerson));
        setContentView(R.layout.activity_add_new_person);
        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(addPerson()){
                    Intent intent = new Intent();
                    Intent startMissionView = new Intent(context, MissionsTabbed.class);
                    startMissionView.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(startMissionView);
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
     *
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            default:
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
        return true;
    }

    private boolean addPerson(){
        EditText editName =(EditText)findViewById(R.id.input_personName);
        EditText editLastName = (EditText)findViewById(R.id.input_personLastName);
        EditText editAcademicTitle = (EditText)findViewById(R.id.input_personAcademicTitle);
        String name = editName.getText().toString();
        String lastName = editLastName.getText().toString();
        String academicTitle = editAcademicTitle.getText().toString();

        if ((name == null) || name.replaceAll(" ","").equals("")) {
            Toast.makeText(context, getResources().getString(R.string.toast_personNoName), Toast.LENGTH_SHORT).show();
            return false;
        }
        if((lastName==null) || lastName.replaceAll(" ","").equals("")) {
            Toast.makeText(context, getResources().getString(R.string.toast_personNoLastName), Toast.LENGTH_SHORT).show();
            return false;
        }

        PersonEntity person = new PersonEntity();
        person.setName(name);
        person.setLastName(lastName);
        person.setAcademicTitle(academicTitle);

        long personID = DB.addPerson(person);
        Singleton.getInstance().setPersonID((int) personID);
        return true;
    }
}
