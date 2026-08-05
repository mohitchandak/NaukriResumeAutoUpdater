package org.example;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public class ResumeDownloader {
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Prefer a bundled local PDF (resume/&lt;baseName&gt; or ./&lt;baseName&gt;), otherwise download from URL.
     * Copies/downloads into datedFileName and returns that path.
     */
    public static String resolveResume(String url, String baseFileName, String datedFileName) throws IOException {
        Path datedPath = Paths.get(System.getProperty("user.dir"), datedFileName);

        Path[] localCandidates = {
                Paths.get(System.getProperty("user.dir"), "resume", baseFileName),
                Paths.get(System.getProperty("user.dir"), baseFileName)
        };

        for (Path candidate : localCandidates) {
            if (Files.isRegularFile(candidate)) {
                Files.copy(candidate, datedPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Using local resume: " + candidate + " -> " + datedPath);
                return datedPath.toString();
            }
        }

        if (url == null || url.trim().isEmpty()) {
            throw new IOException("No local resume found under resume/ or project root, and RESUME_PDF_URL is empty");
        }

        return downloadResume(url, datedFileName);
    }

    public static String downloadResume(String url, String fileName) throws IOException {
        String filePath = Paths.get(System.getProperty("user.dir"), fileName).toString();

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() != 200) {
                    throw new IOException("Failed to download resume: HTTP " + response.statusCode());
                }

                String contentType = response.headers().firstValue("Content-Type").orElse("");
                byte[] body = response.body();

                // Google Drive sometimes returns an HTML interstitial instead of the PDF
                if (contentType.contains("text/html") || looksLikeHtml(body)) {
                    throw new IOException("Download returned HTML instead of PDF (Drive may be blocking this network). Prefer bundling resume/ in the repo.");
                }

                if (!(contentType.contains("application/pdf")
                        || contentType.contains("application/octet-stream")
                        || contentType.isEmpty()
                        || looksLikePdf(body))) {
                    throw new IOException("Unexpected content type: " + contentType + ". Expected application/pdf or application/octet-stream.");
                }

                if (!looksLikePdf(body)) {
                    throw new IOException("Downloaded file does not look like a PDF");
                }

                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    fos.write(body);
                }
                System.out.println("Resume downloaded successfully to " + filePath + " (attempt " + attempt + ")");
                return filePath;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted: " + e.getMessage());
            } catch (IOException e) {
                lastError = e;
                System.err.println("Download attempt " + attempt + "/" + MAX_ATTEMPTS + " failed: " + e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted during retry", ie);
                    }
                }
            }
        }

        throw new IOException("Failed to download resume after " + MAX_ATTEMPTS + " attempts: " + lastError.getMessage(), lastError);
    }

    private static boolean looksLikePdf(byte[] body) {
        return body != null && body.length >= 4
                && body[0] == '%' && body[1] == 'P' && body[2] == 'D' && body[3] == 'F';
    }

    private static boolean looksLikeHtml(byte[] body) {
        if (body == null || body.length < 15) {
            return false;
        }
        String start = new String(body, 0, Math.min(body.length, 64)).trim().toLowerCase();
        return start.startsWith("<!DOCTYPE") || start.startsWith("<html");
    }
}
