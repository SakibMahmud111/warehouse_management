package com.waremanager.repository;

import com.waremanager.model.SpecialEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpecialEventRepository extends JpaRepository<SpecialEvent, Long> {

    List<SpecialEvent> findAllByOrderByStartDateAsc();
}
