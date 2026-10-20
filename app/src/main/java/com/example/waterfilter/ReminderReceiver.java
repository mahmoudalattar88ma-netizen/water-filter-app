
package com.example.waterfilter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {

        // التأكد إن الإشعارات مفعّلة
        boolean enabled = c.getSharedPreferences(
                MainActivity.PREFS, Context.MODE_PRIVATE
        ).getBoolean("notifications_enabled", true);

        if (!enabled) return;

        String title = i.getStringExtra("title");
        String msg = i.getStringExtra("message");
        int id = i.getIntExtra("id", 1);

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent tap = PendingIntent.getActivity(
                c,
                id,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(c, "filter_reminders");
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH);
        }

        b.setSmallIcon(R.drawable.water_filter)
         .setContentTitle(
                 title == null ? "متابع فلتر المياه" : title
         )
         .setContentText(
                 msg == null ? "راجع موعد تغيير الشمعة" : msg
         )
         .setStyle(
                 new Notification.BigTextStyle().bigText(
                         msg == null
                                 ? "راجع موعد تغيير الشمعة"
                                 : msg
                 )
         )
         .setAutoCancel(true)
         .setContentIntent(tap);

        try {
            b.setLargeIcon(
                    BitmapFactory.decodeResource(
                            c.getResources(),
                            R.drawable.water_filter
                    )
            );
        } catch (Exception ignored) {
        }

        NotificationManager nm =
                (NotificationManager) c.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (nm != null) {
            nm.notify(id, b.build());
        }
    }
}
