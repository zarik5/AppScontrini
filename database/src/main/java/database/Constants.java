package database;

/**
 * Created by Federico Taschin on 12/11/2017.
 * Modified by Marco Olivieri
 *
 * Modified: improve refundable Ticket
 * @author Matteo Mascotto
 */

public final class Constants {

    //Database Constants
    public static final String DATABASE_NAME = "ticket-database";
    public static final String TICKET_TABLE_NAME = "tickets";
    public static final String MISSION_TABLE_NAME = "missions";
    public static final String PERSON_TABLE_NAME = "persons";
    public static final String SETTINGS_TABLE_NAME = "settings";

    //Foreign Keys
    public static final String TICKET_CHILD_COLUMNS = "ticket_ID";
    public static final String PERSON_CHILD_COLUMNS = "person_ID";
    public static final String MISSION_CHILD_COLUMNS = "mission_ID";

    //Entity constants
    //Tickets constants
    public static final String TICKET_PRIMARY_KEY = "ID";
    public static final String TICKET_FIELD_DATE = "date";
    public static final String TICKET_FIELD_CATEGORY = "category";
    public static final String TICKET_INSERTION_DATE = "insertionDate";
    public static final String TICKET_TAG_PLACES = "tagPlaces";
    public static final String TICKET_IS_REFUNDABLE = "isRefundable";

    //Mission's constants
    public static final String MISSION_PRIMARY_KEY = "ID";
    public static final String MISSION_FIELD_START_DATE = "startDate";
    public static final String MISSION_FIELD_END_DATE = "endDate";
    public static final String MISSION_FIELD_LOCATION = "location";
    public static final String MISSION_FIELD_CLOSED = "isClosed";
    public static final String MISSION_FIELD_NAME = "name";

    //Person's constants
    public static final String PERSON_PRIMARY_KEY = "ID";
    public static final String PERSON_FIELD_NAME = "name";
    public static final String PERSON_FIELD_LAST_NAME = "lastName";

    //Settings' contants
    public static final String SETTINGS_PRIMARY_KEY = "ID";

}