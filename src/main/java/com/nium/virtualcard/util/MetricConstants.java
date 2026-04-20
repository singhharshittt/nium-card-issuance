package com.nium.virtualcard.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MetricConstants {

    public static final String CARD_CREATED_COUNT = "card.created.count";
    public static final String CARD_TOP_UP_SUCCESS_COUNT = "card.topup.success.count";
    public static final String CARD_TOP_UP_FAILURE_COUNT = "card.topup.failure.count";
    public static final String CARD_SPEND_SUCCESS_COUNT = "card.spend.success.count";
    public static final String CARD_SPEND_DECLINED_COUNT = "card.spend.declined.count";
    public static final String CARD_SPEND_FAILURE_COUNT = "card.spend.failure.count";
    public static final String IDEMPOTENCY_HIT_COUNT = "idempotency.hit.count";
    public static final String OPTIMISTIC_LOCK_RETRY_COUNT = "optimistic.lock.retry.count";
    public static final String OPTIMISTIC_LOCK_FAILURE_COUNT = "optimistic.lock.failure.count";

}
