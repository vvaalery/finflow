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
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional
    public ImportResult importTransactions(MultipartFile file, UUID accountId) {
        Account account=accountRepository.findById(accountId).orElseThrow(()->new RuntimeException("Счёт не найден"));
        if (isCsv(file)) return importTransactionRows(readCsv(file), account);
        return importExcelTransactions(file,account);
    }
    @Transactional
    public ImportResult importPayments(MultipartFile file, UUID companyId) {
        Company company=companyRepository.findById(companyId).orElseThrow(()->new RuntimeException("Компания не найдена"));
        if (isCsv(file)) return importPaymentRows(readCsv(file),company);
        return importExcelPayments(file,company);
    }
    private boolean isCsv(MultipartFile f){String n=Optional.ofNullable(f.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);return n.endsWith(".csv") || "text/csv".equalsIgnoreCase(f.getContentType());}

    private ImportResult importTransactionRows(List<String[]> rows,Account account){ImportResult r=new ImportResult();r.setTotalRows(rows.size());for(int i=0;i<rows.size();i++){try{String[] a=rows.get(i);Transaction t=new Transaction();t.setAccount(account);t.setDate(LocalDate.parse(a[0].trim(),DATE_FORMAT));String type=a[1].trim().toUpperCase();if(!Set.of("INCOME","EXPENSE").contains(type))throw new IllegalArgumentException("Тип INCOME или EXPENSE");t.setTransactionType(type);t.setAmount(positiveAmount(a[2]));t.setCategory(a.length>3?a[3].trim():"");t.setDescription(a.length>4?a[4].trim():"");transactionRepository.save(t);account.setBalance("INCOME".equals(type)?account.getBalance().add(t.getAmount()):account.getBalance().subtract(t.getAmount()));r.addSuccess();}catch(Exception e){r.addError("Строка "+(i+2)+": "+e.getMessage());}}accountRepository.save(account);return r;}
    private ImportResult importPaymentRows(List<String[]> rows,Company company){ImportResult r=new ImportResult();r.setTotalRows(rows.size());for(int i=0;i<rows.size();i++){try{String[] a=rows.get(i);PaymentCalendar p=new PaymentCalendar();p.setCompany(company);p.setTitle(a[0].trim());p.setAmount(positiveAmount(a[1]));p.setDueDate(LocalDate.parse(a[2].trim(),DATE_FORMAT));p.setCategory(a.length>3?a[3].trim():"");p.setCounterparty(a.length>4?a[4].trim():"");p.setStatus("PENDING");paymentRepository.save(p);r.addSuccess();}catch(Exception e){r.addError("Строка "+(i+2)+": "+e.getMessage());}}return r;}

    private ImportResult importExcelTransactions(MultipartFile file,Account account){ImportResult r=new ImportResult();try(InputStream in=file.getInputStream();Workbook wb=new XSSFWorkbook(in)){Sheet s=wb.getSheetAt(0);r.setTotalRows(Math.max(0,s.getPhysicalNumberOfRows()-1));for(int i=1;i<s.getPhysicalNumberOfRows();i++){Row row=s.getRow(i);if(row==null)continue;try{Transaction t=new Transaction();t.setAccount(account);t.setDate(LocalDate.parse(cell(row,0),DATE_FORMAT));String type=cell(row,1).trim().toUpperCase(Locale.ROOT);if(!Set.of("INCOME","EXPENSE").contains(type))throw new IllegalArgumentException("Тип INCOME или EXPENSE");t.setTransactionType(type);t.setAmount(positiveAmount(cell(row,2)));t.setCategory(cell(row,3));t.setDescription(cell(row,4));transactionRepository.save(t);account.setBalance("INCOME".equals(type)?account.getBalance().add(t.getAmount()):account.getBalance().subtract(t.getAmount()));r.addSuccess();}catch(Exception e){r.addError("Строка "+(i+1)+": "+e.getMessage());}}accountRepository.save(account);}catch(Exception e){r.addError("Ошибка чтения Excel: "+e.getMessage());}return r;}
    private ImportResult importExcelPayments(MultipartFile file,Company company){ImportResult r=new ImportResult();try(InputStream in=file.getInputStream();Workbook wb=new XSSFWorkbook(in)){Sheet s=wb.getSheetAt(0);r.setTotalRows(Math.max(0,s.getPhysicalNumberOfRows()-1));for(int i=1;i<s.getPhysicalNumberOfRows();i++){Row row=s.getRow(i);if(row==null)continue;try{PaymentCalendar p=new PaymentCalendar();p.setCompany(company);p.setTitle(cell(row,0));p.setAmount(positiveAmount(cell(row,1)));p.setDueDate(LocalDate.parse(cell(row,2),DATE_FORMAT));p.setCategory(cell(row,3));p.setCounterparty(cell(row,4));p.setStatus("PENDING");paymentRepository.save(p);r.addSuccess();}catch(Exception e){r.addError("Строка "+(i+1)+": "+e.getMessage());}}}catch(Exception e){r.addError("Ошибка чтения Excel: "+e.getMessage());}return r;}
    private BigDecimal positiveAmount(String raw) {
        BigDecimal amount = new BigDecimal(raw.trim());
        if (amount.signum() <= 0) throw new IllegalArgumentException("Сумма должна быть больше нуля");
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }
    private String cell(Row row,int idx){Cell c=row.getCell(idx);if(c==null)return "";if(c.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(c))return c.getLocalDateTimeCellValue().toLocalDate().toString();if(c.getCellType()==CellType.NUMERIC)return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();return c.toString().trim();}
    private List<String[]> readCsv(MultipartFile file){try(BufferedReader br=new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))){List<String[]> rows=new ArrayList<>();String line;boolean first=true;while((line=br.readLine())!=null){if(line.isBlank())continue;if(first){first=false;continue;}rows.add(parseCsvLine(line));}return rows;}catch(IOException e){throw new RuntimeException("Ошибка чтения CSV",e);}}
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }
        if (quoted) throw new IllegalArgumentException("Незакрытая кавычка в CSV");
        values.add(value.toString().trim());
        return values.toArray(String[]::new);
    }
}
