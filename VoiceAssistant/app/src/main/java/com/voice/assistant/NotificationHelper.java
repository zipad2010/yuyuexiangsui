package com.voice.assistant;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "community_messages";
    private static final int SUMMARY_NOTIFICATION_ID = 3101;

    private NotificationHelper() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "社区与私信通知",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("新的私信和论坛回复");
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static void updateSummary(Context context, int forumUnread, int messageUnread) {
        int totalUnread = forumUnread + messageUnread;
        if (totalUnread <= 0) {
            NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID);
            return;
        }
        String content = buildContent(forumUnread, messageUnread);
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("月下誓约")
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setNumber(totalUnread)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, builder.build());
    }

    private static String buildContent(int forumUnread, int messageUnread) {
        StringBuilder content = new StringBuilder();
        if (messageUnread > 0) {
            content.append(messageUnread).append(" 条未读私信");
        }
        if (forumUnread > 0) {
            if (content.length() > 0) {
                content.append("，");
            }
            content.append(forumUnread).append(" 条新的论坛回复");
        }
        return content.toString();
    }
}