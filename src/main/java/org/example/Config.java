package org.example;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private final String email;
    private final String password;
    private final String resumePdfUrl;
    private final String resumeFileName;
    private final int runEverySecs;
    private final boolean headless;
    private final String gmailAddress;
    private final String gmailAppPassword;

    public Config() {
        Properties props = new Properties();
        File configFile = new File("config.properties");

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                throw new IllegalStateException("Could not load config.properties: " + e.getMessage());
            }
        }

        this.email = readRequiredValue("naukri.user.email", "NAUKRI_EMAIL", props);
        this.password = readRequiredValue("naukri.user.password", "NAUKRI_PASSWORD", props);
        this.resumePdfUrl = readRequiredValue("resume.pdf.url", "RESUME_PDF_URL", props);
        this.resumeFileName = readRequiredValue("resume.file.name", "RESUME_FILE_NAME", props);

        String runEverySecsStr = props.getProperty("run.every.secs", System.getenv().getOrDefault("RUN_EVERY_SECS", "10"));
        this.runEverySecs = Integer.parseInt(runEverySecsStr);

        String headlessValue = props.getProperty("browser.headless", System.getenv().getOrDefault("BROWSER_HEADLESS", "true"));
        this.headless = Boolean.parseBoolean(headlessValue);

        String gmail = readOptionalValue("gmail.address", "GMAIL_ADDRESS", props);
        this.gmailAddress = (gmail == null || gmail.trim().isEmpty()) ? this.email : gmail.trim();
        this.gmailAppPassword = readOptionalValue("gmail.app.password", "GMAIL_APP_PASSWORD", props);
    }

    private String readRequiredValue(String configKey, String envKey, Properties props) {
        String value = readOptionalValue(configKey, envKey, props);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required configuration is missing. Set either config.properties or the environment variable " + envKey + " for " + configKey);
        }
        return value;
    }

    private String readOptionalValue(String configKey, String envKey, Properties props) {
        String value = System.getenv(envKey);
        if (value == null || value.trim().isEmpty()) {
            value = props.getProperty(configKey);
        }
        return value;
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

    public boolean isHeadless() {
        return headless;
    }

    public String getGmailAddress() {
        return gmailAddress;
    }

    public String getGmailAppPassword() {
        return gmailAppPassword;
    }

    public boolean hasGmailOtpConfig() {
        return gmailAppPassword != null && !gmailAppPassword.trim().isEmpty();
    }
}
