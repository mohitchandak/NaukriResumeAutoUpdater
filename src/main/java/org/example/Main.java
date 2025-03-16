package org.example;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        try {
            Config config = new Config();
            String resumePath = ResumeDownloader.downloadResume(config.getResumePdfUrl(), config.getResumeFileName());

            // Define the task to be run
            Runnable task = () -> {
                NaukriAutomation naukri = null;
                try {
                    naukri = new NaukriAutomation();
                    System.out.println("Running update at " + LocalDateTime.now());
                    naukri.login(config.getEmail(), config.getPassword());
                    naukri.uploadResume(resumePath);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (naukri != null) {
                        naukri.quit();
                    }
                }
            };

            // Run the task immediately
            System.out.println("Starting immediate run at " + LocalDateTime.now());
            task.run();

            // Schedule subsequent runs on the hour
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            long initialDelaySeconds = ChronoUnit.SECONDS.between(now, nextHour);
            long periodSeconds = 3600;

            System.out.println("Scheduling hourly runs starting at " + nextHour + " (in " + initialDelaySeconds + " seconds)");

            scheduler.scheduleAtFixedRate(task, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);

            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                scheduler.shutdown();
                System.out.println("Scheduler interrupted and shut down");
            }
        } catch (IOException e) {
            System.err.println("Failed to download resume: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
        }
    }
}
