import java.text.SimpleDateFormat;
import java.util.*;

/**
 * this builds an RFC 5322 compliant email message.
 * Supports:
 *   - Proper Date: header  (RFC 5322 3.3)
 *   - Unique Message-ID:   (RFC 5322 3.6.4)
 *   - CC recipients
 *   - MIME multipart/alternative with plain-text + HTML parts (RFC 2045/2046)
 */
public class EmailMessage {
    //these are the fields that will be used to construct the email message. 
    // They are all final, meaning they cannot be changed after the object is created.
    private final String       from;
    private final String       to;
    private final String       subject;
    private final List<String> ccList;
    private final String       plainBody;
    private final String       htmlBody;

    private EmailMessage(Builder b) {
        this.from      = b.from;
        this.to        = b.to;
        this.subject   = b.subject;
        this.ccList    = Collections.unmodifiableList(b.ccList);
        this.plainBody = b.plainBody;
        this.htmlBody  = b.htmlBody;
    }

    //Accessors
    //these are getter methods that allow other classes to access the private fields of the EmailMessage class.
    public String       getFrom()    { return from; }
    public String       getTo()      { return to; }
    public List<String> getCcList()  { return ccList; }
    public boolean      hasCc()      { return !ccList.isEmpty(); }

    //RFC 5322 Date header

    /**
     * Returns the current date/time formatted exactly as RFC 5322 3.3 requires:
     *   day-of-week, DD Mon YYYY HH:MM:SS ±HHMM
     * Example: Thu, 17 Apr 2026 14:32:01 +0200
     */
    private String rfc5322Date() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        return sdf.format(new Date());
    }

    //Unique Message-ID

    /**
     * Generates a globally unique Message-ID per RFC 5322 §3.6.4.
     * Format: <timestamp.threadId@hostname>
     */
    private String messageId() {
        String hostname = "COS332Client";
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        return String.format("<%d.%d@%s>",
            System.currentTimeMillis(),
            Thread.currentThread().getId(),
            hostname);
    }

    //MIME boundary

    private String boundary() {
        return "----=_Part_" + System.currentTimeMillis() + "_" + hashCode();
    }

    //Full message assembly 

    /**
     * Returns the complete RFC 5322 message (headers + blank line + body)
     * ready to pipe into the DATA phase of an SMTP session.
     */
    public String getFullMessage() {
        String boundary = boundary();
        StringBuilder msg = new StringBuilder();

        //Headers
        msg.append("From: ").append(from).append("\r\n");
        msg.append("To: ").append(to).append("\r\n");

        if (!ccList.isEmpty()) {
            msg.append("Cc: ").append(String.join(", ", ccList)).append("\r\n");
        }

        msg.append("Subject: ").append(subject).append("\r\n");

        // RFC 5322 §3.3 — Date header (required)
        msg.append("Date: ").append(rfc5322Date()).append("\r\n");

        // RFC 5322 §3.6.4 — Message-ID header (required for compliant messages)
        msg.append("Message-ID: ").append(messageId()).append("\r\n");

        // RFC 2045 §4 — MIME version
        msg.append("MIME-Version: 1.0\r\n");

        // RFC 2046 §5.1.4 — multipart/alternative: clients pick the best part
        msg.append("Content-Type: multipart/alternative; boundary=\"")
           .append(boundary).append("\"\r\n");

        // Blank line separating headers from body (RFC 5322 §2.1)
        msg.append("\r\n");

        //Plain-text part (RFC 2046 5.1) 
        msg.append("--").append(boundary).append("\r\n");
        msg.append("Content-Type: text/plain; charset=UTF-8\r\n");
        msg.append("Content-Transfer-Encoding: 8bit\r\n");
        msg.append("\r\n");
        msg.append(plainBody).append("\r\n\r\n");

        //HTML part
        msg.append("--").append(boundary).append("\r\n");
        msg.append("Content-Type: text/html; charset=UTF-8\r\n");
        msg.append("Content-Transfer-Encoding: 8bit\r\n");
        msg.append("\r\n");
        msg.append(htmlBody).append("\r\n\r\n");

        // Closing boundary (RFC 2046 5.1.1) 
        msg.append("--").append(boundary).append("--\r\n");

        return msg.toString();
    }

    //Builder

    public static class Builder {
        private String       from      = "";
        private String       to        = "";
        private String       subject   = "";
        private List<String> ccList    = new ArrayList<>();
        private String       plainBody = "";
        private String       htmlBody  = "";

        public Builder from(String from)         { this.from = from;      return this; }
        public Builder to(String to)             { this.to = to;          return this; }
        public Builder subject(String subject)   { this.subject = subject; return this; }
        public Builder cc(List<String> cc)       { this.ccList = cc;      return this; }

        public Builder plainBody(String body)    { this.plainBody = body; return this; }
        public Builder htmlBody(String body)     { this.htmlBody = body;  return this; }

        /** Convenience: auto-generate a simple HTML version from the plain-text body. */
        public Builder body(String plain) {
            this.plainBody = plain;
            this.htmlBody  = autoHtml(plain, subject);
            return this;
        }

        public EmailMessage build() { return new EmailMessage(this); }

        private static String autoHtml(String plain, String subject) {
            // Escape HTML entities in the plain body
            String escaped = plain
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>\n");

            return "<!DOCTYPE html>\r\n"
                + "<html>\r\n<head>\r\n"
                + "<meta charset='UTF-8'>\r\n"
                + "<style>\r\n"
                + "  body { font-family: Arial, sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }\r\n"
                + "  .card { background: #fff; border-radius: 8px; padding: 24px 32px; max-width: 520px; margin: auto; box-shadow: 0 2px 8px rgba(0,0,0,.1); }\r\n"
                + "  h1 { color: #2c3e50; font-size: 1.4em; margin-bottom: 8px; }\r\n"
                + "  .body-text { color: #444; font-size: 1em; line-height: 1.6; }\r\n"
                + "  .footer { margin-top: 24px; color: #999; font-size: .85em; }\r\n"
                + "</style>\r\n</head>\r\n<body>\r\n"
                + "<div class='card'>\r\n"
                + "  <h1>&#127874; " + subject + "</h1>\r\n"
                + "  <div class='body-text'>" + escaped + "</div>\r\n"
                + "  <div class='footer'>Sent by BirthdayReminder &mdash; COS332 Practical 6</div>\r\n"
                + "</div>\r\n</body>\r\n</html>";
        }
    }
}