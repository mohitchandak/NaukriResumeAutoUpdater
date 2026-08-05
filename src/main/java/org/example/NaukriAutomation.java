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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class NaukriAutomation {
    private static final Logger logger = Logger.getLogger(NaukriAutomation.class.getName());
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String PROFILE_URL = "https://www.naukri.com/mnjuser/profile";

    private final WebDriver driver;
    private final GmailOtpReader otpReader;

    public NaukriAutomation(boolean headless, GmailOtpReader otpReader) {
        this.otpReader = otpReader;
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

    /**
     * @param preferOtpLogin when true (Gmail configured / CI), uses "Use OTP to Login".
     *                       when false, uses email + password Login button.
     */
    public void login(String email, String password, boolean preferOtpLogin) {
        try {
            driver.get("https://www.naukri.com/nlogin/login");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(45));

            dismissCommonOverlays();

            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField")));
            emailField.clear();
            emailField.sendKeys(email);

            Instant otpNotBefore = Instant.now();
            if (preferOtpLogin) {
                loginWithOtpButton(wait, email, otpNotBefore);
            } else {
                loginWithPassword(wait, password, otpNotBefore);
            }

            waitForLoggedInState(wait);
            logger.info("Logged in successfully");
        } catch (Exception e) {
            logger.severe("Login failed: " + e.getMessage());
            logger.severe("Current URL: " + safeCurrentUrl());
            logger.severe("Page title: " + safeTitle());
            saveDebugArtifacts("login-failure");
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    private void loginWithPassword(WebDriverWait wait, String password, Instant otpNotBefore) throws Exception {
        WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("passwordField")));
        passwordField.clear();
        passwordField.sendKeys(password);

        WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[@type='submit' and not(contains(@class,'otpButton'))]")
        ));
        loginButton.click();
        logger.info("Submitted email + password login");
        handleMfaOtpIfPresent(otpNotBefore);
    }

    /** Prefer email OTP: may land on mobile OTP first, then switch to email. */
    private void loginWithOtpButton(WebDriverWait wait, String email, Instant otpNotBefore) throws Exception {
        if (otpReader == null) {
            throw new IllegalStateException("OTP login requires GMAIL_APP_PASSWORD");
        }

        WebElement otpLoginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.otpButton")));
        otpLoginButton.click();
        logger.info("Clicked 'Use OTP to Login'");

        // Naukri often shows mobile OTP first — switch to email OTP
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement useEmail = shortWait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(.,'Use Email to Login') or contains(@class,'email-btn')]")
            ));
            useEmail.click();
            logger.info("Clicked 'Use Email to Login'");
        } catch (Exception e) {
            logger.info("No mobile-OTP intermediate screen (or already on email OTP)");
        }

        // On email OTP screen: fill email and click Get OTP if needed
        if (driver.findElements(By.id("Input_1")).isEmpty()
                || driver.findElements(By.id("Input_1")).stream().noneMatch(WebElement::isDisplayed)) {
            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField")));
            emailField.clear();
            emailField.sendKeys(email);

            WebElement getOtp = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(.,'Get OTP')]")
            ));
            getOtp.click();
            logger.info("Clicked Get OTP for email");
        }

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("Input_1")));
        enterOtpFromGmail(otpNotBefore);
    }

    private void handleMfaOtpIfPresent(Instant otpNotBefore) throws Exception {
        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(12));
        try {
            shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.id("Input_1")));
        } catch (Exception e) {
            logger.info("No extra OTP after password login");
            return;
        }
        logger.info("Naukri showed verification OTP after password login");
        enterOtpFromGmail(otpNotBefore);
    }

    private void enterOtpFromGmail(Instant otpNotBefore) throws Exception {
        if (otpReader == null) {
            throw new IllegalStateException(
                    "Naukri asked for email OTP. Set GMAIL_APP_PASSWORD to auto-read it."
            );
        }

        String otp = otpReader.waitForOtp(otpNotBefore);
        logger.info("Fetched OTP from Gmail");

        for (int i = 0; i < Math.min(6, otp.length()); i++) {
            WebElement box = driver.findElement(By.id("Input_" + (i + 1)));
            box.clear();
            box.sendKeys(String.valueOf(otp.charAt(i)));
        }

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        List<WebElement> verifyButtons = driver.findElements(By.cssSelector("button.verify-button"));
        if (!verifyButtons.isEmpty()) {
            wait.until(ExpectedConditions.elementToBeClickable(verifyButtons.get(0)));
            verifyButtons.get(0).click();
        } else {
            wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(.,'Verify')]")
            )).click();
        }
        logger.info("Submitted OTP from Gmail");
    }

    public void uploadResume(String resumePath) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        try {
            if (!openProfilePage(wait)) {
                throw new IllegalStateException("Could not open Naukri profile page after login");
            }

            String uploadXPath = "//input[@type='file' and (@id='attachCV' or contains(@id,'attachCV'))]";
            WebElement uploadButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(uploadXPath)));
            Thread.sleep(2000);
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("arguments[0].scrollIntoView(true);", uploadButton);
            Thread.sleep(2000);
            uploadButton.sendKeys(resumePath);
            Thread.sleep(2000);
            logger.info("Resume uploaded successfully");

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(@class, 'updateOn') and contains(., 'Uploaded')] | //*[contains(text(),'Uploaded')]")
            ));
            logger.info("Upload confirmed on page");
        } catch (Exception e) {
            logger.severe("Resume upload failed: " + e.getMessage());
            saveDebugArtifacts("upload-failure");
            throw e;
        }
    }

    public void loginWithCookies(String cookiesJsonOrBase64) {
        try {
            SessionCookies.applyFromJson(driver, cookiesJsonOrBase64);
            driver.get(PROFILE_URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            // If redirected to login, cookies are expired/invalid
            wait.until(d -> {
                String url = d.getCurrentUrl();
                return url != null && !url.contains("/nlogin/");
            });
            if (!driver.findElements(By.id("usernameField")).isEmpty()
                    && driver.findElement(By.id("usernameField")).isDisplayed()) {
                throw new IllegalStateException("Cookie session is not logged in (login form visible)");
            }
            logger.info("Logged in using saved session cookies");
        } catch (Exception e) {
            logger.severe("Cookie login failed: " + e.getMessage());
            logger.severe("Current URL: " + safeCurrentUrl());
            saveDebugArtifacts("cookie-login-failure");
            throw new IllegalStateException(
                    "NAUKRI_COOKIES expired or invalid. Re-export cookies locally and update the GitHub secret.", e
            );
        }
    }

    public void exportCookies(Path path) throws IOException {
        SessionCookies.saveToFile(driver, path);
        logger.info("Saved session cookies to " + path.toAbsolutePath());
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            logger.info("WebDriver closed");
        }
    }

    public WebDriver getDriver() {
        return driver;
    }

    private void waitForLoggedInState(WebDriverWait wait) {
        wait.until(driver -> {
            if (!driver.findElements(By.id("Input_1")).isEmpty()
                    && driver.findElement(By.id("Input_1")).isDisplayed()) {
                return false;
            }
            if (!driver.findElements(By.id("usernameField")).isEmpty()
                    && driver.findElement(By.id("usernameField")).isDisplayed()) {
                return false;
            }
            String url = driver.getCurrentUrl();
            return url != null && !url.contains("/nlogin/");
        });
    }

    private boolean openProfilePage(WebDriverWait wait) {
        try {
            List<WebElement> completeProfile = driver.findElements(By.xpath("//div[@class='view-profile-wrapper']/a"));
            if (!completeProfile.isEmpty() && completeProfile.get(0).isDisplayed()) {
                completeProfile.get(0).click();
                logger.info("Clicked the 'Complete Profile' button successfully");
                return true;
            }
        } catch (Exception ignored) {
            // fall through
        }

        driver.get(PROFILE_URL);
        try {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.xpath("//input[@type='file' and contains(@id,'attachCV')]")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".uploadContainer"))
            ));
            logger.info("Opened profile page directly");
            return true;
        } catch (Exception e) {
            return false;
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
