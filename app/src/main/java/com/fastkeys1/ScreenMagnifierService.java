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
    private int screenW, screenH, densityDpi;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(72, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent == null ? 0 : intent.getIntExtra("resultCode", 0);
        Intent data = intent == null ? null : intent.getParcelableExtra("data");
        if (data == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            MediaProjectionManager mpm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mpm.getMediaProjection(resultCode,data);
            if(projection==null){stopSelf();return START_NOT_STICKY;}
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
        reader=ImageReader.newInstance(rw,rh,PixelFormat.RGBA_8888,2);
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                float sx=(float)latest.getWidth()/screenW, sy=(float)latest.getHeight()/screenH;
                float screenX=(lp==null?screenW/2f:lp.x+cx)/Math.max(.01f,sx);
                float screenY=(lp==null?screenH/2f:lp.y+cy)/Math.max(.01f,sy);
                float srcW=latest.getWidth()*.22f, srcH=latest.getHeight()*.22f;
                RectF src=new RectF(screenX-srcW/2,screenY-srcH/2,screenX+srcW/2,screenY+srcH/2);
                src.left=Math.max(0,src.left); src.top=Math.max(0,src.top); src.right=Math.min(latest.getWidth(),src.right); src.bottom=Math.min(latest.getHeight(),src.bottom);
                c.drawBitmap(latest,src,new RectF(0,0,getWidth(),getHeight()),paint);
            }else{paint.setColor(0xDDFFFFFF);c.drawCircle(cx,cy,cx-4,paint);}
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(4)); paint.setColor(0xFF35CD37); c.drawCircle(cx,cy,Math.min(cx,cy)-3,paint); paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            switch(e.getAction()){
                case MotionEvent.ACTION_DOWN: downX=e.getRawX();downY=e.getRawY();oldX=lp.x;oldY=lp.y;return true;
                case MotionEvent.ACTION_MOVE: lp.x=oldX+(int)(e.getRawX()-downX);lp.y=oldY+(int)(e.getRawY()-downY);lp.x=Math.max(0,Math.min(screenW-getWidth(),lp.x));lp.y=Math.max(0,Math.min(screenH-getHeight(),lp.y));wm.updateViewLayout(this,lp);invalidate();return true;
                case MotionEvent.ACTION_UP: if(Math.hypot(e.getRawX()-downX,e.getRawY()-downY)<18) stopSelf(); return true;
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
