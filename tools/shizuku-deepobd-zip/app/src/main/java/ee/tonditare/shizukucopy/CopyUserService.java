package ee.tonditare.shizukucopy;

import android.content.Context;
import android.os.RemoteException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CopyUserService extends ICopyService.Stub {
    private static final String DEEP_OBD_FILES = "/storage/emulated/0/Android/data/de.holeschak.bmw_deep_obd/files";

    public CopyUserService() {}
    public CopyUserService(Context context) {}

    private static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    private static String run(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line).append('\n');
        }
        int rc = p.waitFor();
        return "exit=" + rc + "\n" + out;
    }

    private File[] zipFiles(String downloadDirectory) {
        File dir = new File(downloadDirectory);
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    private File checkedDeepObdDirectory(String path, boolean create) throws Exception {
        File base = new File(DEEP_OBD_FILES).getCanonicalFile();
        if (!base.exists() && !base.mkdirs()) throw new Exception("cannot create Deep OBD files directory: " + base);

        File dir = (path == null || path.trim().isEmpty()) ? base : new File(path.trim()).getCanonicalFile();
        String basePath = base.getCanonicalPath();
        String dirPath = dir.getCanonicalPath();
        if (!dirPath.equals(basePath) && !dirPath.startsWith(basePath + File.separator)) {
            throw new SecurityException("directory is outside Deep OBD files: " + dirPath);
        }
        if (create && !dir.exists() && !dir.mkdirs()) throw new Exception("cannot create directory: " + dir);
        if (!dir.isDirectory()) throw new Exception("not a directory: " + dir);
        return dir;
    }

    @Override public String findNewestZip(String downloadDirectory) throws RemoteException {
        try {
            File[] files = zipFiles(downloadDirectory);
            return files.length == 0 ? "ERROR: no .zip files in " + downloadDirectory : files[0].getAbsolutePath();
        } catch (Exception e) { return "ERROR: " + e; }
    }

    @Override public String listZipFiles(String downloadDirectory) throws RemoteException {
        try {
            File[] files = zipFiles(downloadDirectory);
            if (files.length == 0) return "ERROR: no .zip files in " + downloadDirectory;
            StringBuilder out = new StringBuilder();
            for (File f : files) out.append(f.getAbsolutePath()).append('\n');
            return out.toString().trim();
        } catch (Exception e) { return "ERROR: " + e; }
    }

    @Override public String listDeepObdDirectories(String parentDirectory) throws RemoteException {
        try {
            File dir = checkedDeepObdDirectory(parentDirectory, true);
            File[] dirs = dir.listFiles(File::isDirectory);
            if (dirs == null || dirs.length == 0) return "";
            Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            StringBuilder out = new StringBuilder();
            for (File d : dirs) out.append(d.getCanonicalPath()).append('\n');
            return out.toString().trim();
        } catch (Exception e) { return "ERROR: " + e; }
    }

    @Override public String createDeepObdDirectory(String parentDirectory, String folderName) throws RemoteException {
        try {
            if (folderName == null) return "ERROR: folder name is empty";
            String name = folderName.trim();
            if (name.isEmpty()) return "ERROR: folder name is empty";
            if (name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
                return "ERROR: invalid folder name";
            }
            File parent = checkedDeepObdDirectory(parentDirectory, true);
            File child = new File(parent, name).getCanonicalFile();
            checkedDeepObdDirectory(child.getCanonicalPath(), true);
            return child.getCanonicalPath();
        } catch (Exception e) { return "ERROR: " + e; }
    }

    @Override public String testDeepObdAccess() throws RemoteException {
        try {
            String cmd = "id; echo PATH=" + q(DEEP_OBD_FILES) + "; mkdir -p " + q(DEEP_OBD_FILES) + " 2>&1; ls -ld " + q(DEEP_OBD_FILES) + " 2>&1";
            return run(cmd);
        } catch (Exception e) { return "ERROR: " + e; }
    }

    @Override public String extractZipToDirectory(String zipPath, String parentDirectory) throws RemoteException {
        if (zipPath == null || zipPath.trim().isEmpty()) return "ERROR: ZIP path is empty";
        try {
            File zipFile = new File(zipPath.trim());
            if (!zipFile.isFile()) return "ERROR: ZIP does not exist: " + zipFile;
            if (!zipFile.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) return "ERROR: selected file is not .zip: " + zipFile.getName();

            String zipName = zipFile.getName();
            String folderName = zipName.substring(0, zipName.length() - 4);
            if (folderName.trim().isEmpty()) return "ERROR: ZIP filename has no usable folder name";

            File parent = checkedDeepObdDirectory(parentDirectory, true);
            File target = new File(parent, folderName).getCanonicalFile();
            checkedDeepObdDirectory(target.getCanonicalPath(), true);

            String targetCanonical = target.getCanonicalPath();
            String targetPrefix = targetCanonical + File.separator;
            int files = 0, dirs = 0;
            long bytes = 0;
            byte[] buffer = new byte[128 * 1024];

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile), 128 * 1024))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name == null || name.isEmpty()) { zis.closeEntry(); continue; }
                    File out = new File(target, name);
                    String outCanonical = out.getCanonicalPath();
                    if (!outCanonical.equals(targetCanonical) && !outCanonical.startsWith(targetPrefix)) return "ERROR: unsafe ZIP entry blocked: " + name;

                    if (entry.isDirectory()) {
                        if (!out.exists() && !out.mkdirs()) return "ERROR: cannot create directory: " + out;
                        dirs++;
                    } else {
                        File parentOut = out.getParentFile();
                        if (parentOut != null && !parentOut.exists() && !parentOut.mkdirs()) return "ERROR: cannot create parent directory: " + parentOut;
                        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out), 128 * 1024)) {
                            int n;
                            while ((n = zis.read(buffer)) != -1) { bos.write(buffer, 0, n); bytes += n; }
                        }
                        if (entry.getTime() > 0) out.setLastModified(entry.getTime());
                        files++;
                    }
                    zis.closeEntry();
                }
            }

            return "EXTRACT OK\nZIP: " + zipFile.getAbsolutePath() + "\nTO:  " + target.getAbsolutePath() + "\nfiles: " + files + "\ndirs: " + dirs + "\nbytes: " + bytes;
        } catch (Exception e) { return "ERROR: " + e; }
    }

    public void destroy() { System.exit(0); }
}
