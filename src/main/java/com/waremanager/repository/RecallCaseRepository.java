package com.waremanager.repository;
import com.waremanager.model.RecallCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RecallCaseRepository extends JpaRepository<RecallCase, Long> {
    List<RecallCase> findAllByOrderByOpenedAtDesc();
    List<RecallCase> findByBatchId(Long batchId);
}
