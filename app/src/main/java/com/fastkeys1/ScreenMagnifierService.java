package com.fastkeys1;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.nio.ByteBuffer;

public class ScreenMagnifierService extends Service {
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private WindowManager wm;
    private LensView lens;
    private WindowManager.LayoutParams lp;
    private Bitmap latest;
    private int captureW, captureH;
    private float zoom=2.0f;
    private int screenW, screenH, densityDpi;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent == null ? 0 : intent.getIntExtra("resultCode", 0);
        Intent data = intent == null ? null : intent.getParcelableExtra("data");
        if (data == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            MediaProjectionManager mpm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mpm.getMediaProjection(resultCode,data);
            if(projection==null){stopSelf();return START_NOT_STICKY;}
            if (Build.VERSION.SDK_INT >= 29) startForeground(72, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION); else startForeground(72, notification());
            startCapture();
        } catch(Exception e){ stopSelf(); }
        return START_NOT_STICKY;
    }

    private void startCapture(){
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW=dm.widthPixels; screenH=dm.heightPixels; densityDpi=dm.densityDpi;
        int rw=Math.max(360, screenW/2), rh=Math.max(640, screenH/2);
        captureW=rw; captureH=rh; reader=ImageReader.newInstance(rw,rh,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(r->{
            Image image=null;
            try{
                image=r.acquireLatestImage(); if(image==null)return;
                Image.Plane plane=image.getPlanes()[0]; ByteBuffer buf=plane.getBuffer();
                int pw=image.getWidth(), ph=image.getHeight();
                Bitmap b=Bitmap.createBitmap(pw,ph,Bitmap.Config.ARGB_8888);
                b.copyPixelsFromBuffer(buf); latest=b;
                if(lens!=null) lens.invalidate();
            }catch(Exception ignored){} finally{if(image!=null)image.close();}
        },handler);
        display=projection.createVirtualDisplay("FastKeysMagnifier",rw,rh,densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
        showLens();
    }

    private void showLens(){
        lens=new LensView(this);
        lp=new WindowManager.LayoutParams(dp(250),dp(250),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=Math.max(0,(screenW-dp(250))/2); lp.y=Math.max(0,(screenH-dp(250))/3);
        try{wm.addView(lens,lp);}catch(Exception e){stopSelf();}
    }

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    private class LensView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private float downX,downY; private int oldX,oldY;
        LensView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f, cy=getHeight()/2f;
            if(latest!=null){
                float sx=(float)latest.getWidth()/Math.max(1,screenW), sy=(float)latest.getHeight()/Math.max(1,screenH);
                float screenX=(lp==null?screenW/2f:lp.x+cx)*sx;
                float screenY=(lp==null?screenH/2f:lp.y+cy)*sy;
                float srcW=latest.getWidth()/Math.max(1f,zoom); float srcH=latest.getHeight()/Math.max(1f,zoom);
                RectF src=new RectF(screenX-srcW/2,screenY-srcH/2,screenX+srcW/2,screenY+srcH/2);
                src.left=Math.max(0,src.left); src.top=Math.max(0,src.top); src.right=Math.min(latest.getWidth(),src.right); src.bottom=Math.min(latest.getHeight(),src.bottom);
                Rect srcRect=new Rect(Math.round(src.left),Math.round(src.top),Math.round(src.right),Math.round(src.bottom));
                c.drawBitmap(latest,srcRect,new RectF(0,0,getWidth(),getHeight()),paint);
            }else{paint.setColor(0xDDFFFFFF);c.drawCircle(cx,cy,cx-4,paint);}
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(4)); paint.setColor(0xFF35CD37); c.drawCircle(cx,cy,Math.min(cx,cy)-3,paint); paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCCFFFFFF); c.drawRoundRect(8,8,68,42,10,10,paint); c.drawRoundRect(getWidth()-68,8,getWidth()-8,42,10,10,paint); c.drawRoundRect(getWidth()/2f-35,8,getWidth()/2f+35,42,10,10,paint); c.drawRoundRect(getWidth()/2f-78,8,getWidth()/2f-40,42,10,10,paint);
            paint.setColor(Color.DKGRAY); paint.setTextSize(dp(16)); paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("×",38,31,paint); c.drawText("−",getWidth()/2f-59,31,paint); c.drawText("×2",getWidth()/2f,31,paint); c.drawText("+",getWidth()-38,31,paint);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            switch(e.getAction()){
                case MotionEvent.ACTION_DOWN: downX=e.getRawX();downY=e.getRawY();oldX=lp.x;oldY=lp.y;return true;
                case MotionEvent.ACTION_MOVE: lp.x=oldX+(int)(e.getRawX()-downX);lp.y=oldY+(int)(e.getRawY()-downY);lp.x=Math.max(0,Math.min(screenW-getWidth(),lp.x));lp.y=Math.max(0,Math.min(screenH-getHeight(),lp.y));wm.updateViewLayout(this,lp);invalidate();return true;
                case MotionEvent.ACTION_UP:
                    if(Math.hypot(e.getRawX()-downX,e.getRawY()-downY)<18){
                        float x=e.getX(), y=e.getY();
                        if(y<55 && x<80){ stopSelf(); return true; }
                        if(y<55 && x>getWidth()/2f-80 && x<getWidth()/2f-40){ zoom=Math.max(1f,zoom-0.5f); invalidate(); return true; }
                        if(y<55 && x>getWidth()-80){ zoom=Math.min(6f,zoom+0.5f); invalidate(); return true; }
                        if(y<55 && x>getWidth()/2f-45 && x<getWidth()/2f+45){ zoom=2f; invalidate(); return true; }
                    }
                    return true;
            }
            return true;
        }
    }

    private Notification notification(){
        Intent i=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,9,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"fastkeys_magnifier").setContentTitle("Fast Keys").setContentText("ذره‌بین صفحه فعال است").setSmallIcon(android.R.drawable.ic_menu_search).setContentIntent(pi).setOngoing(true).build();
    }
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel("fastkeys_magnifier","Fast Keys Magnifier",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}
    }
    @Override public void onDestroy(){
        try{if(lens!=null&&wm!=null)wm.removeView(lens);}catch(Exception ignored){}
        if(display!=null)display.release(); if(reader!=null)reader.close(); if(projection!=null)projection.stop(); latest=null; super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
