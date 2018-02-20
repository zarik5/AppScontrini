package com.ing.software.ticketapp;

import android.app.Activity;
import android.content.Intent;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import database.DataManager;
import database.PersonEntity;

public class MissionsTabbed extends AppCompatActivity {

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    DataManager DB;
    int personID;
    PersonEntity thisPerson;
    final int MISSION_MOD = 1;
    final int PERSON_MOD = 2;
    Toolbar toolbar;
    MissionsOpen missionsOpen = new MissionsOpen();
    MissionsClosed missionsClosed = new MissionsClosed();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_missions_tabbed);

        Intent intent = getIntent();
        personID = Singleton.getInstance().getPersonID();
        DB = new DataManager(this);
        thisPerson = DB.getPerson(personID);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(thisPerson.getName()+" "+thisPerson.getLastName());
        toolbar.setSubtitle("Missioni disponibili");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initializeComponents();
    }

    /** Dal Maso
     * Initialize the activity
     */
    private void initializeComponents(){
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        //Set the currency
        if(DB.getAllSettings().size() > 0)
            Singleton.getInstance().setCurrency(DB.getAllSettings().get(0).getCurrencyDefault());
        else
            Singleton.getInstance().setCurrency("EUR");

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);

        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(mViewPager));
    }

    /** Dal Maso
     * Catch events on toolbar
     * @param item object on the toolbar
     * @return flag of success
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent();
        // Handle item selection
        switch (item.getItemId()) {
            default:
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
                default:
                    refresh();
                    break;
            }
        }
        if (resultCode == Activity.RESULT_CANCELED) {
            refresh();
        }
    }

    /** Dal Maso
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Bundle bundle = new Bundle();
            bundle.putInt("personID", personID);
            switch(position){
                case 0:
                    missionsOpen.setArguments(bundle);
                    return missionsOpen;
                case 1:
                    missionsClosed.setArguments(bundle);
                    return missionsClosed;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 2;
        }
    }

    /** Dal Maso
     * Update the listViews of the missions after item delete
     * @param choice (0: mission open, 1: mission closed)
     * @param v view who get the animation
     * @param position item list position
     */
    public void updateThisAdapter(int choice, View v, final int position){
        switch (choice){
            case 0:
                missionsOpen.deleteCell(v, position);
                break;
            case 1:
                missionsClosed.deleteCell(v, position);
        }
    }

    /** Dal Maso
     * Refresh the listViews
     */
    public void refresh(){
        missionsOpen.printAllMissions();
        missionsClosed.printAllMissions();
    }

    /** Dal Maso
     * It manages the physical back button
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent();
        setResult(RESULT_CANCELED, intent);
        finish();
    }

    /** Dal Maso
     * Reload the activity
     */
    private void reload() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }
}
