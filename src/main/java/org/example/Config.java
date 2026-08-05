package org.example;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final String sessionCookies;

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

        this.email = readOptionalValue("naukri.user.email", "NAUKRI_EMAIL", props);
        this.password = readOptionalValue("naukri.user.password", "NAUKRI_PASSWORD", props);
        this.resumePdfUrl = readRequiredValue("resume.pdf.url", "RESUME_PDF_URL", props);
        this.resumeFileName = readRequiredValue("resume.file.name", "RESUME_FILE_NAME", props);

        String runEverySecsStr = props.getProperty("run.every.secs", System.getenv().getOrDefault("RUN_EVERY_SECS", "10"));
        this.runEverySecs = Integer.parseInt(runEverySecsStr);

        String headlessValue = props.getProperty("browser.headless", System.getenv().getOrDefault("BROWSER_HEADLESS", "true"));
        this.headless = Boolean.parseBoolean(headlessValue);

        String gmail = readOptionalValue("gmail.address", "GMAIL_ADDRESS", props);
        this.gmailAddress = (gmail == null || gmail.trim().isEmpty()) ? (email == null ? "" : email.trim()) : gmail.trim();
        this.gmailAppPassword = readOptionalValue("gmail.app.password", "GMAIL_APP_PASSWORD", props);
        this.sessionCookies = loadSessionCookies(props);

        if (!hasSessionCookies()) {
            if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Set NAUKRI_COOKIES (recommended for GitHub Actions) or naukri.user.email + naukri.user.password for local login"
                );
            }
        }
    }

    private String loadSessionCookies(Properties props) {
        String fromEnv = System.getenv("NAUKRI_COOKIES");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        String fromProps = props.getProperty("naukri.cookies");
        if (fromProps != null && !fromProps.trim().isEmpty()) {
            return fromProps.trim();
        }
        Path file = Paths.get("naukri-cookies.json");
        if (Files.isRegularFile(file)) {
            try {
                return Files.readString(file).trim();
            } catch (IOException e) {
                throw new IllegalStateException("Could not read naukri-cookies.json: " + e.getMessage());
            }
        }
        return null;
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

    public String getSessionCookies() {
        return sessionCookies;
    }

    public boolean hasSessionCookies() {
        return sessionCookies != null && !sessionCookies.trim().isEmpty();
    }
}
