package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.exception.LlmUnavailableException;

/** The provider rejected tool definitions ("model does not support tools"); triggers the rag fallback. */
public class ToolsUnsupportedException extends LlmUnavailableException {

    public ToolsUnsupportedException() {
        super("The configured model does not support tool calling", false);
    }
}
