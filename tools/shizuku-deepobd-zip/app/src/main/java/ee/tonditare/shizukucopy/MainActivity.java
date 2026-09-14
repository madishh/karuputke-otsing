package ee.tonditare.shizukucopy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_SHIZUKU = 1001;
    private static final String DOWNLOADS = "/storage/emulated/0/Download";
    private static final String DEEP_OBD_FILES = "/storage/emulated/0/Android/data/de.holeschak.bmw_deep_obd/files";

    private TextView status;
    private TextView log;
    private EditText zipPath;
    private ICopyService service;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == REQ_SHIZUKU) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) { append("Shizuku permission granted."); bindService(); }
            else append("Shizuku permission denied.");
            refreshStatus();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ICopyService.Stub.asInterface(binder);
            append("Privileged UserService connected.");
            refreshStatus();
            findNewestZip();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            append("UserService disconnected.");
            refreshStatus();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        buildUi();
        refreshStatus();
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindService();
    }

    private void buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("ZIP → Deep OBD");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(12));
        root.addView(status);

        TextView info = new TextView(this);
        info.setText("Newest ZIP from Downloads is extracted into:\n" + DEEP_OBD_FILES + "/<ZIP name>/\n\nExample: Test.zip → .../files/Test/");
        root.addView(info, matchWrap());

        zipPath = new EditText(this);
        zipPath.setHint("ZIP path");
        zipPath.setSingleLine(true);
        zipPath.setText(DOWNLOADS + "/");
        root.addView(zipPath, matchWrap());

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button grant = new Button(this);
        grant.setText("GRANT SHIZUKU");
        grant.setOnClickListener(v -> requestPermission());
        row1.addView(grant, weight());
        Button newest = new Button(this);
        newest.setText("NEWEST ZIP");
        newest.setOnClickListener(v -> findNewestZip());
        row1.addView(newest, weight());
        root.addView(row1, matchWrap());

        Button test = new Button(this);
        test.setText("TEST DEEP OBD ACCESS");
        test.setOnClickListener(v -> testDestination());
        root.addView(test, matchWrap());

        Button extract = new Button(this);
        extract.setText("EXTRACT ZIP → DEEP OBD");
        extract.setOnClickListener(v -> extractNow());
        root.addView(extract, matchWrap());

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(12);
        log.setTextIsSelectable(true);
        log.setPadding(0, dp(8), 0, 0);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(log);
        root.addView(scroller, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    private void append(String text) { runOnUiThread(() -> log.append(text + "\n")); }

    private void refreshStatus() {
        boolean running = Shizuku.pingBinder();
        int perm = running ? Shizuku.checkSelfPermission() : PackageManager.PERMISSION_DENIED;
        int uid = running ? Shizuku.getUid() : -1;
        status.setText("Shizuku: " + (running ? "RUNNING" : "NOT RUNNING") + " | permission: " + (perm == PackageManager.PERMISSION_GRANTED ? "YES" : "NO") + " | uid: " + uid + " | service: " + (service != null ? "CONNECTED" : "NO"));
    }

    private void requestPermission() {
        if (!Shizuku.pingBinder()) { append("Start Shizuku first."); refreshStatus(); return; }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { append("Permission already granted."); bindService(); }
        else if (!Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQ_SHIZUKU);
        else append("Permission was denied before. Allow this app in Shizuku manager.");
    }

    private void bindService() {
        if (!Shizuku.pingBinder()) { append("Shizuku is not running."); return; }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { requestPermission(); return; }
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(this, CopyUserService.class))
                .daemon(false).processNameSuffix("zipcopy").debuggable(BuildConfig.DEBUG).version(3).tag("deep-obd-zip-v3");
        Shizuku.bindUserService(args, connection);
    }

    private void findNewestZip() {
        if (service == null) { append("Service not connected; trying to connect…"); bindService(); return; }
        new Thread(() -> {
            try {
                String result = service.findNewestZip(DOWNLOADS);
                append("Newest ZIP: " + result);
                if (!result.startsWith("ERROR:")) runOnUiThread(() -> zipPath.setText(result));
            } catch (RemoteException e) { append("Remote error: " + e); }
        }).start();
    }

    private void testDestination() {
        if (service == null) { append("Service not connected; trying to connect…"); bindService(); return; }
        new Thread(() -> { try { append(service.testDeepObdAccess()); } catch (RemoteException e) { append("Remote error: " + e); } }).start();
    }

    private void extractNow() {
        if (service == null) { append("Service not connected; trying to connect…"); bindService(); return; }
        final String path = zipPath.getText().toString().trim();
        append("Extracting: " + path);
        new Thread(() -> { try { append(service.extractZipToDeepObd(path)); } catch (RemoteException e) { append("Remote error: " + e); } }).start();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }
}
