package com.wildbond.nexasocial;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    LinearLayout root, list; Api api; Db db; ExecutorService io=Executors.newCachedThreadPool();
    String token="", username=""; int activeChat=0; SharedPreferences prefs;
    int bg=Color.rgb(13,13,18), card=Color.rgb(28,28,38), accent=Color.rgb(108,77,255), text=Color.WHITE, muted=Color.rgb(180,180,195);

    @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("nexa",MODE_PRIVATE); db=new Db(this); showLogin(); }
    TextView tv(String s,int sp){ TextView v=new TextView(this); v.setText(s); v.setTextColor(text); v.setTextSize(sp); v.setPadding(14,14,14,14); return v; }
    EditText input(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(muted); e.setTextColor(text); e.setSingleLine(true); e.setBackgroundColor(card); e.setPadding(16,12,16,12); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,8,0,8); e.setLayoutParams(p); return e; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setBackgroundColor(accent); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,8,0,8); b.setLayoutParams(p); return b; }
    void base(){ root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,28,18,18); root.setBackgroundColor(bg); setContentView(root); }
    void toast(String s){ runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show()); }
    void runNet(NetJob j){ io.submit(()->{ try{ j.run(); }catch(Exception e){toast(e.getMessage());} }); }
    interface NetJob{void run()throws Exception;}

    void showLogin(){
        base(); root.addView(tv("Nexa Social",28)); root.addView(tv("Klient pro vlastní Nexa server",15));
        EditText server=input("Adresa serveru, např. http://192.168.1.50:8787"); server.setText(prefs.getString("server","http://10.0.2.2:8787"));
        EditText user=input("Uživatelské jméno"); EditText pass=input("Heslo"); pass.setInputType(0x81);
        Button login=btn("Přihlásit"); Button reg=btn("Vytvořit účet");
        root.addView(server);root.addView(user);root.addView(pass);root.addView(login);root.addView(reg);
        View.OnClickListener go=v->{ String u=user.getText().toString().trim(), p=pass.getText().toString(), base=server.getText().toString().trim();
            if(u.isEmpty()||p.length()<4||base.isEmpty()){toast("Vyplň server, jméno a heslo.");return;}
            prefs.edit().putString("server",base).apply(); api=new Api(base);
            runNet(()->{ JSONObject body=new JSONObject().put("username",u).put("password",p); JSONObject r=api.post(v==reg?"/register":"/login",body); token=r.getString("token"); username=r.getJSONObject("user").getString("username"); api.token=token; runOnUiThread(this::showHome); });
        };
        login.setOnClickListener(go); reg.setOnClickListener(go);
    }

    void showHome(){
        base();
        LinearLayout bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView title=tv("Nexa Social  @"+username,22); bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=btn("↻"); bar.addView(refresh,new LinearLayout.LayoutParams(90,-2)); root.addView(bar);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button newChat=btn("Nový chat"), status=btn("Status"), group=btn("Skupina");
        actions.addView(newChat,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(status,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(group,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        ScrollView sv=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        refresh.setOnClickListener(v->loadChats()); newChat.setOnClickListener(v->promptDirect()); status.setOnClickListener(v->showStatuses()); group.setOnClickListener(v->promptGroup()); loadChats();
    }

    void loadChats(){
        runNet(()->{ JSONArray a=api.get("/chats").getJSONArray("chats"); runOnUiThread(()->{ list.removeAllViews();
            try{ for(int i=0;i<a.length();i++){ JSONObject c=a.getJSONObject(i); Button b=btn((c.optBoolean("is_group")?"👥 ":"💬 ")+c.getString("title")+"\n"+c.optString("last_message","")); int id=c.getInt("id"); String title=c.optString("title","Chat"); b.setOnClickListener(v->showChat(id,title)); list.addView(b); } }catch(Exception e){toast(e.getMessage());}
        });});
    }

    void promptDirect(){
        final EditText e=input("@uživatelské jméno");
        new AlertDialog.Builder(this).setTitle("Nový chat").setView(e).setPositiveButton("Vytvořit",(d,w)->runNet(()->{
            String name=e.getText().toString().replace("@","").trim();
            JSONObject r=api.post("/chats/direct",new JSONObject().put("username",name)); int id=r.getJSONObject("chat").getInt("id"); runOnUiThread(()->showChat(id,name));
        })).setNegativeButton("Zrušit",null).show();
    }

    void promptGroup(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText name=input("Název skupiny"), members=input("Členové: anna,petr"); box.addView(name); box.addView(members);
        new AlertDialog.Builder(this).setTitle("Nová skupina").setView(box).setPositiveButton("Vytvořit",(d,w)->runNet(()->{
            JSONArray arr=new JSONArray(); for(String s:members.getText().toString().split(",")) if(!s.trim().isEmpty()) arr.put(s.trim().replace("@",""));
            JSONObject r=api.post("/groups",new JSONObject().put("name",name.getText().toString().trim()).put("members",arr));
            int id=r.getJSONObject("chat").getInt("id"); runOnUiThread(()->showChat(id,name.getText().toString()));
        })).setNegativeButton("Zrušit",null).show();
    }

    void showChat(int id,String title){
        activeChat=id; base();
        LinearLayout bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); Button back=btn("←");
        bar.addView(back,new LinearLayout.LayoutParams(90,-2)); bar.addView(tv(title,21),new LinearLayout.LayoutParams(0,-2,1)); root.addView(bar); back.setOnClickListener(v->showHome());
        ScrollView sv=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout send=new LinearLayout(this); send.setOrientation(LinearLayout.HORIZONTAL); EditText msg=input("Napiš zprávu…"); Button b=btn("➤");
        send.addView(msg,new LinearLayout.LayoutParams(0,-2,1)); send.addView(b,new LinearLayout.LayoutParams(90,-2)); root.addView(send);
        b.setOnClickListener(v->{ String m=msg.getText().toString().trim(); if(m.isEmpty())return; msg.setText(""); runNet(()->{api.post("/messages/send",new JSONObject().put("chat_id",id).put("body",m)); loadMessages(id);});});
        loadMessages(id);
    }

    void loadMessages(int id){
        runNet(()->{ JSONArray a; try{a=api.get("/messages?chat_id="+id).getJSONArray("messages");}catch(Exception ex){a=db.cachedMessages(id);} JSONArray out=a;
            runOnUiThread(()->{ list.removeAllViews(); try{ for(int i=0;i<out.length();i++){ JSONObject m=out.getJSONObject(i); String sender=m.optString("sender"); TextView t=tv((sender.equals(username)?"Ty":sender)+": "+m.optString("body"),16); t.setBackgroundColor(card); list.addView(t); db.cacheMessage(id,m.optInt("id"),sender,m.optString("body"),m.optString("created_at")); } }catch(Exception e){toast(e.getMessage());} });
        });
    }

    void showStatuses(){
        base(); Button back=btn("← Zpět"), add=btn("Přidat status"); root.addView(back); root.addView(add);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); ScrollView sv=new ScrollView(this); sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        back.setOnClickListener(v->showHome());
        add.setOnClickListener(v->{ EditText e=input("Co se děje?"); new AlertDialog.Builder(this).setTitle("Nový status").setView(e).setPositiveButton("Přidat",(d,w)->runNet(()->{api.post("/statuses",new JSONObject().put("body",e.getText().toString().trim()));loadStatuses();})).setNegativeButton("Zrušit",null).show();});
        loadStatuses();
    }

    void loadStatuses(){
        runNet(()->{ JSONArray a=api.get("/statuses").getJSONArray("statuses"); runOnUiThread(()->{ list.removeAllViews(); try{ for(int i=0;i<a.length();i++){ JSONObject s=a.getJSONObject(i); TextView t=tv("@"+s.getString("username")+"\n"+s.getString("body"),17); t.setBackgroundColor(card); list.addView(t); } }catch(Exception e){toast(e.getMessage());} });});
    }
}
