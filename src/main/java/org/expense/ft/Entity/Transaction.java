package org.expense.ft.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Entity
@Data
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;
    private String category;          // e.g. UPI Payment
    private LocalDate date;           // Transaction date
    private String type;              // DEBIT / CREDIT
    private String reference;         // UPI reference number
    private String toUpi;             // Payee VPA
    private String payeeName;         // Merchant/Person name
    private String accountNumber;     // Last 4 digits
    private String bankName;          // e.g. HDFC Bank
    private Long emailUid;            // IMAP UID
    private Date emailReceivedDate;   // Email received timestamp
//    @Column(length = 2000)
//    private String notes;

    @Column(name = "created_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;


    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public Long getEmailUid() {
        return emailUid;
    }

    public void setEmailUid(Long emailUid) {
        this.emailUid = emailUid;
    }

    public Date getEmailReceivedDate() {
        return emailReceivedDate;
    }

    public void setEmailReceivedDate(Date emailReceivedDate) {
        this.emailReceivedDate = emailReceivedDate;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getToUpi() {
        return toUpi;
    }

    public void setToUpi(String toUpi) {
        this.toUpi = toUpi;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

//    public String getNotes() {
//        return notes;
//    }
//
//    public void setNotes(String notes) {
//        this.notes = notes;
//    }
}
