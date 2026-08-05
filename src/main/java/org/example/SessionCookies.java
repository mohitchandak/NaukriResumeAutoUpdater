package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/** Export/import Selenium cookies as JSON for GitHub Actions session reuse. */
public final class SessionCookies {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type LIST_TYPE = new TypeToken<List<CookieData>>() {}.getType();

    private SessionCookies() {
    }

    public static String exportToJson(WebDriver driver) {
        List<CookieData> cookies = new ArrayList<>();
        for (Cookie cookie : driver.manage().getCookies()) {
            cookies.add(CookieData.from(cookie));
        }
        return GSON.toJson(cookies);
    }

    public static void saveToFile(WebDriver driver, Path path) throws IOException {
        Files.writeString(path, exportToJson(driver));
    }

    public static void applyFromJson(WebDriver driver, String jsonOrBase64) {
        String json = normalizeToJson(jsonOrBase64);
        List<CookieData> cookies = GSON.fromJson(json, LIST_TYPE);
        if (cookies == null || cookies.isEmpty()) {
            throw new IllegalStateException("NAUKRI_COOKIES is empty or invalid JSON");
        }

        // Must be on the domain before adding cookies
        driver.get("https://www.naukri.com/");
        for (CookieData data : cookies) {
            try {
                driver.manage().addCookie(data.toCookie());
            } catch (Exception e) {
                System.err.println("Skipping cookie " + data.name + ": " + e.getMessage());
            }
        }
        driver.navigate().refresh();
    }

    public static String toBase64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static String normalizeToJson(String raw) {
        if (raw == null) {
            throw new IllegalStateException("Cookie payload is null");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8).trim();
            if (decoded.startsWith("[")) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64
        }
        throw new IllegalStateException("NAUKRI_COOKIES must be JSON array or base64(JSON array)");
    }

    public static final class CookieData {
        String name;
        String value;
        String domain;
        String path;
        Long expiryEpochMs;
        boolean secure;
        boolean httpOnly;

        static CookieData from(Cookie cookie) {
            CookieData data = new CookieData();
            data.name = cookie.getName();
            data.value = cookie.getValue();
            data.domain = cookie.getDomain();
            data.path = cookie.getPath() == null ? "/" : cookie.getPath();
            data.expiryEpochMs = cookie.getExpiry() == null ? null : cookie.getExpiry().getTime();
            data.secure = cookie.isSecure();
            data.httpOnly = cookie.isHttpOnly();
            return data;
        }

        Cookie toCookie() {
            Date expiry = expiryEpochMs == null ? null : new Date(expiryEpochMs);
            String domain = this.domain;
            if (domain != null && domain.startsWith(".")) {
                // Selenium accepts both; keep as-is
            }
            return new Cookie(name, value, domain, path == null ? "/" : path, expiry, secure, httpOnly);
        }
    }
}
