package org.expense.ft.Service;

import jakarta.persistence.criteria.Predicate;
import org.expense.ft.Entity.Transaction;
import org.expense.ft.Repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImapTransactionService {

    private final TransactionRepository repository;

    public ImapTransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public void fetchAndSaveTransactions(String email, String password, String imapHost,
                                         List<String> bankEmails, String fromDateStr, String toDateStr) throws Exception {

        LocalDate today = LocalDate.now();

        LocalDate fromDate = (fromDateStr == null || fromDateStr.isEmpty()) ? today : LocalDate.parse(fromDateStr);
        LocalDate toDate = (toDateStr == null || toDateStr.isEmpty()) ? LocalDate.of(today.getYear(), 1, 1) : LocalDate.parse(toDateStr);

        if (fromDateStr != null && !fromDateStr.isEmpty() &&
                toDateStr != null && !toDateStr.isEmpty() &&
                fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.port", "993");

        Session session = Session.getInstance(props);
        session.setDebug(true);

        IMAPStore store = null;
        IMAPFolder inbox = null;

        try {
            store = (IMAPStore) session.getStore("imaps");
            store.connect(imapHost, email, password);

            inbox = (IMAPFolder) store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            long lastProcessedUid = repository.findMaxUid().orElse(0L);
            System.out.println("Last processed UID from DB: " + lastProcessedUid);

            Message[] messages;

            if (lastProcessedUid > 0) {
                messages = inbox.getMessagesByUID(lastProcessedUid + 1, UIDFolder.LASTUID);
            } else {
                Date from = java.sql.Date.valueOf(fromDate);
                Date to = java.sql.Date.valueOf(toDate);
                SearchTerm dateTerm = new AndTerm(
                        new ReceivedDateTerm(ComparisonTerm.GE, from),
                        new ReceivedDateTerm(ComparisonTerm.LE, to)
                );
                messages = inbox.search(dateTerm);
            }

            System.out.println("Total messages fetched: " + messages.length);

            for (Message message : messages) {
                long uid = inbox.getUID(message);
                if (uid <= lastProcessedUid) continue;

                String fromEmail = message.getFrom()[0].toString();
                if (!bankEmails.isEmpty() && bankEmails.stream().noneMatch(fromEmail::contains)) continue;

                String bankName = bankEmails.stream()
                        .filter(fromEmail::contains)
                        .findFirst()
                        .orElse("UNKNOWN");

                String body = getTextFromMessage(message);
                Date receivedDate = message.getReceivedDate();

                if (parseAndSave(body, uid, receivedDate, bankName)) {
                    message.setFlag(Flags.Flag.SEEN, true);
                    System.out.println("✅ Processed UID: " + uid);
                }
            }

        } finally {
            if (inbox != null && inbox.isOpen()) inbox.close(false);
            if (store != null) store.close();
        }
    }

    private String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) return message.getContent().toString();
        else if (message.isMimeType("text/html")) return cleanEmailBody(message.getContent().toString());
        else if (message.isMimeType("multipart/*")) return getTextFromMimeMultipart((MimeMultipart) message.getContent());
        else return "";
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < mimeMultipart.getCount(); i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) result.append(bodyPart.getContent());
            else if (bodyPart.isMimeType("text/html")) result.append(cleanEmailBody(bodyPart.getContent().toString()));
            else if (bodyPart.getContent() instanceof MimeMultipart)
                result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
        }
        return result.toString();
    }

    private boolean parseAndSave(String body, long uid, Date emailReceivedDate, String bankName) {
        if (body == null || body.isEmpty()) return false;
        String test = body.toLowerCase();
        if (test.contains("credit card") && (body.contains("1132") || body.contains("7586"))) {
            System.out.println("⏩ Skipped credit card message UID: " + uid);
            return false;
        }

        // Step 1: Match amount and rest of the body
        Pattern mainPattern = Pattern.compile("(?i)(?:Rs\\.|INR)\\s?(\\d+\\.\\d{2})\\s?(.*)");
        Matcher mainMatcher = mainPattern.matcher(body);

        if (!mainMatcher.find()) return false;

        BigDecimal amount = new BigDecimal(mainMatcher.group(1));
        String rest = mainMatcher.group(2);

        // Step 2: Extract account number
        Pattern accountPattern = Pattern.compile("from account \\*?(\\d+)");
        Matcher mAccount = accountPattern.matcher(rest);
        String account = mAccount.find() ? mAccount.group(1) : "";

        // Step 3: Extract VPA
        Pattern vpaPattern = Pattern.compile("to VPA\\s([\\w.@_-]+)");
        Matcher mVpa = vpaPattern.matcher(rest);
        String vpa = mVpa.find() ? mVpa.group(1) : "";

        // Step 4: Extract Payee
        Pattern payeePattern = Pattern.compile("to VPA\\s[\\w.@_-]+\\s(.+?)\\s+on\\s\\d{2}-\\d{2}-\\d{2}");
        Matcher mPayee = payeePattern.matcher(rest);
        String payee = mPayee.find() ? mPayee.group(1).trim() : "";

        // Step 5: Extract Date
        Pattern datePattern = Pattern.compile("on\\s(\\d{2}-\\d{2}-\\d{2})");
        Matcher mDate = datePattern.matcher(rest);
        LocalDate txnDate = mDate.find() ? LocalDate.parse(mDate.group(1), DateTimeFormatter.ofPattern("dd-MM-yy")) : null;

        // Step 6: Extract Reference
        Pattern refPattern = Pattern.compile("reference number (?:is|no\\.)\\s?(\\d+)");
        Matcher mRef = refPattern.matcher(rest);
        String ref = mRef.find() ? mRef.group(1) : "";

        if (ref.isEmpty() || txnDate == null || repository.existsByReferenceAndDateAndAmount(ref, txnDate, amount))
            return false;

        // Save transaction
        Transaction txn = new Transaction();
        txn.setAmount(amount);
        txn.setAccountNumber(account);
        txn.setToUpi(vpa);
        txn.setPayeeName(payee);
        txn.setDate(txnDate);
        txn.setReference(ref);

        String lowerBody = body.toLowerCase();
        if (lowerBody.matches(".*\\b(debit|debited)\\b.*")) txn.setType("DEBIT");
        else if (lowerBody.matches(".*\\b(credit|credited)\\b.*")) txn.setType("CREDIT");
        else txn.setType("UNKNOWN");

        txn.setCategory("UPI Payment");
        txn.setBankName(bankName);
        txn.setEmailUid(uid);
        txn.setEmailReceivedDate(emailReceivedDate);
        txn.setCreatedDate(new Date());

        repository.save(txn);
        System.out.println("✅ Saved transaction: " + txn);
        return true;
    }

    private String cleanEmailBody(String body) {
        if (body == null) return "";
        body = body.replaceAll("(?s)<style.*?>.*?</style>", " ");
        body = body.replaceAll("(?s)@media[^{]+\\{[^}]+\\}", " ");
        body = body.replaceAll("<[^>]*>", " ");
        return body.replaceAll("\\s+", " ").trim();
    }
    public BigDecimal getTotalSpentInMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return repository.getTotalSpentBetween(start, end);
    }

    public BigDecimal getTotalSpentOnDates(String datesStr) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        List<LocalDate> dates = Arrays.stream(datesStr.split(","))
                .map(s -> s.trim().replace("/", "-"))
                .map(s -> LocalDate.parse(s, fmt))
                .toList();

        return repository.getTotalSpentOnDates(dates);
    }
    public Map<LocalDate, BigDecimal> getTotalByDates(String datesStr) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        List<LocalDate> dates = Arrays.stream(datesStr.split(","))
                .map(s -> s.trim().replace("/", "-"))
                .map(s -> LocalDate.parse(s, fmt))
                .toList();

        List<Object[]> results = repository.getTotalSpentByDates(dates);

        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (Object[] row : results) {
            LocalDate d = (LocalDate) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            totals.put(d, sum);
        }

        for (LocalDate d : dates) {
            totals.putIfAbsent(d, BigDecimal.ZERO);
        }

        return totals;
    }

    public Page<Transaction> getTransactions(
            LocalDate fromDate,
            LocalDate toDate,
            String toUpi,
            boolean toUpiExact,
            String payeeName,
            boolean payeeNameExact,
            String type,
            BigDecimal amountMin,
            BigDecimal amountMax,
            String bankName,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        if (fromDate == null && toDate != null) fromDate = toDate;
        if (toDate == null && fromDate != null) toDate = fromDate;

        Pageable pageable = PageRequest.of(
                page,
                size,
                sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending()
        );

        LocalDate finalFromDate = fromDate;
        LocalDate finalToDate = toDate;
        Specification<Transaction> spec = (root, query, cb) -> {
            Predicate p = cb.conjunction();

            if (finalFromDate != null && finalToDate != null) {
                p = cb.and(p, cb.between(root.get("date"), finalFromDate, finalToDate));
            }

            if (toUpi != null && !toUpi.isEmpty()) {
                if (toUpiExact) p = cb.and(p, cb.equal(root.get("toUpi"), toUpi));
                else p = cb.and(p, cb.like(cb.lower(root.get("toUpi")), "%" + toUpi.toLowerCase() + "%"));
            }

            if (payeeName != null && !payeeName.isEmpty()) {
                if (payeeNameExact) p = cb.and(p, cb.equal(root.get("payeeName"), payeeName));
                else p = cb.and(p, cb.like(cb.lower(root.get("payeeName")), "%" + payeeName.toLowerCase() + "%"));
            }

            p = cb.and(p, cb.equal(root.get("type"), "DEBIT"));

            if (amountMin != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.get("amount"), amountMin));
            }
            if (amountMax != null) {
                p = cb.and(p, cb.lessThanOrEqualTo(root.get("amount"), amountMax));
            }

            if (bankName != null && !bankName.isEmpty()) {
                p = cb.and(p, cb.equal(root.get("bankName"), bankName));
            }

            return p;
        };

        return repository.findAll(spec, pageable);
    }

    public List<Transaction> getTransactionsForDay(LocalDate date) {
        return repository.findByDate(date);
    }
    public Optional<Long> getSum(){
        return repository.getSum();
    }
}
