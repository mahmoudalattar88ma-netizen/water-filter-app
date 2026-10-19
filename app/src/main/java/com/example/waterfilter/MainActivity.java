
package com.example.waterfilter;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    WebView web;

    public static final String PREFS = "filterprefs";
    public static final String DATA = "stagedata";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        createChannel();

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44
            );
        }

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(this), "AndroidBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreSchedule(this);
    }

    void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                "filter_reminders",
                "تذكيرات فلتر المياه",
                NotificationManager.IMPORTANCE_HIGH
            );
            c.setDescription("مواعيد تغيير شمعات فلتر المياه");
            NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(c);
        }
    }

    public static void restoreSchedule(Context ctx) {
        String data = ctx.getSharedPreferences(PREFS, 0)
            .getString(DATA, null);
        if (data != null) {
            schedule(ctx, data);
        }
    }

    public static void schedule(Context ctx, String json) {
        try {
            JSONArray a = new JSONArray(json);
            ctx.getSharedPreferences(PREFS, 0)
                .edit().putString(DATA, json).apply();

            AlarmManager am = (AlarmManager)
                ctx.getSystemService(Context.ALARM_SERVICE);

            for (int i = 0; i < 7; i++) {
                cancel(ctx, i * 10 + 1);
                cancel(ctx, i * 10 + 2);

                JSONObject s = a.optJSONObject(i);
                if (s == null) continue;

                String ds = s.optString("date", "");
                if (ds.isEmpty()) continue;

                SimpleDateFormat f =
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                f.setLenient(false);
                Date installed = f.parse(ds);
                if (installed == null) continue;

                Calendar due = Calendar.getInstance();
                due.setTime(installed);

                int amount = s.optInt("duration", 180);
                String unit = s.optString("unit", "months");

                if ("days".equals(unit)) {
                    due.add(Calendar.DAY_OF_YEAR, amount);
                } else if ("years".equals(unit)) {
                    due.add(Calendar.YEAR, amount);
                } else {
                    due.add(Calendar.MONTH, amount);
                }

                due.set(Calendar.HOUR_OF_DAY, 9);
                due.set(Calendar.MINUTE, 0);
                due.set(Calendar.SECOND, 0);
                due.set(Calendar.MILLISECOND, 0);

                long now = System.currentTimeMillis();
                long dueTime = due.getTimeInMillis();

                String label = s.optString("label", "المرحلة " + (i + 1));
                String name = s.optString("name", "الشمعة");

                Calendar before = (Calendar) due.clone();
                before.add(Calendar.DAY_OF_YEAR, -2);
                long beforeTime = before.getTimeInMillis();

                if (beforeTime > now) {
                    set(ctx, am, beforeTime, i * 10 + 1,
                        label, name, "فاضل يومين على تغيير الشمعة");
                }

                if (dueTime > now) {
                    set(ctx, am, dueTime, i * 10 + 2,
                        label, name, "ميعاد تغيير الشمعة النهارده");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("WaterFilter", "schedule", e);
        }
    }

    static void set(Context c, AlarmManager am, long at, int id,
                    String label, String name, String message) {
        Intent in = new Intent(c, ReminderReceiver.class);
        in.putExtra("id", id);
        in.putExtra("title", label + " — " + name);
        in.putExtra("message", message);

        PendingIntent pi = PendingIntent.getBroadcast(
            c, id, in,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void cancel(Context c, int id) {
        Intent in = new Intent(c, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
            c, id, in,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager)
            c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        pi.cancel();
    }

    public class Bridge {
        Context c;

        Bridge(Context x) {
            c = x;
        }

        @JavascriptInterface
        public void updateSchedule(String json) {
            schedule(c, json);
        }
    }
}
