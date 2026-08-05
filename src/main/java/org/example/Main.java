package org.example;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Main {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            Config config = new Config();
            String datedFileName = withTodayDate(config.getResumeFileName());
            System.out.println("Using resume file name: " + datedFileName);
            String resumePath = ResumeDownloader.resolveResume(
                    config.getResumePdfUrl(),
                    config.getResumeFileName(),
                    datedFileName
            );

            NaukriAutomation naukri = null;
            try {
                naukri = new NaukriAutomation(config.isHeadless());
                naukri.login(config.getEmail(), config.getPassword());
                naukri.uploadResume(resumePath);
            } catch (Exception e) {
                e.printStackTrace();
                exitCode = 1;
            } finally {
                if (naukri != null) {
                    naukri.quit();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to download resume: " + e.getMessage());
            exitCode = 1;
        } catch (IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
            exitCode = 1;
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** Mohit_Chandak_SDET.pdf → Mohit_Chandak_SDET_2026-08-05.pdf (IST date) */
    static String withTodayDate(String fileName) {
        String today = LocalDate.now(IST).format(DATE_FORMAT);
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot) + "_" + today + fileName.substring(dot);
        }
        return fileName + "_" + today + ".pdf";
    }
}
