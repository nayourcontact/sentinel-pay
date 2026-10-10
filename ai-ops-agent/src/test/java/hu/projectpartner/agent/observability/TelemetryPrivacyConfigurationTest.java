package hu.projectpartner.agent.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryPrivacyConfigurationTest {

    @Test
    void removesSensitiveContentButPreservesOperationalDimensions() {
        Observation.Context context = new Observation.Context()
                .addHighCardinalityKeyValue(KeyValue.of("db.vector.query.content", "Customer private prompt"))
                .addHighCardinalityKeyValue(KeyValue.of("db.vector.query.response.documents", "Private document"))
                .addHighCardinalityKeyValue(KeyValue.of("db.collection.name", "vector_store"))
                .addHighCardinalityKeyValue(KeyValue.of("db.vector.query.top_k", "4"));

        Observation.Context filtered = new TelemetryPrivacyConfiguration()
                .sensitiveObservationAttributeFilter().map(context);

        assertThat(filtered.getHighCardinalityKeyValues().stream().map(KeyValue::getKey))
                .contains("db.collection.name", "db.vector.query.top_k")
                .doesNotContain("db.vector.query.content", "db.vector.query.response.documents");
    }
}
