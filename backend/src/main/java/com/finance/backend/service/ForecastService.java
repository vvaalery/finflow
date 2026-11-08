package com.finance.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.backend.analytics.*;
import com.finance.backend.dto.response.ForecastPointResponse;
import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.entity.CashFlowForecast;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.ForecastItem;
import com.finance.backend.repository.CashFlowForecastRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.ForecastItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ForecastService {
    public static final int MIN_HISTORY_MONTHS = 6;

    private final CompanyRepository companyRepository;
    private final CashFlowForecastRepository forecastRepository;
    private final ForecastItemRepository itemRepository;
    private final CashFlowHistoryService historyService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ForecastResponse generateForecast(UUID companyId, int months, String strategy, Double multiplier) {
        return generateForecast(companyId, months, ForecastMethod.parse(strategy), legacyScenario(strategy, multiplier));
    }

    @Transactional
    public ForecastResponse generateForecast(UUID companyId, int months, ForecastMethod method, Scenario scenario) {
        if (months < 1 || months > 24) throw new IllegalArgumentException("Период прогноза: от 1 до 24 месяцев");
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));

        CashFlowHistoryService.History history = historyService.load(companyId);
        if (history.months() < MIN_HISTORY_MONTHS) {
            throw new IllegalArgumentException("Недостаточно истории для прогноза: нужно минимум " + MIN_HISTORY_MONTHS
                    + " завершённых месяцев с операциями в BYN, сейчас — " + history.months() + ".");
        }

        ForecastEngine.Result result = ForecastEngine.forecast(method, scenario, history.inflow(), history.outflow(), history.start(), months);
        BigDecimal balance = historyService.baseCurrencyBalance(companyId);

        List<ForecastPointResponse> points = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO, totalOut = BigDecimal.ZERO, running = balance;
        for (ForecastEngine.Point p : result.points()) {
            BigDecimal in = money(p.inflow()), out = money(p.outflow()), net = in.subtract(out);
            running = running.add(net);
            totalIn = totalIn.add(in); totalOut = totalOut.add(out);
            points.add(new ForecastPointResponse(p.month().atDay(1), in, out, net,
                    money(p.inflowLow()), money(p.inflowHigh()), money(p.outflowLow()), money(p.outflowHigh()),
                    money(p.netLow()), money(p.netHigh()), running));
        }
        BigDecimal totalNet = totalIn.subtract(totalOut);

        CashFlowForecast forecast = new CashFlowForecast();
        forecast.setCompany(company);
        forecast.setForecastPeriodStart(points.get(0).getDate());
        forecast.setForecastPeriodEnd(YearMonth.from(points.get(points.size() - 1).getDate()).atEndOfMonth());
        forecast.setTotalProjectedInflow(totalIn); forecast.setTotalProjectedOutflow(totalOut); forecast.setNetCashFlow(totalNet);
        forecast.setScenario(scenario.name()); forecast.setStrategyUsed(method.name());
        forecast.setParameters(buildParameters(history, months, result, points, balance));
        forecastRepository.save(forecast);

        for (ForecastPointResponse p : points) {
            ForecastItem item = new ForecastItem();
            item.setForecast(forecast); item.setForecastDate(p.getDate());
            item.setProjectedInflow(p.getInflow()); item.setProjectedOutflow(p.getOutflow());
            itemRepository.save(item);
        }

        ForecastResponse response = baseResponse(forecast, points, history, balance);
        response.setPeriod(months + " мес.");
        response.setQuality(quality(result.quality()));
        response.setModelParams(modelParams(result));
        response.setWarnings(warnings(history, result));
        response.setComparison(comparison(history, method));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ForecastResponse.ModelScore> compareMethods(UUID companyId, ForecastMethod selected) {
        CashFlowHistoryService.History h = historyService.load(companyId);
        if (h.months() < MIN_HISTORY_MONTHS) return List.of();
        return comparison(h, selected);
    }

    @Transactional(readOnly = true)
    public ForecastResponse getLatestForecast(UUID companyId) {
        CashFlowForecast f = forecastRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Прогнозы не найдены"));
        return restore(f);
    }

    @Transactional(readOnly = true)
    public List<ForecastResponse> getForecastHistory(UUID companyId) {
        return forecastRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::restore).toList();
    }

    private ForecastResponse restore(CashFlowForecast f) {
        JsonNode params = f.getParameters();
        Map<LocalDate, JsonNode> stored = new HashMap<>();
        if (params != null && params.has("points")) for (JsonNode n : params.get("points")) stored.put(LocalDate.parse(n.get("date").asText()), n);
        List<ForecastPointResponse> points = new ArrayList<>();
        for (ForecastItem i : itemRepository.findByForecastIdOrderByForecastDate(f.getId())) {
            BigDecimal in = i.getProjectedInflow(), out = i.getProjectedOutflow();
            ForecastPointResponse p = new ForecastPointResponse(i.getForecastDate(), in, out, in.subtract(out));
            JsonNode n = stored.get(i.getForecastDate());
            if (n != null) {
                p.setInflowLow(dec(n, "inflowLow")); p.setInflowHigh(dec(n, "inflowHigh"));
                p.setOutflowLow(dec(n, "outflowLow")); p.setOutflowHigh(dec(n, "outflowHigh"));
                p.setNetLow(dec(n, "netLow")); p.setNetHigh(dec(n, "netHigh")); p.setProjectedBalance(dec(n, "projectedBalance"));
            }
            points.add(p);
        }
        CashFlowHistoryService.History history = historyService.load(f.getCompany().getId());
        BigDecimal balance = params != null && params.has("currentBalance") ? new BigDecimal(params.get("currentBalance").asText())
                : historyService.baseCurrencyBalance(f.getCompany().getId());
        ForecastResponse r = baseResponse(f, points, history, balance);
        r.setPeriod(f.getForecastPeriodStart() + " — " + f.getForecastPeriodEnd());
        if (params != null && params.has("quality")) {
            JsonNode q = params.get("quality");
            r.setQuality(new ForecastResponse.Quality(q.path("available").asBoolean(false), q.path("windows").asInt(0),
                    dbl(q, "mae"), dbl(q, "rmse"), dbl(q, "wape"), q.path("note").asText(null)));
        }
        if (params != null && params.has("modelParams")) r.setModelParams(objectMapper.convertValue(params.get("modelParams"), Map.class));
        return r;
    }

    private ForecastResponse baseResponse(CashFlowForecast f, List<ForecastPointResponse> points,
                                          CashFlowHistoryService.History history, BigDecimal balance) {
        ForecastMethod m = ForecastMethod.parse(f.getStrategyUsed());
        ForecastResponse r = new ForecastResponse();
        r.setId(f.getId());
        r.setTotalProjectedInflow(f.getTotalProjectedInflow()); r.setTotalProjectedOutflow(f.getTotalProjectedOutflow());
        r.setNetCashFlow(f.getNetCashFlow());
        r.setStrategyUsed(m.label + " / " + f.getScenario());
        r.setMethod(m.name()); r.setMethodLabel(m.label); r.setMethodFamily(m.family); r.setScenario(f.getScenario());
        r.setItems(points);
        r.setCurrentBalance(balance);
        r.setProjectedClosingBalance(points.isEmpty() ? balance : points.get(points.size() - 1).getProjectedBalance());
        List<ForecastResponse.HistoryPoint> hp = new ArrayList<>();
        int from = Math.max(0, history.months() - 12);
        for (int i = from; i < history.months(); i++) {
            BigDecimal in = money(history.inflow()[i]), out = money(history.outflow()[i]);
            hp.add(new ForecastResponse.HistoryPoint(history.start().plusMonths(i).atDay(1), in, out, in.subtract(out)));
        }
        r.setHistory(hp);
        return r;
    }

    private ObjectNode buildParameters(CashFlowHistoryService.History history, int months, ForecastEngine.Result result,
                                       List<ForecastPointResponse> points, BigDecimal balance) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("algorithm", result.method().name()); params.put("scenario", result.scenario().name());
        params.put("historyMonths", history.months()); params.put("forecastMonths", months);
        params.put("currentBalance", balance.toPlainString());
        params.set("modelParams", objectMapper.valueToTree(modelParams(result)));
        ForecastResponse.Quality q = quality(result.quality());
        params.set("quality", objectMapper.valueToTree(q));
        ArrayNode arr = params.putArray("points");
        for (ForecastPointResponse p : points) {
            ObjectNode n = arr.addObject();
            n.put("date", p.getDate().toString());
            n.put("inflowLow", p.getInflowLow()); n.put("inflowHigh", p.getInflowHigh());
            n.put("outflowLow", p.getOutflowLow()); n.put("outflowHigh", p.getOutflowHigh());
            n.put("netLow", p.getNetLow()); n.put("netHigh", p.getNetHigh());
            n.put("projectedBalance", p.getProjectedBalance());
        }
        return params;
    }

    private Map<String, Object> modelParams(ForecastEngine.Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inflow", r.inflowParams());
        m.put("outflow", r.outflowParams());
        return m;
    }

    private ForecastResponse.Quality quality(Backtester.Result q) {
        if (!q.available()) return new ForecastResponse.Quality(false, 0, null, null, null,
                "Для оценки точности нужно минимум 7 месяцев истории");
        Backtester.Metrics n = q.net();
        return new ForecastResponse.Quality(true, q.windows(), round(n.mae()), round(n.rmse()), round(n.wape() * 100),
                "Скользящий бэктест по чистому потоку: прогноз на 1–3 месяца вперёд на исторических окнах");
    }

    private List<ForecastResponse.ModelScore> comparison(CashFlowHistoryService.History h, ForecastMethod selected) {
        List<ForecastEngine.Score> scores = ForecastEngine.compare(h.inflow(), h.outflow(), h.start());
        ForecastEngine.Score best = scores.isEmpty() || !scores.get(0).quality().available() ? null : scores.get(0);
        List<ForecastResponse.ModelScore> out = new ArrayList<>();
        for (ForecastEngine.Score s : scores) {
            boolean ok = s.quality().available();
            Backtester.Metrics n = s.quality().net();
            out.add(new ForecastResponse.ModelScore(s.method().name(), s.method().label, s.method().family,
                    s.method() == selected, best != null && s == best,
                    ok ? round(n.wape() * 100) : null, ok ? round(n.mae()) : null, ok ? round(n.rmse()) : null));
        }
        return out;
    }

    private List<String> warnings(CashFlowHistoryService.History h, ForecastEngine.Result r) {
        List<String> w = new ArrayList<>();
        if (h.months() < 12) w.add("История короче 12 месяцев — прогноз менее надёжен, интервалы шире реальных.");
        if (h.months() < 24 && r.method() == ForecastMethod.HOLT_WINTERS) w.add("Сезонность не учитывается: для неё нужно минимум 24 месяца истории.");
        if (h.months() < 24 && r.method() == ForecastMethod.GRADIENT_BOOSTING) w.add("Сезонные признаки бустинга включаются при истории от 24 месяцев.");
        if (h.excludedOtherCurrency() > 0) w.add("Операции в иностранной валюте (" + h.excludedOtherCurrency() + ") не вошли в расчёт: без курса валют их нельзя суммировать с BYN.");
        if (!r.quality().available()) w.add("Точность модели не оценена: недостаточно истории для бэктеста.");
        return w;
    }

    private static Scenario legacyScenario(String strategy, Double multiplier) {
        if (strategy != null) {
            String s = strategy.toUpperCase(Locale.ROOT);
            if (s.contains("OPTIMISTIC")) return Scenario.OPTIMISTIC;
            if (s.contains("PESSIMISTIC")) return Scenario.PESSIMISTIC;
        }
        if (multiplier != null) return multiplier > 1 ? Scenario.OPTIMISTIC : multiplier < 1 ? Scenario.PESSIMISTIC : Scenario.BASE;
        return Scenario.BASE;
    }

    public static Scenario parseScenario(String raw) {
        if (raw == null || raw.isBlank()) return Scenario.BASE;
        try { return Scenario.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return Scenario.BASE; }
    }

    private static BigDecimal money(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP); }
    private static Double round(double v) { return Double.isNaN(v) || Double.isInfinite(v) ? null : Math.round(v * 100d) / 100d; }
    private static BigDecimal dec(JsonNode n, String f) { return n.hasNonNull(f) ? new BigDecimal(n.get(f).asText()) : null; }
    private static Double dbl(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asDouble() : null; }
}
