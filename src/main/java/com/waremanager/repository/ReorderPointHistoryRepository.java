package com.waremanager.repository;

import com.waremanager.model.Product;
import com.waremanager.model.ReorderPointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReorderPointHistoryRepository extends JpaRepository<ReorderPointHistory, Long> {

    Optional<ReorderPointHistory> findFirstByProductOrderByRecordedAtDescIdDesc(Product product);

    List<ReorderPointHistory> findTop5ByProductOrderByRecordedAtDescIdDesc(Product product);
}
