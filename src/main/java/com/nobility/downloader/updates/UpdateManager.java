package com.nobility.downloader.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nobility.downloader.Model;
import com.nobility.downloader.settings.Defaults;
import javafx.application.Platform;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class UpdateManager {

    private final Model model;

    public UpdateManager(Model model) {
        this.model = model;
    }

    public static final String CURRENT_VERSION = "1.4.9";
    public static final String RELEASES_LINK = "https://github.com/NobilityDeviant/Wcofun.com_Downloader/releases";
    protected final String githubLatest = "https://api.github.com/repos/NobilityDeviant/Wcofun.com_Downloader/releases/latest";
    public Update latestUpdate = null;

    private JsonObject apiSheet() {
        try {
            removeValidation();
            HttpsURLConnection urlConnection = (HttpsURLConnection) new URL(githubLatest).openConnection();
            urlConnection.setReadTimeout(20_000);
            urlConnection.setConnectTimeout(20_000);
            urlConnection.setInstanceFollowRedirects(true);
            urlConnection.setRequestProperty("user-agent", model.getRandomUserAgent());
            urlConnection.connect();
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                stringBuilder.append(inputLine).append("\n");
            }
            in.close();
            urlConnection.disconnect();
            //return new JsonParser().parse(stringBuilder.toString()).getAsJsonObject();
            return JsonParser.parseString(stringBuilder.toString()).getAsJsonObject();
        } catch (Exception e) {
            System.out.println("Failed to get github api response. Error: " + e.getLocalizedMessage());
        }
        return null;
    }

    public Thread saveUpdateDetails() {
        return new Thread(() -> latestUpdate = latestUpdate());
    }

    public Update latestUpdate() {
        JsonObject json = apiSheet();
        if (json != null) {
            String version = json.get("tag_name").getAsString();
            String body = json.get("body").getAsString();
            JsonArray array = json.getAsJsonArray("assets");
            if (array != null) {
                for (JsonElement element : array) {
                    if (element.isJsonObject()) {
                        JsonObject o = element.getAsJsonObject();
                        if (o.has("browser_download_url")) {
                            String url = o.get("browser_download_url").getAsString();
                            if (url.endsWith(".jar")) {
                                return new Update(version, url, body);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isLatest(String latest) {
        if (latest == null || latest.equals(CURRENT_VERSION)) {
            return true;
        }
        try {
            String[] latestSplit = latest.split("\\.");
            String[] current = CURRENT_VERSION.split("\\.");
            if (Integer.parseInt(latestSplit[0]) > Integer.parseInt(current[0])) {
                return false;
            }
            if (Integer.parseInt(latestSplit[1]) > Integer.parseInt(current[1])) {
                return false;
            }
            if (Integer.parseInt(latestSplit[2]) > Integer.parseInt(current[2])) {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
        return false;
    }

    public void checkForUpdates(boolean prompt, boolean refresh) {
        new Thread(() -> {
            if (latestUpdate == null || refresh) {
                Thread thread = saveUpdateDetails();
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    System.out.println("Failed to check for updates. Error: " + e.getLocalizedMessage());
                    return;
                }
            }
            if (latestUpdate == null) {
                System.out.println("Failed to find latest update details.");
                return;
            }
            if (!model.settings().getString(Defaults.UPDATEVERSION).equalsIgnoreCase(latestUpdate.getVersion())) {
                model.settings().setBoolean(Defaults.DENIEDUPDATE, false);
                model.saveSettings();
            }

            if (!model.settings().getBoolean(Defaults.DENIEDUPDATE)) {
                model.settings().setString(Defaults.UPDATEVERSION, latestUpdate.getVersion());
                model.saveSettings();
                boolean latest = isLatest(latestUpdate.getVersion());
                if (latest && !prompt) {
                    return;
                }
                Platform.runLater(() ->
                        model.showUpdateConfirm((latest ? "Updated" : "Update Available") + " - ver. "
                                + latestUpdate.getVersion(), false, latest));
            }
        }).start();
    }

    private void removeValidation() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return null;}
            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
            }
            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
            }
        }};
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ignored) {}
    }
}
