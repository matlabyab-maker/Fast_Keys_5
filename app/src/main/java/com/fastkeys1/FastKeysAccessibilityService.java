package com.fastkeys1;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class FastKeysAccessibilityService extends AccessibilityService {
    private static FastKeysAccessibilityService instance;
    private WindowManager wm;
    private TextView cursor;
    private WindowManager.LayoutParams lp;
    private int screenW, screenH;
    private float cursorX, cursorY;
    private final Handler handler=new Handler(Looper.getMainLooper());

    public static boolean isEnabled(){ return instance!=null; }

    @Override protected void onServiceConnected(){
        super.onServiceConnected(); instance=this;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(dm);
        screenW=dm.widthPixels; screenH=dm.heightPixels; cursorX=screenW/2f; cursorY=screenH/2f;
        showCursor();
    }
    private void showCursor(){
        if(wm==null||cursor!=null)return;
        cursor=new TextView(this); cursor.setText("➤"); cursor.setTextColor(Color.BLACK); cursor.setTextSize(30); cursor.setGravity(Gravity.CENTER);
        cursor.setBackground(new ColorDrawable(Color.TRANSPARENT));
        lp=new WindowManager.LayoutParams(dp(46),dp(46),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; updatePos();
        try{wm.addView(cursor,lp);}catch(Exception ignored){}
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void updatePos(){if(lp==null||wm==null)return;lp.x=Math.max(0,Math.min(screenW-dp(46),(int)cursorX-dp(23)));lp.y=Math.max(0,Math.min(screenH-dp(46),(int)cursorY-dp(23)));try{wm.updateViewLayout(cursor,lp);}catch(Exception ignored){}}
    public static void movePointer(float dx,float dy){ if(instance!=null) instance.move(dx,dy); }
    private void move(float dx,float dy){ cursorX=Math.max(0,Math.min(screenW,cursorX+dx));cursorY=Math.max(0,Math.min(screenH,cursorY+dy));updatePos(); }
    public static void click(boolean right){ if(instance!=null)instance.tap(right); }
    public static void disable(){ if(instance!=null) instance.stopSelf(); }
    private void tap(boolean right){
        if(right){
            // Android has no universal right-click action in AccessibilityService; long-press is the closest portable action.
            longPress(cursorX,cursorY);
            return;
        }
        // First try the actual accessibility node under the pointer. This makes web links/buttons
        // clickable even when a browser does not expose them reliably to coordinate gestures.
        if (clickNodeAt(getRootInActiveWindow(), cursorX, cursorY)) return;
        clickAt(cursorX,cursorY);
    }
    private boolean clickNodeAt(AccessibilityNodeInfo node, float x, float y){
        if(node==null) return false;
        try{
            // Search children first so the smallest actual web button/link wins.
            for(int i=node.getChildCount()-1;i>=0;i--){
                if(clickNodeAt(node.getChild(i),x,y)) return true;
            }
            android.graphics.Rect b=new android.graphics.Rect();
            node.getBoundsInScreen(b);
            if(b.contains((int)x,(int)y) && node.isVisibleToUser() && node.isClickable()){
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }catch(Exception ignored){}
        return false;
    }
    private void clickAt(float x,float y){
        Path p=new Path();p.moveTo(x,y);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,60)).build();dispatchGesture(g,null,null);
    }
    private void longPress(float x,float y){
        Path p=new Path();p.moveTo(x,y);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,650)).build();dispatchGesture(g,null,null);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){instance=null;try{if(cursor!=null&&wm!=null)wm.removeView(cursor);}catch(Exception ignored){}cursor=null;super.onDestroy();}
}
