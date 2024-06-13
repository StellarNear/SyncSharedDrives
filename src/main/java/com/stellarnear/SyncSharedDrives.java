package com.stellarnear;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
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
    private static NetHttpTransport HTTP_TRANSPORT = null;
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static Drive service;
    private static CustomLog log = new CustomLog(SyncSharedDrives.class);

    private static String pathIN = null;
    private static String pathOUT = null;
    private static String mpcContribUrl = null;

    private static int totalNewFile = 0;
    private static int totalAlreadyFile = 0;
    private static AtomicInteger totalFile;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY,
            DriveScopes.DRIVE_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "creds.json";

    /**
     * Creates an authorized Credential object.
     * 
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the cc.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = SyncSharedDrives.class.getClassLoader().getResourceAsStream(CREDENTIALS_FILE_PATH);
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

    private static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest) throws IOException {
                requestInitializer.initialize(httpRequest);
                httpRequest.setConnectTimeout(5 * 60000); // 3 minutes connect timeout
                httpRequest.setReadTimeout(5 * 60000); // 3 minutes read timeout
            }
        };
    }

    public static void main(String... args) throws Exception {
        // Build a new authorized API client service.

        HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive.Builder builder = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                setHttpTimeout(getCredentials(HTTP_TRANSPORT))).setApplicationName(APPLICATION_NAME);

        service = builder.build();

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
                if (line.startsWith("MPC_FILL_CONTRIB=")) {
                    mpcContribUrl = line.replace("MPC_FILL_CONTRIB=", "");
                    log.info("MPC autofill contrib url : " + mpcContribUrl);
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
            String errMsg = "Input forlder don't exist ! please create it at : " +
                    folderIn.getCanonicalPath();
            log.err(errMsg);
            throw new Exception(errMsg);
        }

        java.io.File directoryOut = new java.io.File(pathOUT);
        if (!directoryOut.exists()) {
            String errMsg = "Output forlder don't exist ! please create it at : " +
                    directoryOut.getCanonicalPath();
            log.err(errMsg);
            throw new Exception(errMsg);
        }

        java.io.File[] directoryListing = folderIn.listFiles();

        Map<String, String> googleIDName = new HashMap<>();

        // scanning all driveFiles
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
                            googleIDName.put(driveId, folderName);
                        }
                    }
                } catch (Exception e) {
                    log.err("An error occured while treating drive : " + folderName + " [id:" +
                            driveId + "]", e);
                }

            }
        } else {
            log.err("The IN folder should have file pointing to the drives to sync");
        }

        // reading MPC contrib page
        if (mpcContribUrl != null) {
            try {
                URL url = new URL(mpcContribUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("accept", "application/json");
                connection.setRequestProperty("User-Agent", "PostmanRuntime/7.39.0");

                int nNewDrive = 0;
                int nOldDrive = 0;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONObject results = jsonObject.getJSONObject("results");

                    for (String key : results.keySet()) {
                        JSONObject child = results.getJSONObject(key);
                        String name = child.getString("name");
                        String googleId = child.getString("identifier");

                        if (!googleIDName.containsKey(googleId)) {
                            log.info(name + " is a new drive found from MPCautofill");
                            nNewDrive++;
                            googleIDName.put(googleId, name);
                            // saving the file for later run in case they reaname the folder name but it's
                            // still the same
                            java.io.File driveFile = new java.io.File(
                                    pathIN + java.io.File.separator + name);
                            driveFile.createNewFile();
                            FileWriter myWriter = new FileWriter(driveFile.getAbsolutePath());
                            myWriter.write(googleId);
                            myWriter.close();
                        } else {
                            nOldDrive++;
                            log.info(name +
                                    " already found in local listed drives (was known as "
                                    + googleIDName.get(googleId) + ")");
                        }
                    }

                    // Do what you want with the nameIdentifierMap here

                } catch (IOException | JSONException e) {
                    log.err("Could not open the stream url : " + mpcContribUrl, e);
                    throw e;
                }

                log.info(nNewDrive + " new drives found in MPCfill");
                log.info(nOldDrive + " old drives already known");

            } catch (MalformedURLException e2) {
                log.err("The MPC url is malformed : " + mpcContribUrl, e2);
                throw e2;
            }
        }

        // start downloading
        AtomicInteger nDrive = new AtomicInteger(0);
        totalFile = new AtomicInteger(0);
        long startTotal = System.currentTimeMillis();

        int availableProcessors = Runtime.getRuntime().availableProcessors();

        List<Callable<Void>> allSync = new ArrayList<>();

        for (Entry<String, String> entry : googleIDName.entrySet()) {
            try {

                allSync.add(new Callable<Void>() {
                    @Override
                    public Void call() {
                        try {

                            AtomicInteger nFilesDriveFound = new AtomicInteger(0);
                            long startSync = System.currentTimeMillis();

                            treatDrive(entry.getValue(), entry.getKey(), nFilesDriveFound);

                            long endSync = System.currentTimeMillis();

                            nDrive.incrementAndGet();
                            log.info("End sync of dive " + nDrive.get() + "/" + googleIDName.entrySet().size() + " : "
                                    + entry.getValue() + " it took " + convertTime(endSync - startSync) + " found "
                                    + nFilesDriveFound.get() + " images");
                        } catch (Exception e) {
                            log.warn("Could not sync the drive : " + entry.getValue(), e);
                        }
                        return null;
                    }
                });

            } catch (Exception e) {
                log.err("An error occured while treating drive : " + entry.getValue() + " [id:" + entry.getKey() + "]",
                        e);

            }
        }
        if (allSync.size() > 0) {
            ExecutorService executor = Executors.newFixedThreadPool(availableProcessors * 4);

            log.debug(
                    allSync.size() + " drives will be sync using " + availableProcessors * 4
                            + " parralel thread  !");
            executor.invokeAll(allSync);

        }

        long endTotal = System.currentTimeMillis();
        log.info("SyncSharedDrive ended it took a total time of " + convertTime(endTotal - startTotal));
        log.info(googleIDName.size() + " drives synchronized");
        log.info(totalFile + " images founds");

        System.exit(0);
    }

    private static void treatDrive(String folderName, String driveId, AtomicInteger nFilesDriveFound) throws Exception {

        // HashMap to store folder ID to path mapping
        HashMap<String, String> nameToId = new HashMap<>();

        // Fetch all files and folders in the drive

        fetchAllFiles(driveId, nameToId, nFilesDriveFound);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // String json = objectMapper.writeValueAsString(nameToId);

            // Save JSON to file
            java.io.File file = new java.io.File(pathOUT + folderName + ".json");
            objectMapper.writeValue(file, nameToId);
            log.info("Data stored in : " + pathOUT + "/" + folderName + ".json");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void fetchAllFiles(String driveId, HashMap<String, String> nameToId, AtomicInteger nFilesDriveFound)
            throws IOException, RefreshTokenException {
        // Implement fetching all files and folders here
        // Make sure to handle pagination if necessary
        // ...

        com.google.api.services.drive.Drive.Files.List request = service.files()
                .list()
                .setQ("'" + driveId
                        + "' in parents and trashed = false")
                .setPageSize(1000).setFields("nextPageToken, files(id, name,parents,mimeType)");

        String nextPageToken = "go";
        while (nextPageToken != null && nextPageToken.length() > 0) {
            try {
                FileList result = request.execute();
                for (File file : result.getFiles()) {
                    if (file.getMimeType().equals("application/vnd.google-apps.folder")) {
                        fetchAllFiles(file.getId(), nameToId, nFilesDriveFound);
                    } else {
                        boolean targetedFile = file.getName().endsWith(".jpg")
                                || file.getName().endsWith(".jpeg")
                                || file.getName().endsWith(".png")
                                || file.getName().endsWith(".PNG")
                                || file.getName().endsWith(".JPEG")
                                || file.getName().endsWith(".JPG");

                        if (targetedFile) {
                            nameToId.put(normalizeName(file.getName()), file.getId());
                            totalFile.getAndIncrement();
                            nFilesDriveFound.getAndIncrement();
                        }
                    }
                }

                nextPageToken = result.getNextPageToken();
                request.setPageToken(nextPageToken);

            } catch (TokenResponseException tokenError) {
                if (tokenError.getDetails().getError().equalsIgnoreCase("invalid_grant")) {
                    log.err("Token no more valid removing it Please retry");
                    java.io.File cred = new java.io.File("./tokens/StoredCredential");
                    if (cred.exists()) {
                        cred.delete();
                    }
                    log.err("Creds invalid will retry re allow for the token");
                    System.exit(1);
                }
                log.err("TOKEN Error while geting response with token for folder id : " + driveId, tokenError);
                nextPageToken = null;
            } catch (Exception e) {
                log.err("Error while reading folder id : " + driveId, e);
                nextPageToken = null;
            }

        }
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

    private static String normalizeName(String name) {
        return name.replace("/", "_").replace(":", "").replace("\\", "_").replace("\"", "").replace("\"", "").trim();
    }
}
