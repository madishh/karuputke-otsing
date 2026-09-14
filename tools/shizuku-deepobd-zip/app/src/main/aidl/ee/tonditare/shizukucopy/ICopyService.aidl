package ee.tonditare.shizukucopy;

interface ICopyService {
    String findNewestZip(String downloadDirectory);
    String extractZipToDeepObd(String zipPath);
    String testDeepObdAccess();
}
