package me.j360.binlog.canal.client.constant;



public class BinlogConfig {


    public final static String SCHEMA_NAME= "j360";
    public final static String TABLE_USER = "user";

    public final static String INDEX_USER = "idx-user";
    public final static String TYPE_USER = "user";

    public final static String INDEX_TOPIC = "idx-topic";
    public final static String TYPE_TOPIC = "topic";

    public final static String PRE_USER_URL = "/"+ INDEX_USER + "/" + TYPE_USER + "/";


    public final static String TOPIC = "j360-user-1";
}
