package ee.tonditare.shizukucopy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_SHIZUKU = 1001;
    private static final String DOWNLOADS = "/storage/emulated/0/Download";
    private static final String DEEP_OBD_FILES = "/storage/emulated/0/Android/data/de.holeschak.bmw_deep_obd/files";
    private static final String UPDATE_URL = "https://github.com/madishh/karuputke-otsing/releases/download/deepobd-latest/DeepOBDZipInstaller.apk";
    private static final String PREFS = "deepobd_zip_installer";
    private static final String PREF_TARGET = "target_directory";

    private TextView status, selectedZip, selectedTarget, log;
    private ICopyService service;
    private boolean binding;
    private String selectedZipPath;
    private String selectedTargetPath;
    private long updateDownloadId = -1;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        append("Shizuku binder received.");
        refreshStatus();
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindService();
        else append("Shizuku is running; permission is not granted yet.");
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        service = null;
        binding = false;
        append("Shizuku binder disconnected.");
        refreshStatus();
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == REQ_SHIZUKU) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                append("Shizuku permission granted.");
                bindService();
            } else {
                append("Shizuku permission denied.");
            }
            refreshStatus();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            service = ICopyService.Stub.asInterface(binder);
            append("Privileged UserService connected.");
            refreshStatus();
            findNewestZip(false);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            binding = false;
            service = null;
            append("UserService disconnected.");
            refreshStatus();
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != updateDownloadId) return;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) {
                append("Update download failed.");
                return;
            }
            append("Update downloaded. Opening Android installer…");
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedTargetPath = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TARGET, DEEP_OBD_FILES);
        if (selectedTargetPath == null || !selectedTargetPath.startsWith(DEEP_OBD_FILES)) selectedTargetPath = DEEP_OBD_FILES;

        buildUi();
        updateTargetDisplay();

        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);

        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, f);

        refreshStatus();
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
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        TextView zipTitle = new TextView(this);
        zipTitle.setText("VALITUD ZIP");
        zipTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        zipTitle.setPadding(0, dp(8), 0, dp(4));
        root.addView(zipTitle);

        selectedZip = new TextView(this);
        selectedZip.setText("Ühtegi ZIP-i pole veel valitud");
        selectedZip.setTextIsSelectable(true);
        selectedZip.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(selectedZip, matchWrap());

        LinearLayout zipRow = new LinearLayout(this);
        zipRow.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = new Button(this);
        choose.setText("VALI ZIP");
        choose.setOnClickListener(v -> chooseZip());
        zipRow.addView(choose, weight());
        Button newest = new Button(this);
        newest.setText("UUSIM ZIP");
        newest.setOnClickListener(v -> findNewestZip(true));
        zipRow.addView(newest, weight());
        root.addView(zipRow, matchWrap());

        TextView targetTitle = new TextView(this);
        targetTitle.setText("SIHTKAUST");
        targetTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        targetTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(targetTitle);

        selectedTarget = new TextView(this);
        selectedTarget.setTextIsSelectable(true);
        selectedTarget.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(selectedTarget, matchWrap());

        LinearLayout targetRow = new LinearLayout(this);
        targetRow.setOrientation(LinearLayout.HORIZONTAL);
        Button chooseTarget = new Button(this);
        chooseTarget.setText("VALI SIHTKAUST");
        chooseTarget.setOnClickListener(v -> chooseTargetDirectory());
        targetRow.addView(chooseTarget, weight());

        Button resetTarget = new Button(this);
        resetTarget.setText("DEFAULT");
        resetTarget.setOnClickListener(v -> {
            setTargetDirectory(DEEP_OBD_FILES);
            append("Target reset to Deep OBD files.");
        });
        targetRow.addView(resetTarget, weight());
        root.addView(targetRow, matchWrap());

        Button extract = new Button(this);
        extract.setText("PAKI VALITUD ZIP → SIHTKAUSTA");
        extract.setOnClickListener(v -> extractNow());
        root.addView(extract, matchWrap());

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button grant = new Button(this);
        grant.setText("SHIZUKU LUBA");
        grant.setOnClickListener(v -> requestPermission());
        row2.addView(grant, weight());

        Button test = new Button(this);
        test.setText("TESTI LIGIPÄÄSU");
        test.setOnClickListener(v -> testDestination());
        row2.addView(test, weight());
        root.addView(row2, matchWrap());

        Button update = new Button(this);
        update.setText("UUENDA ÄPPI  •  v" + BuildConfig.VERSION_NAME);
        update.setOnClickListener(v -> updateApp());
        root.addView(update, matchWrap());

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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void append(String text) {
        runOnUiThread(() -> {
            if (log != null) log.append(text + "\n");
        });
    }

    private String finalExtractPath() {
        if (selectedZipPath == null || selectedZipPath.isEmpty()) return selectedTargetPath;
        File f = new File(selectedZipPath);
        String folder = f.getName().replaceFirst("(?i)\\.zip$", "");
        return selectedTargetPath + "/" + folder;
    }

    private void updateZipDisplay() {
        if (selectedZipPath == null || selectedZipPath.isEmpty()) {
            selectedZip.setText("Ühtegi ZIP-i pole veel valitud");
            return;
        }
        File f = new File(selectedZipPath);
        selectedZip.setText(f.getName() + "\n" + selectedZipPath + "\n→ " + finalExtractPath() + "/");
    }

    private void updateTargetDisplay() {
        if (selectedTarget == null) return;
        String rel = selectedTargetPath.equals(DEEP_OBD_FILES)
                ? "[Deep OBD files — DEFAULT]"
                : selectedTargetPath.substring(DEEP_OBD_FILES.length() + 1);
        selectedTarget.setText(rel + "\n" + selectedTargetPath);
        updateZipDisplay();
    }

    private void setSelectedZip(String path) {
        selectedZipPath = path;
        updateZipDisplay();
    }

    private void setTargetDirectory(String path) {
        selectedTargetPath = path;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_TARGET, path).apply();
        updateTargetDisplay();
    }

    private void refreshStatus() {
        boolean running = Shizuku.pingBinder();
        int perm = running ? Shizuku.checkSelfPermission() : PackageManager.PERMISSION_DENIED;
        int uid = running ? Shizuku.getUid() : -1;
        status.setText("Shizuku: " + (running ? "RUNNING" : "WAITING")
                + " | permission: " + (perm == PackageManager.PERMISSION_GRANTED ? "YES" : "NO")
                + " | uid: " + uid
                + " | service: " + (service != null ? "CONNECTED" : (binding ? "CONNECTING" : "NO")));
    }

    private void requestPermission() {
        if (!Shizuku.pingBinder()) {
            append("Waiting for Shizuku binder…");
            refreshStatus();
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            append("Permission already granted.");
            bindService();
        } else if (!Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(REQ_SHIZUKU);
        } else {
            append("Permission was denied before. Allow this app in Shizuku manager.");
        }
    }

    private void bindService() {
        if (service != null || binding) return;
        if (!Shizuku.pingBinder()) {
            append("Waiting for Shizuku binder…");
            refreshStatus();
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
            return;
        }

        binding = true;
        refreshStatus();
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(this, CopyUserService.class))
                .daemon(false)
                .processNameSuffix("zipcopy")
                .debuggable(BuildConfig.DEBUG)
                .version(6)
                .tag("deep-obd-zip-v6");
        Shizuku.bindUserService(args, connection);
    }

    private void findNewestZip(boolean announce) {
        if (service == null) {
            bindService();
            return;
        }

        new Thread(() -> {
            try {
                String result = service.findNewestZip(DOWNLOADS);
                if (result.startsWith("ERROR:")) {
                    append(result);
                } else {
                    runOnUiThread(() -> setSelectedZip(result));
                    if (announce) append("Selected newest ZIP: " + result);
                }
            } catch (RemoteException e) {
                append("Remote error: " + e);
            }
        }).start();
    }

    private void chooseZip() {
        if (service == null) {
            append("Shizuku service not connected yet.");
            bindService();
            return;
        }

        new Thread(() -> {
            try {
                String result = service.listZipFiles(DOWNLOADS);
                if (result.startsWith("ERROR:")) {
                    append(result);
                    return;
                }
                String[] paths = result.split("\\n");
                String[] names = new String[paths.length];
                for (int i = 0; i < paths.length; i++) names[i] = new File(paths[i]).getName();

                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Vali ZIP Downloads kaustast")
                        .setItems(names, (d, which) -> {
                            setSelectedZip(paths[which]);
                            append("Selected ZIP: " + paths[which]);
                        })
                        .setNegativeButton("Tühista", null)
                        .show());
            } catch (RemoteException e) {
                append("Remote error: " + e);
            }
        }).start();
    }

    private void chooseTargetDirectory() {
        if (service == null) {
            append("Shizuku service not connected yet.");
            bindService();
            return;
        }
        showDirectoryBrowser(selectedTargetPath);
    }

    private void showDirectoryBrowser(String currentPath) {
        new Thread(() -> {
            try {
                String result = service.listDeepObdDirectories(currentPath);
                if (result.startsWith("ERROR:")) {
                    append(result);
                    return;
                }

                String[] paths = result.isEmpty() ? new String[0] : result.split("\\n");
                String[] names = new String[paths.length];
                for (int i = 0; i < paths.length; i++) names[i] = "📁 " + new File(paths[i]).getName();

                runOnUiThread(() -> {
                    String rel = currentPath.equals(DEEP_OBD_FILES)
                            ? "Deep OBD files"
                            : currentPath.substring(DEEP_OBD_FILES.length() + 1);

                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setTitle("Sihtkaust: " + rel)
                            .setItems(names, (d, which) -> showDirectoryBrowser(paths[which]))
                            .setPositiveButton("VALI SEE KAUST", (d, w) -> {
                                setTargetDirectory(currentPath);
                                append("Target folder: " + currentPath);
                            })
                            .setNeutralButton("UUS KAUST", null)
                            .setNegativeButton(currentPath.equals(DEEP_OBD_FILES) ? "SULGE" : "← ÜLES", null)
                            .create();

                    dialog.setOnShowListener(x -> {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> promptCreateDirectory(currentPath));
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                            if (currentPath.equals(DEEP_OBD_FILES)) {
                                dialog.dismiss();
                            } else {
                                File parent = new File(currentPath).getParentFile();
                                String parentPath = parent == null ? DEEP_OBD_FILES : parent.getAbsolutePath();
                                if (!parentPath.startsWith(DEEP_OBD_FILES)) parentPath = DEEP_OBD_FILES;
                                dialog.dismiss();
                                showDirectoryBrowser(parentPath);
                            }
                        });
                    });
                    dialog.show();
                });
            } catch (RemoteException e) {
                append("Remote error: " + e);
            }
        }).start();
    }

    private void promptCreateDirectory(String parentPath) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Uue kausta nimi");

        new AlertDialog.Builder(this)
                .setTitle("Loo uus kaust")
                .setView(input)
                .setPositiveButton("LOO", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Sisesta kausta nimi", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    new Thread(() -> {
                        try {
                            String result = service.createDeepObdDirectory(parentPath, name);
                            if (result.startsWith("ERROR:")) {
                                append(result);
                            } else {
                                append("Created folder: " + result);
                                runOnUiThread(() -> showDirectoryBrowser(result));
                            }
                        } catch (RemoteException e) {
                            append("Remote error: " + e);
                        }
                    }).start();
                })
                .setNegativeButton("Tühista", null)
                .show();
    }

    private void testDestination() {
        if (service == null) {
            bindService();
            return;
        }
        new Thread(() -> {
            try {
                append(service.testDeepObdAccess());
            } catch (RemoteException e) {
                append("Remote error: " + e);
            }
        }).start();
    }

    private void extractNow() {
        if (service == null) {
            bindService();
            return;
        }
        if (selectedZipPath == null || selectedZipPath.isEmpty()) {
            Toast.makeText(this, "Vali kõigepealt ZIP", Toast.LENGTH_SHORT).show();
            return;
        }

        final String zip = selectedZipPath;
        final String target = selectedTargetPath;
        append("Extracting selected ZIP: " + zip);
        append("Parent target: " + target);

        new Thread(() -> {
            try {
                append(service.extractZipToDirectory(zip, target));
            } catch (RemoteException e) {
                append("Remote error: " + e);
            }
        }).start();
    }

    private void updateApp() {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            append("Allow 'Install unknown apps' for this app, then press UPDATE again.");
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(s);
            return;
        }

        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null) {
                File old = new File(dir, "DeepOBDZipInstaller.apk");
                if (old.exists()) old.delete();
            }

            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(UPDATE_URL));
            r.setTitle("Deep OBD ZIP Installer update");
            r.setDescription("Downloading latest APK from GitHub");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "DeepOBDZipInstaller.apk");

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            updateDownloadId = dm.enqueue(r);
            append("Downloading latest APK from GitHub…");
        } catch (Exception e) {
            append("Update error: " + e);
        }
    }

    @Override protected void onDestroy() {
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {}

        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }
}
