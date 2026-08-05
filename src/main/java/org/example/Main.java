package org.example;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            Config config = new Config();
            String resumePath = ResumeDownloader.downloadResume(config.getResumePdfUrl(), config.getResumeFileName());

            NaukriAutomation naukri = null;
            try {
                naukri = new NaukriAutomation(config.isHeadless());
                naukri.login(config.getEmail(), config.getPassword());
                naukri.uploadResume(resumePath);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (naukri != null) {
                    naukri.quit();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to download resume: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
        }
    }
}
