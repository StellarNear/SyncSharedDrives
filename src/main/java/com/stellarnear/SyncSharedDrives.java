package com.stellarnear;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class SyncSharedDrives {
    private static final String APPLICATION_NAME = "Google Drive API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static Drive service;
    private static CustomLog log = new CustomLog(SyncSharedDrives.class);

    private static String pathIN = null;
    private static String pathOUT = null;

    private static int totalNewFile = 0;
    private static int totalAlreadyFile = 0;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY,
            DriveScopes.DRIVE_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/creds.json";

    /**
     * Creates an authorized Credential object.
     * 
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the cc.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = SyncSharedDrives.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                        .setAccessType("offline")
                        .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("dummy@gmail.com");
    }

    public static void main(String... args) throws Exception {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME).build();

        log.info("Thanks for using SyncSharedDrive !");
        log.info("Reading Config file");
        java.io.File config = new java.io.File("./config.ini");
        FileReader frConf = new FileReader(config); // reads the file

        try (BufferedReader br = new BufferedReader(frConf)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("IN=")) {
                    pathIN = line.replace("IN=", "");
                    log.info("Path IN : " + pathIN);
                }
                if (line.startsWith("OUT=")) {
                    pathOUT = line.replace("OUT=", "");
                    log.info("Path OUT : " + pathOUT);
                }
            }
        }
        if (pathIN == null || pathOUT == null) {
            String errMsg = "The config file don't have the value for the paths IN=? or OUT=?";
            log.err(errMsg);
            throw new Exception(errMsg);
        }

        log.info("Reading IN folder");
        java.io.File folderIn = new java.io.File(pathIN);
        if (!folderIn.exists()) {
            String errMsg = "Input forlder don't exist ! please create it at : " + folderIn.getCanonicalPath();
            log.err(errMsg);
            throw new Exception(errMsg);
        }

        java.io.File directoryOut = new java.io.File(pathOUT);
        if (!directoryOut.exists()) {
            String errMsg = "Output forlder don't exist ! please create it at : " + directoryOut.getCanonicalPath();
            log.err(errMsg);
            throw new Exception(errMsg);
        }

        java.io.File[] directoryListing = folderIn.listFiles();

        long startTotal = System.currentTimeMillis();
        if (directoryListing != null && directoryListing.length > 0) {
            for (java.io.File child : directoryListing) {
                String folderName = null;
                String driveId = null;
                try {
                    FileReader fr = new FileReader(child); // reads the file
                    try (BufferedReader br = new BufferedReader(fr)) {
                        folderName = normalizeName(child.getName());
                        driveId = br.readLine();
                        if (driveId != null && driveId.length() > 0) {
                            treatDrive(folderName, driveId);
                        }
                    }
                } catch (Exception e) {
                    log.err("An error occured while treating drive : " + folderName + " [id:" + driveId + "]", e);
                }

            }
        } else {
            log.err("The IN folder should have file pointing to the drives to sync");
        }
        long endTotal = System.currentTimeMillis();
        log.info("SyncSharedDrive ended it took a total time of " + convertTime(endTotal - startTotal));
        log.info(directoryListing.length + " drives synchronized");
        log.info(totalNewFile + " new files downloaded");
        log.info(totalAlreadyFile + " were already present");
    }

    private static void treatDrive(String folderName, String driveId) throws Exception {
        log.info("Starting sync of dive : " + folderName);
        long start = System.currentTimeMillis();
        HashMap<String, String> foldersPathToID = new HashMap<>();

        searchAllFoldersRecursive(folderName, driveId, foldersPathToID);

        HashMap<String, List<File>> pathFile = new HashMap<>();
        int nFiles = 0;
        for (Entry<String, String> pathFolder : foldersPathToID.entrySet()) {
            List<File> result = search(Type.FILE, pathFolder.getValue());
            if (result.size() > 0) {
                pathFile.putIfAbsent(pathFolder.getKey(), new ArrayList<>());
                for (File file : result) {
                    nFiles++;
                    pathFile.get(pathFolder.getKey()).add(file);
                }
            }
        }

        int nFileAlready = 0;
        int nFileDownloaded = 0;
        if (pathFile.isEmpty()) {
            log.info("No files found");
        } else {
            log.info(nFiles + " files found on the drive");

            for (Entry<String, List<File>> pathName : pathFile.entrySet()) {

                String folderOutName = pathOUT + "/" + pathName.getKey();
                new java.io.File(folderOutName).mkdirs();

                for (File file : pathName.getValue()) {
                    boolean targetedFile = file.getName().endsWith(".jpg")
                            || file.getName().endsWith(".jpeg")
                            || file.getName().endsWith(".png");
                    if (!targetedFile) {
                        log.debug("Skipping unwanted file : " + file.getName());
                        continue;
                    }

                    java.io.File img = new java.io.File(folderOutName + "/" + normalizeName(file.getName()));
                    if (img.exists()) {
                        nFileAlready++;
                        continue;
                    }
                    try {
                        FileOutputStream fout = new FileOutputStream(img);
                        service.files().get(file.getId()).executeMediaAndDownloadTo(fout);
                        nFileDownloaded++;
                    } catch (Exception e) {
                        log.warn("Could not download the file : " + folderOutName + "/" + file.getName(), e);
                    }
                }
            }
            log.info(nFileDownloaded + " new files downloaded ! (" + nFileAlready + " files already present)");
            totalNewFile += nFileDownloaded;
            totalAlreadyFile += nFileAlready;
        }
        long end = System.currentTimeMillis();
        log.info("End sync of dive : " + folderName + " (it took " + convertTime(end - start) + ")");
    }

    private static String convertTime(long l) {
        int nHour = (int) (((l / 1000) / 60) / 60);
        if (nHour > 0) {
            int nMinute = (int) ((l / 1000) / 60) - 60 * nHour;
            return nHour + " hours " + nMinute + " minutes";
        } else {
            int nMinute = (int) ((l / 1000) / 60);
            if (nMinute > 0) {
                return nMinute + " minutes";
            } else {
                return (int) (l / 1000) + " seconds";
            }
        }
    }

    private static void searchAllFoldersRecursive(String nameFold, String id, HashMap<String, String> map)
            throws IOException {

        map.putIfAbsent(nameFold, id);
        List<File> result = search(Type.FOLDER, id);
        // dig deeper
        if (result.size() > 0) {
            for (File folder : result) {
                searchAllFoldersRecursive(nameFold + "/" + normalizeName(folder.getName()), folder.getId(), map);
            }
        }
    }

    private static List<com.google.api.services.drive.model.File> search(Type type, String folderId)
            throws IOException {
        String nextPageToken = "go";
        List<File> driveFolders = new ArrayList<>();
        com.google.api.services.drive.Drive.Files.List request = service.files()
                .list()
                .setQ("'" + folderId
                        + "' in parents and mimeType" + (type == Type.FOLDER ? "=" : "!=")
                        + "'application/vnd.google-apps.folder' and trashed = false")
                .setPageSize(100).setFields("nextPageToken, files(id, name)");

        while (nextPageToken != null && nextPageToken.length() > 0) {
            try {
                FileList result = request.execute();
                driveFolders.addAll(result.getFiles());
                nextPageToken = result.getNextPageToken();
                request.setPageToken(nextPageToken);
                return driveFolders;
            } catch (Exception e) {
                log.err("Error while reading folder id : " + folderId, e);
                nextPageToken = null;
            }

        }
        return new ArrayList<>();
    }

    private static String normalizeName(String name) {
        return name.replace("/", "_").replace(":", "").replace("\\", "_");
    }
}
