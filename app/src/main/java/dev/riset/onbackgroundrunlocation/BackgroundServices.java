package dev.riset.onbackgroundrunlocation;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.greenrobot.eventbus.EventBus;

public class BackgroundServices extends Service {

    private static final String CHANNEL_KODE = "channel";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = "dev.riset.onbackgroundrunlocation"+"_started_from_notification";
    private final IBinder varmBinder = new LocalBinder();
    private static final long UPDATE_LOCATION_INTERVAL_IN_MIL = 10000;
    private static final long FASTEST_UPDATE_LOCATION_INTERVAL_IN_MUL = UPDATE_LOCATION_INTERVAL_IN_MIL / 2;
    private static final int NOTIF_ID = 2000;
    private boolean mChangingConfiguration = false;
    private NotificationManager mNotificationManager;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Handler mServicesHandler;
    private Location mLocation;

    public BackgroundServices() {

    }

    @Override
    public void onCreate() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());

            }
        };
        createLocationRequest();
        getLastLocation();

        HandlerThread handlerThread = new HandlerThread("RISETBACK");
        handlerThread.start();
        mServicesHandler = new Handler(handlerThread.getLooper());
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_KODE,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(mChannel);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false);
        if (startedFromNotification) {
            removeLocationUpdates();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    public void removeLocationUpdates() {
        try {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            Common.setRequestStringLocationUpdates(this, false);
            stopSelf();

        } catch (SecurityException ex) {
            Common.setRequestStringLocationUpdates(this, true);
            Log.e("RISET_BACK", "Lost location permission, Cloud not remove updates. "+ex);
        }
    }

    private void getLastLocation() {
        try {
            fusedLocationProviderClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        mLocation = task.getResult();
                    } else {
                        Log.e("RISET_BACK", "Failed get location");
                    }
                }
            });
        } catch (SecurityException ex) {
            Log.e("RISET_BACK", "Lost location permission. "+ex);
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_LOCATION_INTERVAL_IN_MIL);
        locationRequest.setFastestInterval(FASTEST_UPDATE_LOCATION_INTERVAL_IN_MUL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void onNewLocation(Location lastLocation) {
        mLocation = lastLocation;
        EventBus.getDefault().postSticky(new SendLocationToActivity(mLocation));

        //Update Notifi Content If Run As a Foreground Services
        if (serviceInRunningInForeGround(this)) {
            mNotificationManager.notify(NOTIF_ID, getNotification());

        }
    }

    private Notification getNotification() {
        Intent intent = new Intent(this, BackgroundServices.class);
        String text = Common.getLocationText(mLocation);

        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_android_black_24dp, "Launch", activityPendingIntent)
                .addAction(R.drawable.ic_close_black_24dp, "Remove", servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Common.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        //Set The Channel id For Android Version O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_KODE);
        }
            return builder.build();

    }

    private boolean serviceInRunningInForeGround(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service:manager.getRunningServices(Integer.MAX_VALUE))
            if (getClass().getName().equals(service.service.getClassName()))
                if (service.foreground)
                    return true;

        return false;

    }

    public void requestLocationUpdates() {
        Common.setRequestStringLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), BackgroundServices.class));
        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

        } catch (SecurityException ex) {
            Log.e("RISET_BACK", "Lost location permission. Could not request it. "+ex);
        }

    }

    public class LocalBinder extends Binder {
        BackgroundServices getServices() {
            return BackgroundServices.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration = false;

        return varmBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration = false;

        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!mChangingConfiguration && Common.requestStringLocationUpdates(this))
            startForeground(NOTIF_ID, getNotification());

        return true;
    }

    @Override
    public void onDestroy() {
        mServicesHandler.removeCallbacks(null);

        super.onDestroy();
    }
}
