package predicciones.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Jackson configuration for the project.
 *
 * <p>Registers the {@link JavaTimeModule} to properly handle Java 8+ date and time classes
 * and disables writing dates as timestamps.</p>
 *
 * <p>This configuration ensures that dates are serialized in a readable and standard format
 * in JSON responses.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates and configures an {@link ObjectMapper} with support for Java date and time
     * and without writing timestamps.
     *
     * @return Configured {@link ObjectMapper} instance.
     */
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
