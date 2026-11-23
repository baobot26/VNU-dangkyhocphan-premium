package vn.edu.vnu.registration.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionControllerTest {
    @Test
    void limitOneRejectsSecondRequestAndAllowsRequestAfterRelease() {
        var admission = new AdmissionController(1, 0, 0);

        admission.enter(AdmissionController.Group.LOGIN);

        assertThatThrownBy(() -> admission.enter(AdmissionController.Group.LOGIN))
                .isInstanceOfSatisfying(vn.edu.vnu.registration.domain.exception.BusinessException.class, e -> {
                    assertThat(e.status()).isEqualTo(429);
                    assertThat(e.body().code()).isEqualTo("RATE_LIMITED");
                    assertThat(e.body().details()).isEqualTo(java.util.Map.of("retryAfter", 3));
                });

        admission.leave(AdmissionController.Group.LOGIN);
        assertThatCode(() -> admission.enter(AdmissionController.Group.LOGIN)).doesNotThrowAnyException();
    }

    @Test
    void writeAndReadGroupsUseOneSecondRetryAfter() {
        var admission = new AdmissionController(0, 1, 1);

        admission.enter(AdmissionController.Group.WRITE);
        assertThatThrownBy(() -> admission.enter(AdmissionController.Group.WRITE))
                .isInstanceOfSatisfying(vn.edu.vnu.registration.domain.exception.BusinessException.class, e ->
                        assertThat(e.body().details()).isEqualTo(java.util.Map.of("retryAfter", 1)));

        admission.enter(AdmissionController.Group.READ);
        assertThatThrownBy(() -> admission.enter(AdmissionController.Group.READ))
                .isInstanceOfSatisfying(vn.edu.vnu.registration.domain.exception.BusinessException.class, e ->
                        assertThat(e.body().details()).isEqualTo(java.util.Map.of("retryAfter", 1)));
    }

    @Test
    void zeroLimitNeverRejects() {
        var admission = new AdmissionController(0, 0, 0);

        assertThatCode(() -> {
            admission.enter(AdmissionController.Group.LOGIN);
            admission.enter(AdmissionController.Group.LOGIN);
        }).doesNotThrowAnyException();
    }
}
