package kr.co.dh.globelog.domain;

import java.time.LocalDate;

public interface TripStatsProjection {
    String getIsoA3();
    LocalDate getVisitedDate();
}
