package org.expense.ft.Controller;

import org.expense.ft.Entity.Transaction;
import org.expense.ft.Service.ImapTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final ImapTransactionService transactionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public TransactionController(ImapTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/fetch")
    public String fetchTransactions(@RequestBody FetchRequest request) {
        try {
            transactionService.fetchAndSaveTransactions(
                    request.getEmail(),
                    request.getPassword(),
                    request.getImapHost(),
                    request.getBankEmails(),
                    request.getFromDate(),
                    request.getToDate()
            );
            return "Transactions fetched successfully!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/sum")
    public Long getSum(){
        return transactionService.getSum().get();
    }

    @GetMapping
    public Page<Transaction> getTransactions(
            @RequestParam(required = false) String fromDate,@RequestParam(required = false) String toDate,
            @RequestParam(required = false) String toUpi,
            @RequestParam(required = false, defaultValue = "false") boolean toUpiExact,
            @RequestParam(required = false) String payeeName,
            @RequestParam(required = false, defaultValue = "false") boolean payeeNameExact,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(required = false) String bankName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        LocalDate from = parseDate(fromDate);
        LocalDate to = parseDate(toDate);
//        return null;

        return transactionService.getTransactions(
                from, to,
                toUpi, toUpiExact,
                payeeName, payeeNameExact,
                type,
                amountMin, amountMax,
                bankName,
                page, size,
                sortBy, sortDir
        );
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        return LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
    }

    @GetMapping("/days")
    public Page<Transaction> getForDay(@RequestParam String date,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        LocalDate day = LocalDate.parse(date);
        return transactionService.getTransactions(
                day, day, null, false, null, false,
                null, null, null, null, page, size,
                "date", "desc"
        );
    }


    @PostMapping("/run")
    public List<Map<String, Object>> runQuery(@RequestBody Map<String, String> request) {
        String sql = request.get("query");
        try {
            return jdbcTemplate.query(sql, new ColumnMapRowMapper());
        } catch (Exception e) {
            throw new RuntimeException("Error executing query: " + e.getMessage());
        }
    }

    @GetMapping("/range")
    public Page<Transaction> getForRange(@RequestParam String from,
                                         @RequestParam String to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        return transactionService.getTransactions(
                fromDate, toDate, null, false, null, false,
                null, null, null, null, page, size,
                "date", "desc"
        );
    }

    @GetMapping("/month")
    public Page<Transaction> getForMonth(@RequestParam int year,
                                         @RequestParam int month,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        LocalDate fromDate = LocalDate.of(year, month, 1);
        LocalDate toDate = fromDate.withDayOfMonth(fromDate.lengthOfMonth());
        return transactionService.getTransactions(
                fromDate, toDate, null, false, null, false,
                null, null, null, null, page, size,
                "date", "desc"
        );
    }

    @GetMapping("/last3months")
    public Page<Transaction> getForLast3Months(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusMonths(3);
        return transactionService.getTransactions(
                fromDate, toDate, null, false, null, false,
                null, null, null, null, page, size,
                "date", "desc"
        );
    }

    @GetMapping("/last6months")
    public Page<Transaction> getForLast6Months(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusMonths(6);
        return transactionService.getTransactions(
                fromDate, toDate, null, false, null, false,
                null, null, null, null, page, size,
                "date", "desc"
        );
    }

    @GetMapping("/year")
    public Page<Transaction> getForYear(@RequestParam int year,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        LocalDate fromDate = LocalDate.of(year, 1, 1);
        LocalDate toDate = LocalDate.of(year, 12, 31);
        return transactionService.getTransactions(
                fromDate, toDate, null, false, null, false,
                null, null, null, null, page, size,
                "date", "desc"
        );
    }

    @GetMapping("/year-range")
    public Page<Transaction> getForYearRange(@RequestParam int fromYear,
                                             @RequestParam int toYear,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        LocalDate fromDate = LocalDate.of(fromYear, 1, 1);
        LocalDate toDate = LocalDate.of(toYear, 12, 31);
        return transactionService.getTransactions(
                fromDate, toDate, null, false, null, false,
                null, null, null, null, page, size,
                "date", "desc"
        );
    }

    @Scheduled(fixedDelay = 9000000,initialDelay = 10000) // 300,000 ms = 5 minutes
    public void scheduleTransactionFetch() {
        try {
            transactionService.fetchAndSaveTransactions(
                    "praguru14@gmail.com",                 // email
                    "xnwb dfrg zyoe fjes",                 // password (App password)
                    "imap.gmail.com",                      // IMAP host
                    List.of("alerts@hdfcbank.net"),        // bank emails
                    "",                                    // fromDate (optional)
                    ""                                     // toDate (optional)
            );
        } catch (Exception e) {
//            log.error("Error while fetching transactions", e);
        }
    }

    @GetMapping("/total/month")
    public BigDecimal getTotalSpentInMonth(@RequestParam int year,
                                           @RequestParam int month) {
        return transactionService.getTotalSpentInMonth(year, month);
    }

    @GetMapping("/total/dates")
    public BigDecimal getTotalSpentOnDates(@RequestParam String dates) {
        return transactionService.getTotalSpentOnDates(dates);
    }
    @GetMapping("/total/by-dates")
    public Map<LocalDate, BigDecimal> getTotalByDates(@RequestParam String dates) {
        return transactionService.getTotalByDates(dates);
    }
    @GetMapping("/day")
    public List<Transaction> getTransactionsForDay(@RequestParam String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_DATE);
        return transactionService.getTransactionsForDay(localDate);
    }


}

class FetchRequest {
    private String email;
    private String password;
    private String imapHost;
    private List<String> bankEmails;
    private String fromDate;
    private String toDate;

    // Getters & setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getImapHost() { return imapHost; }
    public void setImapHost(String imapHost) { this.imapHost = imapHost; }

    public List<String> getBankEmails() { return bankEmails; }
    public void setBankEmails(List<String> bankEmails) { this.bankEmails = bankEmails; }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }
}
