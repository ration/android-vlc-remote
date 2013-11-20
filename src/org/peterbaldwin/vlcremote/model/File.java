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

package org.peterbaldwin.vlcremote.model;

import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class File {

    private static final MimeTypeMap sMimeTypeMap = MimeTypeMap.getSingleton();

    private static String parseExtension(String path) {
        int index = path.lastIndexOf('.');
        if (index != -1) {
            return path.substring(index + 1);
        } else {
            return null;
        }
    }

    private String mType;
    private Long mSize;
    private String mDate;
    private String mPath;
    private String mName;
    private String mExtension;

    public File(String type, Long size, String date, String path, String name, String extension) {
        mType = type;
        mSize = size;
        mDate = date;
        mPath = path;
        mName = name;
        mExtension = extension != null ? extension : path != null ? parseExtension(path) : null;
    }

    public String getType() {
        return mType;
    }

    public void setType(String type) {
        mType = type;
    }

    public boolean isDirectory() {
        // Type is "directory" in VLC 1.0 and "dir" in VLC 1.1
        return "directory".equals(mType) || "dir".equals(mType);
    }

    public boolean isImage() {
        String mimeType = getMimeType();
        return mimeType != null && mimeType.startsWith("image/");
    }

    public Long getSize() {
        return mSize;
    }

    public void setSize(Long size) {
        mSize = size;
    }

    public String getDate() {
        return mDate;
    }

    public void setDate(String date) {
        mDate = date;
    }

    public String getPath() {
        return mPath;
    }

    public String getMrl() {
        if (isImage()) {
            return "fake://";
        } else {
            java.io.File file = new java.io.File(mPath);
            Uri uri = Uri.fromFile(file);
            return uri.toString();
        }
    }

    public List<String> getOptions() {
        if (isImage()) {
            return Collections.singletonList(":fake-file=" + getPath());
        } else {
            return Collections.emptyList();
        }
    }

    public List<String> getStreamingOptions() {
        List<String> options = new ArrayList<String>(getOptions());
        String mimeType = getMimeType();
        if (mimeType != null && mimeType.startsWith("audio/")) {
            options.add(":sout=#transcode{acodec=vorb,ab=128}:standard{access=http,mux=ogg,dst=0.0.0.0:8000}");
        } else {
            options.add(":sout=#transcode{vcodec=mp4v,vb=384,acodec=mp4a,ab=64,channels=2,fps=25,venc=x264{profile=baseline,keyint=50,bframes=0,no-cabac,ref=1,vbv-maxrate=4096,vbv-bufsize=1024,aq-mode=0,no-mbtree,partitions=none,no-weightb,weightp=0,me=dia,subme=0,no-mixed-refs,no-8x8dct,trellis=0,level1.3},vfilter=canvas{width=320,height=180,aspect=320:180,padd},senc,soverlay}:rtp{sdp=rtsp://0.0.0.0:5554/stream.sdp,caching=4000}}");
        }
        return options;
    }

    public Intent getIntentForStreaming(String authority) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String mimeType = getMimeType();
        if (mimeType != null && mimeType.startsWith("audio/")) {
            Uri.Builder builder = new Uri.Builder();
            builder.scheme("rtsp");
            builder.encodedAuthority(swapPortNumber(authority, 5554));
            builder.path("stream.sdp");
            Uri data = builder.build();
            intent.setData(data);
        } else {
            Uri.Builder builder = new Uri.Builder();
            builder.scheme("http");
            builder.encodedAuthority(swapPortNumber(authority, 8000));
            Uri data = builder.build();
            intent.setDataAndType(data, "application/ogg");
        }
        return intent;
    }

    public void setPath(String path) {
        mPath = path;
        if (mExtension == null && path != null) {
            mExtension = parseExtension(path);
        }
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getExtension() {
        return mExtension;
    }

    public void setExtension(String extension) {
        mExtension = extension;
    }

    public String getMimeType() {
        if (mExtension != null) {
            // See http://code.google.com/p/android/issues/detail?id=8806
            String extension = mExtension.toLowerCase(Locale.US);
            return sMimeTypeMap.getMimeTypeFromExtension(extension);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return mName;
    }

    private static String removePortNumber(String authority) {
        int index = authority.lastIndexOf(':');
        if (index != -1) {
            // Remove port number
            authority = authority.substring(0, index);
        }
        return authority;
    }

    private static String swapPortNumber(String authority, int port) {
        return removePortNumber(authority) + ":" + port;
    }
}
