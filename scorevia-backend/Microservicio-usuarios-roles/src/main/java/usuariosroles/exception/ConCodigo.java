package usuariosroles.exception;

/**
 * Una excepcion que sabe identificarse con un codigo estable y sin idioma.
 *
 * <p>Ese codigo viaja en el {@code type} del ProblemDetail y es lo que el frontend usa
 * para traducir el mensaje. El {@code detail} del backend esta escrito en espanol, asi que
 * sin un codigo no habia forma de dar la misma frase en ingles sin duplicar los textos en
 * los cinco microservicios.</p>
 */
public interface ConCodigo {
    String getCodigo();
}
