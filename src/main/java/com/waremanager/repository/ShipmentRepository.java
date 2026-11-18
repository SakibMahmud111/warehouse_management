package com.waremanager.repository;
import com.waremanager.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    List<Shipment> findByBatchIdOrderByShippedAtDesc(Long batchId);
    List<Shipment> findByOrderLineSalesOrderId(Long orderId);
}
