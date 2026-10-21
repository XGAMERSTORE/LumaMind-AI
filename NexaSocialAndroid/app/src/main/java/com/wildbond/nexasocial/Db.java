package com.wildbond.nexasocial;

import android.content.*;
import android.database.sqlite.*;
import android.database.Cursor;
import org.json.*;

public class Db extends SQLiteOpenHelper {
    public Db(Context c){ super(c,"nexa_local.db",null,1); }
    public void onCreate(SQLiteDatabase db){ db.execSQL("CREATE TABLE cache(chat_id INTEGER, msg_id INTEGER PRIMARY KEY, sender TEXT, body TEXT, created_at TEXT)"); }
    public void onUpgrade(SQLiteDatabase db,int a,int b){}
    public void cacheMessage(int chat,int id,String sender,String body,String created){
        ContentValues v=new ContentValues(); v.put("chat_id",chat); v.put("msg_id",id); v.put("sender",sender); v.put("body",body); v.put("created_at",created);
        getWritableDatabase().insertWithOnConflict("cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public JSONArray cachedMessages(int chat){
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT msg_id,sender,body,created_at FROM cache WHERE chat_id=? ORDER BY msg_id",new String[]{String.valueOf(chat)});
        try{ while(c.moveToNext()){ JSONObject o=new JSONObject(); o.put("id",c.getInt(0)); o.put("sender",c.getString(1)); o.put("body",c.getString(2)); o.put("created_at",c.getString(3)); a.put(o);} }catch(Exception ignored){} finally{c.close();}
        return a;
    }
}
