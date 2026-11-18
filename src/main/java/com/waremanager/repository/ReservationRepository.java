package com.waremanager.repository;
import com.waremanager.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByOrderLineSalesOrderIdOrderByIdAsc(Long orderId);
    List<Reservation> findByBatchIdOrderByIdAsc(Long batchId);
    List<Reservation> findByOrderLineIdAndStatus(Long lineId, Reservation.Status status);
    @Query("select coalesce(sum(r.quantity),0) from Reservation r where r.orderLine.product.id=:id and r.status=:status")
    long unitsForProduct(@Param("id") Long productId, @Param("status") Reservation.Status status);
    @Query("select coalesce(sum(r.quantity),0) from Reservation r where r.batch.id=:id and r.status=:status")
    long unitsForBatch(@Param("id") Long batchId, @Param("status") Reservation.Status status);
    @Query("select coalesce(sum(r.quantity),0) from Reservation r where r.batch is null and r.orderLine.product.id=:id and r.status=:status")
    long unitsWithoutBatch(@Param("id") Long productId, @Param("status") Reservation.Status status);
}
