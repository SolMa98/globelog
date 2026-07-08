package kr.co.dh.globelog.api;

import java.util.List;

public record SearchResponse(
        List<SearchCountryResult> countries,
        List<SearchRegionResult> regions,
        List<SearchTripResult> trips) {
}
