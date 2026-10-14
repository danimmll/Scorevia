package usuariosroles.config;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import usuariosroles.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Utility class for generating Excel files from generic lists of data.
 *
 * <p>Uses Apache POI to create Excel workbooks, sheets, headers, and rows dynamically
 * based on provided data and mapping functions.</p>
 *
 * <p>This class is non-instantiable and only contains static methods.</p>
 *
 * @author Daniel
 * @version 1.0
 */
public class ExcelUtils {

    // Filas que SXSSFWorkbook mantiene en memoria antes de volcar las más antiguas a disco
    // temporal -- mismo valor por defecto de POI, explícito para que quede claro que es
    // intencional. Independiente del tamaño de lote con el que el caller lea de BD.
    private static final int VENTANA_FILAS_MEMORIA = 100;

    /** Private constructor to prevent instantiation. */
    private ExcelUtils() {
        //CONSTRUCTOR VACIO SONAR
    }

    /** Recibe una fila ya mapeada a valores de celda (una por columna) y la escribe. */
    @FunctionalInterface
    public interface EscritorFila {
        void escribirFila(String[] valoresCeldas);
    }

    /**
     * Exporta datos a un Excel SIN materializarlos todos en memoria de golpe: en vez de
     * recibir una {@code List<T>} completa, recibe un "productor" que empuja cada fila a un
     * {@link EscritorFila} a medida que la va teniendo disponible (típicamente, mientras el
     * caller pagina la consulta a BD por lotes) -- así ni la consulta ni la escritura del
     * Excel necesitan tener el conjunto completo en memoria en ningún momento. Usa
     * {@link SXSSFWorkbook} (el workbook streaming de POI) en vez de {@code XSSFWorkbook},
     * que mantiene solo una ventana de {@value #VENTANA_FILAS_MEMORIA} filas en memoria y
     * vuelca el resto a disco temporal automáticamente.
     *
     * @param nombreHoja Name of the Excel sheet.
     * @param cabeceras  Array of header titles for the columns.
     * @param productor  Recibe un {@link EscritorFila} y le empuja cada fila (ya mapeada a
     *                   {@code String[]}) según las va produciendo; puede leer su fuente de
     *                   datos por lotes internamente sin que esta función lo sepa.
     * @return {@link ByteArrayInputStream} containing the generated Excel file.
     * @throws BadRequestException if an unexpected error occurs during file generation.
     */
    public static ByteArrayInputStream exportarListaStreaming(
            String nombreHoja,
            String[] cabeceras,
            Consumer<EscritorFila> productor) {

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(VENTANA_FILAS_MEMORIA);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(nombreHoja);

            // Create header row with bold style
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int i = 0; i < cabeceras.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cabeceras[i]);
                cell.setCellStyle(headerStyle);
                // sheet.autoSizeColumn(i) no es compatible con SXSSFWorkbook una vez las
                // filas ya se han volcado a disco (lanza IllegalStateException) -- lo que
                // pasa casi seguro en un export de toda la tabla. Ancho fijo razonable en su
                // lugar en vez de intentar ajustarlo al contenido real.
                sheet.setColumnWidth(i, 20 * 256);
            }

            int[] rowIdx = {1};
            productor.accept(valoresCeldas -> {
                Row row = sheet.createRow(rowIdx[0]++);
                for (int i = 0; i < valoresCeldas.length; i++) {
                    row.createCell(i).setCellValue(sanearCelda(valoresCeldas[i]));
                }
            });

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new BadRequestException("Error inesperado al generar el archivo Excel");
        }
        // workbook.close() (llamado automáticamente por el try-with-resources de arriba) ya
        // libera los ficheros temporales que SXSSFWorkbook usa para las filas volcadas a
        // disco -- dispose() es el método antiguo para lo mismo, ahora obsoleto en POI.
    }

    /**
     * Prevents formula/CSV injection: a value from user-controlled data (nombre,
     * apellido, correo...) that starts with a formula-trigger character would be
     * interpreted as a live formula by Excel/Sheets when the admin opens the export.
     * Prefixing it with an apostrophe forces it to render as plain text.
     */
    private static String sanearCelda(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.startsWith("=") || valor.startsWith("+") || valor.startsWith("-")
                || valor.startsWith("@") || valor.startsWith("\t") || valor.startsWith("\r")) {
            return "'" + valor;
        }
        return valor;
    }
}
