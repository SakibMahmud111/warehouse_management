package com.waremanager.repository;

import com.waremanager.model.Product;
import com.waremanager.model.ReorderSuggestion;
import com.waremanager.model.SuggestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, Long> {

    Optional<ReorderSuggestion> findFirstByProductAndStatus(Product product, SuggestionStatus status);

    /** The newest suggestion for a product, whatever its status. */
    Optional<ReorderSuggestion> findFirstByProductOrderByCreatedAtDescIdDesc(Product product);

    List<ReorderSuggestion> findTop30ByOrderByCreatedAtDescIdDesc();

    long countByStatus(SuggestionStatus status);

    List<ReorderSuggestion> findByStatus(SuggestionStatus status);
}
