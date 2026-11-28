package com.finance.backend.service;

import com.finance.backend.dto.response.ImportResult;
import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.entity.Transaction;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.TransactionRepository;
import com.finance.kafka.event.EventTypes;
import com.finance.kafka.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImportService {
    private final TransactionRepository transactionRepository;
    private final PaymentCalendarRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final CompanyDirectoryService directory;
    private final OutboxEventService outbox;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional
    public ImportResult importTransactions(MultipartFile file, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        if (isCsv(file)) return importTransactionRows(readCsv(file), account);
        return importExcelTransactions(file, account);
    }

    @Transactional
    public ImportResult importPayments(MultipartFile file, UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));
        if (isCsv(file)) return importPaymentRows(readCsv(file), company);
        return importExcelPayments(file, company);
    }

    private boolean isCsv(MultipartFile f) {
        String n = Optional.ofNullable(f.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        return n.endsWith(".csv") || "text/csv".equalsIgnoreCase(f.getContentType());
    }

    private ImportResult importTransactionRows(List<String[]> rows, Account account) {
        ImportResult r = new ImportResult();
        r.setTotalRows(rows.size());
        BigDecimal delta = BigDecimal.ZERO;
        int ok = 0;
        int failed = 0;

        for (int i = 0; i < rows.size(); i++) {
            try {
                String[] a = rows.get(i);
                delta = delta.add(saveRow(account, LocalDate.parse(a[0].trim(), DATE_FORMAT), a[1].trim(),
                        a[2].trim(), a.length > 3 ? a[3].trim() : "", a.length > 4 ? a[4].trim() : ""));
                r.addSuccess();
                ok++;
            } catch (Exception e) {
                r.addError("Строка " + (i + 2) + ": " + e.getMessage());
                failed++;
            }
        }

        finishImport(account, delta, ok, failed, rows.size());
        return r;
    }

    private ImportResult importPaymentRows(List<String[]> rows, Company company) {
        ImportResult r = new ImportResult();
        r.setTotalRows(rows.size());

        for (int i = 0; i < rows.size(); i++) {
            try {
                String[] a = rows.get(i);
                PaymentCalendar p = new PaymentCalendar();
                p.setCompany(company);
                p.setTitle(a[0].trim());
                p.setAmount(new BigDecimal(a[1].trim()));
                p.setDueDate(LocalDate.parse(a[2].trim(), DATE_FORMAT));
                p.setCategory(a.length > 3 ? a[3].trim() : "");
                p.setCounterparty(a.length > 4 ? a[4].trim() : "");
                p.setStatus("PENDING");
                paymentRepository.save(p);
                r.addSuccess();
            } catch (Exception e) {
                r.addError("Строка " + (i + 2) + ": " + e.getMessage());
            }
        }

        return r;
    }

    private ImportResult importExcelTransactions(MultipartFile file, Account account) {
        ImportResult r = new ImportResult();
        BigDecimal delta = BigDecimal.ZERO;
        int ok = 0;
        int failed = 0;
        int total = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            total = Math.max(0, s.getPhysicalNumberOfRows() - 1);
            r.setTotalRows(total);

            for (int i = 1; i < s.getPhysicalNumberOfRows(); i++) {
                Row row = s.getRow(i);
                if (row == null) continue;

                try {
                    delta = delta.add(saveRow(account, LocalDate.parse(cell(row, 0), DATE_FORMAT), cell(row, 1),
                            cell(row, 2), cell(row, 3), cell(row, 4)));
                    r.addSuccess();
                    ok++;
                } catch (Exception e) {
                    r.addError("Строка " + (i + 1) + ": " + e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            r.addError("Ошибка чтения Excel: " + e.getMessage());
        }

        finishImport(account, delta, ok, failed, total);
        return r;
    }

    private ImportResult importExcelPayments(MultipartFile file, Company company) {
        ImportResult r = new ImportResult();

        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            r.setTotalRows(Math.max(0, s.getPhysicalNumberOfRows() - 1));

            for (int i = 1; i < s.getPhysicalNumberOfRows(); i++) {
                Row row = s.getRow(i);
                if (row == null) continue;

                try {
                    PaymentCalendar p = new PaymentCalendar();
                    p.setCompany(company);
                    p.setTitle(cell(row, 0));
                    p.setAmount(new BigDecimal(cell(row, 1)));
                    p.setDueDate(LocalDate.parse(cell(row, 2), DATE_FORMAT));
                    p.setCategory(cell(row, 3));
                    p.setCounterparty(cell(row, 4));
                    p.setStatus("PENDING");
                    paymentRepository.save(p);
                    r.addSuccess();
                } catch (Exception e) {
                    r.addError("Строка " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            r.addError("Ошибка чтения Excel: " + e.getMessage());
        }

        return r;
    }

    private BigDecimal saveRow(Account account, LocalDate date, String rawType, String rawAmount,
                               String category, String description) {
        String type = rawType.toUpperCase(Locale.ROOT);
        if (!Set.of("INCOME", "EXPENSE").contains(type)) {
            throw new IllegalArgumentException("Тип INCOME или EXPENSE");
        }

        Transaction t = new Transaction();
        t.setAccount(account);
        t.setDate(date);
        t.setTransactionType(type);
        t.setAmount(new BigDecimal(rawAmount));
        t.setCategory(category);
        t.setDescription(description);
        transactionRepository.save(t);

        return "INCOME".equals(type) ? t.getAmount() : t.getAmount().negate();
    }

    private void finishImport(Account account, BigDecimal delta, int ok, int failed, int total) {
        if (delta.signum() != 0) {
            directory.applyBalance(account, delta, UUID.randomUUID());
        }

        if (ok > 0) {
            outbox.enqueue(EventTypes.TRANSACTIONS_IMPORTED, account.getId(), account.getCompany().getId(),
                    "accountId", account.getId(), "imported", ok, "failed", failed, "totalRows", total,
                    "balanceAfter", account.getBalance().toPlainString());
        }
    }

    private String cell(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        if (c.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return c.toString().trim();
    }

    private List<String[]> readCsv(MultipartFile file) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = new ArrayList<>();
            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                if (first) {
                    first = false;
                    continue;
                }
                rows.add(parseCsvLine(line));
            }

            return rows;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения CSV", e);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean quote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quote = !quote;
            } else if (c == ',' && !quote) {
                out.add(b.toString());
                b.setLength(0);
            } else {
                b.append(c);
            }
        }

        out.add(b.toString());
        return out.toArray(String[]::new);
    }
}
