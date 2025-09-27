package org.expense.ft.Repository;


import org.expense.ft.Entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    @Query("SELECT MAX(t.emailUid) FROM Transaction t")
    Optional<Long> findMaxUid();

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END " +
            "FROM Transaction t " +
            "WHERE t.reference = :reference AND t.date = :date AND t.amount = :amount and t.type='DEBIT'")
    boolean existsByReferenceAndDateAndAmount(@Param("reference") String reference,
                                              @Param("date") LocalDate date,
                                              @Param("amount") BigDecimal amount);

    @Query("SELECT SUM(t.amount) FROM Transaction t where t.type='DEBIT'")
    Optional<Long> getSum();

    // Sum between any two dates
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.date BETWEEN :fromDate AND :toDate and t.type='DEBIT'")
    BigDecimal getTotalSpentBetween(@Param("fromDate") LocalDate fromDate,
                                    @Param("toDate") LocalDate toDate);

    // Daily total
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.date IN :dates AND t.type = 'DEBIT'")
    BigDecimal getTotalSpentOnDates(@Param("dates") List<LocalDate> dates);

    @Query("SELECT t.date, COALESCE(SUM(t.amount), 0) " +
            "FROM Transaction t " +
            "WHERE t.date IN :dates AND t.type = 'DEBIT' " +
            "GROUP BY t.date")
    List<Object[]> getTotalSpentByDates(@Param("dates") List<LocalDate> dates);

    List<Transaction> findByDate(LocalDate date);
}
