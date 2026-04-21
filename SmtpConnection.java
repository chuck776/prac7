import java.io.*;
import java.net.*;
import java.util.*;
//creating a class to handle the SMTP connection and communication with the server
//bonus features: ESMTP support with EHLO and multi-recipient handling with multiple RCPT TO commands
/**
 * Low-level SMTP socket handler.
 * <p>
 * Bonus features implemented:
 * <ul>
 *   <li>ESMTP: uses {@code EHLO} (RFC 2821) instead of deprecated {@code HELO} (RFC 821)
 *       and parses server capability lines.</li>
 *   <li>Multi-recipient: issues one {@code RCPT TO:} per address, covering CC/BCC
 *       as specified in RFC 821 §4.1.1.</li>
 * </ul>
 */
public class SmtpConnection {

    private final String host;
    private final int    port;

    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;

    /** Capabilities advertised by the server after EHLO (lower-cased). */
    private final Set<String> capabilities = new HashSet<>();

    public SmtpConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    //Connect & greet

    /**
     * Opens the TCP connection, reads the 220 banner, and sends EHLO.
     * Falls back to HELO if the server rejects EHLO (rare but possible with
     * very old RFC 821-only servers).
     */
    public void connect() throws IOException {
        System.out.println("[SMTP] Connecting to " + host + ":" + port + "...");
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        // Read 220 banner
        String banner = readResponse();
        System.out.println("[SMTP] Server: " + banner);
        expectCode(banner, "220");

        // Prefer EHLO (RFC 2821 / ESMTP) over HELO (RFC 821)
        sendCommand("EHLO COS332Client");
        String line = readResponse();
        if (line.startsWith("250")) {
            // Multi-line 250 response — keep reading until the line without a dash
            parseCapabilityLine(line);
            while (line.startsWith("250-")) {
                line = readResponse();
                parseCapabilityLine(line);
            }
            System.out.println("[SMTP] ESMTP capabilities: " + capabilities);
        } else {
            // Server doesn't speak ESMTP — fall back to RFC 821 HELO
            System.out.println("[SMTP] EHLO rejected, falling back to HELO");
            sendCommand("HELO COS332Client");
            expectResponse("250");
        }
    }

    /** Extracts the capability keyword from a 250-KEYWORD or 250 KEYWORD line. */
    private void parseCapabilityLine(String line) {
        // logged by readResponse()
        if (line.length() > 4) {
            // Strip "250-" or "250 "
            capabilities.add(line.substring(4).trim().toUpperCase());
        }
    }

    //Send email

    /**
     * Executes the full SMTP mail transaction for one message.
     * Issues one {@code RCPT TO:} command per recipient (To + all CC entries).
     */
    public void sendEmail(EmailMessage email) throws IOException {
        // MAIL FROM
        sendCommand("MAIL FROM:<" + email.getFrom() + ">");
        expectResponse("250");

        // Primary recipient
        sendCommand("RCPT TO:<" + email.getTo() + ">");
        expectResponse("250");

        // CC recipients — each gets its own RCPT TO (RFC 821 §4.1.1)
        if (email.hasCc()) {
            for (String cc : email.getCcList()) {
                String addr = cc.trim();
                if (!addr.isEmpty()) {
                    sendCommand("RCPT TO:<" + addr + ">");
                    expectResponse("250");
                }
            }
        }

        // DATA phase
        sendCommand("DATA");
        expectResponse("354");

        // Write the full RFC 5322 message, then the end-of-data marker
        String fullMessage = email.getFullMessage();
        System.out.println("[SMTP] -> [message body: " + fullMessage.length() + " bytes]");
        writer.print(fullMessage);
        writer.print("\r\n.\r\n");
        writer.flush();

        expectResponse("250");
    }

    //Quit

    public void quit() throws IOException {
        sendCommand("QUIT");
        try { readResponse(); } catch (IOException ignored) {}
        socket.close();
    }

    //I/O helpers

    private void sendCommand(String cmd) throws IOException {
        System.out.println("[SMTP] -> " + cmd);
        writer.print(cmd + "\r\n");
        writer.flush();
    }

    private String readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Server closed connection unexpectedly");
        // logged by readResponse()
        return line;
    }

    /** Read one response line and throw if it doesn't start with {@code expectedCode}. */
    private void expectResponse(String expectedCode) throws IOException {
        String response = readResponse();
        expectCode(response, expectedCode);
    }

    private void expectCode(String response, String expectedCode) throws IOException {
        if (!response.startsWith(expectedCode)) {
            throw new IOException("Expected " + expectedCode + " but got: " + response);
        }
    }

    /** True if the server advertised this capability during EHLO. */
    public boolean hasCapability(String cap) {
        return capabilities.contains(cap.toUpperCase());
    }
}