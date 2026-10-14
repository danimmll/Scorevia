package usuariosroles.security.filter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Abstract class used to deserialize {@code SimpleGrantedAuthority} objects from JSON.
 *
 * <p>Spring Security uses {@code SimpleGrantedAuthority} to represent a user's roles
 * and permissions. This creator allows proper reconstruction of the role when reading
 * a JWT or a JSON object.</p>
 *
 * <p>Annotated with {@link JsonCreator} and {@link JsonProperty} to instruct Jackson
 * how to map the {@code authority} field.</p>
 *
 * @author Daniel
 * @version 1.0
 */
public abstract class SimpleGrantedAuthorityJsonCreator {

    /**
     * Constructor used by Jackson to create instances from the "authority" field.
     *
     * @param role Name of the role or authority (e.g., "ROLE_ADMIN").
     */
    @JsonCreator
    protected SimpleGrantedAuthorityJsonCreator(@JsonProperty("authority") String role) {
        // No implementation needed; Jackson uses this internally for data mapping.
    }
}
