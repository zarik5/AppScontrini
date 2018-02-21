package com.ing.software.ticketapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.daimajia.swipe.SwipeLayout;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import database.DataManager;
import database.TicketEntity;

/**
 * Created by nicoladalmaso on 28/10/17.
 */

public class CustomAdapter extends ArrayAdapter<TicketEntity> {

    Context context;
    String path = "";
    int ticketID = 0;
    int missionID;
    DataManager DB;
    List<TicketEntity> t = new ArrayList<TicketEntity>();

    //Dal Maso, adapter declare
    public CustomAdapter(Context context, int textViewResourceId,
                         List<TicketEntity> objects, int missionID, DataManager DB) {
        super(context, textViewResourceId, objects);
        this.context = context;
        this.missionID = missionID;
        this.DB = new DataManager(context);
        this.t = objects;
    }

    /** Dal Maso
     * It manages the Adapter
     * @param position item position
     * @param convertView my custom view
     * @param parent
     * @return view setted
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        convertView = inflater.inflate(R.layout.cardview, null);

        ImageView img = (ImageView)convertView.findViewById(R.id.image);
        TextView ticketTitle = (TextView)convertView.findViewById(R.id.title);
        TextView ticketShop = (TextView)convertView.findViewById(R.id.shop);
        TextView tot = (TextView)convertView.findViewById(R.id.description);

        TicketEntity c = getItem(position);
        File photo = new File(c.getFileUri().toString().substring(7));

        if(c.getTitle().replace(" ", "").length() == 0){
            ticketTitle.setText(context.getResources().getString(R.string.title_Ticket));
        } else {
            ticketTitle.setText(c.getTitle());
        }

        if(c.isRefundable()){
            ticketShop.setText(context.getResources().getString(R.string.refundable));
            ticketShop.setTextColor(Color.parseColor("#00C853"));
        } else {
            ticketShop.setText(context.getResources().getString(R.string.nonRefundable));
            ticketShop.setTextColor(Color.parseColor("#D50000"));
        }

        //Amount text fixes
        String amount = "";
        if(c.getAmount() == null || c.getAmount().compareTo(new BigDecimal(0.00, MathContext.DECIMAL64)) <= 0){
            amount = context.getString(R.string.string_NoAmount);
            tot.setText(amount);
        } else {
            amount = c.getPricePerson().setScale(2, RoundingMode.HALF_EVEN).toString();
            tot.setText(amount + " " + Singleton.getInstance().getCurrency());
        }

        //Ticket image bitmap set
        Glide.with(context)
                .load(photo.getAbsolutePath())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(img);

        //For next ticket manages
        convertView.setTag(c.getID());

        //Dal Maso
        convertView.setOnClickListener(new View.OnClickListener(){
            public void onClick (View v){
                ticketID = Integer.parseInt(v.getTag().toString());
                for(int i = 0; i < t.size(); i++){
                    if(t.get(i).getID() == ticketID){
                        TicketEntity thisTicket = t.get(i);
                        Intent startImageView = new Intent(context, BillViewer.class);
                        File photo = new File(thisTicket.getFileUri().toString().substring(7));

                        //Put data to next activity
                        Singleton.getInstance().setTicketID(ticketID);

                        //Start new activity
                        ((BillActivity)context).startActivityForResult(startImageView, 4);
                        return;
                    }
                }
            }//onClick
        });
        return convertView;
    }
}