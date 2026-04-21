import java.util.*;

/**
 * Parses RFC 5322 email headers from raw POP3 message strings.
 * Handles:
 *   - Basic headers (From, To, Subject, Date, Message-ID)
 *   - Mailing list detection (RFC 2369)
 *   - Folded header lines (RFC 5322 Section 2.2.3)
 *   - Address extraction from various From: formats
 */
public class EmailParser {

    // ----------------------------------------------------------------
    // PARSED EMAIL — holds all extracted fields
    // ----------------------------------------------------------------
    public static class ParsedEmail {
        public String messageNumber = "";
        public String from          = "";
        public String fromAddress   = "";  // just the email address
        public String to            = "";
        public String subject       = "";
        public String date          = "";
        public String messageId     = "";
        public String precedence    = "";
        public String listId        = "";
        public int    size          = 0;
        public boolean isMailingList = false;

        @Override
        public String toString() {
            return String.format(
                "From   : %s\nSubject: %s\nDate   : %s\nSize   : %d bytes",
                from, subject, date, size);
        }
    }

    // ----------------------------------------------------------------
    // PARSE HEADERS FROM RAW MESSAGE STRING
    // Handles RFC 5322 folded headers where a header continues
    // on the next line if it starts with whitespace
    // ----------------------------------------------------------------
    public static ParsedEmail parse(String rawMessage, int messageNumber,
                                    int size) {
        ParsedEmail email = new ParsedEmail();
        email.messageNumber = String.valueOf(messageNumber);
        email.size          = size;

        if (rawMessage == null || rawMessage.isEmpty()) return email;

        // Unfold headers — RFC 5322 Section 2.2.3
        // A header field may span multiple lines if continuation
        // lines start with whitespace (space or tab)
        String unfolded = rawMessage.replace("\r\n ", " ")
                                    .replace("\r\n\t", " ")
                                    .replace("\n ", " ")
                                    .replace("\n\t", " ");

        // Split into lines
        String[] lines = unfolded.split("\n");

        // Only parse up to the blank line that separates
        // headers from body (RFC 5322 Section 2.1)
        for (String line : lines) {
            if (line.trim().isEmpty()) break;

            String lower = line.toLowerCase();

            if (lower.startsWith("from:")) {
                email.from        = line.substring(5).trim();
                email.fromAddress = extractAddress(email.from);
            } else if (lower.startsWith("to:")) {
                email.to = line.substring(3).trim();
            } else if (lower.startsWith("subject:")) {
                email.subject = line.substring(8).trim();
            } else if (lower.startsWith("date:")) {
                email.date = line.substring(5).trim();
            } else if (lower.startsWith("message-id:")) {
                email.messageId = line.substring(11).trim();
            } else if (lower.startsWith("precedence:")) {
                email.precedence = line.substring(11).trim().toLowerCase();
            } else if (lower.startsWith("list-id:")) {
                email.listId = line.substring(8).trim();
            }
        }

        // Detect mailing lists (RFC 2369)
        // A message is from a mailing list if it has:
        // - A List-Id header (RFC 2919)
        // - Precedence: bulk or list header
        email.isMailingList = !email.listId.isEmpty()
            || email.precedence.equals("bulk")
            || email.precedence.equals("list");

        return email;
    }

    // ----------------------------------------------------------------
    // EXTRACT EMAIL ADDRESS FROM FROM: HEADER
    // Handles multiple formats:
    //   - "John Smith <john@example.com>"  → john@example.com
    //   - "john@example.com"               → john@example.com
    //   - "<john@example.com>"             → john@example.com
    // ----------------------------------------------------------------
    public static String extractAddress(String fromHeader) {
        if (fromHeader == null || fromHeader.isEmpty()) return "";

        // Format: "Name <address>"
        int angleStart = fromHeader.indexOf('<');
        int angleEnd   = fromHeader.indexOf('>');
        if (angleStart >= 0 && angleEnd > angleStart) {
            return fromHeader.substring(angleStart + 1, angleEnd).trim();
        }

        // Format: bare address
        return fromHeader.trim();
    }

    // ----------------------------------------------------------------
    // CHECK IF SUBJECT MATCHES OUR VACATION TRIGGER
    // Assignment requires only responding to subject "prac7"
    // Case insensitive comparison
    // ----------------------------------------------------------------
    public static boolean isVacationTrigger(ParsedEmail email) {
        return email.subject.trim().equalsIgnoreCase("prac7");
    }

    // ----------------------------------------------------------------
    // CHECK IF MESSAGE IS FROM A MAILING LIST
    // RFC 2369 defines List-* headers for mailing lists
    // We should not send vacation replies to mailing lists
    // ----------------------------------------------------------------
    public static boolean isMailingList(ParsedEmail email) {
        return email.isMailingList;
    }

    // ----------------------------------------------------------------
    // CHECK IF ADDRESS IS EMPTY OR INVALID
    // We should not reply to empty or daemon addresses
    // ----------------------------------------------------------------
    public static boolean isValidSender(ParsedEmail email) {
        String addr = email.fromAddress.toLowerCase();
        if (addr.isEmpty())           return false;
        if (addr.contains("mailer-daemon")) return false;
        if (addr.contains("postmaster"))    return false;
        if (addr.contains("no-reply"))      return false;
        if (addr.contains("noreply"))       return false;
        if (!addr.contains("@"))            return false;
        return true;
    }

    // ----------------------------------------------------------------
    // FORMAT MESSAGE SUMMARY FOR DISPLAY
    // Used by mail manager to show one line per message
    // ----------------------------------------------------------------
    public static String formatSummary(ParsedEmail email) {
        String from    = truncate(email.from, 30);
        String subject = truncate(email.subject, 30);
        String size    = formatSize(email.size);

        return String.format("  [%s] %-30s %-30s %s",
            email.messageNumber, from, subject, size);
        }

    // ----------------------------------------------------------------
    // HELPER — truncate string to max length
    // ----------------------------------------------------------------
    private static String truncate(String s, int maxLen) {
        if (s == null)         return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    // ----------------------------------------------------------------
    // HELPER — format byte size to human readable
    // ----------------------------------------------------------------
    private static String formatSize(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }
}