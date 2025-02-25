package com.maths22.ftc;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SheetRetriever {
    private static final Logger LOG = LoggerFactory.getLogger(SheetRetriever.class);
    /** Application name. */
    private static final String APPLICATION_NAME =
            "FTC Scoring Display Manager";

    /** Directory to store user credentials for this application. */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.home"), ".credentials/sheets.googleapis.com-maths22-ftc-displaymanager");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/sheets.googleapis.com-java-quickstart
     */
    private static final List<String> SCOPES =
            Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            LOG.error("Google initialization failed", t);
            System.exit(1);
        }
    }

    private final VerificationCodeReceiver verificationCodeReceiver;

    public SheetRetriever(VerificationCodeReceiver verificationCodeReceiver) {
        this.verificationCodeReceiver = verificationCodeReceiver;
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    public Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in =
                SheetRetriever.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(
                flow, verificationCodeReceiver).authorize("user");
        LOG.info("Credentials saved to {}", DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Sheets API client service.
     * @return an authorized Sheets API client service
     * @throws IOException
     */
    public Sheets getSheetsService() throws IOException {
        Credential credential = authorize();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public Result getTeamInfo(String spreadsheetId, String worksheetName, String keyColumn) throws IOException {
        // Build a new authorized API client service.
        Sheets service = getSheetsService();

        // Prints the names and majors of students in a sample spreadsheet:
        // https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
        String range = "'" + worksheetName + "'!A2:YY";
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        if(response.getValues() == null) {
            return null;
        }
        List<List<String>> values = response.getValues().stream().map((row) -> row.stream().map(Object::toString).toList()).toList();
        String titleRange = "'" + worksheetName + "'!A1:YY1";
        ValueRange titleResponse = service.spreadsheets().values()
                .get(spreadsheetId, titleRange)
                .execute();
        List<String> titleValues = titleResponse.getValues().get(0).stream().map(Object::toString).toList();
        if (values.isEmpty()) {
            return null;
        } else {
            return new Result(titleValues, values.stream().map((row) -> zipToMap(titleValues, row))
                    .collect(Collectors.toMap(
                            (e) -> Integer.parseInt(e.get(keyColumn)),
                            (e) -> e)));
        }
    }

    public static <K, V> Map<K, V> zipToMap(List<K> keys, List<V> values) {
        Iterator<K> keyIter = keys.iterator();
        Iterator<V> valIter = values.iterator();
        return IntStream.range(0, Math.min(keys.size(), values.size())).boxed()
                .collect(Collectors.toMap(_i -> keyIter.next(), _i -> valIter.next()));
    }

    public String pickWorksheet(String spreadsheetId) throws IOException {
        Sheets service = getSheetsService();
        Spreadsheet sheet = service.spreadsheets().get(spreadsheetId).execute();
        sheet.getSheets().stream().map(s -> s.getProperties().getTitle());
        return (String) JOptionPane.showInputDialog(null, "Pick a worksheet", "Pick a worksheet", JOptionPane.QUESTION_MESSAGE, null, sheet.getSheets().stream().map(s -> s.getProperties().getTitle()).toArray(), null);
    }

    public String pickKeyColumn(String spreadsheetId, String worksheetName) throws IOException {
        // Build a new authorized API client service.
        Sheets service = getSheetsService();

        String titleRange = "'" + worksheetName + "'!A1:YY1";
        ValueRange titleResponse = service.spreadsheets().values()
                .get(spreadsheetId, titleRange)
                .execute();
        List<String> titleValues = titleResponse.getValues().get(0).stream().map(Object::toString).toList();
        return (String) JOptionPane.showInputDialog(null, "Pick a key column", "Pick a key column", JOptionPane.QUESTION_MESSAGE, null, titleValues.toArray(), null);
    }

    public record Result(List<String> titles, Map<Integer, Map<String, String>> entries) {
    }
}
