package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties (ignoreUnknown = true)
public record EmployeeSummary(
        Long id,
        String name,
        String email,
        String department,
        String position) {
    
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public String getPosition() { return position; }
    public Long getId() { return id; }
    public String getInitial() { return name == null || name.isEmpty() ? "" : name.substring(0, 1); }

}
