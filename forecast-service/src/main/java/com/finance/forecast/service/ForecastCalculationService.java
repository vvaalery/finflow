package com.finance.forecast.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.forecast.analytics.Backtester;
import com.finance.forecast.analytics.ForecastEngine;
import com.finance.forecast.analytics.ForecastMethod;
import com.finance.forecast.analytics.Scenario;
import com.finance.forecast.dto.ForecastRequest;
import com.finance.forecast.dto.ForecastResponse;
import com.finance.forecast.entity.CashFlowForecast;
import com.finance.forecast.entity.ForecastItem;
import com.finance.forecast.repository.CashFlowForecastRepository;
import com.finance.forecast.repository.ForecastItemRepository;
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
public class ForecastCalculationService {
    public static final int MIN_HISTORY_MONTHS = 6;

    private final CashFlowForecastRepository forecastRepository;
    private final ForecastItemRepository itemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ForecastResponse generate(ForecastRequest request) {
        validate(request);

        int months = request.months() == null ? 6 : request.months();
        ForecastMethod method = ForecastMethod.parse(request.method());
        Scenario scenario = parseScenario(request.scenario());

        double[] inflow = request.inflow();
        double[] outflow = request.outflow();

        ForecastEngine.Result result = ForecastEngine.forecast(
                method, scenario, inflow, outflow, request.historyStart(), months
        );

        BigDecimal balance = request.baseCurrencyBalance() == null
                ? BigDecimal.ZERO : request.baseCurrencyBalance();

        List<ForecastResponse.ForecastPoint> points = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal running = balance;

        for (ForecastEngine.Point point : result.points()) {
            BigDecimal in = money(point.inflow());
            BigDecimal out = money(point.outflow());
            BigDecimal net = in.subtract(out);
            running = running.add(net);
            totalIn = totalIn.add(in);
            totalOut = totalOut.add(out);

            points.add(new ForecastResponse.ForecastPoint(
                    point.month().atDay(1),
                    in,
                    out,
                    net,
                    money(point.inflowLow()),
                    money(point.inflowHigh()),
                    money(point.outflowLow()),
                    money(point.outflowHigh()),
                    money(point.netLow()),
                    money(point.netHigh()),
                    running
            ));
        }

        BigDecimal totalNet = totalIn.subtract(totalOut);

        CashFlowForecast forecast = new CashFlowForecast();
        forecast.setCompanyId(request.companyId());
        forecast.setForecastPeriodStart(points.get(0).date());
        forecast.setForecastPeriodEnd(
                YearMonth.from(points.get(points.size() - 1).date()).atEndOfMonth()
        );
        forecast.setTotalProjectedInflow(totalIn);
        forecast.setTotalProjectedOutflow(totalOut);
        forecast.setNetCashFlow(totalNet);
        forecast.setScenario(scenario.name());
        forecast.setStrategyUsed(method.name());
        forecast.setParameters(buildParameters(request, result, points, balance));

        CashFlowForecast saved = forecastRepository.save(forecast);

        List<ForecastItem> items = new ArrayList<>();
        for (ForecastResponse.ForecastPoint point : points) {
            ForecastItem item = new ForecastItem();
            item.setForecast(saved);
            item.setForecastDate(point.date());
            item.setProjectedInflow(point.inflow());
            item.setProjectedOutflow(point.outflow());
            items.add(item);
        }
        itemRepository.saveAll(items);

        return response(saved, points, request.historyStart(), inflow, outflow,
                request.excludedOtherCurrency(), balance, months, result, method);
    }

    @Transactional(readOnly = true)
    public ForecastResponse latest(UUID companyId) {
        CashFlowForecast forecast = forecastRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Прогнозы не найдены"));

        return restore(forecast);
    }

    @Transactional(readOnly = true)
    public List<ForecastResponse> history(UUID companyId) {
        return forecastRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(this::restore)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ForecastResponse.ModelScore> compare(ForecastRequest request) {
        validateHistory(request);

        ForecastMethod selected = ForecastMethod.parse(request.method());
        List<ForecastEngine.Score> scores = ForecastEngine.compare(
                request.inflow(), request.outflow(), request.historyStart()
        );

        ForecastEngine.Score best = scores.isEmpty()
                || !scores.get(0).quality().available()
                ? null : scores.get(0);

        List<ForecastResponse.ModelScore> result = new ArrayList<>();

        for (ForecastEngine.Score score : scores) {
            boolean available = score.quality().available();
            Backtester.Metrics metrics = score.quality().net();

            result.add(new ForecastResponse.ModelScore(
                    score.method().name(),
                    score.method().label,
                    score.method().family,
                    score.method() == selected,
                    best != null && score == best,
                    available ? round(metrics.wape() * 100) : null,
                    available ? round(metrics.mae()) : null,
                    available ? round(metrics.rmse()) : null
            ));
        }

        return result;
    }

    private ForecastResponse restore(CashFlowForecast forecast) {
        JsonNode params = forecast.getParameters();

        Map<LocalDate, JsonNode> storedPoints = new HashMap<>();
        if (params != null && params.has("points")) {
            for (JsonNode node : params.get("points")) {
                storedPoints.put(LocalDate.parse(node.get("date").asText()), node);
            }
        }

        List<ForecastResponse.ForecastPoint> points = new ArrayList<>();

        for (ForecastItem item : itemRepository.findByForecastIdOrderByForecastDate(forecast.getId())) {
            BigDecimal in = item.getProjectedInflow();
            BigDecimal out = item.getProjectedOutflow();
            JsonNode node = storedPoints.get(item.getForecastDate());

            points.add(new ForecastResponse.ForecastPoint(
                    item.getForecastDate(),
                    in,
                    out,
                    in.subtract(out),
                    decimal(node, "inflowLow"),
                    decimal(node, "inflowHigh"),
                    decimal(node, "outflowLow"),
                    decimal(node, "outflowHigh"),
                    decimal(node, "netLow"),
                    decimal(node, "netHigh"),
                    decimal(node, "projectedBalance")
            ));
        }

        YearMonth start = params != null && params.hasNonNull("historyStart")
                ? YearMonth.parse(params.get("historyStart").asText())
                : YearMonth.from(forecast.getForecastPeriodStart());

        double[] inflow = readArray(params, "historyInflow");
        double[] outflow = readArray(params, "historyOutflow");

        int excluded = params == null ? 0 : params.path("excludedOtherCurrency").asInt();
        BigDecimal balance = params != null && params.hasNonNull("currentBalance")
                ? new BigDecimal(params.get("currentBalance").asText())
                : BigDecimal.ZERO;

        ForecastMethod method = ForecastMethod.parse(forecast.getStrategyUsed());
        Scenario scenario = parseScenario(forecast.getScenario());

        ForecastResponse.Quality quality = readQuality(params);
        Map<String, Object> modelParams = readMap(params, "modelParams");

        List<ForecastResponse.HistoryPoint> history = historyPoints(start, inflow, outflow);

        return new ForecastResponse(
                forecast.getId(),
                forecast.getTotalProjectedInflow(),
                forecast.getTotalProjectedOutflow(),
                forecast.getNetCashFlow(),
                method.label + " / " + forecast.getScenario(),
                forecast.getForecastPeriodStart() + " — " + forecast.getForecastPeriodEnd(),
                points,
                method.name(),
                method.label,
                method.family,
                forecast.getScenario(),
                balance,
                points.isEmpty() ? balance : points.get(points.size() - 1).projectedBalance(),
                history,
                quality,
                List.of(),
                readWarnings(params),
                modelParams,
                forecast.getCompanyId(),
                forecast.getForecastPeriodStart(),
                forecast.getForecastPeriodEnd(),
                forecast.getCreatedAt()
        );
    }

    private ForecastResponse response(
            CashFlowForecast forecast,
            List<ForecastResponse.ForecastPoint> points,
            YearMonth start,
            double[] inflow,
            double[] outflow,
            int excluded,
            BigDecimal balance,
            int months,
            ForecastEngine.Result result,
            ForecastMethod method
    ) {
        ForecastResponse.Quality quality = quality(result.quality());

        return new ForecastResponse(
                forecast.getId(),
                forecast.getTotalProjectedInflow(),
                forecast.getTotalProjectedOutflow(),
                forecast.getNetCashFlow(),
                method.label + " / " + result.scenario().name(),
                months + " мес.",
                points,
                method.name(),
                method.label,
                method.family,
                result.scenario().name(),
                balance,
                points.isEmpty() ? balance : points.get(points.size() - 1).projectedBalance(),
                historyPoints(start, inflow, outflow),
                quality,
                comparisonFromHistory(inflow, outflow, start, method),
                warnings(inflow.length, excluded, result),
                modelParams(result),
                forecast.getCompanyId(),
                forecast.getForecastPeriodStart(),
                forecast.getForecastPeriodEnd(),
                forecast.getCreatedAt()
        );
    }

    private ObjectNode buildParameters(
            ForecastRequest request,
            ForecastEngine.Result result,
            List<ForecastResponse.ForecastPoint> points,
            BigDecimal balance
    ) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("algorithm", result.method().name());
        params.put("scenario", result.scenario().name());
        params.put("historyMonths", request.inflow().length);
        params.put("forecastMonths", request.months() == null ? 6 : request.months());
        params.put("historyStart", request.historyStart().toString());
        params.put("currentBalance", balance.toPlainString());
        params.put("excludedOtherCurrency", request.excludedOtherCurrency());
        params.set("historyInflow", objectMapper.valueToTree(request.inflow()));
        params.set("historyOutflow", objectMapper.valueToTree(request.outflow()));
        params.set("expenseByCategory", objectMapper.valueToTree(request.expenseByCategory()));
        params.set("modelParams", objectMapper.valueToTree(modelParams(result)));
        params.set("quality", objectMapper.valueToTree(quality(result.quality())));

        ArrayNode array = params.putArray("points");
        for (ForecastResponse.ForecastPoint point : points) {
            ObjectNode node = array.addObject();
            node.put("date", point.date().toString());
            put(node, "inflowLow", point.inflowLow());
            put(node, "inflowHigh", point.inflowHigh());
            put(node, "outflowLow", point.outflowLow());
            put(node, "outflowHigh", point.outflowHigh());
            put(node, "netLow", point.netLow());
            put(node, "netHigh", point.netHigh());
            put(node, "projectedBalance", point.projectedBalance());
        }

        return params;
    }

    private List<ForecastResponse.HistoryPoint> historyPoints(
            YearMonth start, double[] inflow, double[] outflow
    ) {
        List<ForecastResponse.HistoryPoint> result = new ArrayList<>();
        int from = Math.max(0, inflow.length - 12);

        for (int i = from; i < inflow.length; i++) {
            BigDecimal in = money(inflow[i]);
            BigDecimal out = money(outflow[i]);
            result.add(new ForecastResponse.HistoryPoint(
                    start.plusMonths(i).atDay(1),
                    in,
                    out,
                    in.subtract(out)
            ));
        }

        return result;
    }

    private List<ForecastResponse.ModelScore> comparisonFromHistory(
            double[] inflow, double[] outflow, YearMonth start, ForecastMethod selected
    ) {
        List<ForecastResponse.ModelScore> result = new ArrayList<>();
        List<ForecastEngine.Score> scores = ForecastEngine.compare(inflow, outflow, start);

        ForecastEngine.Score best = scores.isEmpty()
                || !scores.get(0).quality().available()
                ? null : scores.get(0);

        for (ForecastEngine.Score score : scores) {
            Backtester.Metrics metrics = score.quality().net();
            boolean available = score.quality().available();

            result.add(new ForecastResponse.ModelScore(
                    score.method().name(),
                    score.method().label,
                    score.method().family,
                    score.method() == selected,
                    best != null && score == best,
                    available ? round(metrics.wape() * 100) : null,
                    available ? round(metrics.mae()) : null,
                    available ? round(metrics.rmse()) : null
            ));
        }

        return result;
    }

    private Map<String, Object> modelParams(ForecastEngine.Result result) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("inflow", result.inflowParams());
        params.put("outflow", result.outflowParams());
        return params;
    }

    private ForecastResponse.Quality quality(Backtester.Result result) {
        if (!result.available()) {
            return new ForecastResponse.Quality(
                    false, 0, null, null, null,
                    "Для оценки точности нужно минимум 7 месяцев истории"
            );
        }

        Backtester.Metrics metrics = result.net();
        return new ForecastResponse.Quality(
                true,
                result.windows(),
                round(metrics.mae()),
                round(metrics.rmse()),
                round(metrics.wape() * 100),
                "Скользящий бэктест по чистому потоку: прогноз на 1–3 месяца вперёд на исторических окнах"
        );
    }

    private List<String> warnings(
            int historyMonths, int excluded, ForecastEngine.Result result
    ) {
        List<String> warnings = new ArrayList<>();

        if (historyMonths < 12) {
            warnings.add("История короче 12 месяцев — прогноз менее надёжен, интервалы шире реальных.");
        }
        if (historyMonths < 24 && result.method() == ForecastMethod.HOLT_WINTERS) {
            warnings.add("Сезонность не учитывается: для неё нужно минимум 24 месяца истории.");
        }
        if (historyMonths < 24 && result.method() == ForecastMethod.GRADIENT_BOOSTING) {
            warnings.add("Сезонные признаки бустинга включаются при истории от 24 месяцев.");
        }
        if (excluded > 0) {
            warnings.add("Операции в иностранной валюте (" + excluded
                    + ") не вошли в расчёт: без курса валют их нельзя суммировать с BYN.");
        }
        if (!result.quality().available()) {
            warnings.add("Точность модели не оценена: недостаточно истории для бэктеста.");
        }

        return warnings;
    }

    private void validate(ForecastRequest request) {
        validateHistory(request);

        int months = request.months() == null ? 6 : request.months();
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("Период прогноза: от 1 до 24 месяцев");
        }
        if (request.companyId() == null) {
            throw new IllegalArgumentException("Не указана компания");
        }
        if (request.baseCurrencyBalance() == null) {
            throw new IllegalArgumentException("Не указан текущий баланс");
        }
    }

    private void validateHistory(ForecastRequest request) {
        if (request == null || request.inflow() == null || request.outflow() == null
                || request.historyStart() == null) {
            throw new IllegalArgumentException("Не переданы исторические данные для прогноза");
        }
        if (request.inflow().length != request.outflow().length) {
            throw new IllegalArgumentException("Массивы поступлений и расходов имеют разную длину");
        }
        if (request.inflow().length < MIN_HISTORY_MONTHS) {
            throw new IllegalArgumentException(
                    "Недостаточно истории для прогноза: нужно минимум "
                            + MIN_HISTORY_MONTHS + " завершённых месяцев с операциями в BYN"
            );
        }
    }

    private static Scenario parseScenario(String raw) {
        if (raw == null || raw.isBlank()) return Scenario.BASE;
        try {
            return Scenario.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Scenario.BASE;
        }
    }

    private double[] readArray(JsonNode params, String field) {
        if (params == null || !params.has(field) || !params.get(field).isArray()) {
            return new double[0];
        }
        return objectMapper.convertValue(params.get(field), double[].class);
    }

    private ForecastResponse.Quality readQuality(JsonNode params) {
        if (params == null || !params.has("quality")) return null;

        JsonNode q = params.get("quality");
        return new ForecastResponse.Quality(
                q.path("available").asBoolean(false),
                q.path("windows").asInt(),
                number(q, "mae"),
                number(q, "rmse"),
                number(q, "wape"),
                q.path("note").asText(null)
        );
    }

    private Map<String, Object> readMap(JsonNode params, String field) {
        if (params == null || !params.has(field)) return Map.of();
        return objectMapper.convertValue(params.get(field), Map.class);
    }

    private List<String> readWarnings(JsonNode params) {
        if (params == null || !params.has("warnings")) return List.of();
        return objectMapper.convertValue(params.get("warnings"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return node != null && node.hasNonNull(field)
                ? new BigDecimal(node.get(field).asText()) : null;
    }

    private static Double number(JsonNode node, String field) {
        return node != null && node.hasNonNull(field)
                ? node.get(field).asDouble() : null;
    }

    private static void put(ObjectNode node, String field, BigDecimal value) {
        if (value != null) node.put(field, value);
        else node.putNull(field);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double round(double value) {
        return Double.isNaN(value) || Double.isInfinite(value)
                ? null : Math.round(value * 100d) / 100d;
    }
}
