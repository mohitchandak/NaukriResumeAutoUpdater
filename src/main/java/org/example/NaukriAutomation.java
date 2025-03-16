package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.time.Duration;
import java.util.logging.Logger;

public class NaukriAutomation {
    private static final Logger logger = Logger.getLogger(NaukriAutomation.class.getName());
    private WebDriver driver;

    public NaukriAutomation() {
        // Setup ChromeDriver using WebDriverManager
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Run in headless mode
        options.addArguments("--no-sandbox"); // Required for Heroku
        options.addArguments("--disable-dev-shm-usage"); // Overcome limited resource issues
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        driver = new ChromeDriver(options);

    }

    public void login(String email, String password) {
        try {
            driver.get("https://www.naukri.com/nlogin/login");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            WebElement emailField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("usernameField")));
            emailField.sendKeys(email);

            WebElement passwordField = driver.findElement(By.id("passwordField"));
            passwordField.sendKeys(password);

            WebElement loginButton = driver.findElement(By.xpath("//button[@type='submit']"));
            loginButton.click();
            logger.info("Logged in successfully");
        } catch (Exception e) {
            logger.severe("Login failed: " + e.getMessage());
            throw e;
        }
    }


    // Upload resume method
    public void uploadResume(String resumePath) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));  // Increased wait time to 20 seconds
        try {
            // Step 1: Click "Complete Profile"
            String completeProfileXPath = "//div[@class='view-profile-wrapper']/a";
            WebElement completeProfileButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(completeProfileXPath)));
            completeProfileButton.click();
            logger.info("Clicked the 'Complete Profile' button successfully");

            // Step 2: Upload the resume
            // Wait for the file input element to be visible
            String uploadXPath = "//div[@class='uploadContainer']//input[@type='file' and @id='attachCV']";
            WebElement uploadButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(uploadXPath)));
            Thread.sleep(2000);
            // Ensure the element is interactable (JavaScript fallback)
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript("arguments[0].scrollIntoView(true);", uploadButton);
            Thread.sleep(2000);
            // Set the file path directly using sendKeys() on the file input element
            uploadButton.sendKeys(resumePath);
            Thread.sleep(2000);
            logger.info("Resume uploaded successfully");

            // Optional: Wait for confirmation (e.g., a success message or updated timestamp)
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[contains(@class, 'updateOn') and contains(text(), 'Uploaded')]")
            ));
            logger.info("Upload confirmed on page");

        } catch (Exception e) {
            logger.severe("Resume upload failed: " + e.getMessage());
            throw e;
        }
    }


//    public void uploadResume(String resumePath) throws InterruptedException {
//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
//        String completeProfileXPath = "//div[@class='view-profile-wrapper']/a";
//        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(completeProfileXPath))).click();
//
//        System.out.println("Clicked the 'Complete Profile' button successfully");
//        Thread.sleep(5000);
//        try {
//            WebElement uploadButton = wait.until(ExpectedConditions.presenceOfElementLocated(
//                    By.xpath("//input[@type='file' and contains(@id, 'attachCV')]")
//            ));
//            uploadButton.sendKeys(resumePath);
//            logger.info("Resume uploaded successfully");
//        } catch (Exception e) {
//            logger.severe("Resume upload failed: " + e.getMessage());
//            throw e;
//        }
//    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            logger.info("WebDriver closed");
        }
    }
}