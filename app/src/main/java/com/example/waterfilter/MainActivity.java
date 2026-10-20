
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView web;

    public static final String PREFS = "filterprefs";
    public static final String DATA = "stagedata";
    public static final String NOTIFICATIONS_ENABLED = "notifications_enabled";

    private static final int CREATE_BACKUP_REQUEST = 501;
    private static final int OPEN_BACKUP_REQUEST = 502;

    private String pendingBackupJson;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        createChannel();

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    44
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreSchedule(this);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "filter_reminders",
                    "تذكيرات فلتر المياه",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("مواعيد تغيير شمعات فلتر المياه");

            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);

            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static boolean notificationsEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(NOTIFICATIONS_ENABLED, true);
    }

    public static void restoreSchedule(Context ctx) {
        if (!notificationsEnabled(ctx)) {
            cancelAllSchedules(ctx);
            return;
        }

        String data = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(DATA, null);

        if (data != null) {
            schedule(ctx, data);
        }
    }

    public static void setNotifications(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(NOTIFICATIONS_ENABLED, enabled)
                .apply();

        if (enabled) {
            String data = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(DATA, null);

            if (data != null) {
                schedule(ctx, data);
            }
        } else {
            cancelAllSchedules(ctx);
        }
    }

    private static void cancelAllSchedules(Context ctx) {
        for (int i = 0; i < 7; i++) {
            cancel(ctx, 700 + i * 10 + 1);
            cancel(ctx, 700 + i * 10 + 2);
        }

        for (int i = 0; i < 5; i++) {
            cancel(ctx, 500 + i * 10 + 1);
            cancel(ctx, 500 + i * 10 + 2);
        }
    }

    public static void schedule(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(DATA, json)
                .apply();

        if (!notificationsEnabled(ctx)) {
            cancelAllSchedules(ctx);
            return;
        }

        try {
            JSONObject root = new JSONObject(json);
            AlarmManager am = (AlarmManager)
                    ctx.getSystemService(Context.ALARM_SERVICE);

            scheduleFilter(ctx, am, root.optJSONArray("7"), 700);
            scheduleFilter(ctx, am, root.optJSONArray("5"), 500);

        } catch (Exception e) {
            try {
                JSONArray old = new JSONArray(json);
                JSONObject wrapper = new JSONObject();
                wrapper.put("7", old);

                String converted = wrapper.toString();

                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(DATA, converted)
                        .apply();

                AlarmManager am = (AlarmManager)
                        ctx.getSystemService(Context.ALARM_SERVICE);

                scheduleFilter(ctx, am, old, 700);
                scheduleFilter(ctx, am, null, 500);

            } catch (Exception ex) {
                android.util.Log.e("WaterFilter", "schedule", ex);
            }
        }
    }

    private static void scheduleFilter(
            Context ctx,
            AlarmManager am,
            JSONArray stages,
            int baseId) {

        int count = baseId == 700 ? 7 : 5;

        for (int i = 0; i < count; i++) {
            int beforeId = baseId + i * 10 + 1;
            int dueId = baseId + i * 10 + 2;

            cancel(ctx, beforeId);
            cancel(ctx, dueId);

            if (stages == null) {
                continue;
            }

            JSONObject s = stages.optJSONObject(i);

            if (s == null) {
                continue;
            }

            String ds = s.optString("date", "");

            if (ds.isEmpty()) {
                continue;
            }

            try {
                SimpleDateFormat f =
                        new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                f.setLenient(false);

                Date installed = f.parse(ds);

                if (installed == null) {
                    continue;
                }

                Calendar due = Calendar.getInstance();
                due.setTime(installed);

                int amount = s.optInt("duration", 3);
                amount = Math.max(1, Math.min(3650, amount));

                String unit = s.optString("unit", "months");

                int originalDay = due.get(Calendar.DAY_OF_MONTH);

                if ("days".equals(unit)) {
                    due.add(Calendar.DAY_OF_YEAR, amount);

                } else if ("years".equals(unit)) {
                    int originalMonth = due.get(Calendar.MONTH);

                    due.set(Calendar.DAY_OF_MONTH, 1);
                    due.add(Calendar.YEAR, amount);
                    due.set(Calendar.MONTH, originalMonth);

                    int maxDay = due.getActualMaximum(Calendar.DAY_OF_MONTH);

                    due.set(
                            Calendar.DAY_OF_MONTH,
                            Math.min(originalDay, maxDay)
                    );

                } else {
                    due.set(Calendar.DAY_OF_MONTH, 1);
                    due.add(Calendar.MONTH, amount);

                    int maxDay = due.getActualMaximum(Calendar.DAY_OF_MONTH);

                    due.set(
                            Calendar.DAY_OF_MONTH,
                            Math.min(originalDay, maxDay)
                    );
                }

                due.set(Calendar.HOUR_OF_DAY, 9);
                due.set(Calendar.MINUTE, 0);
                due.set(Calendar.SECOND, 0);
                due.set(Calendar.MILLISECOND, 0);

                long now = System.currentTimeMillis();
                long dueTime = due.getTimeInMillis();

                String filterName = baseId == 700
                        ? "فلتر 7 مراحل"
                        : "فلتر 5 مراحل";

                String label = s.optString(
                        "label", "المرحلة " + (i + 1)
                );

                String name = s.optString("name", "الشمعة");

                String title = filterName + " — " + label + " — " + name;

                Calendar before = (Calendar) due.clone();
                before.add(Calendar.DAY_OF_YEAR, -2);

                long beforeTime = before.getTimeInMillis();

                if (beforeTime > now) {
                    set(
                            ctx,
                            am,
                            beforeTime,
                            beforeId,
                            title,
                            "فاضل يومين على تغيير الشمعة"
                    );
                }

                if (dueTime > now) {
                    set(
                            ctx,
                            am,
                            dueTime,
                            dueId,
                            title,
                            "ميعاد تغيير الشمعة النهارده"
                    );
                }

            } catch (Exception e) {
                android.util.Log.e(
                        "WaterFilter",
                        "stage " + i,
                        e
                );
            }
        }
    }

    private static void set(
            Context c,
            AlarmManager am,
            long at,
            int id,
            String title,
            String message) {

        if (am == null) {
            return;
        }

        Intent in = new Intent(c, ReminderReceiver.class);
        in.putExtra("id", id);
        in.putExtra("title", title);
        in.putExtra("message", message);

        PendingIntent pi = PendingIntent.getBroadcast(
                c,
                id,
                in,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            at,
                            pi
                    );
                } else {
                    am.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            at,
                            pi
                    );
                }

            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        at,
                        pi
                );

            } else if (Build.VERSION.SDK_INT >= 19) {
                am.setExact(
                        AlarmManager.RTC_WAKEUP,
                        at,
                        pi
                );

            } else {
                am.set(
                        AlarmManager.RTC_WAKEUP,
                        at,
                        pi
                );
            }

        } catch (SecurityException e) {
            android.util.Log.e(
                    "WaterFilter",
                    "Alarm permission",
                    e
            );

            try {
                am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        at,
                        pi
                );
            } catch (Exception ex) {
                android.util.Log.e(
                        "WaterFilter",
                        "Fallback alarm",
                        ex
                );
            }
        }
    }

    private static void cancel(Context c, int id) {
        Intent in = new Intent(c, ReminderReceiver.class);

        PendingIntent pi = PendingIntent.getBroadcast(
                c,
                id,
                in,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager)
                c.getSystemService(Context.ALARM_SERVICE);

        if (am != null) {
            am.cancel(pi);
        }

        pi.cancel();
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        if (requestCode == CREATE_BACKUP_REQUEST) {
            try {
                OutputStream out =
                        getContentResolver().openOutputStream(uri);

                if (out == null) {
                    throw new Exception("تعذر فتح الملف");
                }

                if (pendingBackupJson == null) {
                    throw new Exception("لا توجد بيانات للتصدير");
                }

                out.write(
                        pendingBackupJson.getBytes(StandardCharsets.UTF_8)
                );
                out.close();

                Toast.makeText(
                        this,
                        "تم حفظ النسخة الاحتياطية",
                        Toast.LENGTH_LONG
                ).show();

            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "فشل حفظ النسخة الاحتياطية",
                        Toast.LENGTH_LONG
                ).show();

            } finally {
                pendingBackupJson = null;
            }

        } else if (requestCode == OPEN_BACKUP_REQUEST) {
            try {
                InputStream in =
                        getContentResolver().openInputStream(uri);

                if (in == null) {
                    throw new Exception("تعذر فتح الملف");
                }

                java.io.ByteArrayOutputStream buffer =
                        new java.io.ByteArrayOutputStream();

                byte[] bytes = new byte[4096];
                int n;

                while ((n = in.read(bytes)) != -1) {
                    buffer.write(bytes, 0, n);
                }

                in.close();

                String json = new String(
                        buffer.toByteArray(),
                        StandardCharsets.UTF_8
                );

                if (web != null) {
                    web.evaluateJavascript(
                            "window.onNativeBackupImported(" +
                                    JSONObject.quote(json) + ");",
                            null
                    );
                }

            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "فشل قراءة ملف النسخة الاحتياطية",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    public class Bridge {

        @JavascriptInterface
        public void updateSchedule(String json) {
            schedule(MainActivity.this, json);
        }

        @JavascriptInterface
        public boolean getNotificationsEnabled() {
            return notificationsEnabled(MainActivity.this);
        }

        @JavascriptInterface
        public void setNotificationsEnabled(boolean enabled) {
            setNotifications(MainActivity.this, enabled);

            runOnUiThread(() -> {
                if (web != null) {
                    web.evaluateJavascript(
                            "window.onNativeNotificationStateChanged(" +
                                    enabled + ");",
                            null
                    );
                }

                Toast.makeText(
                        MainActivity.this,
                        enabled
                                ? "تم تشغيل الإشعارات"
                                : "تم إيقاف الإشعارات",
                        Toast.LENGTH_SHORT
                ).show();
            });
        }

        @JavascriptInterface
        public void exportBackup(String json) {
            runOnUiThread(() -> {
                pendingBackupJson = json;

                Intent intent = new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        "نسخة-فلتر-المياه.json"
                );

                startActivityForResult(
                        intent,
                        CREATE_BACKUP_REQUEST
                );
            });
        }

        @JavascriptInterface
        public void importBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        new String[]{
                                "application/json",
                                "text/json"
                        }
                );

                startActivityForResult(
                        intent,
                        OPEN_BACKUP_REQUEST
                );
            });
        }
    }
}
