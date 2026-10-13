package com.company.contractanalyzer.config;

import com.company.contractanalyzer.ingestion.ChunkingOptions;
import com.company.contractanalyzer.ingestion.ClauseAwareChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free chunker with sizes taken from configuration. */
@Configuration(proxyBeanMethods = false)
public class IngestionConfig {

    @Bean
    public ChunkingOptions chunkingOptions(AppProperties properties) {
        AppProperties.Chunking c = properties.getChunking();
        return new ChunkingOptions(c.getMaxTokens(), c.getOverlapTokens(), c.getMinTokens());
    }

    @Bean
    public ClauseAwareChunker clauseAwareChunker(ChunkingOptions chunkingOptions) {
        return new ClauseAwareChunker(chunkingOptions);
    }
}
