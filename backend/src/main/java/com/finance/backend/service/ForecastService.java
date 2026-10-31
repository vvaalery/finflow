package com.finance.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.dto.response.ForecastPointResponse;
import com.finance.backend.entity.CashFlowForecast;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.ForecastItem;
import com.finance.backend.entity.Transaction;
import com.finance.backend.repository.CashFlowForecastRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.ForecastItemRepository;
import com.finance.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForecastService {
    private final TransactionRepository transactionRepository;
    private final CompanyRepository companyRepository;
    private final CashFlowForecastRepository forecastRepository;
    private final ForecastItemRepository itemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ForecastResponse generateForecast(UUID companyId, int months, String strategy, Double multiplier) {
        if (months < 1 || months > 24) throw new IllegalArgumentException("Период прогноза: от 1 до 24 месяцев");
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new RuntimeException("Компания не найдена"));
        String scenario = normalizeScenario(strategy, multiplier);
        String algorithm = normalizeAlgorithm(strategy);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(12).withDayOfMonth(1);
        List<Transaction> history = transactionRepository.findByAccountCompanyIdAndDateBetween(companyId, startDate, endDate);

        List<BigDecimal> inflows = monthly(history, startDate, true);
        List<BigDecimal> outflows = monthly(history, startDate, false);
        double factor = switch (scenario) { case "OPTIMISTIC" -> 1.15; case "PESSIMISTIC" -> 0.85; default -> 1.0; };

        double projectedIn = project(inflows, months, algorithm) * factor;
        double projectedOut = project(outflows, months, algorithm) * (scenario.equals("PESSIMISTIC") ? 1.10 : 1.0);
        BigDecimal in = money(projectedIn), out = money(projectedOut), net = in.subtract(out);

        CashFlowForecast forecast = new CashFlowForecast();
        forecast.setCompany(company); forecast.setForecastPeriodStart(LocalDate.now()); forecast.setForecastPeriodEnd(LocalDate.now().plusMonths(months));
        forecast.setTotalProjectedInflow(in); forecast.setTotalProjectedOutflow(out); forecast.setNetCashFlow(net);
        forecast.setScenario(scenario); forecast.setStrategyUsed(algorithm);
        ObjectNode params = objectMapper.createObjectNode(); params.put("algorithm", algorithm); params.put("scenario", scenario); params.put("historyMonths", 12); params.put("forecastMonths", months); params.put("learningRate", 0.05); params.put("estimators", 40); params.put("treeDepth", 1);
        if ("GRADIENT_BOOSTING".equals(algorithm)) params.put("ensemble", "70% gradient boosting + 30% linear trend");
        forecast.setParameters(params);
        forecastRepository.save(forecast);

        List<ForecastPointResponse> points = new ArrayList<>();
        for (int i=1;i<=months;i++) {
            BigDecimal monthlyIn = money(projectPoint(inflows, i, algorithm) * factor);
            BigDecimal monthlyOut = money(projectPoint(outflows, i, algorithm) * (scenario.equals("PESSIMISTIC") ? 1.10 : 1.0));
            ForecastItem item = new ForecastItem();
            item.setForecast(forecast);
            item.setForecastDate(LocalDate.now().plusMonths(i));
            item.setProjectedInflow(monthlyIn);
            item.setProjectedOutflow(monthlyOut);
            itemRepository.save(item);
            points.add(new ForecastPointResponse(item.getForecastDate(), monthlyIn, monthlyOut, monthlyIn.subtract(monthlyOut)));
        }
        ForecastResponse response = toResponse(forecast, months + " месяцев");
        response.setItems(points);
        return response;
    }

    private String normalizeScenario(String strategy, Double multiplier) {
        if (strategy == null) return "BASE";
        String s = strategy.toUpperCase(Locale.ROOT);
        if (s.contains("OPTIMISTIC")) return "OPTIMISTIC";
        if (s.contains("PESSIMISTIC")) return "PESSIMISTIC";
        if (multiplier != null) return multiplier > 1 ? "OPTIMISTIC" : multiplier < 1 ? "PESSIMISTIC" : "BASE";
        return "BASE";
    }
    private String normalizeAlgorithm(String strategy) {
        if (strategy == null) return "GRADIENT_BOOSTING";
        String s=strategy.toLowerCase(Locale.ROOT);
        if (s.contains("moving")) return "MOVING_AVERAGE";
        if (s.contains("linear")) return "LINEAR_REGRESSION";
        return "GRADIENT_BOOSTING";
    }
    private List<BigDecimal> monthly(List<Transaction> history, LocalDate start, boolean income) {
        Map<String,List<Transaction>> grouped=history.stream().collect(Collectors.groupingBy(t->t.getDate().withDayOfMonth(1).toString()));
        List<BigDecimal> result=new ArrayList<>();
        for(int i=0;i<12;i++){String key=start.plusMonths(i).withDayOfMonth(1).toString(); result.add(grouped.getOrDefault(key,List.of()).stream().filter(t->income?"INCOME".equals(t.getTransactionType()):"EXPENSE".equals(t.getTransactionType())).map(Transaction::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));}
        return result;
    }
    private double project(List<BigDecimal> history, int months, String algorithm) {
        double total = 0;
        for (int step = 1; step <= months; step++) total += projectPoint(history, step, algorithm);
        return total;
    }

    private double projectPoint(List<BigDecimal> history, int step, String algorithm) {
        if (history.stream().allMatch(value -> value.compareTo(BigDecimal.ZERO) == 0)) return 0;
        return switch (algorithm) {
            case "MOVING_AVERAGE" -> history.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            case "LINEAR_REGRESSION" -> linearPoint(history, step);
            default -> gradientBoostingPoint(history, step);
        };
    }

    private double linearPoint(List<BigDecimal> history, int step) {
        int n = history.size();
        double sx = 0, sy = 0, sxy = 0, sx2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i + 1, y = history.get(i).doubleValue();
            sx += x; sy += y; sxy += x * y; sx2 += x * x;
        }
        double denominator = n * sx2 - sx * sx;
        if (denominator == 0) return history.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double slope = (n * sxy - sx * sy) / denominator;
        double intercept = (sy - slope * sx) / n;
        return Math.max(0, slope * (n + step) + intercept);
    }

    /** Gradient boosting regression with an additive ensemble of shallow regression trees. */
    private double gradientBoostingPoint(List<BigDecimal> history, int step) {
        int n = history.size();
        double[] y = new double[n];
        for (int i = 0; i < n; i++) y[i] = history.get(i).doubleValue();
        double base = Arrays.stream(y).average().orElse(0);
        double[] predictions = new double[n];
        Arrays.fill(predictions, base);
        List<Stump> ensemble = new ArrayList<>();
        double learningRate = 0.05;
        for (int iteration = 0; iteration < 40; iteration++) {
            double[] residuals = new double[n];
            for (int i = 0; i < n; i++) residuals[i] = y[i] - predictions[i];
            Stump best = null;
            double bestLoss = Double.POSITIVE_INFINITY;
            for (int threshold = 1; threshold < n; threshold++) {
                double leftSum = 0, rightSum = 0;
                int leftCount = 0, rightCount = 0;
                for (int i = 0; i < n; i++) {
                    if (i < threshold) { leftSum += residuals[i]; leftCount++; }
                    else { rightSum += residuals[i]; rightCount++; }
                }
                double leftValue = leftCount == 0 ? 0 : leftSum / leftCount;
                double rightValue = rightCount == 0 ? 0 : rightSum / rightCount;
                double loss = 0;
                for (int i = 0; i < n; i++) {
                    double error = residuals[i] - (i < threshold ? leftValue : rightValue);
                    loss += error * error;
                }
                if (loss < bestLoss) { bestLoss = loss; best = new Stump(threshold, leftValue, rightValue); }
            }
            if (best == null) break;
            ensemble.add(best);
            for (int i = 0; i < n; i++) predictions[i] += learningRate * best.predict(i);
        }
        int futureIndex = n + step - 1;
        double treePrediction = base;
        for (Stump tree : ensemble) treePrediction += learningRate * tree.predict(futureIndex);
        // Tree models are piecewise constant beyond the training range. Blend in a
        // modest linear trend so monthly forecasts do not collapse to one flat value.
        double trendPrediction = linearPoint(history, step);
        return Math.max(0, treePrediction * 0.70 + trendPrediction * 0.30);
    }

    private record Stump(int threshold, double left, double right) {
        double predict(int x) { return x < threshold ? left : right; }
    }
    private BigDecimal money(double v){return BigDecimal.valueOf(Math.max(0,v)).setScale(2,RoundingMode.HALF_UP);}

    public ForecastResponse getLatestForecast(UUID companyId){return forecastRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().findFirst().map(f->toResponse(f,f.getForecastPeriodStart()+" - "+f.getForecastPeriodEnd())).orElseThrow(()->new RuntimeException("Прогнозы не найдены"));}
    public List<ForecastResponse> getForecastHistory(UUID companyId){return forecastRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(f->toResponse(f,f.getForecastPeriodStart()+" - "+f.getForecastPeriodEnd())).toList();}
    private ForecastResponse toResponse(CashFlowForecast f,String period){ForecastResponse r=new ForecastResponse();r.setId(f.getId());r.setTotalProjectedInflow(f.getTotalProjectedInflow());r.setTotalProjectedOutflow(f.getTotalProjectedOutflow());r.setNetCashFlow(f.getNetCashFlow());r.setStrategyUsed(f.getStrategyUsed()+" / "+f.getScenario());r.setPeriod(period);r.setItems(itemRepository.findByForecastIdOrderByForecastDate(f.getId()).stream().map(i -> new ForecastPointResponse(i.getForecastDate(), i.getProjectedInflow(), i.getProjectedOutflow(), i.getProjectedInflow().subtract(i.getProjectedOutflow()))).toList());return r;}
}
