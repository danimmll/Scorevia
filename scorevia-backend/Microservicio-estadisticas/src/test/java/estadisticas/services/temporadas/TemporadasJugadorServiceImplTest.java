package estadisticas.services.temporadas;

import estadisticas.dto.jugadores.TemporadaJugadorDTO;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * El mapeo del historial por temporadas de un jugador.
 *
 * <p>Las filas de abajo tienen la forma exacta que devuelve
 * {@code resumenPorTemporada}: temporada, liga (id, nombre, logo, bandera), equipo (id,
 * nombre, escudo), y luego los agregados.</p>
 */
@ExtendWith(MockitoExtension.class)
class TemporadasJugadorServiceImplTest {

    private static final int ID_JUGADOR = 56;

    @Mock private EstadisticaJugadorPartidoRepository estadisticaRepository;

    @InjectMocks private TemporadasJugadorServiceImpl servicio;

    /** Una fila con la forma que devuelve la consulta. Los SUM/COUNT de JPA vuelven como Long. */
    private Object[] fila(Integer temporada, Integer idLiga, String liga, Integer idEquipo,
                          String equipo, long partidos, Long minutos, Long goles, Double valoracion) {
        return new Object[]{
                temporada, idLiga, liga, "logo.png", "bandera.svg",
                idEquipo, equipo, "escudo.png",
                partidos, minutos, goles, 3L, 4L, 0L, valoracion, 30L
        };
    }

    @Test
    @DisplayName("Mapea una temporada con todos sus datos")
    void mapeaUnaTemporada() {
        when(estadisticaRepository.resumenPorTemporada(ID_JUGADOR)).thenReturn(List.<Object[]>of(
                fila(2024, 140, "La Liga", 541, "Real Madrid", 34L, 2810L, 17L, 7.214285)));

        TemporadaJugadorDTO t = servicio.porTemporadas(ID_JUGADOR).get(0);

        assertEquals(2024, t.getTemporada());
        assertEquals("La Liga", t.getLiga());
        assertEquals("bandera.svg", t.getBanderaLiga());
        assertEquals("Real Madrid", t.getEquipo());
        assertEquals(34, t.getPartidos());
        assertEquals(2810, t.getMinutos());
        assertEquals(17, t.getGoles());
        assertEquals(30, t.getTitularidades());
    }

    @Test
    @DisplayName("La valoracion media se redondea a dos decimales")
    void redondeaLaValoracion() {
        // Sin redondear sale un 7.214285714285714 que ni aporta ni cabe en la tabla.
        when(estadisticaRepository.resumenPorTemporada(ID_JUGADOR)).thenReturn(List.<Object[]>of(
                fila(2024, 140, "La Liga", 541, "Real Madrid", 34L, 2810L, 17L, 7.214285)));

        assertEquals(7.21, servicio.porTemporadas(ID_JUGADOR).get(0).getValoracionMedia());
    }

    @Test
    @DisplayName("Una competicion sin valoraciones deja el campo a null, no a cero")
    void sinValoracion() {
        // Un 0.0 se leeria como "jugo fatal"; null es "aqui no se publica ese dato".
        when(estadisticaRepository.resumenPorTemporada(ID_JUGADOR)).thenReturn(List.<Object[]>of(
                fila(2024, 140, "La Liga", 541, "Real Madrid", 34L, 2810L, 17L, null)));

        assertNull(servicio.porTemporadas(ID_JUGADOR).get(0).getValoracionMedia());
    }

    @Test
    @DisplayName("Un cambio de club a mitad de temporada sale como dos filas, no mezclado")
    void dosEquiposEnLaMismaTemporada() {
        // Mezclar las dos etapas en una sola fila esconderia justo lo que se quiere ver.
        when(estadisticaRepository.resumenPorTemporada(ID_JUGADOR)).thenReturn(List.<Object[]>of(
                fila(2024, 140, "La Liga", 541, "Real Madrid", 18L, 1500L, 9L, 7.5),
                fila(2024, 39, "Premier League", 40, "Liverpool", 15L, 1200L, 6L, 7.1)));

        List<TemporadaJugadorDTO> temporadas = servicio.porTemporadas(ID_JUGADOR);

        assertEquals(2, temporadas.size());
        assertEquals("Real Madrid", temporadas.get(0).getEquipo());
        assertEquals("Liverpool", temporadas.get(1).getEquipo());
    }

    @Test
    @DisplayName("Un jugador sin ningun partido devuelve lista vacia, no revienta")
    void sinPartidos() {
        when(estadisticaRepository.resumenPorTemporada(ID_JUGADOR)).thenReturn(List.of());

        assertTrue(servicio.porTemporadas(ID_JUGADOR).isEmpty());
    }
}
