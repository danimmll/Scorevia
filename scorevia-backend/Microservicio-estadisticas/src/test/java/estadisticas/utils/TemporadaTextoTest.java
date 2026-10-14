package estadisticas.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporadaTextoTest {

    @Test
    @DisplayName("Los dos formatos de la API dan el mismo anio")
    void losDosFormatos() {
        // Son la misma temporada escrita distinto segun la competicion, no dos distintas.
        assertEquals(2023, TemporadaTexto.anioDeInicio("2023/2024"));
        assertEquals(2023, TemporadaTexto.anioDeInicio("2023"));
    }

    @Test
    @DisplayName("Lo que no se puede leer no revienta ni se cuela por delante")
    void valoresRaros() {
        int sinTemporada = TemporadaTexto.anioDeInicio(null);
        assertEquals(sinTemporada, TemporadaTexto.anioDeInicio(""));
        assertEquals(sinTemporada, TemporadaTexto.anioDeInicio("20"));
        assertEquals(sinTemporada, TemporadaTexto.anioDeInicio("N/A"));
        assertEquals(sinTemporada, TemporadaTexto.anioDeInicio("20xx"));
    }

    @Test
    @DisplayName("Ordena de la mas reciente a la mas antigua, y las desconocidas al final")
    void ordena() {
        // Con un ORDER BY temporada DESC en SQL, los nulos irian PRIMERO: la linea del tiempo
        // de titulos empezaria por los que no se sabe cuando fueron. Son 108.788 filas.
        List<String> temporadas = new ArrayList<>(List.of("2019", "2023/2024", "2011/2012"));
        temporadas.add(null);
        temporadas.add("2021");

        temporadas.sort(TemporadaTexto.masRecientePrimero(t -> t));

        assertEquals(List.of("2023/2024", "2021", "2019", "2011/2012"), temporadas.subList(0, 4));
        assertEquals(null, temporadas.get(4));
    }
}
