package com.finance.internal.service;

import com.finance.backend.analytics.ForecastMethod;
import com.finance.backend.dto.response.ForecastPointResponse;
import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.User;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.service.ForecastService;
import com.finance.internal.dto.GraphQLInput.GenerateForecastInput;
import com.finance.internal.dto.GraphQLView.ForecastPointView;
import com.finance.internal.dto.GraphQLView.ForecastResultView;
import com.finance.internal.dto.GraphQLView.ForecastView;
import com.finance.internal.security.AccessPolicy;
import com.finance.kafka.event.EventTypes;
import com.finance.kafka.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalForecastService {

    private final CompanyRepository companies;
    private final ForecastService forecastService;
    private final OutboxEventService outbox;
    private final AccessPolicy policy;

    @Transactional(readOnly = true)
    public List<ForecastView> forecasts(UUID companyId) {
        User user = policy.currentUser();
        Company company = company(companyId);
        policy.requireReadCompany(user, company);

        return forecastService.getForecastHistory(company.getId())
                .stream()
                .map(forecast -> new ForecastView(
                        forecast.getId(),
                        forecast.getCompanyId(),
                        forecast.getForecastPeriodStart(),
                        forecast.getForecastPeriodEnd(),
                        forecast.getTotalProjectedInflow(),
                        forecast.getTotalProjectedOutflow(),
                        forecast.getNetCashFlow(),
                        forecast.getStrategyUsed(),
                        forecast.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public ForecastResultView generate(GenerateForecastInput in) {
        User user = policy.currentUser();
        Company company = company(in.companyId());
        policy.requireReadCompany(user, company);

        int months = in.months() == null ? 6 : in.months();

        ForecastResponse response = forecastService.generateForecast(
                company.getId(),
                months,
                ForecastMethod.parse(in.method()),
                ForecastService.parseScenario(in.scenario())
        );

        List<ForecastPointView> items = response.getItems()
                .stream()
                .map(this::point)
                .toList();

        outbox.enqueue(
                EventTypes.FORECAST_GENERATED,
                response.getId(),
                company.getId(),
                "forecastId", response.getId(),
                "method", response.getMethod(),
                "scenario", response.getScenario(),
                "months", months,
                "reason", "user-request"
        );

        return new ForecastResultView(
                response.getId(),
                company.getId(),
                response.getMethod(),
                response.getScenario(),
                response.getForecastPeriodStart(),
                response.getForecastPeriodEnd(),
                response.getTotalProjectedInflow(),
                response.getTotalProjectedOutflow(),
                response.getNetCashFlow(),
                response.getCurrentBalance(),
                response.getProjectedClosingBalance(),
                items,
                response.getWarnings()
        );
    }

    private ForecastPointView point(ForecastPointResponse point) {
        return new ForecastPointView(
                point.getDate(),
                point.getInflow(),
                point.getOutflow(),
                point.getNetCashFlow(),
                point.getInflowLow(),
                point.getInflowHigh(),
                point.getOutflowLow(),
                point.getOutflowHigh(),
                point.getNetLow(),
                point.getNetHigh(),
                point.getProjectedBalance()
        );
    }

    private Company company(UUID id) {
        return companies.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
    }
}
