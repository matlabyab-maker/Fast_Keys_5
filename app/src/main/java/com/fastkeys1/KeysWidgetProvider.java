package com.fastkeys1;
import android.app.PendingIntent;import android.appwidget.AppWidgetManager;import android.appwidget.AppWidgetProvider;import android.content.*;import android.provider.Settings;import android.widget.RemoteViews;
public class KeysWidgetProvider extends AppWidgetProvider{
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_keyboard);Intent k=new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);PendingIntent pk=PendingIntent.getActivity(c,1,k,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);v.setOnClickPendingIntent(R.id.widget_keyboard,pk);Intent s=new Intent(c,SteeringOverlayService.class);PendingIntent ps=PendingIntent.getService(c,2,s,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);v.setOnClickPendingIntent(R.id.widget_steering,ps);m.updateAppWidget(id,v);}}
}
