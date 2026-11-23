package vn.edu.vnu.registration.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.vnu.registration.domain.exception.BusinessException;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Component
public class AdmissionController {
    public enum Group {
        LOGIN,
        WRITE,
        READ
    }

    private final Map<Group, Semaphore> permits = new EnumMap<>(Group.class);

    public AdmissionController(
            @Value("${app.admission.login:0}") int loginLimit,
            @Value("${app.admission.write:0}") int writeLimit,
            @Value("${app.admission.read:0}") int readLimit
    ) {
        permits.put(Group.LOGIN, semaphore(loginLimit));
        permits.put(Group.WRITE, semaphore(writeLimit));
        permits.put(Group.READ, semaphore(readLimit));
    }

    public void enter(Group group) {
        Semaphore semaphore = permits.get(group);
        if (semaphore != null && !semaphore.tryAcquire()) {
            int retryAfter = group == Group.LOGIN ? 3 : 1;
            throw new BusinessException(429, "RATE_LIMITED", "Máy chủ đang bận, vui lòng thử lại sau.", Map.of("retryAfter", retryAfter));
        }
    }

    public void leave(Group group) {
        Semaphore semaphore = permits.get(group);
        if (semaphore != null) semaphore.release();
    }

    public int availablePermits(Group group) {
        Semaphore semaphore = permits.get(group);
        return semaphore == null ? Integer.MAX_VALUE : semaphore.availablePermits();
    }

    private Semaphore semaphore(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Admission limits cannot be negative");
        return limit == 0 ? null : new Semaphore(limit);
    }
}
