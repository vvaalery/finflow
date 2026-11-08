package com.finance.backend.service;

import com.finance.backend.analytics.SeriesAnalyzer;
import com.finance.backend.analytics.SeriesStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsInsightsService {
    public static final int MIN_MONTHS = 6;

    private final CashFlowHistoryService historyService;

    public record AnomalyView(String month, String series, double value, double expected, double score) {}
    public record CategoryShare(String name, double amount, double sharePercent) {}
    public record Insights(boolean sufficient, int months, String from, String to,
                           SeriesStats inflow, SeriesStats outflow, SeriesStats net,
                           Double savingsRatePercent, Double averageNetLast3, Double runwayMonths,
                           BigDecimal cashBalance, String currency,
                           List<AnomalyView> anomalies, List<CategoryShare> expenseCategories, List<String> findings) {}

    public Insights insights(UUID companyId) {
        CashFlowHistoryService.History h = historyService.load(companyId);
        BigDecimal balance = historyService.baseCurrencyBalance(companyId);
        if (h.months() < MIN_MONTHS) {
            return new Insights(false, h.months(), h.months() == 0 ? null : h.start().toString(), h.months() == 0 ? null : h.end().toString(),
                    null, null, null, null, null, null, balance, CashFlowHistoryService.BASE_CURRENCY, List.of(), List.of(),
                    List.of("Для статистического анализа нужно минимум " + MIN_MONTHS + " завершённых месяцев операций; сейчас — " + h.months() + "."));
        }
        SeriesStats in = SeriesAnalyzer.analyze(h.inflow()), out = SeriesAnalyzer.analyze(h.outflow()), net = SeriesAnalyzer.analyze(h.net());

        double totalIn = Arrays.stream(h.inflow()).sum(), totalOut = Arrays.stream(h.outflow()).sum();
        Double savings = totalIn > 0 ? (totalIn - totalOut) / totalIn * 100 : null;
        int n = h.months();
        double avgNet3 = (sum(h.net(), n - 3, n)) / 3;
        Double runway = avgNet3 < 0 && balance.signum() > 0 ? balance.doubleValue() / -avgNet3 : null;

        List<AnomalyView> anomalies = new ArrayList<>();
        collect(anomalies, "Поступления", in, h);
        collect(anomalies, "Расходы", out, h);
        anomalies.sort(Comparator.comparing(AnomalyView::month).reversed());

        double catTotal = h.expenseByCategory().values().stream().mapToDouble(Double::doubleValue).sum();
        List<CategoryShare> cats = h.expenseByCategory().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(6)
                .map(e -> new CategoryShare(e.getKey(), e.getValue(), catTotal > 0 ? e.getValue() / catTotal * 100 : 0)).toList();

        return new Insights(true, n, h.start().toString(), h.end().toString(), in, out, net, savings, avgNet3, runway,
                balance, CashFlowHistoryService.BASE_CURRENCY, anomalies, cats, findings(in, out, net, savings, runway, anomalies, h));
    }

    private static void collect(List<AnomalyView> target, String label, SeriesStats s, CashFlowHistoryService.History h) {
        for (SeriesStats.Anomaly a : s.anomalies())
            target.add(new AnomalyView(h.start().plusMonths(a.index()).toString(), label, a.value(), a.expected(), a.score()));
    }

    private static List<String> findings(SeriesStats in, SeriesStats out, SeriesStats net, Double savings, Double runway,
                                         List<AnomalyView> anomalies, CashFlowHistoryService.History h) {
        List<String> f = new ArrayList<>();
        f.add(trendText("Поступления", in));
        f.add(trendText("Расходы", out));
        if (savings != null) f.add(String.format(Locale.forLanguageTag("ru"), "Норма накопления за период: %.1f%% от поступлений.", savings));
        if (in.cv() > 0.35) f.add(String.format(Locale.forLanguageTag("ru"), "Поступления нестабильны: коэффициент вариации %.0f%% — прогноз будет менее точным.", in.cv() * 100));
        if (in.seasonalStrength() >= 0.4) f.add(String.format(Locale.forLanguageTag("ru"), "Обнаружена сезонность поступлений (сила %.0f%%).", in.seasonalStrength() * 100));
        if (runway != null) f.add(String.format(Locale.forLanguageTag("ru"), "При текущем среднем дефиците запаса на счетах в BYN хватит примерно на %.1f мес.", runway));
        YearMonth lastMonth = h.end();
        long recent = anomalies.stream().filter(a -> YearMonth.parse(a.month()).isAfter(lastMonth.minusMonths(3))).count();
        if (recent > 0) f.add("За последние 3 месяца есть аномальные значения: " + recent + " — проверьте крупные операции.");
        return f;
    }

    private static String trendText(String label, SeriesStats s) {
        Locale ru = Locale.forLanguageTag("ru");
        return switch (s.trend()) {
            case "UP" -> String.format(ru, "%s растут: %+.1f%% в месяц (R² = %.2f).", label, s.slopePercent(), s.r2());
            case "DOWN" -> String.format(ru, "%s снижаются: %+.1f%% в месяц (R² = %.2f).", label, s.slopePercent(), s.r2());
            default -> label + " без статистически значимого тренда.";
        };
    }

    private static double sum(double[] v, int from, int to) {
        double s = 0;
        for (int i = Math.max(0, from); i < to; i++) s += v[i];
        return s;
    }
}
