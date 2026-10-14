package usuariosroles.controllers.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.usuario.UsuarioService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUsuarioControllerImplTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private InternalUsuarioControllerImpl controller;

    @Test
    void obtenerNicknames_DelegaEnUsuarioService() {
        List<Integer> ids = List.of(1, 2);
        Map<Integer, String> nicknames = Map.of(1, "capitanFutbol", 2, "goleador10");
        when(usuarioService.obtenerNicknames(ids)).thenReturn(nicknames);

        Map<Integer, String> resultado = controller.obtenerNicknames(ids);

        assertEquals(nicknames, resultado);
    }

    @Test
    void existeUsuario_Existe_DevuelveOk() {
        when(usuarioService.existeUsuario(1)).thenReturn(true);

        ResponseEntity<Void> response = controller.existeUsuario(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void existeUsuario_NoExiste_LanzaNotFound() {
        when(usuarioService.existeUsuario(99)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> controller.existeUsuario(99));
    }

    @Test
    void usuarioActivo_Activo_DevuelveOk() {
        when(usuarioService.usuarioActivo(1)).thenReturn(true);

        ResponseEntity<Void> response = controller.usuarioActivo(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void usuarioActivo_NoExisteONoActivo_LanzaNotFound() {
        when(usuarioService.usuarioActivo(99)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> controller.usuarioActivo(99));
    }
}