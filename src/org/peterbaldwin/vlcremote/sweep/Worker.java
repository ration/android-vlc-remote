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

package org.peterbaldwin.vlcremote.sweep;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;

final class Worker extends Thread {
    public interface Manager {
        /**
         * Retrieves and removes the next IP address, or returns {@code null} if
         * there are not more addresses to scan.
         */
        byte[] pollIpAddress();
    }

    public interface Callback {
        /**
         * Indicates that an address is reachable.
         */
        void onReachable(byte[] ipAddress, HttpResponse response);

        /**
         * Indicates that an address is unreachable.
         */
        void onUnreachable(byte[] ipAddress, IOException e);
    }

    private final int mPort;
    private final String mPath;
    private Manager mManager;
    private Callback mCallback;

    public Worker(int port, String path) {
        mPort = port;
        mPath = path;
    }

    public void setManager(Manager manager) {
        mManager = manager;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private static URL createUrl(String scheme, String host, int port, String path) {
        try {
            return new URL("http", host, port, path);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void run() {
        // Note: InetAddress#isReachable(int) always returns false for Windows
        // hosts because applications do not have permission to perform ICMP
        // echo requests.
        for (;;) {
            byte[] ipAddress = mManager.pollIpAddress();
            if (ipAddress == null) {
                break;
            }
            try {
                InetAddress address = InetAddress.getByAddress(ipAddress);
                String hostAddress = address.getHostAddress();
                URL url = createUrl("http", hostAddress, mPort, mPath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(1000);
                try {
                    int responseCode = connection.getResponseCode();
                    String responseMessage = connection.getResponseMessage();
                    InputStream inputStream = (responseCode == HttpURLConnection.HTTP_OK)
                            ? connection.getInputStream() : connection.getErrorStream();
                    try {
                        int length = connection.getContentLength();
                        InputStreamEntity entity = new InputStreamEntity(inputStream, length);
                        entity.setContentType(connection.getContentType());

                        // Copy the entire response body into memory
                        // before the HTTP connection is closed:
                        String body = EntityUtils.toString(entity);

                        ProtocolVersion version = new ProtocolVersion("HTTP", 1, 1);
                        String hostname = address.getHostName();
                        StatusLine statusLine =
                                new BasicStatusLine(version, responseCode, responseMessage);
                        HttpResponse response = new BasicHttpResponse(statusLine);
                        response.setHeader(HTTP.TARGET_HOST, hostname + ":" + mPort);
                        response.setEntity(new StringEntity(body));
                        mCallback.onReachable(ipAddress, response);
                    } finally {
                        inputStream.close();
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                mCallback.onUnreachable(ipAddress, e);
            }
        }
    }
}
