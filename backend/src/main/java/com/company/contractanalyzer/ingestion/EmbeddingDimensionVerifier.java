package com.company.contractanalyzer.ingestion;

import com.company.contractanalyzer.config.AppProperties;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup check. The embedding model's output dimension, the configured dimension
 * ({@code app.vector.dimensions}) and the actual {@code vector_store.embedding} column type must
 * all agree. Otherwise every insert or search would fail at runtime, and swapping the model
 * would silently make stored vectors incomparable. A mismatch aborts context start-up with a
 * clear message.
 */
@Component
public class EmbeddingDimensionVerifier implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDimensionVerifier.class);
    private static final Pattern VECTOR_TYPE = Pattern.compile("^vector\\((\\d+)\\)$");
    private static final String COLUMN_TYPE_SQL =
            "SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a "
                    + "WHERE a.attrelid = to_regclass('vector_store') AND a.attname = 'embedding' AND NOT a.attisdropped";

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public EmbeddingDimensionVerifier(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate,
                                      AppProperties properties) {
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        verify();
    }

    /** @return the verified dimension */
    public int verify() {
        int configured = properties.getVector().getDimensions();
        int modelDimension = embeddingModel.dimensions();
        if (modelDimension != configured) {
            throw new IllegalStateException("Embedding dimension mismatch: the embedding model produces "
                    + modelDimension + "-dimensional vectors but app.vector.dimensions is " + configured
                    + ". Use a " + configured + "-dimensional model or migrate the vector_store column and re-ingest.");
        }
        List<String> columnTypes = jdbcTemplate.queryForList(COLUMN_TYPE_SQL, String.class);
        if (columnTypes.isEmpty()) {
            throw new IllegalStateException("vector_store.embedding column not found: Flyway migration V1 has not run");
        }
        Matcher matcher = VECTOR_TYPE.matcher(columnTypes.get(0));
        int columnDimension = matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
        if (columnDimension != configured) {
            throw new IllegalStateException("Embedding dimension mismatch: vector_store.embedding is "
                    + columnTypes.get(0) + " but the embedding model produces " + configured + " dimensions");
        }
        log.info("Embedding dimension verified: model={} column={}", modelDimension, columnTypes.get(0));
        return configured;
    }
}
