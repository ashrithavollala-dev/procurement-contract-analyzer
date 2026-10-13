package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.entity.AuditFinding;
import com.company.contractanalyzer.entity.AuditRun;
import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.AuditStatus;
import com.company.contractanalyzer.repository.AuditFindingRepository;
import com.company.contractanalyzer.repository.AuditRunRepository;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.RetrievedChunk;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Read-only tools for the Q&A agent.
 *
 * <ul>
 *   <li><b>Scope.</b> The contract id comes only from the server-side {@link ToolContext}. The
 *       ToolContext parameter is excluded from the JSON schema the model sees, so the model
 *       cannot pick or override the contract. Spring AI ignores extra arguments it sends.</li>
 *   <li><b>Validation.</b> Arguments are validated and clamped. Invalid input returns an
 *       explanatory string instead of throwing.</li>
 *   <li><b>Read-only.</b> No tool writes anything. Every retrieved chunk is recorded in the
 *       request's {@link CitationCollector}.</li>
 * </ul>
 */
@Component
public class ContractTools {

    public static final String CTX_CONTRACT_ID = "contractId";
    public static final String CTX_CITATIONS = "citations";
    private static final Pattern RULE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    private static final int MAX_TOOL_TOP_K = 8;

    private final VectorChunkRepository vectorChunks;
    private final ContractRepository contractRepository;
    private final AuditRunRepository auditRunRepository;
    private final AuditFindingRepository findingRepository;
    private final AppProperties properties;

    public ContractTools(VectorChunkRepository vectorChunks, ContractRepository contractRepository,
                         AuditRunRepository auditRunRepository, AuditFindingRepository findingRepository,
                         AppProperties properties) {
        this.vectorChunks = vectorChunks;
        this.contractRepository = contractRepository;
        this.auditRunRepository = auditRunRepository;
        this.findingRepository = findingRepository;
        this.properties = properties;
    }

    @Tool(description = "Search the selected contract for passages relevant to a query. Returns numbered excerpts with page numbers.")
    public String searchClauses(
            @ToolParam(description = "What to look for, e.g. 'termination notice period'") String query,
            @ToolParam(description = "Number of passages to return, 1 to 8", required = false) Integer topK,
            ToolContext toolContext) {
        if (query == null || query.strip().length() < 2 || query.length() > 500) {
            return "Invalid query: provide 2 to 500 characters describing what to look for.";
        }
        int k = topK == null ? properties.getQa().getTopK() : Math.max(1, Math.min(MAX_TOOL_TOP_K, topK));
        List<RetrievedChunk> chunks = vectorChunks.search(query.strip(), contractId(toolContext), k,
                properties.getQa().getSimilarityThreshold());
        collector(toolContext).addAll(chunks);
        if (chunks.isEmpty()) {
            return "No matching passages were found in this contract.";
        }
        return Excerpt.renderAll(Excerpt.number(chunks));
    }

    @Tool(description = "Get the selected contract's title, vendor, page count and processing status.")
    public String getContractInfo(ToolContext toolContext) {
        Optional<Contract> contract = contractRepository.findById(contractId(toolContext));
        return contract.map(c -> "Title: " + c.getTitle()
                        + "; Vendor: " + (c.getVendorName() == null ? "unknown" : c.getVendorName())
                        + "; Pages: " + c.getPageCount()
                        + "; Status: " + c.getStatus())
                .orElse("The contract no longer exists.");
    }

    @Tool(description = "Get the summary and risk score of the most recent completed compliance audit of the selected contract.")
    public String getLatestAuditSummary(ToolContext toolContext) {
        return latestCompletedAudit(contractId(toolContext))
                .map(run -> "Audit " + run.getId() + " completed: " + run.getSummary()
                        + " Risk score " + run.getRiskScore() + " (" + run.getRiskBand() + ").")
                .orElse("No completed audit exists for this contract.");
    }

    @Tool(description = "Get the latest audit finding for one compliance rule code, e.g. LIABILITY_CAP or AUDIT_RIGHTS.")
    public String getFinding(
            @ToolParam(description = "Upper-case rule code such as LIABILITY_CAP") String ruleCode,
            ToolContext toolContext) {
        String code = ruleCode == null ? "" : ruleCode.strip().toUpperCase();
        if (!RULE_CODE.matcher(code).matches()) {
            return "Invalid rule code: use an upper-case code such as LIABILITY_CAP.";
        }
        Optional<AuditRun> run = latestCompletedAudit(contractId(toolContext));
        if (run.isEmpty()) {
            return "No completed audit exists for this contract.";
        }
        for (AuditFinding finding : findingRepository.findByAuditRunId(run.get().getId())) {
            if (finding.getRule().getCode().equals(code)) {
                return "Rule " + code + " (" + finding.getRule().getTitle() + "): " + finding.getStatus()
                        + ", confidence " + finding.getConfidence()
                        + (finding.getEvidencePage() == null ? "" : ", evidence on page " + finding.getEvidencePage())
                        + ". " + finding.getExplanation();
            }
        }
        return "The latest audit did not evaluate rule " + code + ".";
    }

    private Optional<AuditRun> latestCompletedAudit(UUID contractId) {
        return auditRunRepository.findFirstByContractIdAndStatusOrderByCreatedAtDesc(contractId, AuditStatus.COMPLETED);
    }

    static UUID contractId(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get(CTX_CONTRACT_ID);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        throw new IllegalStateException("Tool invoked without a server-side contract scope");
    }

    static CitationCollector collector(ToolContext toolContext) {
        Object value = toolContext.getContext().get(CTX_CITATIONS);
        if (value instanceof CitationCollector collector) {
            return collector;
        }
        throw new IllegalStateException("Tool invoked without a citation collector");
    }
}
