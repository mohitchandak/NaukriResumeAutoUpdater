package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

public class Main {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** e.g. 8_Sep, 6_Oct (day + short month, IST) */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d_MMM", Locale.ENGLISH);
    private static final Path COOKIE_FILE = Paths.get("naukri-cookies.json");

    public static void main(String[] args) {
        boolean exportCookiesOnly = Arrays.asList(args).contains("--export-cookies");
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
                GmailOtpReader otpReader = null;
                if (config.hasGmailOtpConfig() && !config.hasSessionCookies()) {
                    otpReader = new GmailOtpReader(config.getGmailAddress(), config.getGmailAppPassword());
                }
                naukri = new NaukriAutomation(config.isHeadless(), otpReader);

                if (config.hasSessionCookies()) {
                    System.out.println("Using saved Naukri session cookies (skipping interactive login)");
                    naukri.loginWithCookies(config.getSessionCookies());
                } else {
                    System.out.println("Using email + password login, then saving cookies for Actions");
                    naukri.login(config.getEmail(), config.getPassword(), false);
                    naukri.exportCookies(COOKIE_FILE);
                    System.out.println("Cookies saved to " + COOKIE_FILE.toAbsolutePath());
                    System.out.println("Upload to GitHub with:");
                    System.out.println("  gh secret set NAUKRI_COOKIES < naukri-cookies.json");
                }

                if (!exportCookiesOnly) {
                    naukri.uploadResume(resumePath);
                } else {
                    System.out.println("--export-cookies: skipping resume upload");
                }
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

    static String withTodayDate(String fileName) {
        String today = LocalDate.now(IST).format(DATE_FORMAT);
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot) + "_" + today + fileName.substring(dot);
        }
        return fileName + "_" + today + ".pdf";
    }
}
