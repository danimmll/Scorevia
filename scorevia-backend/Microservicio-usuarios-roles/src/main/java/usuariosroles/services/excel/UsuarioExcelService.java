package usuariosroles.services.excel;

import java.io.ByteArrayInputStream;
import java.util.function.Consumer;
import usuariosroles.dto.UsuarioDTO;

/**
 * Interface for the service that exports user data to Excel format.
 * Defines the contract for generating an Excel file containing user information.
 */
public interface UsuarioExcelService {

    /**
     * Exports users to an Excel file sin materializar la lista completa en memoria: recibe
     * un "productor" que le empuja cada {@link UsuarioDTO} a un {@code Consumer} a medida
     * que los va teniendo disponibles (típicamente, paginando la consulta a BD por lotes).
     *
     * @param productor Recibe el consumidor al que empujar cada usuario, uno a uno.
     * @return ByteArrayInputStream representing the generated Excel file.
     */
    ByteArrayInputStream exportarUsuariosAExcel(Consumer<Consumer<UsuarioDTO>> productor);
}
