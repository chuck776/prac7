import java.io.*;
import java.util.*;

/**
 * Maintains a persistent list of email addresses that have already
 * received a vacation reply.
 *
 * Addresses are stored one per line in a plain text file (replied.txt).
 * This file persists across program restarts so we never reply twice
 * to the same sender even if the program is stopped and restarted.
 *
 * This solves the key requirement:
 * "It is sufficient to inform any given person only once that you
 *  are on vacation"
 */
public class RepliedTracker {

    private static final String REPLIED_FILE = "replied.txt";

    // In-memory set for fast lookups
    // HashSet gives O(1) lookup time regardless of how many
    // addresses are stored
    private final Set<String> repliedAddresses = new HashSet<>();

    // ----------------------------------------------------------------
    // CONSTRUCTOR — loads existing replied addresses from file
    // ----------------------------------------------------------------
    public RepliedTracker() {
        loadFromFile();
    }

    // ----------------------------------------------------------------
    // CHECK IF WE HAVE ALREADY REPLIED TO THIS ADDRESS
    // Case insensitive — john@example.com and JOHN@EXAMPLE.COM
    // are treated as the same address
    // ----------------------------------------------------------------
    public boolean hasReplied(String emailAddress) {
        return repliedAddresses.contains(
            emailAddress.toLowerCase().trim());
    }

    // ----------------------------------------------------------------
    // RECORD THAT WE HAVE REPLIED TO THIS ADDRESS
    // Adds to in-memory set AND immediately saves to file
    // so the record persists even if the program crashes
    // ----------------------------------------------------------------
    public void recordReply(String emailAddress) {
        String normalised = emailAddress.toLowerCase().trim();
        if (repliedAddresses.add(normalised)) {
            // Only write to file if it was actually new
            appendToFile(normalised);
            System.out.println("[TRACKER] Recorded reply to: " + normalised);
        }
    }

    // ----------------------------------------------------------------
    // LOAD ALL PREVIOUSLY REPLIED ADDRESSES FROM FILE
    // Called once at startup
    // ----------------------------------------------------------------
    private void loadFromFile() {
        File file = new File(REPLIED_FILE);
        if (!file.exists()) {
            System.out.println("[TRACKER] No replied.txt found " +
                "— starting fresh.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty()) {
                    repliedAddresses.add(line);
                }
            }
            System.out.println("[TRACKER] Loaded " +
                repliedAddresses.size() +
                " previously replied address(es).");
        } catch (IOException e) {
            System.out.println("[TRACKER] Could not load replied.txt: "
                + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // APPEND A SINGLE ADDRESS TO THE FILE
    // We append rather than rewrite the whole file for efficiency
    // and safety — if the program crashes mid-write we don't lose
    // the existing records
    // ----------------------------------------------------------------
    private void appendToFile(String emailAddress) {
        try (PrintWriter writer = new PrintWriter(
                new FileWriter(REPLIED_FILE, true))) {
            writer.println(emailAddress);
        } catch (IOException e) {
            System.out.println("[TRACKER] Could not save to replied.txt: "
                + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // CLEAR ALL RECORDS — useful for testing
    // Clears both in-memory set and the file
    // ----------------------------------------------------------------
    public void clearAll() {
        repliedAddresses.clear();
        try (PrintWriter writer = new PrintWriter(
                new FileWriter(REPLIED_FILE, false))) {
            // Overwrite with empty file
            writer.print("");
        } catch (IOException e) {
            System.out.println("[TRACKER] Could not clear replied.txt: "
                + e.getMessage());
        }
        System.out.println("[TRACKER] All records cleared.");
    }

    // ----------------------------------------------------------------
    // GET COUNT OF REPLIED ADDRESSES
    // ----------------------------------------------------------------
    public int count() {
        return repliedAddresses.size();
    }

    // ----------------------------------------------------------------
    // LIST ALL REPLIED ADDRESSES — useful for debugging
    // ----------------------------------------------------------------
    public void printAll() {
        System.out.println("[TRACKER] Replied addresses (" +
            repliedAddresses.size() + "):");
        for (String addr : repliedAddresses) {
            System.out.println("  " + addr);
        }
    }
}