package org.example;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;

public class ResumeDownloader {
    public static String downloadResume(String url, String fileName) throws IOException {
        String filePath = Paths.get(System.getProperty("user.dir"), fileName).toString();

        // Create an HttpClient instance with redirect following enabled
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        // Build the HTTP GET request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            // Send the request and get the response
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            // Check the response status and content type
            if (response.statusCode() == 200) {
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                // Accept both application/pdf and application/octet-stream
                if (contentType.contains("application/pdf") || contentType.contains("application/octet-stream")) {
                    // Write the content to the file
                    try (FileOutputStream fos = new FileOutputStream(filePath)) {
                        fos.write(response.body());
                    }
                    System.out.println("Resume downloaded successfully to " + filePath);
                } else {
                    throw new IOException("Unexpected content type: " + contentType + ". Expected application/pdf or application/octet-stream.");
                }
            } else {
                throw new IOException("Failed to download resume: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + e.getMessage());
        }

        return filePath;
    }
}