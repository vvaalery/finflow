package com.finance.forecast.dto;

import java.util.UUID;

public record GenerateCommand(UUID companyId, Integer months, String method, String scenario) {}
