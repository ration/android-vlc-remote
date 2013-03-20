package org.peterbaldwin.vlcremote.appwidget;

import org.peterbaldwin.client.android.vlcremote.R;
import org.peterbaldwin.vlcremote.intent.Intents;
import org.peterbaldwin.vlcremote.model.Preferences;
import org.peterbaldwin.vlcremote.model.Status;
import org.peterbaldwin.vlcremote.model.Track;
import org.peterbaldwin.vlcremote.net.MediaServer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;


/**
 * Give basic seek controls through the widget 
 */
public class SeekControlWidgetProvider extends AppWidgetProvider {
    static final String LOG_TAG = "VlcSeekControlAppWidgetProvider";
    

    @Override
    public void onReceive(Context context, Intent intent) {       
        String action = intent.getAction();
        if (Intents.ACTION_STATUS.equals(action)) {
            Status status = (Status) intent.getSerializableExtra(Intents.EXTRA_STATUS);
            String noMedia = context.getString(R.string.no_media);

            String text1;
            String text2;
            if (status.isStopped()) {
                text1 = noMedia;
                text2 = "";
            } else {
                Track track = status.getTrack();
                text1 = track.getTitle();
                text2 = track.getArtist();
                if (TextUtils.isEmpty(text1) && TextUtils.isEmpty(text2)) {
                    text1 = track.getName();
                }
            }
            int[] appWidgetIds = null;
            performUpdate(context, text1, text2, status.isPlaying(), appWidgetIds);

            long time = status.getTime();
            long length = status.getLength();
            if (status.isPlaying() && time >= 0L && length > 0L && time <= length) {
                // Schedule an update shortly after the current track is
                // expected to end.
                long delay = length - time + 1000;
                scheduleUpdate(context, delay);
            }
        } else if (Intents.ACTION_ERROR.equals(action)) {
            CharSequence text1 = context.getText(R.string.connection_error);
            Throwable t = (Throwable) intent.getSerializableExtra(Intents.EXTRA_THROWABLE);
            String text2 = t.getMessage();
            if (text2 == null) {
                text2 = t.getClass().getName();
            }
            Boolean playing = null;
            int[] appWidgetIds = null;
            performUpdate(context, text1, text2, playing, appWidgetIds);
            cancelPendingUpdate(context);
        } else if (Intents.ACTION_MANUAL_APPWIDGET_UPDATE.equals(action)
                || ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            int[] appWidgetIds = null;
            update(context, appWidgetIds);
        } else {
            super.onReceive(context, intent);
        }
    }

    private static PendingIntent createManualAppWidgetUpdateIntent(Context context) {
        int requestCode = 0;
        Intent intent = new Intent(Intents.ACTION_MANUAL_APPWIDGET_UPDATE);
        int flags = 0;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private void scheduleUpdate(Context context, long delay) {
        Object service = context.getSystemService(Context.ALARM_SERVICE);
        AlarmManager alarmManager = (AlarmManager) service;
        int type = AlarmManager.ELAPSED_REALTIME_WAKEUP;
        long triggerAtTime = SystemClock.elapsedRealtime() + delay;
        PendingIntent operation = createManualAppWidgetUpdateIntent(context);
        alarmManager.set(type, triggerAtTime, operation);
    }

    private void cancelPendingUpdate(Context context) {
        Object service = context.getSystemService(Context.ALARM_SERVICE);
        AlarmManager alarmManager = (AlarmManager) service;
        PendingIntent operation = createManualAppWidgetUpdateIntent(context);
        alarmManager.cancel(operation);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetIds);
    }

    private void update(Context context, int[] appWidgetIds) {
        Preferences preferences = Preferences.get(context);
        String authority = preferences.getAuthority();
        if (authority != null) {
            MediaServer server = new MediaServer(context, authority);
            server.status().get();
        } else {
            CharSequence text1 = context.getText(R.string.noserver);
            CharSequence text2 = "";
            Boolean playing = null;
            performUpdate(context, text1, text2, playing, appWidgetIds);
        }
    }

    private void pushUpdate(Context context, int[] appWidgetIds, RemoteViews views) {
        // Update specific list of appWidgetIds if given,
        // otherwise default to all
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            manager.updateAppWidget(appWidgetIds, views);
        } else {
            Class<? extends AppWidgetProvider> cls = getClass();
            ComponentName provider = new ComponentName(context, cls);
            manager.updateAppWidget(provider, views);
        }
    }

    /**
     * Update all active widget instances by pushing changes
     */
    void performUpdate(Context context, CharSequence title, CharSequence artist, Boolean playing,
            int[] appWidgetIds) {
        String packageName = context.getPackageName();
        RemoteViews views = new RemoteViews(packageName, R.layout.seekcontrol_appwidget);
        
        if (playing != null) {
            views.setImageViewResource(R.id.control_play,
                    playing ? R.drawable.ic_appwidget_music_pause
                            : R.drawable.ic_appwidget_music_play);
        } else {
            views.setImageViewResource(R.id.control_play, R.drawable.ic_popup_sync_2);
        }
        
        views.setViewVisibility(R.id.control_next, playing != null ? View.VISIBLE : View.GONE);
        
        // Link actions buttons to intents
        linkButtons(context, views, playing);
        pushUpdate(context, appWidgetIds, views);
    }

    /**
     * Link up various button actions using {@link PendingIntent}.
     */
    private void linkButtons(Context context, RemoteViews views, Boolean playing) {
        {
            int requestCode = 0;
            Intent intent = getLaunchIntent(context);
            int flags = 0;
            PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent,
                    flags);
            views.setOnClickPendingIntent(R.id.seekcontrol_clickicon, pendingIntent);
        }
        
        Preferences preferences = Preferences.get(context);
        String authority = preferences.getAuthority();
        if (authority == null) {
            return;
        }
        MediaServer server = new MediaServer(context, authority);

        if (playing != null) {
            PendingIntent intent = server.status().command.playback.pendingPause();
            views.setOnClickPendingIntent(R.id.control_play, intent);
        } else {
            PendingIntent intent = server.status().pendingGet();
            views.setOnClickPendingIntent(R.id.control_play, intent);
        }

        {
            PendingIntent intent = server.status().command.playback.pendingNext();
            views.setOnClickPendingIntent(R.id.control_next, intent);
        }
        
        {
            PendingIntent intent = server.status().command.playback.pendingSeek("+10");
            views.setOnClickPendingIntent(R.id.control_seek_forward, intent);
        }
        
        {
            PendingIntent intent = server.status().command.playback.pendingSeek("-10");
            views.setOnClickPendingIntent(R.id.control_seek_backward, intent);
        }

        
        
    }

    /**
     * Returns the {@link Intent} to launch VLC Remote.
     */
    private static Intent getLaunchIntent(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    }
}
