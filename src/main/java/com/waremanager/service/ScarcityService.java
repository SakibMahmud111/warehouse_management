package com.waremanager.service;

import com.waremanager.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** F18: preview and commit allocation by priority, FIFO order date, or proportional fair share. */
@Service
public class ScarcityService {
    public enum Rule { PRIORITY, ORDER_DATE, FAIR_SHARE }
    public record Decision(SalesOrderLine line, int requested, int allocated, int backordered) {}
    private final ReservationService reservation;
    private final StockService stock;

    public ScarcityService(ReservationService reservation, StockService stock) {
        this.reservation = reservation;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    public List<Decision> preview(Rule rule) {
        List<SalesOrderLine> pending = reservation.allLines().stream()
            .filter(x -> x.getSalesOrder().getStatus() == SalesOrder.Status.DRAFT
                || x.getSalesOrder().getStatus() == SalesOrder.Status.PARTIAL)
            .filter(x -> x.getQuantity() > x.getReservedQuantity()).toList();
        Map<Long, List<SalesOrderLine>> byProduct = pending.stream()
            .collect(Collectors.groupingBy(x -> x.getProduct().getId(), TreeMap::new, Collectors.toList()));
        List<Decision> result = new ArrayList<>();
        for (var entry : byProduct.entrySet()) {
            int capacity = stock.available(entry.getKey());
            List<SalesOrderLine> demand = new ArrayList<>(entry.getValue());
            Comparator<SalesOrderLine> chronological = Comparator
                .comparing((SalesOrderLine x) -> x.getSalesOrder().getCreatedAt())
                .thenComparing(x -> x.getSalesOrder().getId()).thenComparing(SalesOrderLine::getId);
            if (rule == Rule.PRIORITY) demand.sort(Comparator.comparingInt(
                (SalesOrderLine x) -> x.getSalesOrder().getPriority()).thenComparing(chronological));
            else demand.sort(chronological);
            Map<Long, Integer> awarded = new HashMap<>();
            if (rule == Rule.FAIR_SHARE) {
                long total = demand.stream().mapToLong(x -> x.getQuantity() - x.getReservedQuantity()).sum();
                int used = 0;
                for (SalesOrderLine line : demand) {
                    int need = line.getQuantity() - line.getReservedQuantity();
                    int eligible = stock.allocatable(entry.getKey(), line.getMinShelfLifeDays());
                    int share = total == 0 ? 0 : (int) Math.min(need, (long) capacity * need / total);
                    int take = Math.min(share, eligible);
                    awarded.put(line.getId(), take);
                    used += take;
                }
                // Distribute remaining units deterministically, without giving one order all of the rounding residue.
                int remainder = Math.max(0, capacity - used);
                while (remainder > 0) {
                    boolean moved = false;
                    for (SalesOrderLine line : demand) {
                        int max = Math.min(line.getQuantity() - line.getReservedQuantity(),
                            stock.allocatable(entry.getKey(), line.getMinShelfLifeDays()));
                        int prior = awarded.getOrDefault(line.getId(), 0);
                        if (prior < max && remainder > 0) {
                            awarded.put(line.getId(), prior + 1);
                            remainder--;
                            moved = true;
                        }
                    }
                    if (!moved) break;
                }
            } else {
                for (SalesOrderLine line : demand) {
                    int need = line.getQuantity() - line.getReservedQuantity();
                    int eligible = stock.allocatable(entry.getKey(), line.getMinShelfLifeDays());
                    int take = Math.min(need, Math.min(capacity, eligible));
                    awarded.put(line.getId(), take);
                    capacity -= take;
                }
            }
            for (SalesOrderLine line : demand) {
                int need = line.getQuantity() - line.getReservedQuantity();
                int allocated = awarded.getOrDefault(line.getId(), 0);
                result.add(new Decision(line, need, allocated, need - allocated));
            }
        }
        return result;
    }

    @Transactional
    public int commit(Rule rule) {
        List<Decision> decisions = preview(rule);
        // Lock all products first in a stable order: two officers cannot double-allocate the same stock.
        decisions.stream().map(x -> x.line().getProduct().getId()).distinct().sorted().forEach(stock::lock);
        // Recompute after acquiring locks, because availability may have changed since the browser preview.
        decisions = preview(rule);
        int allocated = 0;
        for (Decision d : decisions) allocated += reservation.reservePart(d.line().getId(), d.allocated());
        return allocated;
    }
}
