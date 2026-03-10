package com.pnu.detox_agent.webserver.dto.query;

public class PeriodQueryDto {

    private String period = "daily";

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = (period == null || period.isBlank()) ? "daily" : period;
    }
}
