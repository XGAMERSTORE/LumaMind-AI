package cz.xgamerstore.autocontinue;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.*;
import android.text.InputType;
import android.graphics.Color;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("config", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(28, 50, 28, 40);
        scroll.addView(l);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("AUTO CONTINUE"); title.setTextSize(29);
        l.addView(title);
        label(l, "ChatGPT • automatické pokračování");
        label(l, "Zpráva");
        EditText message = new EditText(this);
        message.setText(p.getString("message", "d")); l.addView(message);
        label(l, "Prodleva po dokončení (sekundy, 3–120)");
        EditText delay = number(p.getInt("delay",5)); l.addView(delay);
        label(l, "Limit odeslání (0 = bez nastaveného limitu)");
        EditText limit = number(p.getInt("limit",20)); l.addView(limit);
        Button save = new Button(this); save.setText("Uložit nastavení"); l.addView(save);
        save.setOnClickListener(v -> {
            try {
                String msg=message.getText().toString().trim();
                int d=Integer.parseInt(delay.getText().toString()), n=Integer.parseInt(limit.getText().toString());
                if(msg.isEmpty() || msg.length()>500 || d<3 || d>120 || n<0 || n>10000) throw new IllegalArgumentException();
                p.edit().putString("message",msg).putInt("delay",d).putInt("limit",n).apply();
                Toast.makeText(this,"Uloženo",Toast.LENGTH_SHORT).show();
            } catch(Exception e) { Toast.makeText(this,"Zkontrolujte zprávu a číselné hodnoty.",Toast.LENGTH_LONG).show(); }
        });
        Button accessibility = new Button(this); accessibility.setText("Povolit službu zpřístupnění"); l.addView(accessibility);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button open = new Button(this); open.setText("Otevřít ChatGPT"); l.addView(open);
        open.setOnClickListener(v -> {
            Intent i=getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
            if(i!=null) startActivity(i); else Toast.makeText(this,"ChatGPT nebyl nalezen.",Toast.LENGTH_SHORT).show();
        });
        label(l, "V ChatGPT klepněte na plovoucí START a spusťte první odpověď ručně. Při opuštění ChatGPT se automatizace vypne. STOP ji ihned zastaví.");
        label(l, "Soukromí: bez internetového oprávnění. Služba čte ovládací prvky obrazovky pouze v ChatGPT. Funguje jen při zobrazeném ChatGPT a odemčeném telefonu. Nevynucuje pokračování při chybách nebo neznámém rozhraní.");
    }
    private EditText number(int v) { EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setText(""+v); return e; }
    private void label(LinearLayout l,String s) { TextView t=new TextView(this); t.setText(s); t.setTextSize(16); t.setPadding(0,14,0,8); l.addView(t); }
}
