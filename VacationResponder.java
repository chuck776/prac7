import java.util.*;

/**
 * Vacation Auto-Responder
 *
 * Periodically checks a POP3 mailbox for new messages and sends
 * an automated vacation reply via SMTP to qualifying senders.
 *
 * Rules (as per assignment requirements):
 *   1. ONLY RESPOND TO  messages with subject "prac7"
 *   2. NEVER reply  to the same sender twice
 *   3. Never replies to mailing lists (RFC 2369)
 *   4. DO NOT DELETE  the original messages from the server
 *   5. Never replies to daemon/system addresses
 */
public class VacationResponder {

    // ----------------------------------------------------------------
    // CONFIGURATION
    // ----------------------------------------------------------------
    private static final String POP3_HOST     = "localhost";
    private static final int    POP3_PORT     = 110;
    private static final String POP3_USER     = "chichi";
    private static final String POP3_PASS     = "butter";

    private static final String SMTP_HOST     = "localhost";
    private static final int    SMTP_PORT     = 25;

    private static final String MY_EMAIL      = "chichi@localhost";
    private static final String MY_NAME       = "Chichi";

    // How often to check for new mail (milliseconds)
    // 60000 = 1 minute
    private static final int    CHECK_INTERVAL = 60_000;

    // Vacation message
    private static final String VACATION_SUBJECT =
        "Re: I am currently on vacation";

    private static final String VACATION_BODY =
        "Thank you for your email.\n\n" +
        "I am currently on vacation and will at the time that i told you i will.\n" +
        "I will only be responding to this message when i get back.\n\n" +
        "upon my return.\n\n" +
        "This is an automated response — please do not reply " +
        "to this message.\n\n" +
        "Kind regards,\n" + MY_NAME;

    // ----------------------------------------------------------------
    // INSTANCE FIELDS
    // ----------------------------------------------------------------
    private final RepliedTracker tracker;
    private boolean running = true;

    // ----------------------------------------------------------------
    // CONSTRUCTOR
    // ----------------------------------------------------------------
    public VacationResponder() {
        this.tracker = new RepliedTracker();
    }

    // ----------------------------------------------------------------
    // MAIN ENTRY POINT
    // ----------------------------------------------------------------
    public static void main(String[] args) {
        VacationResponder responder = new VacationResponder();

        // Print startup banner
        responder.printBanner();

        // Show menu before starting
        responder.showStartupMenu();
    }

    // ----------------------------------------------------------------
    // STARTUP MENU
    // ----------------------------------------------------------------
    private void showStartupMenu() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n┌─────────────────────────────────────┐");
            System.out.println("│  VACATION RESPONDER MENU             │");
            System.out.println("│  1. Start vacation responder         │");
            System.out.println("│  2. Check mailbox once (test mode)   │");
            System.out.println("│  3. View replied addresses           │");
            System.out.println("│  4. Clear replied history            │");
            System.out.println("│  5. Exit                             │");
            System.out.println("└─────────────────────────────────────┘");
            System.out.print("Enter choice: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    startResponder();
                    break;
                case "2":
                    checkOnce();
                    break;
                case "3":
                    tracker.printAll();
                    break;
                case "4":
                    tracker.clearAll();
                    break;
                case "5":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    // ----------------------------------------------------------------
    // START THE CONTINUOUS RESPONDER LOOP
    // Checks mailbox every CHECK_INTERVAL milliseconds
    // Press Ctrl+C to stop
    // ----------------------------------------------------------------
    private void startResponder() {
        System.out.println("\n[RESPONDER] Starting vacation responder...");
        System.out.println("[RESPONDER] Checking every " +
            (CHECK_INTERVAL / 1000) + " seconds.");
        System.out.println("[RESPONDER] Press Ctrl+C to stop.\n");

        running = true;
        int checkCount = 0;

        while (running) {
            checkCount++;
            System.out.println("\n[RESPONDER] === Check #" + checkCount +
                " at " + new java.util.Date() + " ===");

            checkOnce();

            System.out.println("[RESPONDER] Next check in " +
                (CHECK_INTERVAL / 1000) + " seconds...");

            try {
                Thread.sleep(CHECK_INTERVAL);
            } catch (InterruptedException e) {
                System.out.println("[RESPONDER] Interrupted — stopping.");
                running = false;
            }
        }
    }

    // ----------------------------------------------------------------
    // CHECK MAILBOX ONCE
    // Connects to POP3, checks all messages, sends replies as needed
    // then disconnects WITHOUT deleting any messages
    // ----------------------------------------------------------------
    private void checkOnce() {
        Pop3Connection pop3 = new Pop3Connection(POP3_HOST, POP3_PORT);

        try {
            // Step 1 — Connect and authenticate
            pop3.connect();
            pop3.authenticate(POP3_USER, POP3_PASS);

            // Step 2 — Check how many messages are waiting
            int[] stat    = pop3.stat();
            int msgCount  = stat[0];

            if (msgCount == 0) {
                System.out.println("[RESPONDER] No messages found.");
                pop3.quitWithoutDeleting();
                return;
            }

            System.out.println("[RESPONDER] Found " + msgCount +
                " message(s) to process.");

            // Step 3 — Get sizes of all messages
            Map<Integer, Integer> sizes = pop3.list();

            // Step 4 — Process each message
            int repliesSent = 0;
            for (int i = 1; i <= msgCount; i++) {
                int size = sizes.getOrDefault(i, 0);
                boolean replied = processMessage(pop3, i, size);
                if (replied) repliesSent++;
            }

            System.out.println("[RESPONDER] Processing complete. " +
                repliesSent + " reply/replies sent.");

            // Step 5 — Quit WITHOUT deleting
            // Assignment requires we leave all mail on server
            pop3.quitWithoutDeleting();

        } catch (Exception e) {
            System.out.println("[RESPONDER] Error: " + e.getMessage());
            e.printStackTrace();
            try { pop3.quitWithoutDeleting(); } catch (Exception ignored) {}
        }
    }

    // ----------------------------------------------------------------
    // PROCESS A SINGLE MESSAGE
    // Returns true if a vacation reply was sent
    // ----------------------------------------------------------------
    private boolean processMessage(Pop3Connection pop3,
                                   int msgNum, int size) {
        try {
            System.out.println("\n[RESPONDER] Processing message " +
                msgNum + " (" + size + " bytes)...");

            // Retrieve full message using RETR
            // We need the full headers to parse From and Subject
            String rawMessage = pop3.retr(msgNum);

            // Parse headers
            EmailParser.ParsedEmail email =
                EmailParser.parse(rawMessage, msgNum, size);

            System.out.println("[RESPONDER] From   : " + email.from);
            System.out.println("[RESPONDER] Subject: " + email.subject);

            // --------------------------------------------------------
            // RULE 1: Only respond to subject "prac7"
            // This prevents the responder from firing during
            // development and testing
            // --------------------------------------------------------
            if (!EmailParser.isVacationTrigger(email)) {
                System.out.println("[RESPONDER] SKIP — subject is not " +
                    "'prac7' (got: '" + email.subject + "')");
                return false;
            }

            // --------------------------------------------------------
            // RULE 2: Never reply to mailing lists (RFC 2369)
            // Check for List-Id, Precedence: bulk/list headers
            // --------------------------------------------------------
            if (EmailParser.isMailingList(email)) {
                System.out.println("[RESPONDER] SKIP — message is from " +
                    "a mailing list");
                return false;
            }

            // --------------------------------------------------------
            // RULE 3: Never reply to invalid/daemon addresses
            // --------------------------------------------------------
            if (!EmailParser.isValidSender(email)) {
                System.out.println("[RESPONDER] SKIP — invalid sender " +
                    "address: " + email.fromAddress);
                return false;
            }

            // --------------------------------------------------------
            // RULE 4: Never reply to the same sender twice
            // --------------------------------------------------------
            if (tracker.hasReplied(email.fromAddress)) {
                System.out.println("[RESPONDER] SKIP — already replied " +
                    "to: " + email.fromAddress);
                return false;
            }

            // --------------------------------------------------------
            // All rules passed — send vacation reply
            // --------------------------------------------------------
            System.out.println("[RESPONDER] Sending vacation reply to: " +
                email.fromAddress);

            boolean sent = sendVacationReply(email.fromAddress,
                email.subject);

            if (sent) {
                // Record that we replied to this address
                tracker.recordReply(email.fromAddress);
                System.out.println("[RESPONDER] Reply sent and recorded.");
                return true;
            } else {
                System.out.println("[RESPONDER] Failed to send reply.");
                return false;
            }

        } catch (Exception e) {
            System.out.println("[RESPONDER] Error processing message " +
                msgNum + ": " + e.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------------
    // SEND VACATION REPLY VIA SMTP
    // Uses SmtpConnection and EmailMessage from Prac 6
    // Returns true if email was sent successfully
    // ----------------------------------------------------------------
    private boolean sendVacationReply(String toAddress,
                                      String originalSubject) {
        SmtpConnection smtp = new SmtpConnection(SMTP_HOST, SMTP_PORT);

        try {
            // Build the vacation reply email
            EmailMessage email = new EmailMessage.Builder()
                .from(MY_EMAIL)
                .to(toAddress)
                .subject(VACATION_SUBJECT)
                .body(VACATION_BODY)
                .build();

            // Connect and send
            smtp.connect();
            smtp.sendEmail(email);
            smtp.quit();

            return true;

        } catch (Exception e) {
            System.out.println("[SMTP] Failed to send reply: " +
                e.getMessage());
            try { smtp.quit(); } catch (Exception ignored) {}
            return false;
        }
    }

    // ----------------------------------------------------------------
    // PRINT STARTUP BANNER
    // ----------------------------------------------------------------
    private void printBanner() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      VACATION AUTO-RESPONDER             ║");
        System.out.println("║      COS332 Practical 7                  ║");
        System.out.println("║      RFC 1939 POP3 + RFC 821 SMTP        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();
        System.out.println("POP3 Server : " + POP3_HOST + ":" + POP3_PORT);
        System.out.println("SMTP Server : " + SMTP_HOST + ":" + SMTP_PORT);
        System.out.println("Checking as : " + POP3_USER);
        System.out.println("Replying as : " + MY_EMAIL);
        System.out.println("Trigger     : subject = 'prac7'");
        System.out.println("Interval    : " +
            (CHECK_INTERVAL / 1000) + " seconds");
        System.out.println();
    }
}