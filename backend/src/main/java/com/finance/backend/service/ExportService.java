package com.finance.backend.service;

import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.entity.*;
import com.finance.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final TransactionRepository transactionRepository;
    private final PaymentCalendarRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final ForecastService forecastService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] exportTransactions(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByDateDesc(accountId);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Транзакции");
            
            Row header = sheet.createRow(0);
            String[] columns = {"Дата", "Тип", "Сумма", "Категория", "Описание"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(getHeaderStyle(workbook));
            }
            
            int rowNum = 1;
            for (Transaction t : transactions) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(t.getDate().format(DATE_FORMAT));
                row.createCell(1).setCellValue("INCOME".equals(t.getTransactionType()) ? "Доход" : "Расход");
                row.createCell(2).setCellValue(t.getAmount().doubleValue());
                row.createCell(3).setCellValue(t.getCategory() != null ? t.getCategory() : "");
                row.createCell(4).setCellValue(t.getDescription() != null ? t.getDescription() : "");
            }
            
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            Row infoRow = sheet.createRow(rowNum + 1);
            infoRow.createCell(0).setCellValue("Счёт:");
            infoRow.createCell(1).setCellValue(account.getAccountName());
            
            Row balanceRow = sheet.createRow(rowNum + 2);
            balanceRow.createCell(0).setCellValue("Баланс:");
            balanceRow.createCell(1).setCellValue(account.getBalance().doubleValue());
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Ошибка экспорта транзакций", e);
        }
    }

    public byte[] exportPayments(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));
        
        List<PaymentCalendar> payments = paymentRepository.findByCompanyId(companyId);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Платежи");
            
            Row header = sheet.createRow(0);
            String[] columns = {"Дата", "Название", "Сумма", "Категория", "Контрагент", "Статус"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(getHeaderStyle(workbook));
            }
            
            int rowNum = 1;
            for (PaymentCalendar p : payments) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getDueDate().format(DATE_FORMAT));
                row.createCell(1).setCellValue(p.getTitle());
                row.createCell(2).setCellValue(p.getAmount().doubleValue());
                row.createCell(3).setCellValue(p.getCategory() != null ? p.getCategory() : "");
                row.createCell(4).setCellValue(p.getCounterparty() != null ? p.getCounterparty() : "");
                
                String status = "";
                if ("PAID".equals(p.getStatus())) status = "Оплачено";
                else if (p.getDueDate().isBefore(LocalDate.now())) status = "Просрочено";
                else status = "Ожидает";
                row.createCell(5).setCellValue(status);
            }
            
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            Row infoRow = sheet.createRow(rowNum + 1);
            infoRow.createCell(0).setCellValue("Компания:");
            infoRow.createCell(1).setCellValue(company.getName());
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Ошибка экспорта платежей", e);
        }
    }

    public byte[] exportForecast(UUID companyId, int months, String strategy) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));
        
        ForecastResponse forecast = forecastService.generateForecast(companyId, months, strategy, 1.0);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Прогноз");
            
            Row header = sheet.createRow(0);
            String[] columns = {"Показатель", "Значение"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(getHeaderStyle(workbook));
            }
            
            int rowNum = 1;
            
            Row row1 = sheet.createRow(rowNum++);
            row1.createCell(0).setCellValue("Компания");
            row1.createCell(1).setCellValue(company.getName());
            
            Row row2 = sheet.createRow(rowNum++);
            row2.createCell(0).setCellValue("Дата формирования");
            row2.createCell(1).setCellValue(LocalDate.now().format(DATE_FORMAT));
            
            Row row3 = sheet.createRow(rowNum++);
            row3.createCell(0).setCellValue("Период прогноза");
            row3.createCell(1).setCellValue(months + " месяцев");
            
            Row row4 = sheet.createRow(rowNum++);
            row4.createCell(0).setCellValue("Прогнозируемые доходы");
            row4.createCell(1).setCellValue(forecast.getTotalProjectedInflow().doubleValue());
            
            Row row5 = sheet.createRow(rowNum++);
            row5.createCell(0).setCellValue("Прогнозируемые расходы");
            row5.createCell(1).setCellValue(forecast.getTotalProjectedOutflow().doubleValue());
            
            Row row6 = sheet.createRow(rowNum++);
            row6.createCell(0).setCellValue("Чистый денежный поток");
            row6.createCell(1).setCellValue(forecast.getNetCashFlow().doubleValue());
            
            Row row7 = sheet.createRow(rowNum++);
            row7.createCell(0).setCellValue("Метод прогноза");
            String methodName = "linearRegression".equals(strategy) ? "Линейная регрессия" : "Скользящее среднее";
            row7.createCell(1).setCellValue(methodName);
            
            Row emptyRow = sheet.createRow(rowNum++);
            Row noteRow = sheet.createRow(rowNum++);
            noteRow.createCell(0).setCellValue("Примечание:");
            noteRow.createCell(1).setCellValue("Прогноз построен на основе исторических данных.");
            
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Ошибка экспорта прогноза", e);
        }
    }

    private CellStyle getHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}