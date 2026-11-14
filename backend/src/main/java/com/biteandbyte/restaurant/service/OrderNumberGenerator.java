package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.config.StoreProperties;
import com.biteandbyte.restaurant.repository.CustomerOrderRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Order numbers in the form {@code BB-YYYYMMDD-XXXXX} (gap G13). The date is the business
 * day in the store time zone. The suffix is 5 random characters from an alphabet without
 * look-alikes (no 0, O, 1, I), which gives about 33 million numbers per day. The unique
 * key on {@code orders.order_number} is the final guard.
 */
@Component
public class OrderNumberGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int SUFFIX_LENGTH = 5;
    private static final int MAX_ATTEMPTS = 10;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final CustomerOrderRepository orders;
    private final StoreProperties store;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderNumberGenerator(CustomerOrderRepository orders, StoreProperties store, Clock clock) {
        this.orders = orders;
        this.store = store;
        this.clock = clock;
    }

    public String next() {
        String prefix = "BB-" + LocalDate.now(clock.withZone(store.timeZone())).format(DAY) + "-";
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = prefix + randomSuffix();
            if (!orders.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free order number after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return suffix.toString();
    }
}
