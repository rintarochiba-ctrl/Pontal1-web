package com.example.pontalweb.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties (ignoreUnknown = true)
public record EmployeeListResponse(
        List<EmployeeSummary> items,
        int totalCount,
        Integer next,
        Integer prev) {
}
