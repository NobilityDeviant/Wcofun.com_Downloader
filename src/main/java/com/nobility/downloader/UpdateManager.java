package com.nobility.downloader;

import com.nobility.downloader.utils.Tools;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.URL;
import java.security.SecureRandom;

public class UpdateManager {

    private final Model model;

    public UpdateManager(Model model) {
        this.model = model;
    }

    public static final int UPDATE_TIMEOUT = 7_000;
    private final String version = "1.4.1";
    private final String updateLink = "https://www.dropbox.com/s/40a90sjr2df0dna/TWCD.jar?dl=1";
    private final String versionLink = "https://www.dropbox.com/s/v1gk104nif8am3p/version.txt?dl=1";

    public final String getLatestVersion() {
        try {
            removeValidation();
            final URL url = new URL(versionLink);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setReadTimeout(UPDATE_TIMEOUT);
            con.setConnectTimeout(UPDATE_TIMEOUT);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String line = in.readLine();
            in.close();
            con.disconnect();
            return line.substring(0, line.indexOf(":"));
        } catch (Exception e) {
            return "1.0";
        }
    }

    public final boolean[] isLatestVersion() {
        try {
            removeValidation();
            final URL url = new URL(versionLink);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setReadTimeout(UPDATE_TIMEOUT);
            con.setConnectTimeout(UPDATE_TIMEOUT);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String line = in.readLine();
            in.close();
            con.disconnect();
            String latestVersion = line.substring(0, line.indexOf(":"));
            boolean required = Boolean.parseBoolean(line.substring(line.indexOf(":") + 1));
            return new boolean[] {version.equalsIgnoreCase(latestVersion), required};
        } catch (Exception e) {
            return new boolean[] {true, false};
        }
    }

    private void removeValidation() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager(){
            public java.security.cert.X509Certificate[] getAcceptedIssuers(){return null;}
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1) {
            }
            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1) {
            }
        }};
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ignored) {}
    }

    public void openUpdateLink(boolean required) {
        try {
            updateClientAndLaunch();
        } catch (Exception e) {
            if (required) {
                model.showError("Failed to download the required client.\n"
                        + updateLink + " \nhas been copied to your clipboard. " +
                        "Please manually download and update it yourself or try again.", e);
                StringSelection selection = new StringSelection(updateLink);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);
                System.exit(-1);
            } else {
                model.showError("Failed to download the client. The update link has been printed " +
                        "to the console. Please manually download and update it " +
                        "yourself or try again with Help > Check For Updates.", e);
                System.out.println("Update Link: " + updateLink);
            }
        }
    }

    private void updateClientAndLaunch() throws Exception {
        model.setClientUpdating(true);
        File downloadedClient = new File(System.getProperty("user.home") + "/TWCD.jar");
        if (downloadedClient.exists()) {
            downloadedClient.delete();
        }
        if (model.getUpdateProgressBar() != null) {
            model.getUpdateProgressBar().setProgress(0);
        }
        HttpsURLConnection con = (HttpsURLConnection) new URL(updateLink).openConnection();
        con.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br");
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9");
        con.addRequestProperty("Connection", "keep-alive");
        con.addRequestProperty("Sec-Fetch-Dest", "document");
        con.addRequestProperty("Sec-Fetch-Mode", "navigate");
        con.addRequestProperty("Sec-Fetch-Site", "cross-site");
        con.addRequestProperty("Sec-Fetch-User", "?1");
        con.addRequestProperty("Upgrade-Insecure-Requests",  "1");
        con.addRequestProperty("User-Agent", model.getRandomUserAgent());
        con.setConnectTimeout(30_000);
        con.setReadTimeout(30_000);
        InputStream in = con.getInputStream();
        long completeFileSize = 25500000;
        if (model.getUpdateFileProgress() != null) {
            model.getUpdateFileProgress().setText("0/" + Tools
                            .bytesToString(completeFileSize));
        }
        FileOutputStream fos = new FileOutputStream(downloadedClient, true);
        byte[] buffer = new byte[1024];
        int count;
        long total = 0L;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (model.getUpdateProgressBar() != null) {
                if (model.getUpdateFileProgress() != null) {
                    model.getUpdateFileProgress().setText(Tools.bytesToString(total) + "/" + Tools
                            .bytesToString(completeFileSize));
                }
                model.getUpdateProgressBar()
                        .setProgress(total / (double) completeFileSize);
            }
            fos.write(buffer, 0, count);
        }
        fos.close();
        con.disconnect();
        in.close();
        System.err.println("Client downloaded successfully! Size: " + Tools.bytesToString(completeFileSize)
                + " | Path: " + downloadedClient.getAbsolutePath());
        try {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(Model.DIALOG_PATH)));
            alert.getDialogPane().getStyleClass().add("dialog");
            stage.getIcons().add(new Image(String.valueOf(Main.class.getResource(Model.MAIN_ICON))));
            alert.setTitle("Download Complete!");
            alert.setHeaderText("Please excuse this tedious update process. (-_-)/");
            alert.setContentText("The new client has been downloaded. It can be found in your User folder. \n" +
                    "Please copy it into the main folder.\n" +
                    "Path: " + downloadedClient.getAbsolutePath() + "\n" +
                    "Close this window to shutdown and open the folder...");
            alert.showAndWait();
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.open(downloadedClient.getParentFile());
                } catch (IOException e) {
                    System.err.println("Failed to open folder. Message: " + e.getLocalizedMessage());
                }
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.open(downloadedClient.getParentFile());
                } catch (IOException e1) {
                    System.err.println("Failed to open folder. Message: " + e1.getLocalizedMessage());
                }
            }
            System.exit(0);
        }
        /*try (InputStream in = new URL(updateLink).openStream()) {
            long size = Files.copy(in, downloadedClient.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.err.println("Client downloaded successfully! Size: " + size + " Path: " + downloadedClient.getAbsolutePath());
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(String.valueOf(Main.class.getResource("icon.png"))));
            alert.setTitle("Download Complete!");
            alert.setHeaderText("");
            alert.setContentText("The new client has been downloaded. It can be found in your User folder. \n" +
                    "Please copy it into the main folder.\n" +
                    "Path: " + downloadedClient.getAbsolutePath() + "\n" +
                    "Close this window to shutdown...");
            alert.showAndWait();
            new Thread(() -> {
                if (Desktop.isDesktopSupported()){
                    Desktop desktop = Desktop.getDesktop();
                    try {
                        desktop.open(downloadedClient.getParentFile());
                    } catch (IOException e) {
                        System.err.println("Failed to open folder. Message: " + e.getLocalizedMessage());
                    }
                }
                System.exit(0);
            }).start();
        } catch (Exception e) {
            model.showError("Failed to download the update. Contact Nobility for a link or try again.\n"
                    + "Close this window to shutdown...", e);
            System.exit(-1);
        }*/
    }

    public String getVersion() {
        return version;
    }
}
