import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiTest2 {
    public static void main(String[] args) throws Exception {
        // First, get the project to find the latest version ID
        String projectUrl = "https://api.modrinth.com/v2/project/advanceddeliverydrones";
        
        HttpURLConnection connection = (HttpURLConnection) new URL(projectUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AdvancedDeliveryDrones");

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        String json = response.toString();
        
        // Extract the latest version ID
        Pattern versionIdPattern = Pattern.compile("\"versions\":\\[(\"[^\"]+\")");
        Matcher versionIdMatcher = versionIdPattern.matcher(json);
        
        String latestVersionId = null;
        if (versionIdMatcher.find()) {
            latestVersionId = versionIdMatcher.group(1).replace("\"", "");
            System.out.println("Latest Version ID: " + latestVersionId);
        } else {
            System.out.println("Could not find version ID");
            return;
        }

        // Now get the actual version details
        String versionUrl = "https://api.modrinth.com/v2/version/" + latestVersionId;
        connection = (HttpURLConnection) new URL(versionUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AdvancedDeliveryDrones");

        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        response = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        json = response.toString();
        System.out.println("Version JSON (first 1000 chars):");
        System.out.println(json.substring(0, Math.min(1000, json.length())));
        System.out.println("\n================\n");

        // Extract version number
        Pattern versionPattern = Pattern.compile("\"version_number\":\"([^\"]+)\"");
        Matcher versionMatcher = versionPattern.matcher(json);
        
        if (versionMatcher.find()) {
            String version = versionMatcher.group(1);
            System.out.println("Actual Version Number: " + version);
        } else {
            System.out.println("Could not find version_number");
        }
    }
}
