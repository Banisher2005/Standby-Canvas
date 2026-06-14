package com.codex.standbycanvas;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.media.session.MediaSession;
import android.graphics.Bitmap;

public class MusicListenerService extends NotificationListenerService {
    public static String currentTrack = "Waiting...";
    public static String currentArtist = "Play some music";
    public static MediaSession.Token sessionToken = null;
    public static Bitmap albumArt = null;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                updateTrackInfo(sbn);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        updateTrackInfo(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // We could clear it here, but often nice to keep the last played track visible
    }

    private void updateTrackInfo(StatusBarNotification sbn) {
        try {
            Notification notif = sbn.getNotification();
            if (notif.extras != null && notif.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                CharSequence title = notif.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence text = notif.extras.getCharSequence(Notification.EXTRA_TEXT);
                if (title != null) currentTrack = title.toString();
                if (text != null) currentArtist = text.toString();
                
                sessionToken = notif.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
                Bitmap b = notif.extras.getParcelable(Notification.EXTRA_PICTURE);
                if (b != null) albumArt = b;
                
                // Notify MainActivity to redraw if it's active
                Intent intent = new Intent("com.codex.standbycanvas.MUSIC_UPDATE");
                sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e("MusicListenerService", "Error parsing notification", e);
        }
    }
}
