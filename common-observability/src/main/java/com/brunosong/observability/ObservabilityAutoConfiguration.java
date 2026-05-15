package com.brunosong.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            ObservabilityProperties properties,
            @Value("${spring.application.name:unknown}") String applicationName) {
        return registry -> registry.config().commonTags(Tags.of(
                "application", applicationName,
                "namespace", properties.getNamespace(),
                "env", properties.getEnvironment()
        ));
    }
}
