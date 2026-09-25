package com.fastkeys1;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

public class FloatingKeyboardOverlayService extends Service {
    private WindowManager wm;
    private LinearLayout root;
    private WindowManager.LayoutParams lp;
    private float sx,sy; private int ox,oy;
    @Override public void onCreate(){ super.onCreate();
        if(!Settings.canDrawOverlays(this)){ stopSelf(); return; }
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(2,2,2,2);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFFF7F5ED); bg.setCornerRadius(18); root.setBackground(bg);
        TextView drag=new TextView(this); drag.setText("⠿  Fast Keys Float   ×"); drag.setTextColor(Color.rgb(18,38,78)); drag.setTextSize(13); drag.setGravity(Gravity.CENTER); drag.setPadding(8,0,8,0);
        root.addView(drag,new LinearLayout.LayoutParams(-1,42));
        FastKeysInputMethodService svc=FastKeysInputMethodService.getInstance();
        if(svc==null){ stopSelf(); return; }
        FastKeysKeyboardView keyboard=new FastKeysKeyboardView(svc);
        root.addView(keyboard,new LinearLayout.LayoutParams(-1,dp(320)));
        drag.setOnTouchListener((v,e)->{ if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();ox=lp.x;oy=lp.y;return true;} if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=ox+(int)(e.getRawX()-sx);lp.y=oy+(int)(e.getRawY()-sy);try{wm.updateViewLayout(root,lp);}catch(Exception ignored){} return true;} if(e.getAction()==MotionEvent.ACTION_UP){ if(Math.abs(e.getRawX()-sx)<20 && Math.abs(e.getRawY()-sy)<20) stopSelf(); return true;} return true;});
        int type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        lp=new WindowManager.LayoutParams(dp(420),dp(365),type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=20; lp.y=80;
        try{wm.addView(root,lp);}catch(Exception e){stopSelf();}
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    @Override public void onDestroy(){try{if(root!=null&&wm!=null)wm.removeView(root);}catch(Exception ignored){} root=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
