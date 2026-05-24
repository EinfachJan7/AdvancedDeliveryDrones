import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiTest {
    public static void main(String[] args) throws Exception {
        String apiUrl = "https://api.modrinth.com/v2/project/advanceddeliverydrones";
        
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AdvancedDeliveryDrones");

        if (connection.getResponseCode() != 200) {
            System.out.println("Error: " + connection.getResponseCode());
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        String json = response.toString();
        System.out.println("Full JSON response (first 2000 chars):");
        System.out.println(json.substring(0, Math.min(2000, json.length())));
        System.out.println("\n================\n");

        // Try different patterns
        System.out.println("Testing patterns:");
        
        Pattern p1 = Pattern.compile("\"version\":\"([^\"]+)\"");
        Matcher m1 = p1.matcher(json);
        if (m1.find()) {
            System.out.println("Pattern 1 (\"version\":\"X\"): " + m1.group(1));
        } else {
            System.out.println("Pattern 1 not found");
        }

        Pattern p2 = Pattern.compile("\"name\":\"([^\"]+)\"");
        Matcher m2 = p2.matcher(json);
        if (m2.find()) {
            System.out.println("Project Name: " + m2.group(1));
        }

        // Look for all version strings
        Pattern p3 = Pattern.compile("\"versions\":\\[(.*?)\\]");
        Matcher m3 = p3.matcher(json);
        if (m3.find()) {
            String versions = m3.group(1);
            System.out.println("Versions array (first 500 chars): " + versions.substring(0, Math.min(500, versions.length())));
        }
    }
}
