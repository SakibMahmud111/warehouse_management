# WareManager

**Intelligent Warehouse and Supply Chain Optimization System**

CSE470: Software Engineering | MVC-based project

## About

WareManager helps warehouse and supply-chain businesses decide what to stock, when to reorder, where to store items, and how to pick and deliver orders. It replaces guesswork with data-driven recommendations, reduces stockouts, overstock, and expiry waste, and keeps every product batch traceable from supplier to customer.

## Target Users

- **Admin / Warehouse Manager:** manages products, suppliers, warehouses, policies, and reports.
- **Purchasing Officer:** handles reorder, supplier, and consolidation decisions.
- **Warehouse Worker / Picker:** picks orders, follows optimized routes, and updates stock.
- **Logistics / Delivery Coordinator:** plans delivery routes and tracks shipments.

## Key Features

| Area | Features |
| :--- | :--- |
| Inventory Planning | Demand forecasting, dynamic safety stock, reorder-point optimization, EOQ recommendations, inventory policy simulation |
| Risk and Alerts | Stockout probability estimation, overstock and dead-stock detection, demand anomaly detection |
| Supplier Management | Supplier reliability scoring, lead-time prediction, order consolidation |
| Warehouse Operations | Slotting optimization, pick-route optimization, multi-order picking, inter-warehouse transfers |
| Order Fulfilment | Dynamic reservation, allocation under scarcity, substitute-product matching, FEFO allocation |
| Traceability and Safety | Batch traceability, recall impact analysis, cold-chain excursion handling |
| Delivery | Delivery-window optimization, shipment-delay prediction |
| Auditing | Inventory discrepancy investigation |

> The final feature list, with CRUD grouped per the course counting rule (minimum 20 features), is in the SRS.

## Architecture

- **Pattern:** MVC (Model / View / Controller) on an approved MVC framework.
- **AI/ML service:** a separate Python service, limited to the forecasting and prediction features, connected to the main app through a REST API.
- **Main backend:** no Python framework, and no MERN stack.

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| Backend / MVC framework |  |
| Frontend / Views |  |
| Database |  |
| AI/ML service |  |

## Setup

```bash
# 1. Clone
git clone https://github.com/SakibMahmud111/warehouse_management
cd warehouse_management

# 2. Install dependencies, configure .env, run migrations


# 3. Run the app

```

