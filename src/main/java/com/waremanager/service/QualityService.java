package com.waremanager.service;

import com.waremanager.model.*;
import com.waremanager.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** F14 recall impact and F15 temperature excursions, sharing the same batch holds. */
@Service
public class QualityService {
    private final StockService stock;
    private final RecallCaseRepository recalls;
    private final TemperatureReadingRepository readings;
    private final ExcursionIncidentRepository incidents;
    private final BatchRepository batches;
    private final ReservationRepository reservations;
    private final ShipmentRepository shipments;

    public QualityService(StockService stock, RecallCaseRepository recalls,
            TemperatureReadingRepository readings, ExcursionIncidentRepository incidents,
            BatchRepository batches, ReservationRepository reservations, ShipmentRepository shipments) {
        this.stock = stock;
        this.recalls = recalls;
        this.readings = readings;
        this.incidents = incidents;
        this.batches = batches;
        this.reservations = reservations;
        this.shipments = shipments;
    }

    @Transactional(readOnly = true)
    public List<RecallCase> recalls() { return recalls.findAllByOrderByOpenedAtDesc(); }
    @Transactional(readOnly = true)
    public List<ExcursionIncident> incidents() { return incidents.findAllByOrderByIdDesc(); }
    @Transactional(readOnly = true)
    public List<TemperatureReading> readings() { return readings.findTop30ByOrderByMeasuredAtDesc(); }
    @Transactional(readOnly = true)
    public List<Reservation> impactReservations(Long batchId) { return reservations.findByBatchIdOrderByIdAsc(batchId); }
    @Transactional(readOnly = true)
    public List<Shipment> impactShipments(Long batchId) { return shipments.findByBatchIdOrderByShippedAtDesc(batchId); }
    @Transactional(readOnly = true)
    public RecallCase recall(Long id) { return recalls.findById(id).orElseThrow(() -> new IllegalArgumentException("Recall case not found")); }

    @Transactional
    public RecallCase recallBatch(Long batchId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("Provide a short reason for the recall");
        Batch batch = stock.batch(batchId);
        if (recalls.findByBatchId(batchId).stream().anyMatch(RecallCase::isActive))
            throw new IllegalArgumentException("An active recall already exists for this batch");
        stock.holdBatch(batch, "Recall: " + reason.trim());
        return recalls.save(new RecallCase(batch, reason.trim()));
    }

    @Transactional
    public void closeRecall(Long id) {
        RecallCase record = recall(id);
        if (!record.isActive()) throw new IllegalArgumentException("Recall is already closed");
        record.setActive(false);
        releaseIfSafe(record.getBatch());
    }

    /** One reading can create incidents for several temperature-sensitive batches in that bin. */
    @Transactional
    public int recordTemperature(String bin, double celsius, LocalDateTime measuredAt) {
        if (bin == null || bin.isBlank() || bin.length() > 80 || !Double.isFinite(celsius)
                || celsius < -100 || celsius > 100 || measuredAt == null
                || measuredAt.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("Enter a bin, realistic Celsius reading and a non-future measurement time");
        TemperatureReading reading = readings.save(new TemperatureReading(bin.trim(), celsius, measuredAt));
        int affected = 0;
        for (Batch batch : batches.findByBinCodeIgnoreCase(bin.trim())) {
            Product p = batch.getProduct();
            if (batch.getQuantity() == 0 || p.getMinTemperatureC() == null || p.getMaxTemperatureC() == null) continue;
            if (celsius >= p.getMinTemperatureC() && celsius <= p.getMaxTemperatureC()) continue;
            incidents.save(new ExcursionIncident(batch, reading));
            stock.holdBatch(batch, "Temperature " + celsius + "°C outside "
                + p.getMinTemperatureC() + "–" + p.getMaxTemperatureC() + "°C");
            affected++;
        }
        return affected;
    }

    @Transactional
    public void review(Long id, String decision, String reason) {
        ExcursionIncident incident = incidents.findById(id).orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        if (incident.getStatus() != ExcursionIncident.Status.HELD)
            throw new IllegalArgumentException("Incident already reviewed");
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("A quality-review reason is required");
        Batch batch = incident.getBatch();
        if ("RELEASE".equals(decision)) {
            incident.setStatus(ExcursionIncident.Status.RELEASED);
            incident.setDecisionReason(reason.trim());
            releaseIfSafe(batch);
        } else if ("DISCARD".equals(decision)) {
            incident.setStatus(ExcursionIncident.Status.DISCARDED);
            incident.setDecisionReason(reason.trim());
            stock.holdBatch(batch, "Discard requested in quality review");
            stock.discard(batch);
        } else {
            throw new IllegalArgumentException("Choose release or discard");
        }
    }

    private void releaseIfSafe(Batch batch) {
        boolean recalled = recalls.findByBatchId(batch.getId()).stream().anyMatch(RecallCase::isActive);
        boolean excursion = !incidents.findByBatchIdAndStatus(batch.getId(), ExcursionIncident.Status.HELD).isEmpty();
        if (!recalled && !excursion && batch.getQuantity() > 0 && !"Discarded after quality review".equals(batch.getHoldReason())) {
            batch.setHeld(false);
            batch.setHoldReason(null);
        }
    }

    @Transactional(readOnly = true)
    public String impactCsv(Long recallId) {
        RecallCase record = recall(recallId);
        Batch b = record.getBatch();
        StringBuilder csv = new StringBuilder("Type,Batch,Product,Bin,Quantity,Customer,Order,Status\n");
        csv.append(row("Remaining", b.getBatchNumber(), b.getProduct().getSku(), b.getBinCode(),
            String.valueOf(b.getQuantity()), "", "", b.isHeld() ? "HELD" : "AVAILABLE"));
        for (Reservation r : impactReservations(b.getId())) {
            csv.append(row("Reservation", b.getBatchNumber(), b.getProduct().getSku(), b.getBinCode(),
                String.valueOf(r.getQuantity()), r.getOrderLine().getSalesOrder().getCustomerName(),
                String.valueOf(r.getOrderLine().getSalesOrder().getId()), r.getStatus().name()));
        }
        for (Shipment s : impactShipments(b.getId())) {
            csv.append(row("Shipment", b.getBatchNumber(), b.getProduct().getSku(), b.getBinCode(),
                String.valueOf(s.getQuantity()), s.getOrderLine().getSalesOrder().getCustomerName(),
                String.valueOf(s.getOrderLine().getSalesOrder().getId()), "SHIPPED"));
        }
        return csv.toString();
    }

    private String row(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) row.append(',');
            String v = values[i] == null ? "" : values[i];
            // Prevent formula execution when CSV files are opened in spreadsheet applications.
            if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
            row.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        return row.append('\n').toString();
    }
}
