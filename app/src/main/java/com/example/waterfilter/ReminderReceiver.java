
package com.example.waterfilter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        // التأكد إن الإشعارات مفعلة من داخل التطبيق.
        if (!MainActivity.notificationsEnabled(context)) return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager == null) return;

        // التأكد إن إشعارات التطبيق مسموح بها من إعدادات أندرويد.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && !manager.areNotificationsEnabled()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        int id = intent.getIntExtra("id", 1);

        if (title == null || title.trim().isEmpty()) {
            title = "متابع فلتر المياه";
        }

        if (message == null || message.trim().isEmpty()) {
            message = "راجع موعد تغيير الشمعة";
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent tap = PendingIntent.getActivity(
                context,
                id,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(
                    context,
                    "filter_reminders"
            );
        } else {
            builder = new Notification.Builder(context);
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        builder.setSmallIcon(R.drawable.water_filter)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(
                        new Notification.BigTextStyle().bigText(message)
                )
                .setAutoCancel(true)
                .setContentIntent(tap);

        try {
            builder.setLargeIcon(
                    BitmapFactory.decodeResource(
                            context.getResources(),
                            R.drawable.water_filter
                    )
            );
        } catch (Exception ignored) {
            // الأيقونة الكبيرة اختيارية.
        }

        manager.notify(id, builder.build());
    }
}
