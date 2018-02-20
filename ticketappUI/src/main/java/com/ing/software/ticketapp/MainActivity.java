package com.ing.software.ticketapp;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.animation.Animation;
import android.widget.ListView;
import android.widget.TextView;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;

import java.util.LinkedList;
import java.util.List;

import database.DataManager;
import database.PersonEntity;


public class MainActivity extends AppCompatActivity {
    public DataManager DB;
    public List<PersonEntity> listPeople = new LinkedList<PersonEntity>();
    static final int person_added = 1;
    ListView listView;
    PeopleAdapter adapter;
    View myView;
    TextView noPeople;

    //Dal Maso
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.titlePeople));
        setContentView(R.layout.activity_main);
        initialize();
        printAllPeople();
    }

    @Override
    public void onResume() {
        super.onResume();
        myView.setVisibility(View.INVISIBLE);
    }

    /** Dal Maso
     * Setting toolbar buttons and style from /res/menu
     * @param menu
     * @return success flag
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
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
                case (person_added):
                    printAllPeople();
                    break;
                default:
                    printAllPeople();
                    break;
            }
        }
        if (resultCode == Activity.RESULT_CANCELED) {
            printAllPeople();
        }
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
            //Open settings panel
            case R.id.action_OCRsettings:
                int cx = myView.getWidth();
                AppUtilities.circularReveal(myView, cx, 0);
                Intent settings = new Intent(getApplicationContext(), ApplicationSettings.class);
                startActivity(settings);
                break;
        }
        return true;
    }

    /** Dal Maso
     * Se non sono presenti persone mostra come aggiungerne una
     */
    private void startFabGuide(){
        TapTargetView.showFor(this,
                TapTarget.forView(findViewById(R.id.fab_addPerson),
                        getResources().getString(R.string.personAddTitle),
                        getResources().getString(R.string.personAddDesc))
            .targetCircleColor(R.color.white)
            .titleTextSize(21)
            .titleTextColor(R.color.white)
            .descriptionTextSize(13)
            .descriptionTextColor(R.color.white)
            .textColor(R.color.white)
            .icon(getResources().getDrawable(R.mipmap.ic_add_white_24dp)),
            new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
                @Override
                public void onTargetClick(TapTargetView v) {
                    super.onTargetClick(v);      // This call is optional
                    int cx = myView.getWidth();
                    int cy = myView.getHeight();
                    AppUtilities.circularReveal(myView, cx, cy);
                    Intent addPerson = new Intent(v.getContext(), AddNewPerson.class);
                    startActivityForResult(addPerson, person_added);
                }
            }
        );
    }

    /** Dal Maso
     * Initialize the activity
     */
    private void initialize(){
        myView = findViewById(R.id.circularAnimation);
        noPeople = (TextView)findViewById(R.id.noPeople);
        listView = (ListView)findViewById(R.id.listPeople);
        DB = new DataManager(this.getApplicationContext());
        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab_addPerson);
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int cx = myView.getWidth();
                int cy = myView.getHeight();
                AppUtilities.circularReveal(myView, cx, cy);
                Intent addPerson = new Intent(v.getContext(), AddNewPerson.class);
                startActivityForResult(addPerson, person_added);
            }
        });
        if(DB.getAllPerson().size() == 0){
            startFabGuide();
        }

    }

    /** PICCOLO
     * Adds in the database the new mission
     */
    public void addToList(){
        adapter = new PeopleAdapter(this, R.layout.person_card, listPeople);
        listView.setAdapter(adapter);
    }

    /** Dal Maso
     * get all missions from the DB and print
     */
    public void printAllPeople(){
        listPeople.clear();
        List<PersonEntity> people = DB.getAllPerson();

        if(people.size() == 0){
            noPeople.setVisibility(View.VISIBLE);
        }
        else{
            noPeople.setVisibility(View.INVISIBLE);
        }
        for (int i = 0; i < people.size(); i++)
        {
            listPeople.add(people.get(i));
        }
        addToList();
    }

    /** Dal Maso
     * Delete one listview cell
     * @param v view to animate after deleting
     * @param index item position
     */
    public void deleteCell(final View v, final int index) {
        Animation.AnimationListener al = new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation arg0) {
                listPeople.remove(index);
                adapter.notifyDataSetChanged();
                if(listPeople.size() == 0){
                    noPeople.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationStart(Animation animation) {}
        };
        AppUtilities.collapse(v, al);
    }

}