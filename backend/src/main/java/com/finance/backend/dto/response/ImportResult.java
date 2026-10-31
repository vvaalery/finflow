package com.finance.backend.dto.response;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResult {
    private int totalRows;
    private int successRows;
    private int errorRows;
    private List<String> errors = new ArrayList<>();
    
    public void addError(String error) {
        errors.add(error);
        errorRows++;
    }
    
    public void addSuccess() {
        successRows++;
    }
}