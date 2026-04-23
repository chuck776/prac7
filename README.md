# COS332 Practical 7 - POP3/SMTP Vacation Auto-Responder

A hand-rolled POP3 client and SMTP responder written in pure Java with **no external email libraries**.  
All POP3 and SMTP protocol interaction is implemented from scratch in compliance with RFC 1939 (POP3), RFC 821 (SMTP), and RFC 5322 (Email Message Format).

## Team Members

Uminathi Mdluli  
Zamokuhle Zwane

## Project Overview

The Vacation Auto-Responder monitors a POP3 mailbox for incoming emails with subject "prac7" and automatically sends vacation replies via SMTP. The program demonstrates low-level socket programming and direct protocol implementation without relying on JavaMail or similar libraries. For more info, visit the practical specification section: prac7.

### Key Features

- Pure socket-based POP3 and SMTP communication (no `javax.mail`)
- RFC 5322 compliant email headers with proper `Date:` and `Message-ID:` generation
- ESMTP support (`EHLO` command with capability detection)
- Multi-recipient support (CC via multiple `RCPT TO:` commands)
- MIME multipart messages (plain text + HTML alternatives)
- Persistent tracking to prevent duplicate replies
- Mailing list detection (RFC 2369)
- Non-destructive mailbox monitoring (messages remain on server)

## Project Structure

```
prac7/
├── VacationResponder.java   # Entry point - main menu and responder loop
├── Pop3Connection.java      # Low-level POP3 socket handler (RFC 1939)
├── SmtpConnection.java      # Low-level SMTP socket handler (RFC 821/2821)
├── EmailMessage.java        # RFC 5322 message builder (headers, MIME, body)
├── EmailParser.java         # RFC 5322 header parser with mailing list detection
├── RepliedTracker.java      # Persistent storage of replied email addresses
├── replied.txt              # Auto-generated reply log (do not edit manually)
├── config.properties        # SMTP/POP3 server settings (optional)
└── README.md                # This file
```

## Prerequisites

| Requirement | Version                          |
| ----------- | -------------------------------- |
| Java (JDK)  | 8+                               |
| SMTP Server | Postfix 3.x / any RFC 821 server |
| POP3 Server | Dovecot 2.x / any RFC 1939 server|

## How to Set Up

### 1. Install Postfix (SMTP Server)

```bash
sudo apt install postfix -y
```

When prompted during installation, select **"Local only"** and set the system mail name to `localhost`.

Configure Postfix for local delivery:

```bash
sudo nano /etc/postfix/main.cf
```

Add your hostname to `mydestination`:

```
mydestination = localhost, yourhostname
```

Restart Postfix:

```bash
sudo systemctl restart postfix
sudo systemctl status postfix
```

You should see `Active: active` in the output.

### 2. Install Dovecot (POP3 Server)

```bash
sudo apt install dovecot-pop3d -y
```

Configure plain-text authentication (for testing):

```bash
sudo nano /etc/dovecot/conf.d/10-auth.conf
```

Modify these lines:

```
disable_plaintext_auth = no
auth_mechanisms = plain login
```

Restart Dovecot:

```bash
sudo systemctl restart dovecot
sudo systemctl status dovecot
```

### 3. Create Test User

```bash
sudo adduser chichi
# Set password: butter (or your choice)
```

### 4. Verify Ports are Listening

**Check SMTP (port 25):**

```bash
sudo ss -tlnp | grep :25
```

**Check POP3 (port 110):**

```bash
sudo ss -tlnp | grep :110
```

### 5. Test with Telnet (Manual POP3 Session)

You can verify your mail server works by checking mail manually before running the Java program:

```bash
telnet localhost 110
```

Then type these commands one by one:

```
USER chichi
PASS butter
STAT
LIST
QUIT
```

Expected server responses at each step:

| Command   | Expected Response       | Meaning                  |
| --------- | ----------------------- | ------------------------ |
| (connect) | `+OK Dovecot ready`     | Server ready             |
| `USER`    | `+OK`                   | Username accepted        |
| `PASS`    | `+OK Logged in`         | Password accepted        |
| `STAT`    | `+OK 0 0`               | 0 messages, 0 bytes      |
| `LIST`    | `+OK ... .`             | Message list (if any)    |
| `QUIT`    | `+OK Logging out`       | Connection closed        |

### 6. Send Test Email

```bash
echo "Please reply to this" | mail -s "prac7" chichi@localhost
```

### 7. Check Your Mailbox

```bash
mail
```

Example of a successfully received email:

```
"/var/mail/chichi": 1 message 1 new
>N   1 root@localhost      Thu Apr 23 17:29  11/407   prac7
? 1
```

> **Note:** The Java program (`VacationResponder.java`) automates everything above.
> The telnet session is just for manual verification and understanding the protocol.

## Compile and Run

```bash
cd ~/prac7
javac *.java
java VacationResponder
```

### Example Output

```
╔══════════════════════════════════════════╗
║      VACATION AUTO-RESPONDER             ║
║      COS332 Practical 7                  ║
║      RFC 1939 POP3 + RFC 821 SMTP        ║
╚══════════════════════════════════════════╝

POP3 Server : localhost:110
SMTP Server : localhost:25
Checking as : chichi
Replying as : chichi@localhost
Trigger     : subject = 'prac7'
Interval    : 60 seconds

┌─────────────────────────────────────┐
│  VACATION RESPONDER MENU             │
│  1. Start vacation responder         │
│  2. Check mailbox once (test mode)   │
│  3. View replied addresses           │
│  4. Clear replied history            │
│  5. Exit                             │
└─────────────────────────────────────┘
Enter choice: 2

[RESPONDER] === Check #1 at Thu Apr 23 17:30:15 SAST 2026 ===
[POP3] Connecting to localhost:110...
[POP3] Server: +OK Dovecot ready.
[POP3] -> USER chichi
[POP3] <- +OK
[POP3] -> PASS butter
[POP3] <- +OK Logged in.
[POP3] Authenticated as: chichi
[POP3] -> STAT
[POP3] <- +OK 1 428
[POP3] Mailbox: 1 message(s), 428 bytes total
[RESPONDER] Found 1 message(s) to process.

[RESPONDER] Processing message 1 (428 bytes)...
[POP3] -> RETR 1
[RESPONDER] From   : root@localhost
[RESPONDER] Subject: prac7
[RESPONDER] Sending vacation reply to: root@localhost

[SMTP] Connecting to localhost:25...
[SMTP] Server: 220 zeze.localdomain ESMTP Postfix (Ubuntu)
[SMTP] -> EHLO COS332Client
[SMTP] ESMTP capabilities: [SMTPUTF8, PIPELINING, SIZE 10240000, ...]
[SMTP] -> MAIL FROM:<chichi@localhost>
[SMTP] -> RCPT TO:<root@localhost>
[SMTP] -> DATA
[SMTP] -> [message body: 1869 bytes]
[SMTP] -> QUIT

[TRACKER] Recorded reply to: root@localhost
[RESPONDER] Reply sent and recorded.
[RESPONDER] Processing complete. 1 reply/replies sent.
[POP3] -> RSET
[POP3] <- +OK
[POP3] All deletion marks removed
[POP3] -> QUIT
[POP3] Disconnected.
```

## Vacation Message Format

The program sends auto-replies in this format:

```
From: chichi@localhost
To: sender@example.com
Subject: Re: I am currently on vacation
Date: Thu, 23 Apr 2026 17:32:01 +0200
Message-ID: <1713891121.1@hostname>
MIME-Version: 1.0
Content-Type: multipart/alternative; boundary="----=_Part_..."

[Plain text version]
Thank you for your email.

I am currently on vacation and will respond at the time that I told you I will.
I will only be responding to this message when I get back.

This is an automated response — please do not reply to this message.

Kind regards,
Chichi

[HTML version with styled card layout]
```

## Edge Cases Handled

- **Subject filtering:** Only responds to messages with subject exactly "prac7" (case-insensitive). This prevents accidental activation during development.
- **Duplicate prevention:** Never sends more than one reply to the same email address. Tracked via `replied.txt` which persists across program restarts.
- **Mailing lists:** Detects and skips mailing list messages via `List-Id`, `Precedence: bulk`, and `Precedence: list` headers (RFC 2369).
- **System addresses:** Skips daemon addresses like `mailer-daemon`, `postmaster`, `no-reply`, `noreply`.
- **Invalid senders:** Skips messages with empty or malformed From addresses.
- **Non-destructive:** Uses `RETR` to read messages and always calls `RSET` before `QUIT` to ensure no messages are deleted.
- **Header folding:** Correctly parses multi-line headers that continue with whitespace (RFC 5322 Section 2.2.3).
- **Multiple formats:** Handles various From: header formats like `john@example.com`, `<john@example.com>`, and `"John Smith" <john@example.com>`.

## Configuration Files

### VacationResponder.java Settings

Edit these constants in the source code before compiling:

```java
private static final String POP3_HOST     = "localhost";
private static final int    POP3_PORT     = 110;
private static final String POP3_USER     = "chichi";
private static final String POP3_PASS     = "butter";

private static final String SMTP_HOST     = "localhost";
private static final int    SMTP_PORT     = 25;

private static final String MY_EMAIL      = "chichi@localhost";
private static final String MY_NAME       = "Chichi";

private static final int    CHECK_INTERVAL = 60_000; // milliseconds
```

### config.properties (Optional)

```properties
# POP3 Server Configuration
pop3.host=localhost
pop3.port=110
pop3.user=chichi
pop3.pass=butter

# SMTP Server Configuration
smtp.host=localhost
smtp.port=25
smtp.from=chichi@localhost
```

## POP3 Protocol Rules (RFC 1939)

Every POP3 command ends with `\r\n`. The typical conversation flow:

| Command     | Response         | Meaning                       |
| ----------- | ---------------- | ----------------------------- |
| (connect)   | `+OK`            | Server ready                  |
| `USER name` | `+OK`            | Username accepted             |
| `PASS pwd`  | `+OK Logged in`  | Authentication successful     |
| `STAT`      | `+OK count size` | Message count and total bytes |
| `LIST`      | `+OK ... .`      | Individual message sizes      |
| `RETR n`    | `+OK ... .`      | Retrieve message n            |
| `RSET`      | `+OK`            | Cancel deletion marks         |
| `QUIT`      | `+OK Logging out`| Connection closed             |

## SMTP Protocol Rules (RFC 821)

Every SMTP command ends with `\r\n`. The typical conversation flow:

| Command              | Response | Meaning                                 |
| -------------------- | -------- | --------------------------------------- |
| (connect)            | `220`    | Server ready                            |
| `EHLO`               | `250`    | Client identified (ESMTP)               |
| `MAIL FROM:`         | `250`    | Sender accepted                         |
| `RCPT TO:`           | `250`    | Recipient accepted (repeat per address) |
| `DATA`               | `354`    | Ready to receive message body           |
| `<message>\r\n.\r\n` | `250`    | Message accepted                        |
| `QUIT`               | `221`    | Connection closed                       |

## RFC Compliance Details

### POP3 Protocol (RFC 1939)

Uses standard POP3 commands plus bonus features:
- `CAPA` - Server capability discovery (RFC 2449)
- `TOP n 0` - Retrieve headers only (bonus for mail manager use case)
- `UIDL` - Unique message IDs for tracking (bonus feature)

### SMTP Protocol (RFC 821 / RFC 2821)

Uses `EHLO` (Extended SMTP) instead of the older `HELO`, and parses the server's capability list in the multi-line `250` response.

### Email Message Format (RFC 5322)

```
From: chichi@localhost
To: sender@example.com
Subject: Re: I am currently on vacation
Date: Thu, 23 Apr 2026 17:32:01 +0200
Message-ID: <1713891121.1@hostname>
MIME-Version: 1.0
Content-Type: multipart/alternative; boundary="----=_Part_..."

[blank line]
[MIME parts follow]
```

## Bonus Features Implemented

### 1. RFC 5322 Compliant Headers

- `Date:` formatted as `EEE, dd MMM yyyy HH:mm:ss Z`
- Unique `Message-ID:` using timestamp + thread ID + hostname

### 2. ESMTP (EHLO)

Uses `EHLO` instead of deprecated `HELO`, parses server capabilities (SIZE, 8BITMIME, PIPELINING, etc.), and falls back to `HELO` for legacy servers.

### 3. Multi-Recipient Support

Issues one `RCPT TO:` per address - covers To and all CC entries from email headers.

### 4. MIME Multipart Messages

Sends both plain text and HTML versions in a `multipart/alternative` envelope per RFC 2045/2046. Modern clients render the HTML; terminal clients fall back to plain text.

### 5. Mailing List Detection

Implements RFC 2369 mailing list detection via `List-Id`, `Precedence: bulk`, and `Precedence: list` headers to prevent loop scenarios.

### 6. POP3 CAPA Command

Discovers server capabilities using the `CAPA` command (RFC 2449) for better protocol negotiation.

### 7. Header Folding Support

Correctly unfolds multi-line RFC 5322 headers that continue with whitespace per Section 2.2.3.

## MD5 Hash

For project submission:

```bash
md5sum VacationResponder.class
```

Expected output:

```
33dfe2377ca6e1916a951ca2971064f1  VacationResponder.class
```

## Demo Flow

A step-by-step walkthrough for demonstrating the system to a marker or teammate.

### Step 1 - Confirm mail servers are running

```bash
sudo systemctl status postfix
sudo systemctl status dovecot
```

Look for `Active: active` on both. If not running:

```bash
sudo systemctl start postfix
sudo systemctl start dovecot
```

### Step 2 - Clear any previous test data

```bash
# Delete old test emails
mail
d *
q

# Clear reply history (optional, for clean demo)
rm replied.txt
```

### Step 3 - Compile

```bash
javac *.java
```

### Step 4 - Send test email with subject "prac7"

```bash
echo "Please reply to this test message" | mail -s "prac7" chichi@localhost
```

Verify it arrived:

```bash
mail
```

Type `q` to quit without reading (we'll let the program process it).

### Step 5 - Run the program

```bash
java VacationResponder
```

Choose option **2** (Check mailbox once - test mode):

```
Enter choice: 2
```

Expected output shows:
- POP3 connection and authentication
- Message found with subject "prac7"
- All filter checks passing (not mailing list, valid sender, not replied before)
- SMTP connection to send reply
- "Reply sent and recorded" confirmation

```
[RESPONDER] Processing message 1 (428 bytes)...
[RESPONDER] From   : root@localhost
[RESPONDER] Subject: prac7
[RESPONDER] Sending vacation reply to: root@localhost

[SMTP] Connecting to localhost:25...
[SMTP] -> EHLO COS332Client
[SMTP] -> MAIL FROM:<chichi@localhost>
[SMTP] -> RCPT TO:<root@localhost>
[SMTP] -> DATA
[SMTP] -> [message body: 1869 bytes]

[TRACKER] Recorded reply to: root@localhost
[RESPONDER] Reply sent and recorded.
[RESPONDER] Processing complete. 1 reply/replies sent.
```

### Step 6 - Verify the vacation reply arrived

Check the sender's mailbox (in our test case, root):

```bash
sudo mail -u root
```

Type the message number to open it:

```
? 1
```

Point out the RFC 5322 headers:
- `Date:` header with proper timezone
- `Message-ID:` header with unique ID
- `MIME-Version: 1.0`
- `Content-Type: multipart/alternative`

### Step 7 - Demonstrate duplicate prevention

Send another test email:

```bash
echo "Another test message" | mail -s "prac7" chichi@localhost
```

Run the program again (option 2):

```
Enter choice: 2
```

Expected output shows the message is **skipped**:

```
[RESPONDER] SKIP — already replied to: root@localhost
```

### Step 8 - Show mailing list detection (optional bonus)

Create a test message with mailing list headers:

```bash
cat > /tmp/testmail.txt << 'EOF'
From: list@example.com
To: chichi@localhost
Subject: prac7
List-Id: <announcements.example.com>

This is from a mailing list.
EOF

sudo sendmail chichi@localhost < /tmp/testmail.txt
```

Run the program again - it should skip this message:

```
[RESPONDER] SKIP — message is from a mailing list
```

### Step 9 - Show the reply tracking file

```bash
cat replied.txt
```

Shows all addresses that have received replies:

```
root@localhost
```

### Step 10 - Clean up (optional)

```bash
rm replied.txt
mail
d *
q
```

## MD5 Hash

For project submission

MD5 - 33dfe2377ca6e1916a951ca2971064f1

```
RUN:

md5sum VacationResponder.class
33dfe2377ca6e1916a951ca2971064f1 VacationResponder.class
```

```bash
cd ~/prac7
tar -cvf ../prac7.tar *.java replied.txt README.md
```
 
To verify the archive contents:
 
```bash
tar -tvf ../prac7.tar
```
 
To extract in a test directory:
 
```bash
mkdir test_view
tar -xvf ../prac7.tar -C test_view
```

**Note:** Do not include `*.class` files in the tar archive, as they contain compilation timestamps that will change the MD5 hash.

## Troubleshooting

### "Connection refused" on port 110

```bash
sudo systemctl restart dovecot
sudo ss -tlnp | grep :110
```

### "Connection refused" on port 25

```bash
sudo systemctl restart postfix
sudo ss -tlnp | grep :25
```

### "Authentication failed"

```bash
# Verify user exists
id chichi

# Test POP3 login manually
telnet localhost 110
USER chichi
PASS butter
QUIT
```

### "No messages in mailbox"

```bash
# Send test email
echo "Test message" | mail -s "prac7" chichi@localhost

# Check mail spool
sudo ls -la /var/mail/chichi
```

### "Replied twice to same person"

```bash
# Check replied.txt exists and is writable
ls -la replied.txt
cat replied.txt

# Clear and re-test using menu option 4
java VacationResponder
```

### Email sent but not in recipient's mailbox

```bash
mailq                         # Check if stuck in queue
sudo tail -f /var/log/mail.log   # Check logs
```

## Technical Notes

### Header Folding (RFC 5322 Section 2.2.3)
The parser handles multi-line headers that continue with whitespace:
```
From: "John Smith"
 <john@example.com>
```

### Mailing List Detection (RFC 2369)
Detects lists via:
- `List-Id:` header (RFC 2919)
- `Precedence: bulk` header
- `Precedence: list` header

### Address Extraction
Handles multiple From: formats:
- `john@example.com`
- `<john@example.com>`
- `"John Smith" <john@example.com>`

### MIME Boundary
Generates unique boundaries using timestamp + hashCode:
```
----=_Part_1713361921_12345678
```

## Performance Considerations

- **Check Interval**: 60 seconds (configurable)
- **Memory Usage**: Minimal - only stores replied addresses
- **Network Efficiency**: Uses POP3 RETR only when necessary
- **File I/O**: Appends to replied.txt (not rewriting entire file)

## Security Considerations
**Warning**: This implementation uses plain-text authentication and unencrypted connections. Suitable for:
- Local testing
- Internal networks
- Educational purposes

**NOT suitable for:**
- Production environments
- Public networks  
- Sensitive data

For production use, implement:
- TLS/SSL encryption
- Secure authentication (SASL)
- Input validation
- Rate limiting

## Standards Compliance

- **RFC 821**: Simple Mail Transfer Protocol (SMTP)
- **RFC 1939**: Post Office Protocol - Version 3 (POP3)
- **RFC 2045**: MIME Part One - Format of Internet Message Bodies
- **RFC 2046**: MIME Part Two - Media Types
- **RFC 2369**: The Use of URLs as Meta-Syntax for Core Mail List Commands
- **RFC 2449**: POP3 Extension Mechanism
- **RFC 2821**: Extended SMTP (ESMTP)
- **RFC 5322**: Internet Message Format

## Assessment Criteria

**Base Mark (8/10)**:
- Working POP3 connection
- Working SMTP connection
- Correct subject filtering
- Duplicate prevention
- Mailing list detection
- Non-destructive operation

**Bonus Marks (9-10/10)**:
- ESMTP support
- RFC 5322 headers
- MIME multipart
- Multi-recipient support
- Additional RFC compliance

## Known Limitations

1. Plain-text authentication only (no encryption)
2. No support for IMAP protocol
3. Vacation message is hardcoded (not user-configurable via UI)
4. Check interval requires recompilation to change
5. Single-threaded (processes messages sequentially)

## Future Enhancements

- [ ] TLS/SSL support for secure connections
- [ ] IMAP protocol support
- [ ] Configurable vacation message via config file
- [ ] GUI interface
- [ ] Multi-threaded message processing
- [ ] Email templates
- [ ] Custom filtering rules
- [ ] Database backend for tracking


## License

This project is submitted as coursework for COS332 - Computer Networks.  
Academic use only.

## Acknowledgments

- RFC specifications from IETF
- COS332 course materials
- Postfix and Dovecot documentation

---

*Last Updated: 23 April 2026*
