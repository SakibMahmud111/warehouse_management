package com.waremanager.repository;
import com.waremanager.model.ExcursionIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ExcursionIncidentRepository extends JpaRepository<ExcursionIncident, Long> {
    List<ExcursionIncident> findAllByOrderByIdDesc();
    List<ExcursionIncident> findByBatchIdAndStatus(Long batchId, ExcursionIncident.Status status);
}
