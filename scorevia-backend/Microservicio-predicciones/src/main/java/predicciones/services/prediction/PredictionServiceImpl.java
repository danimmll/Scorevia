package predicciones.services.prediction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import predicciones.dto.ArbitroStatsDTO;
import predicciones.dto.ContextoJugadorPrediccionDTO;
import predicciones.dto.LivePredictionDTO;
import predicciones.dto.internal.ClasificacionDTO;
import predicciones.dto.internal.EntrenadorDTO;
import predicciones.dto.internal.EstadisticaEquipoDTO;
import predicciones.dto.internal.EstadisticaJugadorPartidoDTO;
import predicciones.dto.FormaPrediccionDTO;
import predicciones.dto.internal.EventoPartidoDTO;
import predicciones.dto.internal.HistorialBajaDTO;
import predicciones.dto.internal.PartidoDTO;
import predicciones.exception.ConflictException;
import predicciones.exception.ForbiddenException;
import predicciones.exception.NotFoundException;
import predicciones.services.predictionengine.PredictionEngineService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionServiceImpl implements PredictionService {

    private static final String ESTADO_FINALIZADO = "FINALIZADO";

    private final EstadisticasFeignGateway estadisticasFeignGateway;
    private final PredictionEngineService predictionEngine;

    @Override
    public Map<String, BigDecimal> predecirPartido(Integer idPartido, Integer idUsuario) {
        log.info("Iniciando orquestación de predicción PRE-MATCH para el partido: {}", idPartido);
        verificarAcceso(idUsuario, idPartido);
        FormaPrediccionDTO contexto = obtenerContexto(idPartido);
        double[] lambdas = calcularLambdasPreMatch(contexto);
        return predictionEngine.calcularProbabilidadesPartido(lambdas[0], lambdas[1]);
    }

    @Override
    public LivePredictionDTO predecirPartidoEnVivo(Integer idPartido, int minuto, Integer idUsuario) {
        log.info("Calculando predicción LIVE para partido {}. Minuto: {}", idPartido, minuto);
        verificarAcceso(idUsuario, idPartido);

        // 1. Obtener contexto base y lambdas pre-match
        FormaPrediccionDTO contexto = obtenerContexto(idPartido);
        double[] lambdasPreMatch = calcularLambdasPreMatch(contexto);

        // El marcador real se toma del propio contexto (siempre se re-lee fresco del
        // partido en estadísticas, ver comentario en InternalDataServiceImpl); el contrato de
        // la API ni siquiera acepta un marcador del cliente (antes exigía golesLocal/
        // golesVisitante como query params obligatorios pero los ignoraba por completo -- es
        // una feature Premium, y de lo contrario cualquiera podría manipular su propia
        // predicción "en vivo" enviando un marcador falso). El minuto sí sigue viniendo
        // del cliente -- no hay hoy una fuente fiable del minuto exacto en el contrato de datos
        // de estadísticas -- pero ya no puede alterar el marcador usado en el cálculo.
        int golesLocalReales = obtenerGolesReales(contexto, true);
        int golesVisitanteReales = obtenerGolesReales(contexto, false);

        int minutoEfectivo = Math.min(minuto, 90);

        // 2. Traer eventos en vivo (para detectar rojas)
        List<EventoPartidoDTO> eventosEnVivo = obtenerEventosEnVivo(idPartido);

        // 3. Modificar por Time Decay
        double porcentajeTiempoRestante = predictionEngine.calcularProporcionTiempoRestante(minutoEfectivo);
        double lambdaLocalLive = lambdasPreMatch[0] * porcentajeTiempoRestante;
        double lambdaVisitanteLive = lambdasPreMatch[1] * porcentajeTiempoRestante;

        // 4. APLICAR IMPACTO EVENTOS EN VIVO (Las rojas destrozan el lambda en directo)
        double[] lambdasCorregidos = predictionEngine.aplicarImpactoEventosEnVivo(
                lambdaLocalLive, lambdaVisitanteLive, eventosEnVivo,
                contexto.getIdEquipoLocal(), contexto.getIdEquipoVisitante()
        );

        // 5. Calcular la campana con los datos definitivos
        Map<String, BigDecimal> probRestantes = predictionEngine.calcularProbabilidades(
                lambdasCorregidos[0], lambdasCorregidos[1], golesLocalReales, golesVisitanteReales);

        return LivePredictionDTO.builder()
                .idPartido(idPartido)
                .minutoActual(minuto)
                .golesLocalActuales(golesLocalReales)
                .golesVisitanteActuales(golesVisitanteReales)
                .lambdaLocalRestante(Math.round(lambdasCorregidos[0] * 1000.0) / 1000.0)
                .lambdaVisitanteRestante(Math.round(lambdasCorregidos[1] * 1000.0) / 1000.0)
                .probabilidades(probRestantes)
                .build();
    }

    // Si por lo que sea el partido no está en el contexto (no debería pasar: obtenerContexto
    // ya validó el contexto completo), se asume 0-0 en vez de confiar en lo que mande el
    // cliente -- más conservador que aceptar un marcador no verificado.
    private int obtenerGolesReales(FormaPrediccionDTO contexto, boolean equipoLocal) {
        PartidoDTO partido = contexto.getPartido();
        if (partido == null) {
            return 0;
        }
        Integer resultado = equipoLocal ? partido.getResultadoLocal() : partido.getResultadoVisitante();
        return resultado != null ? resultado : 0;
    }

    // Las predicciones son una feature de pago: solo ADMIN y PREMIUM tienen acceso. El filtro
    // de seguridad no bloquea por rol a nivel de ruta, así que la decisión de negocio vive aquí.
    private void verificarAcceso(Integer idUsuario, Integer idPartido) {
        boolean esPremiumOAdmin = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getAuthorities)
                .map(authorities -> authorities.stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_PREMIUM".equals(a.getAuthority())))
                .orElse(false);

        if (!esPremiumOAdmin) {
            log.info("Usuario {} sin rol Premium intentó acceder a la predicción del partido {}", idUsuario, idPartido);
            throw new ForbiddenException("Las predicciones son exclusivas para usuarios Premium.");
        }
    }

    private double[] calcularLambdasPreMatch(FormaPrediccionDTO contexto) {
        Long idLocal = contexto.getIdEquipoLocal();
        Long idVisitante = contexto.getIdEquipoVisitante();

        double lambdaLocalBase = calcularMediaGolesAnotados(contexto.getHistoricoStatsEquiposLocal());
        double lambdaVisitanteBase = calcularMediaGolesAnotados(contexto.getHistoricoStatsEquiposVisitante());

        double modRachaLocal = calcularModificadorRacha(contexto.getUltimosPartidosLocal(), idLocal, contexto.getPartido());
        double modRachaVisitante = calcularModificadorRacha(contexto.getUltimosPartidosVisitante(), idVisitante, contexto.getPartido());
        double modRachaPuraLocal = calcularModificadorRacha(contexto.getUltimosPartidosLocalComoLocal(), idLocal, contexto.getPartido());
        double modRachaPuraVisitante = calcularModificadorRacha(contexto.getUltimosPartidosVisitanteComoVisitante(), idVisitante, contexto.getPartido());

        double rachaFinalLocal = (modRachaPuraLocal * 0.70) + (modRachaLocal * 0.30);
        double rachaFinalVisitante = (modRachaPuraVisitante * 0.70) + (modRachaVisitante * 0.30);

        double[] modsH2H = calcularModificadorH2H(contexto.getHistorialH2H(), idLocal);

        double modBajasLocal = calcularPenalizacionBajas(contexto.getBajasActivasLocal(), contexto.getPlantillaLocalConStats());
        double modBajasVisitante = calcularPenalizacionBajas(contexto.getBajasActivasVisitante(), contexto.getPlantillaVisitanteConStats());

        double modFatigaLocal = calcularModificadorFatiga(contexto.getPartido(), contexto.getUltimosPartidosLocal());
        double modFatigaVisitante = calcularModificadorFatiga(contexto.getPartido(), contexto.getUltimosPartidosVisitante());

        double modPunteriaLocal = calcularModificadorPunteria(contexto.getHistoricoStatsEquiposLocal(), contexto.getUltimosPartidosLocal(), idLocal);
        double modPunteriaVisitante = calcularModificadorPunteria(contexto.getHistoricoStatsEquiposVisitante(), contexto.getUltimosPartidosVisitante(), idVisitante);

        double modEntrenadorLocal = calcularModificadorEntrenador(contexto.getEntrenadorLocal());
        double modEntrenadorVisitante = calcularModificadorEntrenador(contexto.getEntrenadorVisitante());

        double modArbitro = calcularModificadorArbitro(contexto.getDatosArbitro());

        // El ataque de un equipo también depende de lo sólida (o débil) que sea la defensa rival,
        // no solo de su propia media anotadora — sin esto un equipo goleador siempre "puntuaba"
        // igual da si enfrenta a la mejor o a la peor defensa de la liga.
        double modDefensaLocal = calcularModificadorDefensaRival(contexto.getClasificacionVisitante());
        double modDefensaVisitante = calcularModificadorDefensaRival(contexto.getClasificacionLocal());

        double ataqueLocal = lambdaLocalBase * rachaFinalLocal * modsH2H[0] * modBajasLocal * modPunteriaLocal * modEntrenadorLocal * modFatigaLocal * modDefensaLocal;
        double ataqueVisitante = lambdaVisitanteBase * rachaFinalVisitante * modsH2H[1] * modBajasVisitante * modPunteriaVisitante * modEntrenadorVisitante * modFatigaVisitante * modDefensaVisitante;

        double lambdaLocalFinal = (ataqueLocal * 1.10) * modArbitro;
        double lambdaVisitanteFinal = ataqueVisitante * modArbitro;

        return new double[]{lambdaLocalFinal, lambdaVisitanteFinal};
    }

    private FormaPrediccionDTO obtenerContexto(Integer idPartido) {
        try {
            FormaPrediccionDTO contexto = estadisticasFeignGateway.obtenerContexto(idPartido);
            if (contexto == null
                    || contexto.getHistoricoStatsEquiposLocal() == null || contexto.getHistoricoStatsEquiposLocal().isEmpty()
                    || contexto.getHistoricoStatsEquiposVisitante() == null || contexto.getHistoricoStatsEquiposVisitante().isEmpty()) {
                throw new NotFoundException("Datos insuficientes para predecir este partido.");
            }
            // Se puede comprar y consultar la predicción en cualquier momento del partido
            // (antes o durante), pero una vez FINALIZADO ya no queda "momento" que predecir:
            // el resultado es un hecho, no una probabilidad.
            if (contexto.getPartido() != null && ESTADO_FINALIZADO.equals(contexto.getPartido().getEstado())) {
                throw new ConflictException("Este partido ya ha finalizado, no se pueden calcular predicciones.");
            }
            return contexto;
        } catch (NotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo al traer contexto vía Feign: ", e);
            throw new ConflictException("Sin comunicación con estadísticas.");
        }
    }

    private List<EventoPartidoDTO> obtenerEventosEnVivo(Integer idPartido) {
        try {
            return estadisticasFeignGateway.obtenerEventosEnVivo(idPartido);
        } catch (Exception e) {
            log.error("Fallo al traer eventos en vivo vía Feign: ", e);
            throw new ConflictException("Sin comunicación con estadísticas.");
        }
    }

    private double calcularMediaGolesAnotados(List<EstadisticaEquipoDTO> statsHistorial) {
        if (statsHistorial == null || statsHistorial.isEmpty()) return 0.5;
        BigDecimal suma = BigDecimal.ZERO;
        int validos = 0;
        for (EstadisticaEquipoDTO stat : statsHistorial) {
            if (stat.getGolesEsperados() != null) {
                suma = suma.add(stat.getGolesEsperados());
                validos++;
            }
        }
        return validos == 0 ? 0.5 : suma.divide(new BigDecimal(validos), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static final long DIAS_RACHA_FIABLE = 45;
    private static final long DIAS_RACHA_IRRELEVANTE = 90;

    private double calcularModificadorRacha(List<PartidoDTO> partidos, Long idEquipo, PartidoDTO partidoObjetivo) {
        if (partidos == null || partidos.isEmpty()) return 1.0;
        double mod = 1.0;
        for (PartidoDTO p : partidos) {
            if (p.getResultadoLocal() != null && p.getResultadoVisitante() != null && !p.getResultadoLocal().equals(p.getResultadoVisitante())) {
                boolean esLocal = p.getIdEquipoLocal().equals(Math.toIntExact(idEquipo));
                boolean gano = (esLocal && p.getResultadoLocal() > p.getResultadoVisitante()) || (!esLocal && p.getResultadoVisitante() > p.getResultadoLocal());
                // Peso por antigüedad calculado POR PARTIDO, no una vez para toda la lista:
                // si la lista mezcla partidos recientes con otros que ya superan
                // DIAS_RACHA_IRRELEVANTE, estos últimos deben pesar poco o nada por sí
                // mismos, no heredar la fiabilidad del partido más reciente de la lista.
                double pesoPartido = calcularPesoPorAntiguedad(p, partidoObjetivo);
                mod += (gano ? 0.03 : -0.03) * pesoPartido;
            }
        }
        return Math.clamp(mod, 0.70, 1.30);
    }

    private double calcularPesoPorAntiguedad(PartidoDTO partido, PartidoDTO partidoObjetivo) {
        if (partido == null || partido.getFecha() == null || partidoObjetivo == null || partidoObjetivo.getFecha() == null) {
            return 1.0;
        }
        long diasDesde = Math.abs(ChronoUnit.DAYS.between(partido.getFecha().toLocalDate(), partidoObjetivo.getFecha().toLocalDate()));
        if (diasDesde <= DIAS_RACHA_FIABLE) return 1.0;
        if (diasDesde >= DIAS_RACHA_IRRELEVANTE) return 0.0;
        double rango = (DIAS_RACHA_IRRELEVANTE - DIAS_RACHA_FIABLE);
        return 1.0 - (((double) diasDesde - DIAS_RACHA_FIABLE) / rango);
    }

    private double[] calcularModificadorH2H(List<PartidoDTO> h2h, Long idLocal) {
        double[] mods = {1.0, 1.0};
        if (h2h == null) return mods;

        for (PartidoDTO p : h2h) {
            if (p.getResultadoLocal() != null && p.getResultadoVisitante() != null && !p.getResultadoLocal().equals(p.getResultadoVisitante())) {
                boolean jugoLocal = p.getIdEquipoLocal().equals(Math.toIntExact(idLocal));
                boolean ganoLocal = (jugoLocal && p.getResultadoLocal() > p.getResultadoVisitante()) || (!jugoLocal && p.getResultadoLocal() < p.getResultadoVisitante());

                mods[0] += ganoLocal ? 0.02 : -0.02;
                mods[1] += ganoLocal ? -0.02 : 0.02;
            }
        }
        mods[0] = Math.clamp(mods[0], 0.70, 1.30);
        mods[1] = Math.clamp(mods[1], 0.70, 1.30);

        return mods;
    }

    private static final double PENALIZACION_BASE_POR_BAJA = 0.02;
    private static final double PESO_IMPORTANCIA_GOLEADORA_BAJAS = 0.25;
    private static final double PENALIZACION_MINIMA_BAJAS = 0.60;

    /**
     * Penaliza el ataque según cuántas bajas hay y según cuánto peso goleador
     * representan esas bajas dentro de su plantilla (perder al máximo goleador no es lo mismo
     * que perder a un suplente sin goles).
     */
    private double calcularPenalizacionBajas(List<HistorialBajaDTO> bajas, List<ContextoJugadorPrediccionDTO> plantillaConStats) {
        if (bajas == null || bajas.isEmpty()) return 1.0;

        double penalizacionPorCantidad = 1.0 - (bajas.size() * PENALIZACION_BASE_POR_BAJA);
        double importanciaGoleadoraPerdida = calcularImportanciaGoleadoraPerdida(bajas, plantillaConStats);
        double penalizacionTotal = penalizacionPorCantidad - (importanciaGoleadoraPerdida * PESO_IMPORTANCIA_GOLEADORA_BAJAS);

        return Math.max(PENALIZACION_MINIMA_BAJAS, penalizacionTotal);
    }

    /**
     * Proporción de los goles totales de la temporada del equipo que corresponden a jugadores
     * actualmente de baja. 0 = las bajas no afectan al goleo del equipo, 1 = se han ido todos
     * sus goleadores.
     */
    private double calcularImportanciaGoleadoraPerdida(List<HistorialBajaDTO> bajas, List<ContextoJugadorPrediccionDTO> plantillaConStats) {
        if (plantillaConStats == null || plantillaConStats.isEmpty()) return 0.0;

        Map<Integer, Integer> golesPorJugador = new HashMap<>();
        int golesTotalesEquipo = 0;
        for (ContextoJugadorPrediccionDTO ctx : plantillaConStats) {
            if (ctx.getJugador() == null || ctx.getJugador().getIdJugador() == null) continue;
            int goles = sumarGoles(ctx.getHistorialStatsTemporada());
            golesPorJugador.put(ctx.getJugador().getIdJugador(), goles);
            golesTotalesEquipo += goles;
        }

        if (golesTotalesEquipo == 0) return 0.0;

        int golesDeBajas = 0;
        for (HistorialBajaDTO baja : bajas) {
            golesDeBajas += golesPorJugador.getOrDefault(baja.getIdJugador(), 0);
        }

        return Math.min(1.0, golesDeBajas / (double) golesTotalesEquipo);
    }

    private int sumarGoles(List<EstadisticaJugadorPartidoDTO> historial) {
        if (historial == null) return 0;
        int total = 0;
        for (EstadisticaJugadorPartidoDTO stat : historial) {
            if (stat.getGoles() != null) total += stat.getGoles();
        }
        return total;
    }

    private static final double MEDIA_GOLES_ENCAJADOS_REFERENCIA = 1.35;
    private static final double MODIFICADOR_DEFENSA_MIN = 0.75;
    private static final double MODIFICADOR_DEFENSA_MAX = 1.25;

    /**
     * Ajusta el ataque de un equipo en función de la fortaleza defensiva real del rival
     * (goles encajados por partido en liga), no solo de su propia media anotadora.
     */
    private double calcularModificadorDefensaRival(ClasificacionDTO clasificacionRival) {
        if (clasificacionRival == null || clasificacionRival.getPartidosJugados() == null
                || clasificacionRival.getPartidosJugados() == 0 || clasificacionRival.getGolesContra() == null) {
            return 1.0;
        }
        double golesEncajadosPorPartido = clasificacionRival.getGolesContra() / (double) clasificacionRival.getPartidosJugados();
        double ratio = golesEncajadosPorPartido / MEDIA_GOLES_ENCAJADOS_REFERENCIA;
        return Math.clamp(ratio, MODIFICADOR_DEFENSA_MIN, MODIFICADOR_DEFENSA_MAX);
    }

    private double calcularModificadorEntrenador(EntrenadorDTO entrenador) {
        return entrenador != null && entrenador.getNombre() != null ? 1.02 : 1.0;
    }

    private double calcularModificadorArbitro(ArbitroStatsDTO arbitro) {
        return arbitro != null && arbitro.getMediaTarjetasRojas() != null && arbitro.getMediaTarjetasRojas().doubleValue() > 0.25 ? 1.03 : 1.0;
    }

    private double calcularModificadorPunteria(List<EstadisticaEquipoDTO> stats, List<PartidoDTO> partidos, Long idEquipo) {
        if (stats == null || stats.isEmpty() || partidos == null || partidos.isEmpty()) return 1.0;
        double xGTotal = 0;
        for (EstadisticaEquipoDTO stat : stats) {
            if (stat.getGolesEsperados() != null) xGTotal += stat.getGolesEsperados().doubleValue();
        }
        double golesReales = 0;
        for (PartidoDTO p : partidos) {
            if (p.getResultadoLocal() != null && p.getResultadoVisitante() != null) {
                golesReales += p.getIdEquipoLocal().equals(Math.toIntExact(idEquipo)) ? p.getResultadoLocal() : p.getResultadoVisitante();
            }
        }
        if (xGTotal == 0) return 1.0;
        double ratio = golesReales / xGTotal;
        if (ratio < 0.8) return 0.90;
        if (ratio > 1.2) return 1.10;
        return 1.0;
    }

    private double calcularModificadorFatiga(PartidoDTO partidoActual, List<PartidoDTO> ultimosPartidos) {
        if (partidoActual == null || partidoActual.getFecha() == null || ultimosPartidos == null || ultimosPartidos.isEmpty()) {
            return 1.0;
        }
        PartidoDTO ultimoPartido = ultimosPartidos.getFirst();
        if (ultimoPartido.getFecha() == null) return 1.0;

        long diasDescanso = ChronoUnit.DAYS.between(ultimoPartido.getFecha().toLocalDate(), partidoActual.getFecha().toLocalDate());
        return Math.abs(diasDescanso) < 4 ? 0.95 : 1.0;
    }
}