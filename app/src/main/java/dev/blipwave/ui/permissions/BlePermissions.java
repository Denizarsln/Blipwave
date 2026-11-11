package dev.blipwave.ui.permissions;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/** Android 12+ için yeni Bluetooth izinleri, öncesi için konum izni. */
public final class BlePermissions {

    private BlePermissions() { }

    public static String[] required() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public static boolean granted(Context context) {
        for (String permission : required()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** İzin penceresi tekrar gösterilebiliyor mu? Reddedildikten sonra false ise kalıcı redde düşülmüştür. */
    public static boolean canAskAgain(Fragment fragment) {
        for (String permission : required()) {
            if (fragment.shouldShowRequestPermissionRationale(permission)) return true;
        }
        return false;
    }

    public static Intent appSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.getPackageName(), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
