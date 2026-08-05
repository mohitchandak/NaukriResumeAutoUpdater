package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class NaukriAutomation {
    private static final Logger logger = Logger.getLogger(NaukriAutomation.class.getName());
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WebDriver driver;

    public NaukriAutomation(boolean headless) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
            options.addArguments("--disable-dev-shm-usage");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=" + USER_AGENT);
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);

        driver = new ChromeDriver(options);
        ((JavascriptExecutor) driver).executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        );
    }

    public void login(String email, String password) {
        try {
            driver.get("https://www.naukri.com/nlogin/login");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(45));

            dismissCommonOverlays();

            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField")));
            emailField.clear();
            emailField.sendKeys(email);

            WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("passwordField")));
            passwordField.clear();
            passwordField.sendKeys(password);

            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@type='submit']")));
            loginButton.click();
            logger.info("Logged in successfully");
        } catch (Exception e) {
            logger.severe("Login failed: " + e.getMessage());
            logger.severe("Current URL: " + safeCurrentUrl());
            logger.severe("Page title: " + safeTitle());
            saveDebugArtifacts("login-failure");
            throw e;
        }
    }

    public void uploadResume(String resumePath) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        try {
            String completeProfileXPath = "//div[@class='view-profile-wrapper']/a";
            WebElement completeProfileButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(completeProfileXPath)));
            completeProfileButton.click();
            logger.info("Clicked the 'Complete Profile' button successfully");

            String uploadXPath = "//div[@class='uploadContainer']//input[@type='file' and @id='attachCV']";
            WebElement uploadButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(uploadXPath)));
            Thread.sleep(2000);
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("arguments[0].scrollIntoView(true);", uploadButton);
            Thread.sleep(2000);
            uploadButton.sendKeys(resumePath);
            Thread.sleep(2000);
            logger.info("Resume uploaded successfully");

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[contains(@class, 'updateOn') and contains(text(), 'Uploaded')]")
            ));
            logger.info("Upload confirmed on page");
        } catch (Exception e) {
            logger.severe("Resume upload failed: " + e.getMessage());
            saveDebugArtifacts("upload-failure");
            throw e;
        }
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            logger.info("WebDriver closed");
        }
    }

    private void dismissCommonOverlays() {
        try {
            List<WebElement> buttons = driver.findElements(By.xpath(
                    "//button[contains(.,'Accept') or contains(.,'Got it') or contains(.,'OK') or contains(.,'Close')]"
            ));
            for (WebElement button : buttons) {
                if (button.isDisplayed()) {
                    button.click();
                }
            }
        } catch (Exception ignored) {
            // Best-effort only
        }
    }

    private void saveDebugArtifacts(String prefix) {
        try {
            Path dir = Paths.get("debug");
            Files.createDirectories(dir);
            if (driver instanceof TakesScreenshot) {
                byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Files.write(dir.resolve(prefix + ".png"), png);
            }
            Files.writeString(dir.resolve(prefix + ".html"), driver.getPageSource());
            logger.info("Saved debug artifacts under " + dir.toAbsolutePath());
        } catch (IOException e) {
            logger.warning("Could not save debug artifacts: " + e.getMessage());
        }
    }

    private String safeCurrentUrl() {
        try {
            return driver.getCurrentUrl();
        } catch (Exception e) {
            return "<unavailable>";
        }
    }

    private String safeTitle() {
        try {
            return driver.getTitle();
        } catch (Exception e) {
            return "<unavailable>";
        }
    }
}
