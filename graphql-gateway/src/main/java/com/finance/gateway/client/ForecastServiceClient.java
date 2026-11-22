package com.finance.gateway.client;

import com.finance.graphqlapi.dto.GraphQLInput.GenerateForecastInput;
import com.finance.graphqlapi.dto.GraphQLView.ForecastResultView;
import com.finance.graphqlapi.dto.GraphQLView.ForecastView;

import java.util.List;
import java.util.UUID;

public interface ForecastServiceClient {
    List<ForecastView> forecasts(UUID companyId);
    ForecastResultView generateForecast(GenerateForecastInput input);
}
