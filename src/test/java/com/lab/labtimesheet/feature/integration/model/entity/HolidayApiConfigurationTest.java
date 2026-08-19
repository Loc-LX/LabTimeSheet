package com.lab.labtimesheet.feature.integration.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.EncryptedSecret;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HolidayApiConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void revisionFollowsDraftTestActivateRetireStateGraph() {
        var configuration = HolidayApiConfiguration.draft(
                new EncryptedSecret(new byte[32], new byte[12], 1), 7L, NOW);

        assertThat(configuration.getStatus()).isEqualTo(HolidayApiStatus.DRAFT);
        configuration.markTested(7L, NOW.plusSeconds(60));
        configuration.activate(7L, NOW.plusSeconds(120));
        configuration.retire(7L, NOW.plusSeconds(180));

        assertThat(configuration.getStatus()).isEqualTo(HolidayApiStatus.RETIRED);
        assertThat(configuration.getTestedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(configuration.getActivatedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(configuration.getRetiredAt()).isEqualTo(NOW.plusSeconds(180));
    }

    @Test
    void untestedDraftCannotActivateAndRetiredRevisionCannotBeEdited() {
        var configuration = HolidayApiConfiguration.draft(
                new EncryptedSecret(new byte[32], new byte[12], 1), 7L, NOW);

        assertThatThrownBy(() -> configuration.activate(7L, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);

        configuration.markTested(7L, NOW.plusSeconds(60));
        configuration.activate(7L, NOW.plusSeconds(120));
        configuration.retire(7L, NOW.plusSeconds(180));

        assertThatThrownBy(() -> configuration.updateDraft(
                new EncryptedSecret(new byte[32], new byte[12], 1), NOW.plusSeconds(240)))
                .isInstanceOf(IllegalStateException.class);
    }
}
