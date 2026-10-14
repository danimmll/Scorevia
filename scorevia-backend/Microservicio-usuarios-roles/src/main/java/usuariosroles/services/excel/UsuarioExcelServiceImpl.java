package usuariosroles.services.excel;

import org.springframework.stereotype.Service;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.config.ExcelUtils;

import java.io.ByteArrayInputStream;
import java.util.function.Consumer;

@Service
public class UsuarioExcelServiceImpl implements UsuarioExcelService {

    @Override
    public ByteArrayInputStream exportarUsuariosAExcel(Consumer<Consumer<UsuarioDTO>> productor) {
        String[] cabeceras = {"ID", "Correo", "Nombre", "Apellidos", "Estado Activo"};

        return ExcelUtils.exportarListaStreaming("Usuarios", cabeceras, escritorFila ->
                productor.accept(usuario -> escritorFila.escribirFila(mapearFila(usuario))));
    }

    private String[] mapearFila(UsuarioDTO usuario) {
        return new String[]{
                usuario.getIdUsuario() != null ? String.valueOf(usuario.getIdUsuario()) : "",
                usuario.getCorreoUsuario() != null ? usuario.getCorreoUsuario() : "",
                usuario.getNombreUsuario() != null ? usuario.getNombreUsuario() : "",
                usuario.getApellidoUsuario() != null ? usuario.getApellidoUsuario() : "",
                usuario.getActivo() != null && usuario.getActivo() ? "Sí" : "No"
        };
    }
}
