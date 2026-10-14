package estadisticas.entities.entrenadores;

import java.time.LocalDateTime;

import estadisticas.entities.equipos.Equipo;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "entrenadores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entrenador {

    @Id
    @Column(name = "id_entrenador", unique = true, nullable = false)
    private Integer idEntrenador;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "edad")
    private Integer edad;

    @Column(name = "nacionalidad")
    private String nacionalidad;

    @Column(name = "foto")
    private String foto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo")
    private Equipo equipo;

    /**
     * Cuándo se le preguntó por última vez a la API por su palmarés.
     *
     * <p>Sin esto no había forma de distinguir "todavía no le hemos preguntado" de "le
     * preguntamos y no ha ganado nada", que es el caso de la mayoría: el relleno buscaba
     * entrenadores sin ninguna fila en `trofeos` y volvía a gastar una petición en los
     * mismos 17.500 en cada relanzamiento, sin terminar nunca.</p>
     */
    @Column(name = "ultima_sync_trofeos")
    private LocalDateTime ultimaSyncTrofeos;
}