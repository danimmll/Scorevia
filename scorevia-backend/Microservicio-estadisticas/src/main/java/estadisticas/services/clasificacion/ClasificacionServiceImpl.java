package estadisticas.services.clasificacion;

import estadisticas.dto.ligas.ClasificacionDTO;
import estadisticas.mappers.ligas.ClasificacionMapper;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClasificacionServiceImpl implements ClasificacionService {

    private static final int PARTIDOS_FORMA = 5;

    private final ClasificacionRepository clasificacionRepository;
    private final ClasificacionMapper clasificacionMapper;
    private final PartidoRepository partidoRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "clasificaciones", key = "#idLiga + '_' + #temporada")
    public List<ClasificacionDTO> obtenerClasificacion(Integer idLiga, Integer temporada) {
        List<ClasificacionDTO> tabla = clasificacionRepository
                .findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(idLiga, temporada).stream()
                .map(clasificacionMapper::toDto).toList();

        Map<Integer, List<String>> formaPorEquipo = calcularFormaDeTodos(idLiga, temporada);
        tabla.forEach(fila -> fila.setForma(
                formaPorEquipo.getOrDefault(fila.getEquipo().getIdEquipo(), List.of())));
        return tabla;
    }

    /**
     * La racha de los ultimos partidos de <b>todos</b> los equipos de la tabla, de una vez.
     *
     * <p>Antes esto era una consulta por equipo, y cada una leia los partidos de ese equipo
     * en toda su historia (875 en el caso medido) para quedarse con cinco, porque el
     * {@code OR} entre local y visitante impide que el indice resuelva el orden por fecha.
     * Veinte equipos a 108 ms cada uno son los 11,4 segundos que tardaba la primera carga de
     * una clasificacion.</p>
     *
     * <p>Y de paso arregla algo que estaba mal, no solo lento: aquella consulta <b>no
     * filtraba por temporada ni por competicion</b>. La racha que salia en la tabla de 2010
     * eran los ultimos cinco partidos que el equipo hubiera jugado en cualquier torneo,
     * normalmente de la temporada en curso. Ahora es lo que dice ser: los ultimos partidos
     * <b>de esa competicion y esa temporada</b>.</p>
     */
    private Map<Integer, List<String>> calcularFormaDeTodos(Integer idLiga, Integer temporada) {
        Map<Integer, List<String>> forma = new HashMap<>();

        // Los partidos llegan del mas antiguo al mas reciente, asi que basta con ir anadiendo
        // por la cola y recortar por la cabeza cuando la lista pasa de cinco.
        for (Object[] fila : partidoRepository.findResultadosDeLigaYTemporada(idLiga, temporada)) {
            Integer local = (Integer) fila[0];
            Integer visitante = (Integer) fila[1];
            int golesLocal = (Integer) fila[2];
            int golesVisitante = (Integer) fila[3];

            anotar(forma, local, resultado(golesLocal, golesVisitante));
            anotar(forma, visitante, resultado(golesVisitante, golesLocal));
        }
        return forma;
    }

    private void anotar(Map<Integer, List<String>> forma, Integer idEquipo, String resultado) {
        if (idEquipo == null) {
            return;
        }
        List<String> racha = forma.computeIfAbsent(idEquipo, k -> new ArrayList<>());
        racha.add(resultado);
        if (racha.size() > PARTIDOS_FORMA) {
            racha.remove(0);
        }
    }

    private String resultado(int golesPropios, int golesRivales) {
        if (golesPropios > golesRivales) return "V";
        if (golesPropios < golesRivales) return "D";
        return "E";
    }
}