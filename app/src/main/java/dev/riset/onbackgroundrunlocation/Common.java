package dev.riset.onbackgroundrunlocation;

import android.content.Context;
import android.location.Location;
import android.preference.PreferenceManager;

import java.text.DateFormat;
import java.util.Date;

public class Common {
    public static final String KEY_REQUEST_LOCATION_UPDATES = "LoactionUpdateEnable";

    public static String getLocationText(Location mLocation) {
        return mLocation == null ? "Not Falid Location" : new StringBuilder()
                .append(mLocation.getLatitude())
                .append("/")
                .append(mLocation.getLongitude())
                .toString();
    }

    public static CharSequence getLocationTitle(BackgroundServices backgroundServices) {
        return String.format("Location Update: %1$s", DateFormat.getDateInstance().format(new Date()));
    }

    public static void setRequestStringLocationUpdates(Context context, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(KEY_REQUEST_LOCATION_UPDATES, value)
                .apply();
    }

    public static boolean requestStringLocationUpdates(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_REQUEST_LOCATION_UPDATES, false);
    }
}
