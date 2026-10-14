package incidencias.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reportes", schema = "sch_incidencias")
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte")
    private Integer idReporte;

    @Column(name = "id_denunciante", nullable = false)
    private Integer idDenunciante;

    @Column(name = "id_denunciado", nullable = false)
    private Integer idDenunciado;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "estado", length = 50)
    private String estado = "PENDIENTE";

    @Column(name = "fecha_reporte", insertable = false, updatable = false)
    private LocalDateTime fechaReporte;

    @Column(name = "resolucion", columnDefinition = "TEXT")
    private String resolucion;

    @Column(name = "id_admin_resolutor")
    private Integer idAdminResolutor;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
}