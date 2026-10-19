package com.example.waterfilter;
import android.content.*;
public class BootReceiver extends BroadcastReceiver { @Override public void onReceive(Context c,Intent i){String data=c.getSharedPreferences(MainActivity.PREFS,0).getString(MainActivity.DATA,null);if(data!=null)MainActivity.schedule(c,data);} }
