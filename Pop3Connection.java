//checking mailbox for new emials
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Low-level POP3 socket handler.
 * Implements RFC 1939 POP3 protocol using raw socket operations.
 * Bonus features:
 *   - TOP command (RFC 1939) for header-only retrieval
 *   - UIDL command (RFC 1939) for unique message IDs
 *   - CAPA command (RFC 2449) for server capability discovery
 *   - RSET command for undoing deletions
 */
public class Pop3Connection {

    private final String host;
    private final int    port;

    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;

    // Server capabilities discovered via CAPA command
    private final Set<String> capabilities = new HashSet<>();

    // ----------------------------------------------------------------
    // CONSTRUCTOR
    // ----------------------------------------------------------------
    public Pop3Connection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ----------------------------------------------------------------
    // CONNECT AND AUTHENTICATE
    // RFC 1939 Section 4 — AUTHORIZATION State
    // ----------------------------------------------------------------
    public void connect() throws IOException {
        System.out.println("[POP3] Connecting to " + host + ":" + port + "...");
        socket = new Socket(host, port);
        reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream()), true);

        // Read server greeting — must be +OK
        String greeting = readResponse();
        System.out.println("[POP3] Server: " + greeting);
        expectOK(greeting);

        // Discover server capabilities (RFC 2449)
        discoverCapabilities();
    }

    // ----------------------------------------------------------------
    // CAPA — discover server capabilities (RFC 2449)
    // ----------------------------------------------------------------
    private void discoverCapabilities() {
        try {
            sendCommand("CAPA");
            String line = readResponse();
            if (line.startsWith("+OK")) {
                // Read capability lines until "."
                while (!(line = readResponse()).equals(".")) {
                    capabilities.add(line.trim().toUpperCase());
                }
                System.out.println("[POP3] Server capabilities: " + capabilities);
            }
        } catch (IOException e) {
            // CAPA not supported — that's fine, continue without it
            System.out.println("[POP3] CAPA not supported by server");
        }
    }

    // ----------------------------------------------------------------
    // AUTHENTICATE
    // RFC 1939 Section 4 — USER and PASS commands
    // ----------------------------------------------------------------
    public void authenticate(String username, String password) 
            throws IOException {
        sendCommand("USER " + username);
        expectOK(readResponse());

        sendCommand("PASS " + password);
        expectOK(readResponse());

        System.out.println("[POP3] Authenticated as: " + username);
    }

    // ----------------------------------------------------------------
    // STAT — get message count and total size
    // RFC 1939 Section 5
    // Returns int[] { messageCount, totalSize }
    // ----------------------------------------------------------------
    public int[] stat() throws IOException {
        sendCommand("STAT");
        String response = readResponse();
        expectOK(response);

        // Response format: +OK count size
        String[] parts = response.split(" ");
        int count = Integer.parseInt(parts[1]);
        int size  = Integer.parseInt(parts[2]);

        System.out.println("[POP3] Mailbox: " + count + 
            " message(s), " + size + " bytes total");
        return new int[]{ count, size };
    }

    // ----------------------------------------------------------------
    // LIST — get size of each message
    // RFC 1939 Section 5
    // Returns map of message number → size in octets
    // ----------------------------------------------------------------
    public Map<Integer, Integer> list() throws IOException {
        sendCommand("LIST");
        String response = readResponse();
        expectOK(response);

        Map<Integer, Integer> sizes = new LinkedHashMap<>();

        // Read multi-line response until "."
        String line;
        while (!(line = readResponse()).equals(".")) {
            String[] parts = line.trim().split(" ");
            if (parts.length >= 2) {
                int msgNum  = Integer.parseInt(parts[0]);
                int msgSize = Integer.parseInt(parts[1]);
                sizes.put(msgNum, msgSize);
            }
        }

        return sizes;
    }

    // ----------------------------------------------------------------
    // RETR — retrieve a complete message
    // RFC 1939 Section 5
    // Returns the full raw message as a string
    // ----------------------------------------------------------------
    public String retr(int messageNumber) throws IOException {
        sendCommand("RETR " + messageNumber);
        String response = readResponse();
        expectOK(response);

        return readMultiLineResponse();
    }

    // ----------------------------------------------------------------
    // TOP — retrieve headers + first n lines of body
    // RFC 1939 Section 5
    // Using TOP n 0 retrieves ONLY headers (no body lines)
    // This is essential for mail manager — avoids downloading large bodies
    // ----------------------------------------------------------------
    public String top(int messageNumber, int lines) throws IOException {
        sendCommand("TOP " + messageNumber + " " + lines);
        String response = readResponse();
        expectOK(response);

        return readMultiLineResponse();
    }

    // ----------------------------------------------------------------
    // DELE — mark a message for deletion
    // RFC 1939 Section 5
    // Message is not actually deleted until QUIT is sent
    // ----------------------------------------------------------------
    public void dele(int messageNumber) throws IOException {
        sendCommand("DELE " + messageNumber);
        expectOK(readResponse());
        System.out.println("[POP3] Message " + messageNumber + 
            " marked for deletion");
    }

    // ----------------------------------------------------------------
    // RSET — unmark all messages marked for deletion
    // RFC 1939 Section 5
    // Useful if user changes their mind before QUIT
    // ----------------------------------------------------------------
    public void rset() throws IOException {
        sendCommand("RSET");
        expectOK(readResponse());
        System.out.println("[POP3] All deletion marks removed");
    }

    // ----------------------------------------------------------------
    // NOOP — do nothing, keep connection alive
    // RFC 1939 Section 5
    // ----------------------------------------------------------------
    public void noop() throws IOException {
        sendCommand("NOOP");
        expectOK(readResponse());
    }

    // ----------------------------------------------------------------
    // UIDL — get unique ID of a message (RFC 1939 Section 5)
    // Unique IDs persist across sessions unlike message numbers
    // Essential for tracking which messages we have already seen
    // ----------------------------------------------------------------
    public String uidl(int messageNumber) throws IOException {
        sendCommand("UIDL " + messageNumber);
        String response = readResponse();
        expectOK(response);

        // Response format: +OK msgnum uniqueid
        String[] parts = response.split(" ");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "";
    }

    // ----------------------------------------------------------------
    // UIDL ALL — get unique IDs of all messages
    // Returns map of message number → unique ID
    // ----------------------------------------------------------------
    public Map<Integer, String> uidlAll() throws IOException {
        sendCommand("UIDL");
        String response = readResponse();
        expectOK(response);

        Map<Integer, String> uids = new LinkedHashMap<>();
        String line;
        while (!(line = readResponse()).equals(".")) {
            String[] parts = line.trim().split(" ");
            if (parts.length >= 2) {
                int    msgNum = Integer.parseInt(parts[0]);
                String uid    = parts[1];
                uids.put(msgNum, uid);
            }
        }
        return uids;
    }

    // ----------------------------------------------------------------
    // QUIT — enter UPDATE state and close connection
    // RFC 1939 Section 5
    // Any messages marked with DELE are now permanently deleted
    // ----------------------------------------------------------------
    public void quit() throws IOException {
        sendCommand("QUIT");
        try { readResponse(); } catch (IOException ignored) {}
        socket.close();
        System.out.println("[POP3] Disconnected.");
    }

    // ----------------------------------------------------------------
    // QUIT WITHOUT DELETING
    // Sends RSET before QUIT to ensure no deletions are committed
    // Used by vacation responder to leave all mail on server
    // ----------------------------------------------------------------
    public void quitWithoutDeleting() throws IOException {
        try {
            rset(); // Cancel any accidental deletion marks
        } catch (IOException ignored) {}
        quit();
    }

    // ----------------------------------------------------------------
    // READ MULTI-LINE RESPONSE
    // RFC 1939 Section 3 — multi-line responses end with "." on own line
    // Handles byte-stuffing: lines starting with ".." become "."
    // ----------------------------------------------------------------
    private String readMultiLineResponse() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while (!(line = readResponse()).equals(".")) {
            // RFC 1939 byte stuffing — leading ".." becomes "."
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    // ----------------------------------------------------------------
    // I/O HELPERS
    // ----------------------------------------------------------------
    private void sendCommand(String cmd) throws IOException {
        System.out.println("[POP3] -> " + cmd);
        writer.print(cmd + "\r\n");
        writer.flush();
    }

    private String readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Server closed connection unexpectedly");
        }
        System.out.println("[POP3] <- " + line);
        return line;
    }

    private void expectOK(String response) throws IOException {
        if (!response.startsWith("+OK")) {
            throw new IOException("Expected +OK but got: " + response);
        }
    }

    public boolean hasCapability(String cap) {
        return capabilities.contains(cap.toUpperCase());
    }
}