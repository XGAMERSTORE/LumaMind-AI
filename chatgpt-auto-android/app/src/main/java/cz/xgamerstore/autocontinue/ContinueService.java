package cz.xgamerstore.autocontinue;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.app.KeyguardManager;
import android.os.PowerManager;
import java.util.Locale;

public class ContinueService extends AccessibilityService {
    private static final String TARGET="com.openai.chatgpt";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private Button overlay;
    private SharedPreferences prefs;
    private boolean armed=false, sawBusy=false, pending=false;
    private long readyAt=0, typedAt=0;
    private String inserted="";
    private int sent=0;
    private final Runnable poll=new Runnable() {
        @Override public void run() {
            try { tick(); } catch(RuntimeException e) { disarm(); }
            handler.postDelayed(this,350);
        }
    };
    @Override protected void onServiceConnected() {
        prefs=getSharedPreferences("config",MODE_PRIVATE);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        handler.removeCallbacks(poll);
        handler.post(poll);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { disarm(); }
    @Override public void onDestroy() {
        handler.removeCallbacks(poll); hideOverlay(); super.onDestroy();
    }
    private void disarm() {
        armed=false; sawBusy=false; pending=false; readyAt=0; inserted="";
        updateLabel("START");
    }
    private void updateLabel(String s) { if(overlay!=null) overlay.setText(s); }
    private void showOverlay() {
        if(overlay!=null) return;
        overlay=new Button(this); overlay.setText("START");
        overlay.setContentDescription("Auto Continue START nebo STOP");
        overlay.setOnClickListener(v -> {
            if(armed) disarm();
            else { armed=true; sawBusy=false; pending=false; readyAt=0; sent=0; updateLabel("STOP • čekám"); }
        });
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.RIGHT; p.x=8; p.y=130;
        wm.addView(overlay,p);
    }
    private void hideOverlay() { if(overlay!=null) { wm.removeView(overlay); overlay=null; } }
    private static String norm(CharSequence s) {
        return s==null?"":s.toString().trim().toLowerCase(Locale.ROOT);
    }
    private static boolean exact(String s,String... options) {
        for(String option:options) if(s.equals(option)) return true;
        return false;
    }
    private static boolean matches(AccessibilityNodeInfo n,String... options) {
        return exact(norm(n.getContentDescription()),options) || exact(norm(n.getText()),options);
    }
    private static boolean button(AccessibilityNodeInfo n) {
        return n.isVisibleToUser() && (n.isClickable() || norm(n.getClassName()).contains("button"));
    }
    private static final class Controls {
        AccessibilityNodeInfo stop,send,composer;
        int composers=0, sends=0;
    }
    private void scan(AccessibilityNodeInfo n,Controls c,int depth) {
        if(n==null || depth>60) return;
        if(n.isVisibleToUser()) {
            if(button(n)) {
                if(matches(n,"stop generating","stop generation","stop streaming","stop response","stop","zastavit generování","zastavit odpověď","zastavit")) c.stop=n;
                if(matches(n,"send","send message","odeslat","odeslat zprávu")) { c.send=n; c.sends++; }
            }
            if(n.isEditable() && n.isEnabled()) { c.composer=n; c.composers++; }
        }
        for(int i=0;i<n.getChildCount();i++) scan(n.getChild(i),c,depth+1);
    }
    private String text(AccessibilityNodeInfo n) {
        if(n==null || n.isShowingHintText() || n.getText()==null) return "";
        return n.getText().toString();
    }
    private boolean setText(AccessibilityNodeInfo n,String value) {
        Bundle b=new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);
    }
    private void tick() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(root==null || !TARGET.contentEquals(root.getPackageName()==null?"":root.getPackageName())
                || !pm.isInteractive() || km.isKeyguardLocked()) {
            disarm(); hideOverlay(); return;
        }
        showOverlay();
        if(!armed) return;
        Controls c=new Controls(); scan(root,c,0);
        long now=SystemClock.elapsedRealtime();
        if(c.stop!=null) {
            // Never type or click while generation is visibly active.
            if(pending) { disarm(); return; }
            sawBusy=true; readyAt=0; updateLabel("STOP • odpověď"); return;
        }
        if(!sawBusy && !pending) return;
        if(c.composers!=1 || c.composer==null) { disarm(); return; }
        if(pending) {
            if(!text(c.composer).equals(inserted)) { disarm(); return; }
            if(c.sends==1 && c.send.isEnabled() && c.send.isClickable()) {
                boolean clicked=c.send.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if(!clicked) { disarm(); return; }
                sent++; pending=false; sawBusy=false; readyAt=0; inserted="";
                int cap=prefs.getInt("limit",20);
                if(cap>0 && sent>=cap) { disarm(); return; }
                updateLabel("STOP • "+sent); return;
            }
            if(now-typedAt>4000) disarm();
            return;
        }
        if(!text(c.composer).isEmpty()) { disarm(); return; }
        if(readyAt==0) readyAt=now;
        int delay=Math.max(3,Math.min(120,prefs.getInt("delay",5)));
        updateLabel("STOP • "+Math.max(0,(delay*1000-(now-readyAt)+999)/1000)+" s");
        if(now-readyAt<delay*1000L) return;
        String msg=prefs.getString("message","d");
        if(msg==null || msg.trim().isEmpty()) { disarm(); return; }
        if(!setText(c.composer,msg)) { disarm(); return; }
        inserted=msg; typedAt=now; pending=true;
    }
}
