/*-
 *  Copyright (C) 2009 Peter Baldwin
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

package org.peterbaldwin.vlcremote.app;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.peterbaldwin.client.android.vlcremote.R;
import org.peterbaldwin.vlcremote.preference.ProgressCategory;
import org.peterbaldwin.vlcremote.receiver.PhoneStateChangedReceiver;
import org.peterbaldwin.vlcremote.sweep.PortSweeper;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListAdapter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;

@SuppressWarnings("deprecation")
public final class PickServerActivity extends PreferenceActivity implements PortSweeper.Callback,
        DialogInterface.OnClickListener, OnPreferenceChangeListener {

    private static final String TAG = "PickServer";

    private static final String PACKAGE_NAME = R.class.getPackage().getName();

    private static final ComponentName PHONE_STATE_RECEIVER = new ComponentName(PACKAGE_NAME,
            PhoneStateChangedReceiver.class.getName());

    public static final String EXTRA_PORT = "org.peterbaldwin.portsweep.intent.extra.PORT";
    public static final String EXTRA_FILE = "org.peterbaldwin.portsweep.intent.extra.FILE";
    public static final String EXTRA_WORKERS = "org.peterbaldwin.portsweep.intent.extra.WORKERS";
    public static final String EXTRA_REMEMBERED = "org.peterbaldwin.portsweep.intent.extra.REMEMBERED";

    private static final String KEY_WIFI = "wifi";
    private static final String KEY_SERVERS = "servers";
    private static final String KEY_ADD_SERVER = "add_server";
    private static final String KEY_PAUSE_FOR_CALL = "pause_for_call";

    public static final int DEFAULT_WORKERS = 16;

    private static final int DIALOG_ADD_SERVER = 1;

    private static final int MENU_SCAN = Menu.FIRST;

    private static final int CONTEXT_FORGET = Menu.FIRST;

    private static byte[] toByteArray(int i) {
        int i4 = (i >> 24) & 0xFF;
        int i3 = (i >> 16) & 0xFF;
        int i2 = (i >> 8) & 0xFF;
        int i1 = i & 0xFF;
        return new byte[] {
                (byte) i1, (byte) i2, (byte) i3, (byte) i4
        };
    }

    private PortSweeper mPortSweeper;

    private BroadcastReceiver mReceiver;

    private AlertDialog mDialogAddServer;
    private EditText mEditHostname;
    private EditText mEditPort;

    private String mFile;
    private int mPort;
    private int mWorkers;
    private long mCreateTime;
    private ArrayList<String> mRemembered;

    private CheckBoxPreference mPreferenceWiFi;
    private CheckBoxPreference mPreferencePauseForCall;
    private ProgressCategory mProgressCategory;
    private Preference mPreferenceAddServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.server_settings);

        PreferenceScreen preferenceScreen = getPreferenceScreen();

        mPreferenceWiFi = (CheckBoxPreference) preferenceScreen.findPreference(KEY_WIFI);
        mPreferencePauseForCall = (CheckBoxPreference) preferenceScreen.findPreference(KEY_PAUSE_FOR_CALL);
        mProgressCategory = (ProgressCategory) preferenceScreen.findPreference(KEY_SERVERS);
        mPreferenceAddServer = preferenceScreen.findPreference(KEY_ADD_SERVER);

        mPreferencePauseForCall.setOnPreferenceChangeListener(this);
        mPreferencePauseForCall.setChecked(getPauseForCall());

        Intent intent = getIntent();
        mPort = intent.getIntExtra(EXTRA_PORT, 0);
        if (mPort == 0) {
            throw new IllegalArgumentException("Port must be specified");
        }

        mFile = intent.getStringExtra(EXTRA_FILE);
        if (mFile == null) {
            throw new IllegalArgumentException("File must be specified");
        }

        mRemembered = intent.getStringArrayListExtra(EXTRA_REMEMBERED);
        if (mRemembered == null) {
            mRemembered = new ArrayList<String>();
        }

        registerForContextMenu(getListView());

        mWorkers = intent.getIntExtra(EXTRA_WORKERS, DEFAULT_WORKERS);

        mPortSweeper = (PortSweeper) getLastNonConfigurationInstance();
        if (mPortSweeper == null) {
            mPortSweeper = createPortSweeper();
            startSweep();
        }
        mPortSweeper.setCallback(this);

        // Registering the receiver triggers a broadcast with the initial state.
        // To tell the difference between a broadcast triggered by registering a
        // receiver and a broadcast triggered by a true network event, note the
        // time and ignore all broadcasts for one second.
        mCreateTime = SystemClock.uptimeMillis();

        mReceiver = new MyBroadcastReceiver();

        // For robustness, update the connection status for all types of events.
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
        updateWifiInfo();
    }

    boolean isInitialBroadcast() {
        return (SystemClock.uptimeMillis() - mCreateTime) < 1000;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        Object nonConfigurationInstance = mPortSweeper;
        // TODO: Set callback to null without triggering NullPointerException in
        // the event that a callback happens while the Activity is recreating
        // itself.
        mPortSweeper = null;
        return nonConfigurationInstance;
    }


    private Preference createServerPreference(String server) {
        // Don't show port number in title if it's the default
        String title = server.endsWith(":8080") ?
                server.substring(0, server.lastIndexOf(':')) :
                server;
        Preference preference = new Preference(this);
        preference.setKey(server);
        preference.setTitle(title);
        preference.setPersistent(false);
        return preference;
    }

    private Preference createServerPreference(HttpResponse response) {
        String server = response.getFirstHeader(HTTP.TARGET_HOST).getValue();
        Preference preference = createServerPreference(server);
        switch (response.getStatusLine().getStatusCode()) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                preference.setSummary(getText(R.string.summary_password_protected));
                break;
            case HttpURLConnection.HTTP_FORBIDDEN:
                preference.setSummary(getText(R.string.summary_forbidden));
                preference.setEnabled(false);
                break;
            case HttpURLConnection.HTTP_OK:
                try {
                    if (EntityUtils.toString(response.getEntity()).contains("--http-password")) {
                        preference.setSummary(getText(R.string.summary_password_not_set));
                        preference.setEnabled(false);
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "Failed to parse response", e);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to parse response", e);
                }
                break;
        }
        return preference;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        mReceiver = null;
        if (mPortSweeper != null) {
            mPortSweeper.destory();
        }
        super.onDestroy();
    }

    private PortSweeper createPortSweeper() {
        PortSweeper.Callback callback = this;
        Looper looper = Looper.myLooper();
        return new PortSweeper(mPort, mFile, mWorkers, callback, looper);
    }

    private WifiInfo getConnectionInfo() {
        Object service = getSystemService(WIFI_SERVICE);
        WifiManager manager = (WifiManager) service;
        WifiInfo info = manager.getConnectionInfo();
        if (info != null) {
            SupplicantState state = info.getSupplicantState();
            if (state.equals(SupplicantState.COMPLETED)) {
                return info;
            }
        }
        return null;
    }

    private byte[] getIpAddress() {
        WifiInfo info = getConnectionInfo();
        if (info != null) {
            return toByteArray(info.getIpAddress());
        }
        return null;
    }

    void startSweep() {
        byte[] ipAddress = getIpAddress();
        if (ipAddress != null) {
            mPortSweeper.sweep(ipAddress);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /** {@inheritDoc} */
    public void onHostFound(HttpResponse response) {
        String server = response.getFirstHeader(HTTP.TARGET_HOST).getValue();
        int responseCode = response.getStatusLine().getStatusCode();
        switch (responseCode) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_FORBIDDEN:
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                if (!mRemembered.contains(server)) {
                    Preference preference = createServerPreference(response);
                    mProgressCategory.addPreference(preference);
                }
                break;
            default:
                Log.d(TAG, "Unexpected response code: " + responseCode);
                break;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ADD_SERVER:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.add_server);
                LayoutInflater inflater = getLayoutInflater();
                View view = inflater.inflate(R.layout.add_server, null);
                mEditHostname = (EditText) view.findViewById(R.id.edit_hostname);
                mEditPort = (EditText) view.findViewById(R.id.edit_port);
                builder.setView(view);
                builder.setPositiveButton(R.string.ok, this);
                builder.setNegativeButton(R.string.cancel, this);
                mDialogAddServer = builder.create();
                return mDialogAddServer;
            default:
                return super.onCreateDialog(id);
        }
    }

    /** {@inheritDoc} */
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mDialogAddServer) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    String hostname = getHostname();
                    int port = getPort();
                    String server = hostname + ":" + port;
                    pick(server);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    dialog.dismiss();
                    break;
            }
        }
    }

    private void pick(String server) {
        Intent data = new Intent();
        Uri uri = Uri.parse("http://" + server);
        data.setData(uri);
        if (!mRemembered.contains(server)) {
            mRemembered.add(server);
        }
        data.putStringArrayListExtra(EXTRA_REMEMBERED, mRemembered);
        setResult(RESULT_OK, data);
        finish();
    }

    private void forget(String server) {
        mRemembered.remove(server);
        int count = mProgressCategory.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference preference = mProgressCategory.getPreference(i);
            if (server.equals(preference.getTitle().toString())) {
                mProgressCategory.removePreference(preference);
                break;
            }
        }

        // Send the updated list of remembered servers even if the activity is
        // canceled
        Intent data = new Intent();
        data.putStringArrayListExtra(EXTRA_REMEMBERED, mRemembered);
        setResult(RESULT_CANCELED, data);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mPreferenceAddServer) {
            showDialog(DIALOG_ADD_SERVER);
            return true;
        } else if (preference == mPreferenceWiFi) {
            Intent intent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
            startActivity(intent);

            // Undo checkbox toggle
            updateWifiInfo();
            return true;
        } else if (preference == mPreferencePauseForCall) {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        } else {
            String server = preference.getKey();
            pick(server);
            return true;
        }
    }

    /** {@inheritDoc} */
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mPreferencePauseForCall) {
            setPauseForCall(Boolean.TRUE.equals(newValue));
            return true;
        } else {
            return false;
        }
    }

    private Preference getPreferenceFromMenuInfo(ContextMenuInfo menuInfo) {
        if (menuInfo != null) {
            if (menuInfo instanceof AdapterContextMenuInfo) {
                AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
                PreferenceScreen screen = getPreferenceScreen();
                ListAdapter root = screen.getRootAdapter();
                Object item = root.getItem(adapterMenuInfo.position);
                return (Preference) item;
            }
        }
        return null;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        Preference preference = getPreferenceFromMenuInfo(menuInfo);
        if (preference != null) {
            String server = preference.getTitle().toString();
            if (mRemembered.contains(server)) {
                menu.add(Menu.NONE, CONTEXT_FORGET, Menu.NONE, R.string.context_forget);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CONTEXT_FORGET:
                ContextMenuInfo menuInfo = item.getMenuInfo();
                Preference preference = getPreferenceFromMenuInfo(menuInfo);
                if (preference != null) {
                    String server = preference.getTitle().toString();
                    forget(server);
                }
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /** {@inheritDoc} */
    public void onProgress(int progress, int max) {
        if (progress == 0) {
            mProgressCategory.removeAll();
            for (String server : mRemembered) {
                Preference preference = createServerPreference(server);
                preference.setSummary(R.string.summary_remembered);
                mProgressCategory.addPreference(preference);
            }
        }
        mProgressCategory.setProgress(progress != max);
    }

    private String getHostname() {
        return mEditHostname.getText().toString();
    }

    private int getPort() {
        String value = String.valueOf(mEditPort.getText());
        if (!TextUtils.isEmpty(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid port number: " + value);
            }
        }
        return mPort;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem scan = menu.add(0, MENU_SCAN, 0, R.string.scan);
        scan.setIcon(R.drawable.ic_menu_scan_network);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SCAN:
                startSweep();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateWifiInfo() {
        WifiInfo info = getConnectionInfo();
        if (info != null) {
            mPreferenceWiFi.setChecked(true);
            String ssid = info.getSSID();
            String template = getString(R.string.summary_wifi_connected);
            Object[] objects = {
                ssid != null ? ssid : ""
            };
            CharSequence summary = MessageFormat.format(template, objects);
            mPreferenceWiFi.setSummary(summary);
        } else {
            mPreferenceWiFi.setChecked(false);
            mPreferenceWiFi.setSummary(R.string.summary_wifi_disconnected);
        }
    }

    private boolean getPauseForCall() {
        switch (getPackageManager().getComponentEnabledSetting(PHONE_STATE_RECEIVER)) {
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            default:
                return false;
        }
    }

    private void setPauseForCall(boolean enabled) {
        getPackageManager().setComponentEnabledSetting(
                PHONE_STATE_RECEIVER,
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Bundle extras = intent.getExtras();
            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo networkInfo = extras.getParcelable(WifiManager.EXTRA_NETWORK_INFO);
                NetworkInfo.State state = networkInfo.getState();
                if (state == NetworkInfo.State.CONNECTED) {
                    if (isInitialBroadcast()) {
                        // Don't perform a sweep if the broadcast was triggered
                        // as a result of a receiver being registered.
                    } else {
                        startSweep();
                    }
                }
            }
            updateWifiInfo();
        }
    }
}
