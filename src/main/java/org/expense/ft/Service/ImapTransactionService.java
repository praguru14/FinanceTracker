package org.expense.ft.Service;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import org.expense.ft.Entity.Transaction;
import org.expense.ft.Repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImapTransactionService {

    private final TransactionRepository repository;

    public ImapTransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public void fetchAndSaveTransactions(String email, String password, String imapHost, List<String> bankEmails) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        Session session = Session.getInstance(props);

        Store store = session.getStore("imaps");
        store.connect(imapHost, email, password);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE); // allows marking messages as read

        // Fetch only unread messages to avoid duplicates
        Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

        for (Message message : messages) {
            String from = message.getFrom()[0].toString();
            boolean bankMatch = bankEmails.stream().anyMatch(from::contains);
            if (!bankMatch) continue;

            String body = getTextFromMessage(message);
            if (parseAndSave(body)) {
                // Mark email as read after successful processing
                message.setFlag(Flags.Flag.SEEN, true);
            }
        }

        inbox.close(false);
        store.close();
    }

    private String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < mimeMultipart.getCount(); i++) {
                BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain") || bodyPart.isMimeType("text/html")) {
                    result.append(bodyPart.getContent().toString());
                }
            }
            return result.toString();
        }
        return "";
    }

    private boolean parseAndSave(String body) {
        if (body == null || body.isEmpty()) return false;

        // Robust regex to capture UPI transactions
        Pattern pattern = Pattern.compile(
                "(?i)(?:Rs\\.|INR)\\s?(\\d+\\.\\d{2}).*?to VPA\\s([\\w.@_-]+).*?on\\s(\\d{2}-\\d{2}-\\d{2}).*?reference number (?:is|no\\.)\\s?(\\d+)"
        );

        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            Transaction txn = new Transaction();
                txn.setAmount(new BigDecimal(matcher.group(1)));
            txn.setToUpi(matcher.group(2));
            txn.setDate(LocalDate.parse(matcher.group(3), DateTimeFormatter.ofPattern("dd-MM-yy")));
            txn.setReference(matcher.group(4));

            // Determine transaction type
            String lowerBody = body.toLowerCase();
            if (lowerBody.contains("debited")) {
                txn.setType("DEBIT");
            } else if (lowerBody.contains("credited")) {
                txn.setType("CREDIT");
            } else {
                txn.setType("UNKNOWN");
            }

            txn.setCategory("UPI Payment");
            txn.setNotes(body);

            repository.save(txn);
            System.out.println("Saved transaction: " + txn); // optional logging
            return true;
        }
        return false;
    }
}
