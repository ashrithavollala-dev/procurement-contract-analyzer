package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.exception.ApiException;
import com.company.contractanalyzer.exception.LlmTimeoutException;
import com.company.contractanalyzer.exception.LlmUnavailableException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Maps provider exceptions to the API's error model by walking the cause chain.
 * <ul>
 *   <li>Connection refused, unknown host, connect timeout, HTTP 401/403/404 (bad key, model
 *       not pulled): {@link LlmUnavailableException} with {@code connectionFailure=true}.</li>
 *   <li>Read timeouts: {@link LlmTimeoutException}. The model is reachable but slow, so this
 *       is not counted by the circuit breaker.</li>
 *   <li>Anything else, such as HTTP 500 or a malformed response:
 *       {@link LlmUnavailableException} with {@code connectionFailure=false}.</li>
 * </ul>
 * Messages are generic: provider error bodies may echo prompt content, so they are not copied.
 */
public final class LlmErrorClassifier {

    private LlmErrorClassifier() {
    }

    public static ApiException classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof LlmUnavailableException || t instanceof LlmTimeoutException) {
                return (ApiException) t;
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException || t instanceof HttpConnectTimeoutException
                    || t instanceof UnresolvedAddressException) {
                return new LlmUnavailableException("The language model server is not reachable", true);
            }
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException
                    || t instanceof TimeoutException) {
                return new LlmTimeoutException("The language model did not answer in time");
            }
        }
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("401") || message.contains("403") || message.contains("api key")
                || message.contains("unauthorized")) {
            return new LlmUnavailableException("The language model rejected the credentials (check OPENAI_API_KEY)", true);
        }
        if (message.contains("404") || (message.contains("model") && message.contains("not found"))) {
            return new LlmUnavailableException("The configured language model is not available (is it pulled?)", true);
        }
        return new LlmUnavailableException("The language model returned an error ("
                + error.getClass().getSimpleName() + ")", false);
    }

    /** True when a provider rejected tool definitions (used for the tools -> rag fallback). */
    public static boolean isToolsUnsupported(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("does not support tools") || message.contains("tools are not supported")
                    || message.contains("tool calling is not supported")) {
                return true;
            }
        }
        return false;
    }
}
