package org.example;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the latest Naukri login OTP from Gmail over IMAP using an App Password.
 */
public class GmailOtpReader {
    private static final Pattern OTP_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final long POLL_INTERVAL_MS = 5000L;
    private static final long DEFAULT_TIMEOUT_MS = 120_000L;

    private final String gmailAddress;
    private final String appPassword;

    public GmailOtpReader(String gmailAddress, String appPassword) {
        this.gmailAddress = gmailAddress;
        this.appPassword = appPassword;
    }

    public String waitForOtp(Instant notBefore) throws InterruptedException, IOException {
        return waitForOtp(notBefore, DEFAULT_TIMEOUT_MS);
    }

    public String waitForOtp(Instant notBefore, long timeoutMs) throws InterruptedException, IOException {
        Instant deadline = Instant.now().plusMillis(timeoutMs);
        IOException lastError = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                String otp = fetchLatestOtp(notBefore);
                if (otp != null) {
                    return otp;
                }
            } catch (MessagingException | IOException e) {
                lastError = (e instanceof IOException) ? (IOException) e : new IOException(e.getMessage(), e);
                System.err.println("Gmail OTP poll failed: " + e.getMessage());
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }

        if (lastError != null) {
            throw new IOException("Timed out waiting for Naukri OTP email. Last Gmail error: " + lastError.getMessage(), lastError);
        }
        throw new IOException("Timed out waiting for Naukri OTP email in Gmail inbox");
    }

    private String fetchLatestOtp(Instant notBefore) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.trust", "*");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        try {
            store.connect("imap.gmail.com", gmailAddress, appPassword);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                Date since = Date.from(notBefore.minusSeconds(30));
                SearchTerm recent = new ReceivedDateTerm(ComparisonTerm.GE, since);
                Message[] messages = inbox.search(recent);
                Arrays.sort(messages, Comparator.comparingLong(GmailOtpReader::receivedMillis).reversed());

                for (Message message : messages) {
                    if (!isLikelyNaukriOtpMail(message)) {
                        continue;
                    }
                    if (receivedMillis(message) + 1000 < notBefore.toEpochMilli()) {
                        continue;
                    }
                    String body = extractText(message);
                    Matcher matcher = OTP_PATTERN.matcher(body);
                    String fallback = null;
                    while (matcher.find()) {
                        String code = matcher.group(1);
                        int idx = matcher.start();
                        String window = body.substring(Math.max(0, idx - 40), Math.min(body.length(), idx + 40)).toLowerCase();
                        if (window.contains("otp") || window.contains("one time") || window.contains("verification")) {
                            return code;
                        }
                        if (fallback == null) {
                            fallback = code;
                        }
                    }
                    if (fallback != null) {
                        return fallback;
                    }
                }
                return null;
            } finally {
                inbox.close(false);
            }
        } finally {
            store.close();
        }
    }

    private static boolean isLikelyNaukriOtpMail(Message message) throws MessagingException {
        String subject = message.getSubject() == null ? "" : message.getSubject().toLowerCase();
        String from = addressesToString(message.getFrom()).toLowerCase();
        boolean fromNaukri = from.contains("naukri.com") || from.contains("infoedge");
        boolean looksOtp = subject.contains("otp")
                || subject.contains("verification")
                || subject.contains("verify")
                || subject.contains("login")
                || subject.contains("one time");
        return fromNaukri || (looksOtp && (subject.contains("naukri") || from.contains("naukri")));
    }

    private static String addressesToString(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Address address : addresses) {
            if (address instanceof InternetAddress) {
                sb.append(((InternetAddress) address).getAddress()).append(' ');
            } else {
                sb.append(address.toString()).append(' ');
            }
        }
        return sb.toString();
    }

    private static long receivedMillis(Message message) {
        try {
            Date received = message.getReceivedDate();
            if (received != null) {
                return received.getTime();
            }
            Date sent = message.getSentDate();
            return sent == null ? 0L : sent.getTime();
        } catch (MessagingException e) {
            return 0L;
        }
    }

    private static String extractText(Message message) throws MessagingException, IOException {
        Object content = message.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof Multipart) {
            return extractFromMultipart((Multipart) content);
        }
        return String.valueOf(content);
    }

    private static String extractFromMultipart(Multipart multipart) throws MessagingException, IOException {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                continue;
            }
            Object content = part.getContent();
            if (content instanceof String) {
                text.append((String) content).append('\n');
            } else if (content instanceof Multipart) {
                text.append(extractFromMultipart((Multipart) content)).append('\n');
            }
        }
        return text.toString();
    }
}
