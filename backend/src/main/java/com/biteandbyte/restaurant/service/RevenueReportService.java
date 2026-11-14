package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.config.StoreProperties;
import com.biteandbyte.restaurant.dto.admin.RevenueReportResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.repository.CustomerOrderRepository;
import com.biteandbyte.restaurant.repository.RevenueRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revenue grouped by day, ISO week or month in the store time zone (gaps G16 and G28).
 * Orders are fetched as (delivered at, total) pairs and grouped in Java, which keeps the query
 * identical on H2, PostgreSQL and Oracle.
 */
@Service
public class RevenueReportService {

    public enum Period {
        DAILY(366), WEEKLY(5 * 366), MONTHLY(5 * 366);

        /** Longest range, in days, a single report may cover. */
        final int maxDays;

        Period(int maxDays) {
            this.maxDays = maxDays;
        }

        public static Period parse(String value) {
            if (value != null) {
                for (Period p : values()) {
                    if (p.name().equalsIgnoreCase(value.trim())) {
                        return p;
                    }
                }
            }
            throw ApiException.badRequest("INVALID_PARAMETER", "Parameter 'period' must be daily, weekly or monthly");
        }

        LocalDate bucketStart(LocalDate day) {
            return switch (this) {
                case DAILY -> day;
                case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTHLY -> day.withDayOfMonth(1);
            };
        }

        LocalDate nextBucket(LocalDate start) {
            return switch (this) {
                case DAILY -> start.plusDays(1);
                case WEEKLY -> start.plusWeeks(1);
                case MONTHLY -> start.plusMonths(1);
            };
        }

        LocalDate bucketEnd(LocalDate day) {
            return nextBucket(bucketStart(day)).minusDays(1);
        }

        String label(LocalDate start) {
            return switch (this) {
                case DAILY -> start.toString();
                case WEEKLY -> String.format(Locale.ROOT, "%d-W%02d",
                        start.get(IsoFields.WEEK_BASED_YEAR), start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                case MONTHLY -> YearMonth.from(start).toString();
            };
        }

        /** Default range ending on {@code to}: 30 days, 12 weeks or 12 months. */
        LocalDate defaultFrom(LocalDate to) {
            return switch (this) {
                case DAILY -> to.minusDays(29);
                case WEEKLY -> to.minusWeeks(11);
                case MONTHLY -> to.minusMonths(11);
            };
        }
    }

    private final CustomerOrderRepository orders;
    private final StoreProperties store;
    private final Clock clock;

    public RevenueReportService(CustomerOrderRepository orders, StoreProperties store, Clock clock) {
        this.orders = orders;
        this.store = store;
        this.clock = clock;
    }

    /**
     * @param from first day, inclusive. Defaults to a range that suits the period.
     * @param to   last day, inclusive. Defaults to today.
     */
    @Transactional(readOnly = true)
    public RevenueReportResponse report(Period period, LocalDate from, LocalDate to) {
        ZoneId zone = store.timeZone();
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : period.defaultFrom(end);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "'from' must not be after 'to'");
        }
        start = period.bucketStart(start);
        end = period.bucketEnd(end);
        if (ChronoUnit.DAYS.between(start, end) + 1 > period.maxDays) {
            throw ApiException.badRequest("DATE_RANGE_TOO_LONG",
                    "A " + period.name().toLowerCase(Locale.ROOT) + " report can cover at most " + period.maxDays + " days");
        }
        List<RevenueRow> rows = orders.findRevenueRows(start.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant());
        return build(period, start, end, zone, rows);
    }

    /** Groups rows into buckets from {@code start} to {@code end}, both already aligned to the period. */
    static RevenueReportResponse build(Period period, LocalDate start, LocalDate end, ZoneId zone, List<RevenueRow> rows) {
        Map<LocalDate, BigDecimal> revenue = new LinkedHashMap<>();
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate b = start; !b.isAfter(end); b = period.nextBucket(b)) {
            revenue.put(b, BigDecimal.ZERO.setScale(2));
            counts.put(b, 0L);
        }
        for (RevenueRow row : rows) {
            LocalDate key = period.bucketStart(row.deliveredAt().atZone(zone).toLocalDate());
            if (revenue.containsKey(key)) {
                revenue.merge(key, row.grandTotal(), BigDecimal::add);
                counts.merge(key, 1L, Long::sum);
            }
        }
        List<RevenueReportResponse.Bucket> buckets = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        long totalOrders = 0;
        for (Map.Entry<LocalDate, BigDecimal> e : revenue.entrySet()) {
            LocalDate b = e.getKey();
            long n = counts.get(b);
            buckets.add(new RevenueReportResponse.Bucket(b, period.nextBucket(b).minusDays(1), period.label(b), n, e.getValue()));
            total = total.add(e.getValue());
            totalOrders += n;
        }
        return new RevenueReportResponse(period.name().toLowerCase(Locale.ROOT), start, end, zone.getId(),
                totalOrders, total, buckets);
    }
}
