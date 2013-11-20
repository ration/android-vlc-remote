/*-
 *  Copyright (C) 2011 Peter Baldwin
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.peterbaldwin.vlcremote.fragment;

import org.peterbaldwin.client.android.vlcremote.R;
import org.peterbaldwin.vlcremote.net.MediaServer;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class ServicesDiscoveryFragment extends ListFragment {
    private MediaServer mMediaServer;

    private String[] mServiceValues;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mServiceValues = getResources().getStringArray(R.array.service_values);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.services_discovery_fragment, root, false);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String value = mServiceValues[position];
        mMediaServer.status().command.playback.sd(value);
    }

    public void setMediaServer(MediaServer server) {
        mMediaServer = server;
    }
}
