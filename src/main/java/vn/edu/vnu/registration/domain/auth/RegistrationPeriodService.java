package vn.edu.vnu.registration.domain.auth;

import vn.edu.vnu.registration.domain.TtlCache;
import vn.edu.vnu.registration.domain.auth.model.RegistrationPeriod;
import vn.edu.vnu.registration.domain.port.RegistrationPeriodRepository;
import vn.edu.vnu.registration.domain.port.TransactionRunner;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static vn.edu.vnu.registration.domain.exception.BusinessException.error;

public class RegistrationPeriodService {
    private final RegistrationPeriodRepository periodRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    // Only GET /registration reads this cache, login and enroll go to the repository
    private final TtlCache<String, Optional<RegistrationPeriod>> periodCache;
    private static final String CURRENT_PERIOD = "current";

    public RegistrationPeriodService(RegistrationPeriodRepository periodRepository, TransactionRunner transactionRunner, Clock clock, long cacheTtlMs) {
        this.periodRepository = periodRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.periodCache = new TtlCache<>(cacheTtlMs);
    }

    public void ping() {
        periodRepository.ping();
    }

    public Map<String, Object> registration() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registration", periodCache.get(CURRENT_PERIOD, periodRepository::currentPeriod).orElse(null));
        // Server time is never cached
        body.put("serverTime", clock.instant());
        return body;
    }

    public RegistrationPeriod savePeriod(RegistrationPeriod input) {
        if (input.loginOpensAt().isAfter(input.opensAt()) || !input.opensAt().isBefore(input.closesAt())) {
            throw error(400, "VALIDATION_ERROR", "Cần loginOpensAt ≤ opensAt < closesAt.");
        }
        transactionRunner.runInTransaction(ctx -> {
            periodRepository.upsertPeriod(input);
            return null;
        });
        return input;
    }
}
