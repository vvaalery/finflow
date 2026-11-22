package com.finance.internal.service;

import com.finance.backend.analytics.ForecastMethod;
import com.finance.backend.dto.response.ForecastPointResponse;
import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.User;
import com.finance.backend.repository.CashFlowForecastRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.service.ForecastService;
import com.finance.internal.dto.GraphQLInput.GenerateForecastInput;
import com.finance.internal.dto.GraphQLView.ForecastPointView;
import com.finance.internal.dto.GraphQLView.ForecastResultView;
import com.finance.internal.dto.GraphQLView.ForecastView;
import com.finance.internal.security.AccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalForecastService {
    private final CashFlowForecastRepository forecasts;
    private final CompanyRepository companies;
    private final ForecastService forecastService;
    private final AccessPolicy policy;

    @Transactional(readOnly = true)
    public List<ForecastView> forecasts(UUID companyId) {
        User u = policy.currentUser();
        Company c = company(companyId);
        policy.requireReadCompany(u, c);
        return forecasts.findByCompanyIdOrderByCreatedAtDesc(c.getId()).stream().map(Views::forecast).toList();
    }

    public ForecastResultView generate(GenerateForecastInput in) {
        User u = policy.currentUser();
        Company c = company(in.companyId());
        policy.requireReadCompany(u, c);
        int months = in.months() == null ? 6 : in.months();
        ForecastResponse r = forecastService.generateForecast(c.getId(), months,
                ForecastMethod.parse(in.method()), ForecastService.parseScenario(in.scenario()));

        List<ForecastPointView> items = r.getItems().stream().map(this::point).toList();
        var start = r.getItems().get(0).getDate();
        var end = YearMonth.from(r.getItems().get(r.getItems().size() - 1).getDate()).atEndOfMonth();
        return new ForecastResultView(r.getId(), c.getId(), r.getMethod(), r.getScenario(), start, end,
                r.getTotalProjectedInflow(), r.getTotalProjectedOutflow(), r.getNetCashFlow(),
                r.getCurrentBalance(), r.getProjectedClosingBalance(), items, r.getWarnings());
    }

    private ForecastPointView point(ForecastPointResponse p) {
        return new ForecastPointView(p.getDate(), p.getInflow(), p.getOutflow(), p.getNetCashFlow(),
                p.getInflowLow(), p.getInflowHigh(), p.getOutflowLow(), p.getOutflowHigh(),
                p.getNetLow(), p.getNetHigh(), p.getProjectedBalance());
    }

    private Company company(UUID id) {
        return companies.findById(id).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
    }
}
