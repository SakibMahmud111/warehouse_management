package com.waremanager.repository;
import com.waremanager.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BatchRepository extends JpaRepository<Batch, Long> {
    boolean existsByBatchNumberIgnoreCase(String number);
    List<Batch> findAllByOrderByExpiryDateAsc();
    List<Batch> findByProductIdOrderByExpiryDateAsc(Long productId);
    List<Batch> findByBinCodeIgnoreCase(String binCode);
}
