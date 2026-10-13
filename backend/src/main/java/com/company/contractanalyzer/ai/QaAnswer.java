package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.enums.QaMode;
import com.company.contractanalyzer.repository.RetrievedChunk;
import java.util.List;

/**
 * Answer to one contract question.
 *
 * @param grounded  true only if contract passages were actually retrieved
 * @param mode      the mode that produced the answer (tools may fall back to rag)
 * @param citations the retrieved chunks, collected server-side
 */
public record QaAnswer(String answer, boolean grounded, QaMode mode, List<RetrievedChunk> citations) {
}
