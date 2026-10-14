package estadisticas.entities.jugadores;

import java.time.LocalDate;
import java.time.LocalDateTime;

import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Jugador de futbol: ficha personal, equipo actual y datos de nacimiento.
 *
 * @author Daniel
 * @version 1.0
 */
@Entity
@Table(name = "jugadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Jugador {

    @Id
    @Column(name = "id_jugador")
    private Integer idJugador;

    @Column(name = "nombre")
    private String nombre;

    // La API a veces devuelve "nombre" ya abreviado ("L. Messi") -- este es
    // firstname+lastname sin abreviar, cuando la API los trae por separado.
    @Column(name = "nombre_completo")
    private String nombreCompleto;

    @Column(name = "altura")
    private String altura;

    @Column(name = "peso")
    private String peso;

    @Column(name = "nacionalidad")
    private String nacionalidad;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "posicion")
    private String posicion;

    @Column(name = "dorsal")
    private Integer dorsal;

    @Column(name = "foto_url")
    private String fotoUrl;

    @UpdateTimestamp
    @Column(name = "ultima_sync_bajas")
    private LocalDateTime ultimaSyncBajas;

    @Column(name = "perfil_verificado")
    private Boolean perfilVerificado;

    @Column(name = "lugar_nacimiento")
    private String lugarNacimiento;

    @Column(name = "latitud_nacimiento")
    private Double latitudNacimiento;

    @Column(name = "longitud_nacimiento")
    private Double longitudNacimiento;

    @Column(name = "pie_bueno")
    private String pieBueno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo")
    private Equipo equipo;

    // Fecha del partido cuya ingesta fijó por última vez "equipo": permite a
    // EstadisticasIngestServiceImpl.prepararEstadisticaJugadorIndividual no pisar el club
    // actual con uno obsoleto cuando un backfill procesa partidos fuera de orden
    // cronológico (null = todavía no gateado, la próxima ingesta lo fija sin comparar).
    @Column(name = "fecha_equipo_actualizado")
    private LocalDateTime fechaEquipoActualizado;
}
