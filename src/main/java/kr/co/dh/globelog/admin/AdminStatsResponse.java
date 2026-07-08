package kr.co.dh.globelog.admin;

import java.util.List;

public record AdminStatsResponse(
        int visitedCountryCount,
        int totalCountryCount,
        List<ContinentCoverage> continents,
        List<YearlyTripCount> yearly) {
}
