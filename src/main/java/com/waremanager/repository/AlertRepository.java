package com.waremanager.repository;

import com.waremanager.model.Alert;
import com.waremanager.model.AlertStatus;
import com.waremanager.model.AlertType;
import com.waremanager.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** F7: the product's current stockout alert, if one is open or being investigated. */
    Optional<Alert> findFirstByAlertTypeAndProductAndStatusIn(AlertType alertType, Product product,
                                                              Collection<AlertStatus> statuses);

    /** F20: the alert for one product and week, whatever its status. */
    Optional<Alert> findFirstByAlertTypeAndProductAndWeekStart(AlertType alertType, Product product, LocalDate weekStart);

    List<Alert> findTop50ByAlertTypeOrderByCreatedAtDescIdDesc(AlertType alertType);

    long countByAlertTypeAndStatusIn(AlertType alertType, Collection<AlertStatus> statuses);
}
