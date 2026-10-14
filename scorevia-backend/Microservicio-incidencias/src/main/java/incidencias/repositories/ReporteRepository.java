package incidencias.repositories;

import incidencias.entities.Reporte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReporteRepository extends JpaRepository<Reporte, Integer> {
    Page<Reporte> findByEstadoOrderByFechaReporteDesc(String estado, Pageable pageable);

    @Query("SELECT COUNT(r) > 0 FROM Reporte r WHERE r.idDenunciante = :idDenunciante "
            + "AND r.idDenunciado = :idDenunciado AND r.estado = 'PENDIENTE'")
    boolean existeReportePendiente(@Param("idDenunciante") Integer idDenunciante,
                                    @Param("idDenunciado") Integer idDenunciado);
}