package com.ing.software.ticketapp;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.daimajia.swipe.SwipeLayout;

import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import database.DataManager;
import database.MissionEntity;
import database.TicketEntity;

/**
 * Created by nicoladalmaso on 30/11/17.
 */

public class MissionAdapterDB extends ArrayAdapter<MissionEntity> implements SwipeLayout.SwipeListener {

    Context context;
    String path = "";
    long missionID = 0;
    List<MissionEntity> missions;
    DataManager DB;

    //swipeLayout does not have and internal swipe state, I need to update this variable with swipe events
    boolean swipeOpen = false;

    public MissionAdapterDB(Context context, int textViewResourceId,
                          List<MissionEntity> objects) {
        super(context, textViewResourceId, objects);
        this.context = context;
        missions = objects;
        DB = new DataManager(getContext());
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

        convertView = inflater.inflate(R.layout.mission_card, null);
        final View view = convertView;

        SwipeLayout card = (SwipeLayout) convertView.findViewById(R.id.swipeMission);
        TextView title = (TextView)convertView.findViewById(R.id.missionTitle);
        TextView location = (TextView)convertView.findViewById(R.id.missionLocation);
        TextView total = (TextView)convertView.findViewById(R.id.missionTotal);
        ImageButton btnDelete = (ImageButton)convertView.findViewById(R.id.deleteMission);
        ImageButton btnUpdate = (ImageButton)convertView.findViewById(R.id.editMission);
        RelativeLayout toTickets = (RelativeLayout) convertView.findViewById(R.id.missionClick);

        ImageButton swipeButton = convertView.findViewById(R.id.mission_swipe_btn);
        card.addSwipeListener(this);
        swipeButton.setOnClickListener(v -> {
            if (swipeOpen) {
                card.close();
            } else {
                card.open();
            }
        });

        MissionEntity mission = getItem(position);

        title.setText(mission.getName());
        location.setText(mission.getLocation());
        total.setText(DB.getTotalAmountForMission(mission.getID()).setScale(2, RoundingMode.HALF_EVEN).toString() + " " + Singleton.getInstance().getCurrency());
        convertView.setTag(mission.getID());
        Date start = mission.getStartDate();
        SimpleDateFormat tr = new SimpleDateFormat("dd/MM/yyyy");
        String startDate = tr.format(start);
        Date finish = mission.getEndDate();
        String finishDate =tr.format(finish);

        if(mission.isClosed()) {
            int textColor = context.getResources().getColor(R.color.white);
            card.setBackgroundColor(Color.parseColor("#7c7c7c"));
            total.setTextColor(textColor);
            location.setTextColor(textColor);
            title.setTextColor(textColor);
            swipeButton.setImageDrawable(convertView.getResources().getDrawable(R.drawable.ic_action_more_vert_dark));
        }

        toTickets.setOnClickListener(new View.OnClickListener(){
            public void onClick (View v){
                if (swipeOpen) {
                    card.close();
                } else {
                	missionID = Integer.parseInt(view.getTag().toString());
                    Intent startTicketsView = new Intent(context, BillActivity.class);
                	startTicketsView.putExtra(IntentCodes.INTENT_MISSION_ID, missionID);
                    ((MissionsTabbed) context).startActivity(startTicketsView);
                }
            }
        });

        btnDelete.setOnClickListener(new View.OnClickListener(){
            public void onClick (View v){
                deleteMission(view, mission, position);
            }
        });

        btnUpdate.setOnClickListener(new View.OnClickListener(){
            public void onClick (View v){
                //Open Edit Person Activity
                missionID = Integer.parseInt(view.getTag().toString());
                Intent editMission = new Intent(context, EditMission.class);
                editMission.putExtra(IntentCodes.INTENT_MISSION_ID, missionID);
                ((MissionsTabbed)context).startActivity(editMission);
            }
        });

        return convertView;
    }

    /**
     * Nicola Dal Maso
     * Delete the person and the missions\tickets associated with it
     */
    public void deleteMission(View v, MissionEntity mission, int position){
        AlertDialog.Builder toast = new AlertDialog.Builder(context);
        //Dialog
        toast.setMessage(context.getString(R.string.deleteMissionToast))
                .setTitle(context.getString(R.string.deleteTitle));
        //Positive button
        toast.setPositiveButton(context.getString(R.string.buttonDelete), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                List<TicketEntity> list = DB.getTicketsForMission(mission.getID());
                for(int i = 0; i < list.size(); i++){
                    DB.deleteTicket((int) list.get(i).getID());
                }
                DB.deleteMission(mission.getID());
                if(!mission.isClosed())
                    ((MissionsTabbed)context).updateThisAdapter(0, v, position);
                else
                    ((MissionsTabbed)context).updateThisAdapter(1, v, position);
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
