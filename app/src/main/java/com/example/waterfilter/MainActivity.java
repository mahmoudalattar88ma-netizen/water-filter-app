package com.example.waterfilter;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.app.AlarmManager;
import android.app.PendingIntent;

public class MainActivity extends Activity {
  WebView web;
  public static final String PREFS="filterprefs", DATA="stagedata";
  @Override public void onCreate(Bundle b){super.onCreate(b); createChannel(); if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
    web=new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true); web.setWebChromeClient(new WebChromeClient()); web.setWebViewClient(new WebViewClient()); web.addJavascriptInterface(new Bridge(this),"AndroidBridge"); setContentView(web); web.loadUrl("file:///android_asset/index.html");
  }
  void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("filter_reminders","تذكيرات فلتر المياه",NotificationManager.IMPORTANCE_HIGH); c.setDescription("مواعيد تغيير شمعات فلتر المياه"); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
  public static void schedule(Context ctx,String json){try{JSONArray a=new JSONArray(json); ctx.getSharedPreferences(PREFS,0).edit().putString(DATA,json).apply(); AlarmManager am=(AlarmManager)ctx.getSystemService(ALARM_SERVICE); for(int i=0;i<7;i++){cancel(ctx, i*10+1);cancel(ctx,i*10+2); JSONObject s=a.optJSONObject(i); if(s==null)continue; String ds=s.optString("date",""); if(ds.isEmpty())continue; SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US); f.setLenient(false); Date installed=f.parse(ds); Calendar due=Calendar.getInstance(); due.setTime(installed); int amount=s.optInt("duration",180); String unit=s.optString("unit","months"); if("days".equals(unit))due.add(Calendar.DAY_OF_YEAR,amount); else if("years".equals(unit))due.add(Calendar.YEAR,amount); else due.add(Calendar.MONTH,amount); due.set(Calendar.HOUR_OF_DAY,9);due.set(Calendar.MINUTE,0);due.set(Calendar.SECOND,0);due.set(Calendar.MILLISECOND,0); String label=s.optString("label","المرحلة "+(i+1)); String name=s.optString("name","الشمعة"); Calendar before=(Calendar)due.clone();before.add(Calendar.DAY_OF_YEAR,-2); if(before.getTimeInMillis()>System.currentTimeMillis())set(ctx,am,before.getTimeInMillis(),i*10+1,label,name,"فاضل يومين على تغيير الشمعة",due.getTimeInMillis()); if(due.getTimeInMillis()>System.currentTimeMillis())set(ctx,am,due.getTimeInMillis(),i*10+2,label,name,"ميعاد تغيير الشمعة النهارده",due.getTimeInMillis()); }}catch(Exception e){android.util.Log.e("WaterFilter","schedule",e);}}
  static void set(Context c,AlarmManager am,long at,int id,String label,String name,String message,long due){Intent in=new Intent(c,ReminderReceiver.class);in.putExtra("id",id);in.putExtra("title",label+" — "+name);in.putExtra("message",message);in.putExtra("due",due);PendingIntent pi=PendingIntent.getBroadcast(c,id,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);else am.set(AlarmManager.RTC_WAKEUP,at,pi);}
  static void cancel(Context c,int id){Intent in=new Intent(c,ReminderReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(c,id,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);((AlarmManager)c.getSystemService(ALARM_SERVICE)).cancel(pi);pi.cancel();}
  public class Bridge {Context c; Bridge(Context x){c=x;} @JavascriptInterface public void updateSchedule(String json){schedule(c,json);} }
}
