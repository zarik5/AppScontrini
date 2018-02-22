package com.ing.software.ticketapp;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.ListView;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.List;

import database.DataManager;
import database.MissionEntity;
import database.PersonEntity;

public class MissionsClosed extends Fragment {

    public DataManager DB;
    int personID;
    PersonEntity thisPerson;
    public List<MissionEntity> listMission = new LinkedList<>();
    View rootView;
    MissionAdapterDB adapter;
    ListView listView;
    TextView noMissions;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.activity_missions_closed, container, false);

        DB = new DataManager(getContext());

        listView = (ListView)rootView.findViewById(R.id.listMission);
        noMissions = (TextView)rootView.findViewById(R.id.noMissionsClosed);
        personID = getArguments().getInt("personID", 0);

        printAllMissions();
        return rootView;
    }

    /** PICCOLO
     * Adds in the database the new mission
     */
    public void addToListDB(){
        adapter = new MissionAdapterDB(getContext(), R.layout.mission_card, listMission);
        listView.setAdapter(adapter);
    }

    /** Dal Maso
     * get all missions from the DB and print
     */
    public void printAllMissions(){
        listMission.clear();
        List<MissionEntity> missions = DB.getMissionsForPerson(personID);
        for (int i = 0; i < missions.size(); i++)
        {
            if(missions.get(i).isClosed()) {
                listMission.add(missions.get(i));
            }
        }
        addToListDB();

        if(missions.size() == 0)
            noMissions.setVisibility(View.VISIBLE);
        else
            noMissions.setVisibility(View.INVISIBLE);

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
                listMission.remove(index);
                adapter.notifyDataSetChanged();
                if(listMission.size() == 0){
                    noMissions.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationStart(Animation animation) {}
        };
        AppUtilities.collapse(v, al);
    }
}
