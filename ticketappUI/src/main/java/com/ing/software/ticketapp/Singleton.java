package com.ing.software.ticketapp;

import android.util.Log;

import java.util.Date;

/**
 * Created by Nicola on 24/01/2018.
 */

public class Singleton {
    private static Singleton mInstance = null;

    private byte[] pictureTaken; //it saves the system from another picture save (-2 sec in photo taking process)
    private Date startDate;
    private Date endDate;
    private String currency;
    private int flag;
    private boolean flagStart;
    private Singleton(){
        pictureTaken = null;
        startDate = null;
        flag = 0;
        currency = "";
    }

    public static synchronized Singleton getInstance(){
        if (mInstance == null) {
            mInstance = new Singleton();
        }
        return mInstance;
    }



    public byte[] getTakenPicture(){
        return pictureTaken;
    }

    public void setTakenPicure(byte[] value){
        pictureTaken = value;
    }


    //lazzarin
    public Date getStartDate(){return startDate;}

    public void setStartDate(Date start){startDate = start;}

    public void setStartFlag(int value){flag = value;}

    public int getStartFlag(){return flag;}

    public Date getEndDate(){return endDate;}

    public void setEndDate(Date end){endDate = end;}

    public void setCurrency(String curr){
        switch (curr){
            case ("EUR"):
                currency = "\u20ac";
                break;
            case ("USD"):
                currency = "\u0024";
                break;
            case ("GBP"):
                currency = "\u20a4";
                break;
            default:
                currency = "\u20ac";
                break;
        }
    }

    public String getCurrency() {return currency;}

}