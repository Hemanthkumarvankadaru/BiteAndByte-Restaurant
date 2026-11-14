package com.biteandbyte.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biteandbyte.restaurant.dto.admin.RevenueReportResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.repository.RevenueRow;
import com.biteandbyte.restaurant.service.RevenueReportService.Period;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bucketing rules of the revenue report, without a database. */
class RevenueReportServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static RevenueRow row(String deliveredAtUtc, String total) {
        return new RevenueRow(Instant.parse(deliveredAtUtc), new BigDecimal(total));
    }

    @Test
    void daysAreCutAtMidnightInIndia() {
        // 18:29:59 UTC is 23:59:59 IST on the 13th. 18:30:00 UTC is 00:00 IST on the 14th.
        List<RevenueRow> rows = List.of(
                row("2026-11-13T18:29:59Z", "100.00"),
                row("2026-11-13T18:30:00Z", "200.00"),
                row("2026-11-14T10:00:00Z", "50.50"));

        RevenueReportResponse report = RevenueReportService.build(Period.DAILY,
                LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-14"), IST, rows);

        assertThat(report.buckets()).extracting(RevenueReportResponse.Bucket::label)
                .containsExactly("2026-11-12", "2026-11-13", "2026-11-14");
        assertThat(report.buckets()).extracting(RevenueReportResponse.Bucket::orders).containsExactly(0L, 1L, 2L);
        assertThat(report.buckets()).extracting(RevenueReportResponse.Bucket::revenue)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("100.00"), new BigDecimal("250.50"));
        assertThat(report.totalOrders()).isEqualTo(3);
        assertThat(report.totalRevenue()).isEqualTo(new BigDecimal("350.50"));
        assertThat(report.timeZone()).isEqualTo("Asia/Kolkata");
        assertThat(report.period()).isEqualTo("daily");
    }

    @Test
    void weeksRunMondayToSunday() {
        List<RevenueRow> rows = List.of(
                row("2026-11-15T12:00:00Z", "100.00"),   // Sunday 15th IST: week 46
                row("2026-11-15T19:00:00Z", "40.00"));   // Monday 16th 00:30 IST: week 47

        RevenueReportResponse report = RevenueReportService.build(Period.WEEKLY,
                LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-22"), IST, rows);

        assertThat(report.buckets()).hasSize(2);
        RevenueReportResponse.Bucket first = report.buckets().get(0);
        assertThat(first.start()).isEqualTo("2026-11-09");
        assertThat(first.end()).isEqualTo("2026-11-15");
        assertThat(first.label()).isEqualTo("2026-W46");
        assertThat(first.revenue()).isEqualByComparingTo("100.00");
        assertThat(report.buckets().get(1).label()).isEqualTo("2026-W47");
        assertThat(report.buckets().get(1).revenue()).isEqualByComparingTo("40.00");
    }

    @Test
    void monthsUseCalendarMonths() {
        List<RevenueRow> rows = List.of(
                row("2026-10-31T18:29:00Z", "10.00"),    // 31 Oct 23:59 IST
                row("2026-10-31T18:31:00Z", "20.00"));   // 1 Nov 00:01 IST

        RevenueReportResponse report = RevenueReportService.build(Period.MONTHLY,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-30"), IST, rows);

        assertThat(report.buckets()).extracting(RevenueReportResponse.Bucket::label).containsExactly("2026-10", "2026-11");
        assertThat(report.buckets().get(0).end()).isEqualTo("2026-10-31");
        assertThat(report.buckets()).extracting(RevenueReportResponse.Bucket::revenue)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10"), new BigDecimal("20"));
    }

    @Test
    void periodsAlignRangesToWholeBuckets() {
        LocalDate wednesday = LocalDate.parse("2026-11-11");
        assertThat(Period.WEEKLY.bucketStart(wednesday)).isEqualTo("2026-11-09");
        assertThat(Period.WEEKLY.bucketEnd(wednesday)).isEqualTo("2026-11-15");
        assertThat(Period.MONTHLY.bucketStart(wednesday)).isEqualTo("2026-11-01");
        assertThat(Period.MONTHLY.bucketEnd(LocalDate.parse("2028-02-10"))).isEqualTo("2028-02-29");
        assertThat(Period.DAILY.bucketEnd(wednesday)).isEqualTo(wednesday);
    }

    @Test
    void parsesPeriodIgnoringCase() {
        assertThat(Period.parse("Weekly")).isEqualTo(Period.WEEKLY);
        assertThatThrownBy(() -> Period.parse("yearly"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_PARAMETER"));
    }
}
