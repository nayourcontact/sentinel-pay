package hu.projectpartner.agent.service;

import hu.projectpartner.agent.web.dto.ChatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;
    private final String modelName;

    public AgentService(
            ChatClient enterpriseAgentChatClient,
            MeterRegistry meterRegistry,
            @Value("${spring.ai.ollama.chat.options.model}") String modelName) {
        this.chatClient = enterpriseAgentChatClient;
        this.meterRegistry = meterRegistry;
        this.modelName = modelName;
    }

    public ChatResponse chat(String message) {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        log.info("event=agent.run.started requestId={} model={} promptChars={}", requestId, modelName, message.length());

        try {
            String answer = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();

            long durationNanos = System.nanoTime() - start;
            meterRegistry.counter("agent_requests_total", "status", "success").increment();
            meterRegistry.timer("agent_request_duration").record(Duration.ofNanos(durationNanos));

            log.info("event=agent.run.completed requestId={} status=SUCCESS answerChars={} durationMs={}",
                    requestId,
                    answer == null ? 0 : answer.length(),
                    durationNanos / 1_000_000);

            return new ChatResponse(
                    requestId,
                    answer,
                    modelName,
                    "Ollama + Spring AI agent + MCP tools + PGVector RAG",
                    Instant.now());
        } catch (RuntimeException ex) {
            meterRegistry.counter("agent_requests_total", "status", "error").increment();
            log.warn("event=agent.run.completed requestId={} status=ERROR errorType={}",
                    requestId,
                    ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
