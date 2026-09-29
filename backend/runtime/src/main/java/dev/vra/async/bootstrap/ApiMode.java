package dev.vra.async.bootstrap;

import dev.vra.async.adapter.out.producer.JdbcReservationOutboxPublisher;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** API-only composition of the accepted projection-target outbox producer. */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = JdbcReservationOutboxPublisher.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
public class ApiMode {
}
