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

The workflow runs **automatically once per hour from 9:00 to 19:00 IST, Monday–Friday**. You can also trigger it manually with **Run workflow**.

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
| `RESUME_PDF_URL` | public/direct download URL of your PDF (fallback) |
| `RESUME_FILE_NAME` | `Mohit_Chandak_SDET.pdf` |
| `GMAIL_ADDRESS` | same Gmail that receives Naukri OTP (optional if same as `NAUKRI_EMAIL`) |
| `GMAIL_APP_PASSWORD` | Gmail App Password (not your normal Gmail password) |

### Gmail App Password (needed for OTP on Actions)

GitHub’s servers often trigger Naukri email OTP. The workflow reads that OTP from Gmail.

1. Open Google Account → **Security**
2. Turn on **2-Step Verification** (required)
3. Create an **App password**: https://myaccount.google.com/apppasswords  
   - App: Mail  
   - Device: Other → `NaukriUpdater`
4. Copy the 16-character password
5. Add it as GitHub secret `GMAIL_APP_PASSWORD`
6. Optionally set `GMAIL_ADDRESS` (defaults to `NAUKRI_EMAIL`)

### 3. Verify

1. Open **Actions → Naukri Auto Updater**
2. Click **Run workflow** (manual test)
3. Confirm the job succeeds (OTP is fetched automatically if shown)

After that, it runs on the schedule automatically.

### Notes

- GitHub may delay scheduled jobs by a few minutes.
- Keep the repo **private** if you prefer; free minutes apply on the free plan.
- Do not put passwords in committed files — use secrets only.
- Prefer bundling the PDF under `resume/` so Actions does not depend on Google Drive.

## What it does

1. Uses the bundled PDF from `resume/<RESUME_FILE_NAME>` when present (recommended for GitHub Actions), otherwise downloads from `RESUME_PDF_URL`
2. Renames it with today’s IST date (e.g. `Mohit_Chandak_SDET.pdf` → `Mohit_Chandak_SDET_2026-08-05.pdf`)
3. Logs in to Naukri; if email OTP is shown, reads it from Gmail and submits it
4. Uploads the dated resume
5. Exits (scheduling is handled by GitHub Actions, not the Java process)

Keep `RESUME_FILE_NAME` / `resume.file.name` as the base name only (no date) — the date is appended automatically.

Google Drive downloads often fail on GitHub’s network. Bundling the file under `resume/` avoids that. Note: this repo is public, so that PDF is publicly readable.

## Project structure

- `pom.xml` — Maven build
- `config.properties.example` — template for local config
- `.github/workflows/naukri-auto-updater.yml` — scheduled runner
- `src/main/java/org/example/Main.java` — entrypoint
- `src/main/java/org/example/Config.java` — loads env vars or `config.properties`
- `src/main/java/org/example/ResumeDownloader.java` — PDF download
- `src/main/java/org/example/NaukriAutomation.java` — Naukri login + upload
