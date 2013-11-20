/*-
 *  Copyright (C) 2013 Peter Baldwin
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

package org.peterbaldwin.vlcremote.net;

import android.content.Context;
import android.content.SharedPreferences;

public final class PasswordManager {

    private final Context mContext;

    public static PasswordManager get(Context context) {
        return new PasswordManager(context);
    }

    private PasswordManager(Context context) {
        mContext = context;
    }

    private SharedPreferences passwords() {
        return mContext.getSharedPreferences("passwords", Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor edit() {
        return passwords().edit();
    }

    public String getPassword(String server) {
        return passwords().getString(server, null);
    }

    public void setPassword(String server, String password) {
        edit().putString(server, password).commit();
    }

    public void clearPassword(String server) {
        edit().remove(server).commit();
    }
}
