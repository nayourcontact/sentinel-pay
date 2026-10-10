package hu.projectpartner.agent.observability;

import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defense-in-depth against capturing user prompts and retrieved data in telemetry.
 * Filtering is independent of Spring AI observation verbosity configuration.
 */
@Configuration(proxyBeanMethods = false)
public class TelemetryPrivacyConfiguration {

    @Bean
    ObservationFilter sensitiveObservationAttributeFilter() {
        return context -> context.removeHighCardinalityKeyValues(
                "db.vector.query.content",
                "db.vector.query.filter",
                "db.vector.query.response.documents",
                "gen_ai.prompt",
                "gen_ai.completion",
                "spring.ai.chat.client.input",
                "spring.ai.chat.client.output",
                "spring.ai.tool.call.arguments",
                "spring.ai.tool.call.result",
                "spring.ai.model.request.messages",
                "spring.ai.model.response.messages"
        );
    }
}
