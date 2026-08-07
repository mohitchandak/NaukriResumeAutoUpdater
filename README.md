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

The workflow targets **about once per hour from 9:00 to 19:00 IST, Monday–Friday**. You can also trigger it manually with **Run workflow**.

GitHub’s built-in `schedule` is **best-effort** (can delay or skip under load). To improve odds we use:

- **Two staggered crons** each hour (UTC `:12` and `:42`, hours 03–13) — more chances than a single `:30` trigger
- **IST time gate** — if a delayed job starts outside 9–19 IST, it skips the upload
- **Concurrency group** — avoids messy overlapping runs

Cron (UTC):
```text
12 3-13 * * 1-5
42 3-13 * * 1-5
```

This is the best we can do **inside GitHub alone**. It is still not a hard guarantee of 11 runs/day. For near-real hourly reliability, use an external cron calling `workflow_dispatch` (optional next step).


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

### Cookie login for GitHub Actions (recommended)

GitHub cloud IPs are often blocked by Naukri captcha. Use a saved browser session instead:

1. On your PC (works with email+password), export cookies:
   ```bash
   mvn -B clean package -q
   java -jar target/nakuri-1.0-SNAPSHOT.jar --export-cookies
   ```
   This creates `naukri-cookies.json` (gitignored).

2. Upload as a secret:
   ```bash
   gh secret set NAUKRI_COOKIES < naukri-cookies.json
   ```

3. Actions will reuse that session and skip login. When it expires, repeat steps 1–2.

| Secret | Required |
|--------|----------|
| `NAUKRI_COOKIES` | Yes for cloud |
| `RESUME_FILE_NAME` | Yes |
| `RESUME_PDF_URL` | Fallback only if `resume/` PDF missing |
| `NAUKRI_EMAIL` / `NAUKRI_PASSWORD` | Only for local cookie export |

### 3. Verify

1. Open **Actions → Naukri Auto Updater**
2. Click **Run workflow** (manual test)
3. Confirm the job succeeds (OTP is fetched automatically if shown)

After that, it runs on the schedule automatically.

### Notes

- GitHub schedule can still delay or skip hours under load; dual cron + IST gate is mitigation, not a guarantee.
- Keep the repo **private** if you prefer; free minutes apply on the free plan.
- Do not put passwords in committed files — use secrets only.
- Prefer bundling the PDF under `resume/` so Actions does not depend on Google Drive.

## What it does

1. Uses the bundled PDF from `resume/<RESUME_FILE_NAME>` when present (recommended for GitHub Actions), otherwise downloads from `RESUME_PDF_URL`
2. Renames it with today’s IST day + month (e.g. `Mohit_Chandak_SDET.pdf` → `Mohit_Chandak_SDET_8_Sep.pdf`)
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
