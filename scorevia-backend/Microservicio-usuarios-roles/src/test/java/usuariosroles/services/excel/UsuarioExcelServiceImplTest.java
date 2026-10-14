package usuariosroles.services.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import usuariosroles.dto.UsuarioDTO;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioExcelServiceImplTest {

    // Instanciamos el servicio real sin Mockito, para que ejecute el Lambda completo
    private final UsuarioExcelServiceImpl service = new UsuarioExcelServiceImpl();

    @Test
    void exportarUsuariosAExcel_DeberiaCubrirTodosLosCaminosYNulls() {
        // Camino 1: Todos los datos llenos y Activo = true
        List<UsuarioDTO> usuarios = getUsuarioDTOS();

        // Ejecución real: Esto forzará que el lambda evalúe todos los operadores ternarios
        ByteArrayInputStream result = service.exportarUsuariosAExcel(usuarios::forEach);

        // Verificaciones
        assertNotNull(result);
        assertTrue(result.available() > 0, "El archivo Excel generado no debe estar vacío");
    }

    @Test
    void exportarUsuariosAExcel_VolumenMayorQueLaVentanaDeStreaming_ExportaTodasLasFilasCompletas() throws Exception {
        // La ventana en memoria de SXSSFWorkbook es 100 (ver ExcelUtils) -- con más filas
        // que eso, algunas ya se habrán volcado a disco antes de terminar, así que esto
        // prueba de verdad el camino de streaming, no solo un puñado de filas en memoria.
        int totalFilas = 250;
        List<UsuarioDTO> usuarios = new ArrayList<>();
        for (int i = 1; i <= totalFilas; i++) {
            UsuarioDTO u = new UsuarioDTO();
            u.setIdUsuario(i);
            u.setCorreoUsuario("user" + i + "@test.com");
            u.setNombreUsuario("Nombre" + i);
            u.setApellidoUsuario("Apellido" + i);
            u.setActivo(i % 2 == 0);
            usuarios.add(u);
        }

        ByteArrayInputStream result = service.exportarUsuariosAExcel(usuarios::forEach);

        try (Workbook workbook = WorkbookFactory.create(result)) {
            Sheet sheet = workbook.getSheetAt(0);
            // Fila 0 = cabecera, filas 1..totalFilas = datos -> el último índice de fila es
            // exactamente totalFilas.
            assertEquals(totalFilas, sheet.getLastRowNum());

            Row cabecera = sheet.getRow(0);
            assertEquals("ID", cabecera.getCell(0).getStringCellValue());
            assertEquals("Correo", cabecera.getCell(1).getStringCellValue());

            Row primeraFila = sheet.getRow(1);
            assertEquals("1", primeraFila.getCell(0).getStringCellValue());
            assertEquals("user1@test.com", primeraFila.getCell(1).getStringCellValue());
            assertEquals("No", primeraFila.getCell(4).getStringCellValue());

            Row ultimaFila = sheet.getRow(totalFilas);
            assertEquals(String.valueOf(totalFilas), ultimaFila.getCell(0).getStringCellValue());
            assertEquals("Sí", ultimaFila.getCell(4).getStringCellValue());
        }
    }

    @Test
    void exportarUsuariosAExcel_ValorConFormulaInyectada_QuedaSaneado() throws Exception {
        UsuarioDTO malicioso = new UsuarioDTO();
        malicioso.setIdUsuario(1);
        malicioso.setNombreUsuario("=cmd|' /C calc'!A1");
        malicioso.setActivo(true);
        List<UsuarioDTO> usuarios = List.of(malicioso);

        ByteArrayInputStream result = service.exportarUsuariosAExcel(usuarios::forEach);

        try (Workbook workbook = WorkbookFactory.create(result)) {
            Sheet sheet = workbook.getSheetAt(0);
            String valorNombre = sheet.getRow(1).getCell(2).getStringCellValue();
            assertTrue(valorNombre.startsWith("'="), "Un valor que empieza por '=' debe quedar prefijado con apóstrofe");
        }
    }

    private static @NonNull List<UsuarioDTO> getUsuarioDTOS() {
        UsuarioDTO u1 = new UsuarioDTO();
        u1.setIdUsuario(1);
        u1.setCorreoUsuario("admin@scorevia.com");
        u1.setNombreUsuario("Daniel");
        u1.setApellidoUsuario("Martin");
        u1.setActivo(true);

        // Camino 2: Todos los datos vacíos (null)
        UsuarioDTO u2 = new UsuarioDTO();

        // Camino 3: Activo explícitamente a false
        UsuarioDTO u3 = new UsuarioDTO();
        u3.setIdUsuario(3);
        u3.setActivo(false);

        return List.of(u1, u2, u3);
    }
}
