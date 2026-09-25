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
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;

public class FastKeysAccessibilityService extends AccessibilityService {
    private static FastKeysAccessibilityService instance;
    private WindowManager wm;
    private TextView cursor;
    private WindowManager.LayoutParams lp;
    private int screenW, screenH;
    private float cursorX, cursorY;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean screenshotBusy=false;

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
        sampleCursorBackground();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void updatePos(){if(lp==null||wm==null)return;lp.x=Math.max(0,Math.min(screenW-dp(46),(int)cursorX-dp(23)));lp.y=Math.max(0,Math.min(screenH-dp(46),(int)cursorY-dp(23)));try{wm.updateViewLayout(cursor,lp);}catch(Exception ignored){} sampleCursorBackground();}
    public static void movePointer(float dx,float dy){ if(instance!=null) instance.move(dx,dy); }
    private void move(float dx,float dy){ cursorX=Math.max(0,Math.min(screenW,cursorX+dx));cursorY=Math.max(0,Math.min(screenH,cursorY+dy));updatePos(); }
    public static void click(boolean right){ if(instance!=null)instance.tap(right); }
    public static void disable(){ if(instance!=null) instance.stopSelf(); }
    public static void scrollToTop(){ if(instance!=null) instance.scrollTop(); }
    private void tap(boolean right){ if(right){longPress(cursorX,cursorY);return;} if(clickNodeAt(getRootInActiveWindow(),cursorX,cursorY)) return; clickAt(cursorX,cursorY); }
    private void scrollTop(){
        // Use both mechanisms, not one as a fallback for the other. Some
        // browsers expose a scrollable node but ignore ACTION_SCROLL_BACKWARD,
        // while WebView/editor pages may only respond to a real finger swipe.
        final int[] pass={0};
        final Runnable[] runner=new Runnable[1];
        runner[0]=() -> {
            if(pass[0]++ >= 60) return;
            AccessibilityNodeInfo root=getRootInActiveWindow();
            scrollNodes(root);
            // Keep the swipe inside the visible content area so the keyboard
            // itself is not the target. A downward finger movement moves the
            // document toward its beginning.
            swipeDownToTop(() -> handler.postDelayed(runner[0],160));
        };
        handler.post(runner[0]);
    }

    private void swipeDownToTop(final Runnable done){
        float x=Math.max(dp(40),Math.min(screenW-dp(40),screenW/2f));
        float y1=Math.max(dp(90),screenH*0.18f);
        float y2=Math.min(screenH*0.68f,screenH-dp(220));
        if(y2<=y1+dp(40)){ done.run(); return; }
        Path p=new Path();
        p.moveTo(x,y1);
        p.lineTo(x,y2);
        GestureDescription g=new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p,0,700))
                .build();
        dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gestureDescription){ done.run(); }
            @Override public void onCancelled(GestureDescription gestureDescription){ handler.postDelayed(done,220); }
        },null);
    }

    private boolean scrollNodes(AccessibilityNodeInfo node){
        if(node==null)return false;
        boolean moved=false;
        try{
            if(node.isScrollable()){
                moved|=node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }
            for(int i=0;i<node.getChildCount();i++) moved|=scrollNodes(node.getChild(i));
        }catch(Exception ignored){}
        return moved;
    }

    private boolean clickNodeAt(AccessibilityNodeInfo node,float x,float y){if(node==null)return false;try{for(int i=node.getChildCount()-1;i>=0;i--)if(clickNodeAt(node.getChild(i),x,y))return true;android.graphics.Rect b=new android.graphics.Rect();node.getBoundsInScreen(b);if(b.contains((int)x,(int)y)&&node.isVisibleToUser()&&node.isClickable())return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);}catch(Exception ignored){}return false;}
    private void clickAt(float x,float y){Path p=new Path();p.moveTo(x,y);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,60)).build();dispatchGesture(g,null,null);}
    private void longPress(float x,float y){Path p=new Path();p.moveTo(x,y);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,650)).build();dispatchGesture(g,null,null);}
    private void sampleCursorBackground(){
        if(android.os.Build.VERSION.SDK_INT<30 || screenshotBusy || cursor==null) return;
        screenshotBusy=true;
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    try {
                        HardwareBuffer hb=result.getHardwareBuffer();
                        Bitmap bm=Bitmap.wrapHardwareBuffer(hb,result.getColorSpace());
                        if(bm!=null){
                            int bx=Math.max(0,Math.min(bm.getWidth()-1,(int)((cursorX+12)*bm.getWidth()/Math.max(1f,screenW))));
                            int by=Math.max(0,Math.min(bm.getHeight()-1,(int)((cursorY+12)*bm.getHeight()/Math.max(1f,screenH))));
                            int rgb=bm.getPixel(bx,by); float lum=(0.299f*Color.red(rgb)+0.587f*Color.green(rgb)+0.114f*Color.blue(rgb))/255f;
                            int cc=lum>0.72f?Color.rgb(18,38,78):(lum<0.30f?Color.WHITE:Color.YELLOW);
                            cursor.setTextColor(cc); bm.recycle();
                        }
                        hb.close();
                    }catch(Exception ignored){} finally{screenshotBusy=false;}
                }
                @Override public void onFailure(int errorCode){screenshotBusy=false;}
            });
        }catch(Exception e){screenshotBusy=false;}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){instance=null;try{if(cursor!=null&&wm!=null)wm.removeView(cursor);}catch(Exception ignored){}cursor=null;super.onDestroy();}
}
