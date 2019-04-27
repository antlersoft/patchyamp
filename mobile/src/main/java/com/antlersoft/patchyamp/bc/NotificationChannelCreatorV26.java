package com.antlersoft.patchyamp.bc;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

@TargetApi(26)
class NotificationChannelCreatorV26 implements IBcNotificationChannelCreator {
    @Override
    public void createChannel(Context context, String id, CharSequence name, String description, int importance, boolean showOnLock, boolean showBadge) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel mChannel = new NotificationChannel(id, name, importance);
        mChannel.setDescription(description);
        mChannel.enableLights(false);
        //mChannel.setLightColor(Color.RED);
        //mChannel.enableVibration(false);
        //mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
        mChannel.setShowBadge(showBadge);
        //mChannel.setLockscreenVisibility(NotificationChannel);
        notificationManager.createNotificationChannel(mChannel);
    }
}
