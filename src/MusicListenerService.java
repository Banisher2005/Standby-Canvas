import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class MusicListenerService extends NotificationListenerService {
    public static String currentTrack = "Waiting...";
    public static String currentArtist = "Play some music";

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
                
                // Notify MainActivity to redraw if it's active
                Intent intent = new Intent("com.codex.standbycanvas.MUSIC_UPDATE");
                sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e("MusicListenerService", "Error parsing notification", e);
        }
    }
}
