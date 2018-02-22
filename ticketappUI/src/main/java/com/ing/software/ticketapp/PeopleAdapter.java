package com.ing.software.ticketapp;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.daimajia.swipe.SwipeLayout;

import java.util.List;

import database.DataManager;
import database.MissionEntity;
import database.PersonEntity;
import database.TicketEntity;

/**
 * Created by nicoladalmaso on 29/12/17.
 */

public class PeopleAdapter extends ArrayAdapter<PersonEntity> implements SwipeLayout.SwipeListener {

    Context context;
    String path = "";
    List<PersonEntity> persons;
    DataManager DB;

    boolean swipeOpen = false;

    public PeopleAdapter(Context context, int textViewResourceId,
                          List<PersonEntity> objects) {
        super(context, textViewResourceId, objects);
        this.context = context;
        persons = objects;
        DB = new DataManager(context);
    }

    /** Dal Maso
     * It manages the Adapter
     * @param position item position
     * @param convertView my custom view
     * @param parent parent view
     * @return view setted
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        convertView = inflater.inflate(R.layout.person_card, null);
        final View view = convertView;

        TextView name = (TextView)convertView.findViewById(R.id.personName);
        TextView title=(TextView)convertView.findViewById(R.id.personAcademicTitle);
        ImageView profilePic = (ImageView)convertView.findViewById(R.id.profile_image);
        ImageButton btnDelete = (ImageButton)convertView.findViewById(R.id.deletePerson);
        ImageButton btnUpdate = (ImageButton)convertView.findViewById(R.id.editPerson);
        LinearLayout toMissions = (LinearLayout) convertView.findViewById(R.id.personClick);
        TextView openMissions=(TextView) convertView.findViewById(R.id.personOpenMissions);
        CardView personCard = (CardView) convertView.findViewById(R.id.personCard);
        PersonEntity person = getItem(position);
        SwipeLayout swipeLayout = convertView.findViewById(R.id.swipePerson);

        swipeLayout.addSwipeListener(this);

        convertView.findViewById(R.id.person_swipe_btn).setOnClickListener(v -> {
            if (swipeOpen) {
                swipeLayout.close();
            } else {
                swipeLayout.open();
            }
        });

        List<MissionEntity> missions = DB.getMissionsForPerson(person.getID());
        int openMissionCounter = 0;
        for(MissionEntity i:missions){
            if(!(i.isClosed())){
                openMissionCounter++;
            }
        }
        openMissions.setText(openMissionCounter+"");
        if(openMissionCounter==0){
            openMissions.setVisibility(View.INVISIBLE);
        }

        profilePic.setImageResource(R.drawable.ic_user);
        name.setText(person.getName()+" "+person.getLastName());
        String academicTitle = person.getAcademicTitle();

        if ((academicTitle == null) || academicTitle.replaceAll(" ","").equals("")) {
            title.setText(context.getString(R.string.noAcademicTitle));
        }
        else {
            title.setText(person.getAcademicTitle());
        }

        //profile pic set, up to cache memory
        Glide.with(context)
                .load("")
                .placeholder(R.drawable.ic_user)
                .into(profilePic);

        toMissions.setOnClickListener(new View.OnClickListener(){
            public void onClick (View v){
                if (swipeOpen) {
                    swipeLayout.close();
                } else {
                    Intent missionTab = new Intent(context, MissionsTabbed.class);
                    missionTab.putExtra(IntentCodes.INTENT_PERSON_ID,person.getID());
                    ((MainActivity) context).startActivityForResult(missionTab, 1);
                }
            }
        });

        btnDelete.setOnClickListener(new View.OnClickListener(){
            public void onClick (View v){
                deletePerson(view, (int)person.getID(), position);
            }
        });

        btnUpdate.setOnClickListener(new View.OnClickListener(){
            public void onClick (View v){
                //Open Edit Person Activity
                Intent editPerson = new Intent(context, EditPerson.class);
                editPerson.putExtra("personID", person.getID());
                editPerson.putExtra(IntentCodes.INTENT_PERSON_ID,person.getID());
                ((MainActivity)context).startActivityForResult(editPerson, 1);
            }
        });

        return convertView;
    }

    /**
     * Mantovan Federico (adapted by Dal Maso)
     * Delete the person and the missions\tickets associated with it
     */
    protected void deletePerson(View v, int personID, final int position){
        AlertDialog.Builder toast = new AlertDialog.Builder(context);
        //Dialog
        toast.setMessage(context.getString(R.string.delete_person))
                .setTitle(context.getString(R.string.delete_title_person));
        //Positive button
        toast.setPositiveButton(context.getString(R.string.buttonDelete), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                List<MissionEntity> listMissions = DB.getMissionsForPerson(personID);
                List<TicketEntity> listTickets;
                for(int i = 0; i < listMissions.size(); i++){
                    listTickets = DB.getTicketsForMission(listMissions.get(i).getID());
                    for(int j = 0; j < listTickets.size(); j++){
                        DB.deleteTicket(listTickets.get(j).getID());
                    }
                    DB.deleteMission(listMissions.get(i).getID());
                }
                DB.deletePerson(personID);
                ((MainActivity)context).deleteCell(v, position);
            }
        });
        //Negative button
        toast.setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //Nothing to do
            }
        });
        //Show toast
        AlertDialog alert = toast.show();
        Button nbutton = alert.getButton(DialogInterface.BUTTON_POSITIVE);
        nbutton.setTextColor(Color.parseColor("#2196F3"));
    }

    @Override
    public void onStartOpen(SwipeLayout layout) {

    }

    @Override
    public void onOpen(SwipeLayout layout) {
        swipeOpen = true;
    }

    @Override
    public void onStartClose(SwipeLayout layout) {

    }

    @Override
    public void onClose(SwipeLayout layout) {
        swipeOpen = false;
    }

    @Override
    public void onUpdate(SwipeLayout layout, int leftOffset, int topOffset) {

    }

    @Override
    public void onHandRelease(SwipeLayout layout, float xvel, float yvel) {

    }
}
