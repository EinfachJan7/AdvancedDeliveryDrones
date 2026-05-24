package de.cb.drones.update;

import de.cb.drones.config.LanguageManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionChecker {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/advanceddeliverydrones/version?include_changelog=false";

    private final JavaPlugin plugin;
    private final String currentVersion;
    private final LanguageManager languageManager;

    public VersionChecker(JavaPlugin plugin, String currentVersion, LanguageManager languageManager) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.languageManager = languageManager;
    }

    public String fetchLatestVersionPublic() throws Exception {
        return fetchLatestVersion();
    }

    private String fetchLatestVersion() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODRINTH_API).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AdvancedDeliveryDrones");

        if (connection.getResponseCode() != 200) {
            throw new Exception("API returned status code: " + connection.getResponseCode());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        // Extract the first version_number from the array (latest version)
        // Response format: [{"version_number":"1.0.5",...}, {"version_number":"1.0.4",...}, ...]
        Pattern versionPattern = Pattern.compile("\"version_number\":\"([^\"]+)\"");
        Matcher versionMatcher = versionPattern.matcher(response.toString());

        if (versionMatcher.find()) {
            return versionMatcher.group(1);
        }

        return null;
    }
}
