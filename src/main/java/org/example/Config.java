package org.example;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private final String email;
    private final String password;
    private final String resumePdfUrl;
    private final String resumeFileName;
    private final int runEverySecs;

    public Config() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load config.properties: " + e.getMessage());
        }

        this.email = props.getProperty("naukri.user.email");
        this.password = props.getProperty("naukri.user.password");
        this.resumePdfUrl = props.getProperty("resume.pdf.url");
        this.resumeFileName = props.getProperty("resume.file.name");
        String runEverySecsStr = props.getProperty("run.every.secs", "10"); // Default to 10 if not set
        this.runEverySecs = Integer.parseInt(runEverySecsStr);

        if (email == null || password == null || resumePdfUrl == null || resumeFileName == null) {
            throw new IllegalStateException("Required properties are missing in config.properties: naukri.user.email, naukri.user.password, resume.pdf.url, resume.file.name");
        }
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getResumePdfUrl() {
        return resumePdfUrl;
    }

    public String getResumeFileName() {
        return resumeFileName;
    }

    public int getRunEverySecs() {
        return runEverySecs;
    }
}