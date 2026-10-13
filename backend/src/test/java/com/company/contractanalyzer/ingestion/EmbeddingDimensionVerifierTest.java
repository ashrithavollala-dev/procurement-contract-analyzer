package com.company.contractanalyzer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.support.FakeEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EmbeddingDimensionVerifierTest {

    private final AppProperties properties = new AppProperties();

    @Test
    void passesWhenModelConfigurationAndColumnAgree() {
        EmbeddingDimensionVerifier verifier =
                new EmbeddingDimensionVerifier(new FakeEmbeddingModel(384), jdbcReturning("vector(384)"), properties);

        assertThat(verifier.verify()).isEqualTo(384);
    }

    @Test
    void failsFastWhenModelDimensionDiffers() {
        EmbeddingDimensionVerifier verifier =
                new EmbeddingDimensionVerifier(new FakeEmbeddingModel(768), jdbcReturning("vector(384)"), properties);

        assertThatThrownBy(verifier::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("768")
                .hasMessageContaining("app.vector.dimensions is 384");
    }

    @Test
    void failsFastWhenColumnDimensionDiffers() {
        EmbeddingDimensionVerifier verifier =
                new EmbeddingDimensionVerifier(new FakeEmbeddingModel(384), jdbcReturning("vector(1536)"), properties);

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector(1536)");
    }

    @Test
    void failsFastWhenTableIsMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        EmbeddingDimensionVerifier verifier = new EmbeddingDimensionVerifier(new FakeEmbeddingModel(384), jdbc, properties);

        assertThatThrownBy(verifier::verify).hasMessageContaining("Flyway");
    }

    private static JdbcTemplate jdbcReturning(String columnType) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(columnType));
        return jdbc;
    }
}
