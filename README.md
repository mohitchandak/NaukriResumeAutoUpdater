# Naukri Resume Auto Updater

Java + Selenium tool that downloads your resume PDF and uploads it to Naukri. Designed to run on a schedule via GitHub Actions.

## Requirements

- Java 11 or later
- Maven
- Chrome / Chromium (for local runs)

## Local setup

1. Copy the example config and fill in your values:
   ```bash
   cp config.properties.example config.properties
   ```

2. Edit `config.properties`:
   ```properties
   naukri.user.email=your_email@example.com
   naukri.user.password=your_password
   resume.pdf.url=https://example.com/resume.pdf
   resume.file.name=resume.pdf
   browser.headless=true
   ```

   `config.properties` is gitignored — never commit real credentials.

3. Build and run:
   ```bash
   mvn clean package
   java -jar target/nakuri-1.0-SNAPSHOT.jar
   ```

## GitHub Actions (hourly Mon–Fri, 9 AM–7 PM IST)

The workflow runs **once per hour from 9:00 to 19:00 IST, Monday–Friday**, and can also be triggered manually.

Cron used (UTC): `30 3-13 * * 1-5`  
→ 03:30–13:30 UTC = 09:00–19:00 IST

### 1. Push the repo

Push to GitHub (default branch `main`). Scheduled workflows only run on the default branch.

### 2. Add repository secrets

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**

| Secret | Example |
|--------|---------|
| `NAUKRI_EMAIL` | your Naukri login email |
| `NAUKRI_PASSWORD` | your Naukri password |
| `RESUME_PDF_URL` | public/direct download URL of your PDF |
| `RESUME_FILE_NAME` | `Mohit_Chandak_SDET.pdf` |

### 3. Verify

1. Open **Actions → Naukri Auto Updater**
2. Click **Run workflow** (manual test)
3. Confirm the job succeeds

After that, it runs on the schedule automatically.

### Notes

- GitHub may delay scheduled jobs by a few minutes.
- Keep the repo **private** if you prefer; free minutes apply on the free plan.
- Do not put your password in committed files — use secrets only.
- `RESUME_PDF_URL` must be a URL that downloads without a login wall (Google Drive “anyone with the link” + `uc?export=download` style links usually work).

## What it does

1. Downloads the resume from `RESUME_PDF_URL` / `resume.pdf.url`
2. Renames it with today’s IST date (e.g. `Mohit_Chandak_SDET.pdf` → `Mohit_Chandak_SDET_2026-08-05.pdf`)
3. Logs in to Naukri and uploads that dated file
4. Exits (scheduling is handled by GitHub Actions, not the Java process)

Keep `RESUME_FILE_NAME` / `resume.file.name` as the base name only (no date) — the date is appended automatically.

## Project structure

- `pom.xml` — Maven build
- `config.properties.example` — template for local config
- `.github/workflows/naukri-auto-updater.yml` — scheduled runner
- `src/main/java/org/example/Main.java` — entrypoint
- `src/main/java/org/example/Config.java` — loads env vars or `config.properties`
- `src/main/java/org/example/ResumeDownloader.java` — PDF download
- `src/main/java/org/example/NaukriAutomation.java` — Naukri login + upload
