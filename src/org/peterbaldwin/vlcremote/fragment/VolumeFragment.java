
package org.peterbaldwin.vlcremote.fragment;

import org.peterbaldwin.client.android.vlcremote.R;
import org.peterbaldwin.vlcremote.intent.Intents;
import org.peterbaldwin.vlcremote.model.Status;
import org.peterbaldwin.vlcremote.net.MediaServer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

public class VolumeFragment extends Fragment implements SeekBar.OnSeekBarChangeListener {

    private static final int MAX_VOLUME = 256 * 5 / 4; // 125% of 256

    private ImageView mIcon;
    private SeekBar mSeekBar;
    private MediaServer mMediaServer;

    private BroadcastReceiver mStatusReceiver;

    public void setMediaServer(MediaServer mediaServer) {
        mMediaServer = mediaServer;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.volume_fragment, root, false);
        mIcon = (ImageView) view.findViewById(android.R.id.icon);
        mSeekBar = (SeekBar) view.findViewById(android.R.id.progress);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setMax(MAX_VOLUME);
        return view;
    }

    /** {@inheritDoc} */
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            setVolume(progress);
        }
    }

    /** {@inheritDoc} */
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    /** {@inheritDoc} */
    public void onStopTrackingTouch(SeekBar seekBar) {
        setVolume(seekBar.getProgress());
    }

    private void setVolume(int value) {
        mMediaServer.status().command.volume(value);
    }

    void onVolumeChanged(int value) {
        mIcon.setImageResource(getVolumeImage(value));
        mSeekBar.setProgress(value);
    }

    private static int getVolumeImage(int volume) {
        if (volume == 0) {
            return R.drawable.ic_media_volume_muted;
        } else if (volume < (MAX_VOLUME / 3)) {
            return R.drawable.ic_media_volume_low;
        } else if (volume < (2 * MAX_VOLUME / 3)) {
            return R.drawable.ic_media_volume_medium;
        } else {
            return R.drawable.ic_media_volume_high;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mStatusReceiver = new StatusReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intents.ACTION_STATUS);
        getActivity().registerReceiver(mStatusReceiver, filter);
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mStatusReceiver);
        mStatusReceiver = null;
        super.onPause();
    }

    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intents.ACTION_STATUS.equals(action)) {
                Status status = (Status) intent.getSerializableExtra(Intents.EXTRA_STATUS);
                onVolumeChanged(status.getVolume());
            }
        }
    }
}
