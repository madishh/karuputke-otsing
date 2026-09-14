package ee.tonditare.shizukucopy;

interface ICopyService {
    String findNewestZip(String downloadDirectory);
    String listZipFiles(String downloadDirectory);
    String listDeepObdDirectories(String parentDirectory);
    String createDeepObdDirectory(String parentDirectory, String folderName);
    String extractZipToDirectory(String zipPath, String parentDirectory);
    String testDeepObdAccess();
}
