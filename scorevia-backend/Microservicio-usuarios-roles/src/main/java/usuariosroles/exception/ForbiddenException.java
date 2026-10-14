package usuariosroles.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException implements ConCodigo {
    /**
     * Codigo estable para identificar ESTE error concreto, o null si vale el generico.
     *
     * <p>El `type` del ProblemDetail solo tenia 11 valores ({@code conflict},
     * {@code not-found}...). El front traduce por ese codigo, asi que con la interfaz en
     * ingles "Ese nombre ya esta en uso" acababa siendo "That conflicts with something that
     * already exists". Con un codigo propio ({@code nickname-en-uso}) el ingles dice lo
     * mismo que el espanol.</p>
     *
     * <p>Es opcional a proposito: quien no lo pase se comporta igual que antes.</p>
     */
    private final String codigo;

public ForbiddenException(String message) {
        super(message);
        this.codigo = null;
    }

    public ForbiddenException(String message, String codigo) {
        super(message);
        this.codigo = codigo;
    }

    @Override
    public String getCodigo() {
        return codigo;
    }
}