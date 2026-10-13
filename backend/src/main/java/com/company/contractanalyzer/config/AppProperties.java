package com.company.contractanalyzer.config;

import com.company.contractanalyzer.enums.QaMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe application settings bound from the {@code app.*} namespace. Every value has an
 * environment variable in application.yml and a sensible default here.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid private Upload upload = new Upload();
    @Valid private Ingestion ingestion = new Ingestion();
    @Valid private Chunking chunking = new Chunking();
    @Valid private Vector vector = new Vector();
    @Valid private Search search = new Search();
    @Valid private Audit audit = new Audit();
    @Valid private Agent agent = new Agent();
    @Valid private Llm llm = new Llm();
    @Valid private Qa qa = new Qa();
    @Valid private Recovery recovery = new Recovery();
    @Valid private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Upload {
        @NotBlank private String dir = System.getProperty("java.io.tmpdir") + "/contract-analyzer/uploads";
        @NotNull private DataSize maxFileSize = DataSize.ofMegabytes(25);
        @Min(1) private int maxPages = 200;
    }

    @Getter
    @Setter
    public static class Ingestion {
        @Min(1) private int corePoolSize = 2;
        @Min(1) private int maxPoolSize = 4;
        @Min(0) private int queueCapacity = 25;
        @NotNull private Duration awaitTermination = Duration.ofSeconds(60);
        @Min(1) @Max(512) private int embeddingBatchSize = 32;
        @Min(0) private int minTextChars = 200;
    }

    @Getter
    @Setter
    public static class Chunking {
        @Min(20) private int maxTokens = 350;
        @Min(0) private int overlapTokens = 50;
        @Min(0) private int minTokens = 60;
    }

    @Getter
    @Setter
    public static class Vector {
        @Min(1) private int dimensions = 384;
    }

    @Getter
    @Setter
    public static class Search {
        @Min(1) @Max(20) private int defaultTopK = 5;
        @DecimalMin("0.0") @DecimalMax("1.0") private double defaultMinScore = 0.0;
        @Min(40) private int snippetLength = 320;
    }

    @Getter
    @Setter
    public static class Audit {
        @Min(1) private int corePoolSize = 1;
        @Min(1) private int maxPoolSize = 1;
        @Min(0) private int queueCapacity = 5;
        @NotNull private Duration awaitTermination = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Agent {
        @Min(1) @Max(20) private int topK = 6;
        @DecimalMin("0.0") @DecimalMax("1.0") private double similarityThreshold = 0.2;
        @Min(1) @Max(5) private int maxIterations = 2;
        @DecimalMin("0.0") @DecimalMax("1.0") private double confidenceThreshold = 0.6;
        @NotNull private Duration ruleTimeout = Duration.ofSeconds(240);
        @Min(1) private int maxConsecutiveLlmFailures = 3;
        @DecimalMin("0.0") @DecimalMax("1.0") private double ungroundedConfidenceCap = 0.3;
    }

    @Getter
    @Setter
    public static class Llm {
        @NotBlank private String provider = "ollama";
        @Min(1) private int maxConcurrentCalls = 1;
        @Min(512) private int numCtx = 4096;
        @Min(64) private int reservedOutputTokens = 700;
        @NotNull private Duration statusCacheTtl = Duration.ofSeconds(15);
        @NotNull private Duration probeTimeout = Duration.ofSeconds(3);
        @NotBlank private String ollamaBaseUrl = "http://localhost:11434";
        @NotBlank private String ollamaModel = "llama3.2:3b";
        @NotBlank private String openaiModel = "gpt-4o-mini";
        private String openaiApiKey = "not-configured";

        public boolean isOpenAi() {
            return "openai".equalsIgnoreCase(provider);
        }

        public String activeModel() {
            return isOpenAi() ? openaiModel : ollamaModel;
        }
    }

    @Getter
    @Setter
    public static class Qa {
        @NotNull private QaMode mode = QaMode.RAG;
        @Min(1) @Max(20) private int topK = 5;
        @DecimalMin("0.0") @DecimalMax("1.0") private double similarityThreshold = 0.3;
        @Min(1) @Max(10) private int maxToolRounds = 3;
        @NotNull private Duration timeout = Duration.ofSeconds(280);
    }

    @Getter
    @Setter
    public static class Recovery {
        @NotNull private Duration stuckThreshold = Duration.ZERO;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
