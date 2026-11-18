# WareManager

**Intelligent Warehouse and Supply Chain Optimization System**  
**Course:** CSE470 — Software Engineering  
**Section:** 12

## Overview

WareManager is a web-based application for managing warehouse inventory and supporting supply-chain decisions. The system is designed for a single distribution warehouse, with an emphasis on product availability, order fulfillment, batch traceability, and data-driven inventory planning.

The application uses the Model–View–Controller (MVC) architectural pattern with Spring Boot and Thymeleaf. The prediction models of F1, F6, F7 and F20 run in Python (scikit-learn, SciPy, NumPy) in a small worker script (`ml/model_worker.py`) that the Spring Boot app starts in the background; it is not a web server.

## Implemented Functionality

**Supporting work (not counted as features)**

- Login and logout with Spring Security; user accounts stored in PostgreSQL (BCrypt-hashed passwords, one role per user, disabled accounts blocked)
- First admin account created automatically when the user table is empty
- Product management (add, edit, list) with validation and duplicate-SKU handling
- Planning settings page (ordering cost, holding cost, service level, cold-day threshold, usual winter stocking days, stockout alert level, anomaly threshold)
- Suppliers (added on the F6 page, chosen on each product)
- Sample data loaded on first start so every feature can be demonstrated immediately

**Features**

| ID | Feature | Page |
| --- | --- | --- |
| F1 | Demand forecasting: predicts this winter's length from temperatures (linear regression, trained in Python with scikit-learn), learns weekly sales per product (winter products from cold days, steady products by moving average, holiday/promotion uplift), forecasts 12 weeks, shows forecast vs actual | `/forecast` |
| F2 | Dynamic safety stock: from forecast error, lead-time variability and service level (profit-optimal level for winter products), with manual override and reason | `/safety-stock` |
| F3 | Reorder points: lead-time demand + safety stock, reorder suggestions (order → receive → stock), history of past values | `/reorder-points` |
| F4 | Order quantity: EOQ rounded up to the supplier minimum with yearly cost comparison; winter stock plan that compares the most profitable stock with "stock for 60 days" | `/eoq` |
| F6 | Supplier lead-time prediction: learns each supplier's days from order to receipt (weighted average of past deliveries and trend, computed by the Python worker), shows a range, and compares every receipt with the prediction | `/lead-times` |
| F7 | Stockout probability: chance that stock on hand runs out before the next delivery (normal distribution, calculated by the Python worker with SciPy); ranked list and alerts linked to the F3 reorder suggestion | `/stockout-risk` |
| F20 | Demand anomaly detection (idea list item 23): weeks whose sales are far above or below the F1 model's expectation (checked by the Python worker with NumPy); the planner marks them explained or under investigation | `/anomalies` |
| F14 | Recall impact: hold a batch, block its reservations, show remaining bin stock and affected orders and past shipments, export CSV | `/recalls` |
| F15 | Cold-chain: record a bin temperature, detect out-of-range products, open incidents and hold batches, then release or discard after review | `/cold-chain` |
| F16 | Product substitution: rank in-stock alternatives matching category and every required `key=value` attribute, then replace a draft sales-order line | `/substitutes?lineId=...` |
| F17 | Inventory reservation: create sales orders, confirm stock atomically, release on cancellation, and dispatch only eligible confirmed reservations | `/orders` |
| F18 | Scarcity allocation: preview customer-priority, first-in-first-out or fair-share rules; commit partial allocations and backorders | `/scarcity` |
| F19 | Supplier consolidation: group F3 suggestions by supplier, edit draft quantities, enforce minimum order value and confirm through F3/F6 purchase ordering | `/consolidation` |

The features run as one pipeline (F6 → F1 → F2 → F4 → F3 → F7 → F20) at startup and after every change, so all pages agree. Marking a reorder suggestion as ordered and received (F3) records a purchase order whose real lead time teaches F6.

## Technology Restrictions

These course rules are mandatory and are checked automatically by `mvn test` and GitHub Actions on every pull request:

- **MVC architecture is mandatory.** Requests flow Controller → Service → Repository; Thymeleaf templates are the Views and contain no business logic. `ArchitectureTests` fails the build if code skips a layer (for example a controller calling a repository).
- **MERN (MongoDB, Express, React, Node) and any combination of it is prohibited.**
- **Django, Flask, FastAPI and all other Python web frameworks are prohibited.** `StackPolicyTests` scans the whole repository and fails the build if any of these appear.
- **Python is used only for the prediction models of F1, F6, F7 and F20**, as a plain script (`ml/model_worker.py`, scikit-learn) that the Spring Boot app starts and talks to through standard input/output. It has no web server and no web framework; every page, request and database access stays in the Java MVC app.

Do not weaken or delete these tests. If a rule seems to block legitimate work, discuss it with the team and the faculty first.

## Technology Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17+ |
| Backend / MVC | Spring Boot 3.5, Spring MVC |
| Views | Thymeleaf, HTML, CSS (charts drawn as inline SVG, no JavaScript) |
| Authentication | Spring Security |
| Persistence | Spring Data JPA (Hibernate) |
| Database | PostgreSQL |
| Test database | H2 (in-memory, used only by automated tests) |
| Prediction models (F1, F6, F7, F20) | Python 3.10+ with scikit-learn, SciPy and NumPy (`ml/model_worker.py`); the same formulas also exist in Java as a backup |
| Build and testing | Maven, JUnit 5, ArchUnit, GitHub Actions |

## Application Architecture

```text
ml/
├── model_worker.py      # Python: F1 regressions (scikit-learn), F6 lead time, F7 stockout chance (SciPy), F20 anomalies (NumPy)
└── requirements.txt     # numpy, scipy, scikit-learn
src/main/
├── java/com/waremanager/
│   ├── config/          # Security, first admin, demo data, startup planning run
│   ├── controller/      # HTTP endpoints (ForecastController = F1, ReplenishmentController = F2-F4,
│   │                    #   LeadTimeController = F6, RiskController = F7, AnomalyController = F20)
│   ├── model/           # JPA entities and validation
│   ├── repository/      # Database access (Spring Data JPA)
│   └── service/         # Business logic; the maths classes are plain Java with no database access
└── resources/
    ├── static/css/      # Styling
    ├── templates/       # Thymeleaf views
    └── application.properties
```

A request is handled by a controller, passed to a service, and persisted via a repository; the controller then renders a Thymeleaf view. For example, `GET /eoq` → `ReplenishmentController.eoqPage()` → `OrderQuantityService` → `InventoryPolicyRepository` → `eoq.html`.

Sign-in follows the same layering: the login form posts to Spring Security, which calls `DatabaseUserDetailsService` → `UserAccountService` → `AppUserRepository` to load the account from the `app_users` table and checks the password against its BCrypt hash. `AuthController` renders the login page and its messages.

The forecasting and inventory formulas live in plain-Java classes in the service package (`Statistics`, `InventoryMath`, `WinterForecaster`, `DemandForecaster`, `SeasonPlan`, `LeadTimePredictor`). The Spring services load data, call these classes and save the results.

**Prediction models in Python.** Every prediction goes through the `ModelTrainer` interface: F1 regressions (`fitLine`), F6 lead time (`leadTime`), F7 chance of running out (`stockoutRisk`) and F20 unusual weeks (`anomalies`). `PythonModelTrainer` (a Spring service) starts `ml/model_worker.py` once, sends each request as one line of JSON and reads the answer. If Python or a package is missing, or the script stops answering, `JavaModelTrainer` runs the same formulas in Java, so the app always works with the same results (`PythonModelTests` checks this; the only difference is F7's chance, where SciPy's exact bell curve and the Java formula can differ by about 0.0000001). The F1, F6, F7 and F20 pages show which one is in use. F2, F3 and F4 are calculations, not predictions: they use the F1 and F6 predictions as inputs.

## Local Setup

**Prerequisites:** Java 17 or later, Apache Maven (IntelliJ IDEA includes it), PostgreSQL 15 or later, and Python 3.10 or later for the machine-learning worker.

1. Create the database once, either with Docker (`docker compose up -d`) or as the `postgres` superuser in psql / pgAdmin:

   ```sql
   CREATE USER waremanager WITH PASSWORD 'waremanager';
   CREATE DATABASE waremanager OWNER waremanager;
   ```

   Tables are created automatically by Hibernate on startup.

2. Clone, install the Python packages once, and run from the project folder:

   ```bash
   git clone https://github.com/SakibMahmud111/warehouse_management.git
   cd warehouse_management
   python3 -m pip install -r ml/requirements.txt     # Windows: py -m pip install -r ml\requirements.txt
   mvn spring-boot:run
   ```

   The F1, F6, F7 and F20 pages show "… by: Python … with scikit-learn …, SciPy …, NumPy …". If they show "Java backup", the reason is printed next to it (for example Python not found, or scikit-learn not installed); the app still works with the same numbers.

   The first start loads sample data (6 products, 4 suppliers with 8 past deliveries each, 3 years of weekly sales, 8 years of daily temperatures), which takes a few seconds.

3. Open **http://localhost:8080/login** in a browser.

Development login (created automatically when the `app_users` table is empty):

- Username: `admin`
- Password: `admin123`

Override these defaults with the `ADMIN_USERNAME`, `ADMIN_PASSWORD` and `ADMIN_FULL_NAME` environment variables before the first start, and change them before any production deployment.

## Database Configuration

PostgreSQL is the project database. The connection is configured in `src/main/resources/application.properties` and can be overridden with environment variables:

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/waremanager` |
| `DB_USER` | `waremanager` |
| `DB_PASSWORD` | `waremanager` |
| `DEMO_DATA` | `true` (set to `false` to skip the sample data) |
| `ML_PYTHON_ENABLED` | `true` (set to `false` to use only the Java version of the models) |
| `ML_PYTHON_COMMAND` | empty: tries `python3`, then `python` (Windows: `py -3`, `python`, `python3`); or a full path such as `C:\Python312\python.exe` |
| `ML_PYTHON_SCRIPT` | `ml/model_worker.py` (relative to the folder the app is started from) |

Sample sales, temperatures and products are added only when no sales history exists; sample suppliers and deliveries only when there are no suppliers and no purchase orders. Products whose SKU already exists are never changed (a demo product without a supplier gets its demo supplier). To see the two sample demand anomalies on a database created by an earlier version, reset the database.

Additional users can be added with SQL until an admin user-management page exists. PostgreSQL's `pgcrypto` extension produces BCrypt hashes that Spring Security accepts:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO app_users (username, password_hash, full_name, role, enabled, created_at)
VALUES ('planner', crypt('planner123', gen_salt('bf', 10)), 'Test Planner', 'INVENTORY_PLANNER', true, now());
```

Usernames are stored in lower case. Valid roles: `ADMIN`, `WAREHOUSE_MANAGER`, `INVENTORY_PLANNER`, `PURCHASING_OFFICER`, `SALES_COORDINATOR`, `WAREHOUSE_STAFF`.

## Testing

Run the project tests with:

```bash
mvn test
```

| Test class | What it checks |
| --- | --- |
| `PlanningMathTests` | Formulas (EOQ, safety stock, normal distribution, regression, lead-time prediction, stockout probability, anomaly rule), winter prediction and the winter plan, with a fixed date |
| `PlanningFeatureTests` | Every page renders; F1-F7 and F20 flows end to end (sales entry, overrides, order → receive with dates, deliveries, suppliers, alerts raised/linked/resolved, anomalies explained, settings, new products) |
| `FeatureChainTests` | The features hand results to each other correctly: F6 lead time → F2 safety stock → F3 reorder point/suggestion; F1 forecast → F4 EOQ; F7 risk and alert linked to the F3 suggestion; order → receive teaches F6 and closes the F7 alert; F20 flags and un-flags a week; F3 and F4 show the same winter numbers |
| `PythonModelTests` | The Python worker and the Java backup give the same regressions, lead times, winter predictions, stockout chances and unusual weeks; the app falls back to Java when Python, a package or the script is missing (the Python comparisons are skipped on a PC without Python) |
| `LoginFlowTests` | Database login: correct/wrong password, case-insensitive username, disabled account, BCrypt storage |
| `ArchitectureTests` | MVC layering rules |
| `StackPolicyTests` | No MERN and no Python web frameworks |
| `WarehouseFeaturesTests` | F14 recall impact and CSV, F15 excursion holds/discard, F16 attribute matching, F17 atomic reservation/cancellation, F18 scarcity/backorders, F19 supplier draft confirmation, new page rendering |

Tests use the `test` profile (`src/test/resources/application-test.properties`) with an in-memory H2 database, so they run without PostgreSQL. GitHub Actions also runs the Maven test workflow on pushes and pull requests, after installing Python and `ml/requirements.txt`, so the Python models are tested there too.

## Third-party Packages

Java, all part of or managed by Spring Boot: Spring Web MVC, Thymeleaf, Spring Data JPA (Hibernate), Spring Security, Bean Validation, Jackson (JSON for the Python worker), PostgreSQL JDBC driver; for tests only: H2, JUnit 5, Spring Security Test, ArchUnit.

Python (`ml/requirements.txt`): scikit-learn (`LinearRegression` fits the straight lines of F1 and the F6 trend), SciPy (`norm.sf`, the normal distribution for the F7 chance) and NumPy (arrays, the F20 comparison). No package implements a feature: what to learn from, the winter and demand models, the lead-time prediction, the stockout and anomaly rules and all inventory formulas are written in this project.

## F14–F19 Demonstration (Minhazul Abdin Emon)

The new modules reuse the existing Spring MVC architecture and PostgreSQL settings. No new Java libraries or services are required. Run the application as described above and sign in as an `ADMIN` for the complete demo. The pages are role-restricted: warehouse staff/manager for batches and temperature, manager for recalls, sales coordinator for orders/substitutes/scarcity, and purchasing officer for consolidation. Admin can use all modules.

1. Open **Products** and edit a medicine: set its category, `size=M,color=blue` attributes and temperature limits (for example 2–8 °C). Create a second same-category product with compatible attributes for the F16 demo.
2. Open **Batches**, receive 10 units of that medicine into `COLD-A1` with a future expiry. Receiving a tracked batch adds those units to `Product.quantity`. Original sample-product quantities remain *unbatched* and can still be reserved for zero-shelf-life orders.
3. Open **Orders** and create a draft customer order with a product and quantity. Use **Find substitutes** on a line to select an eligible alternative (F16). Confirm the order: it reserves eligible FEFO batches without reducing physical stock, preventing concurrent overbooking (F17). Cancellation releases units; shipping deducts stock and creates a shipment record. Orders requesting a positive minimum shelf life cannot use unbatched inventory because its expiry is unknown.
4. Open **Recalls**, select a batch and give a reason (F14). The batch becomes held, existing reservations of that batch become `BLOCKED`, the order becomes partial, and the case shows affected customers, orders and shipments. Export the report as CSV. Closing a recall releases the batch only if no open temperature incident is holding it.
5. Open **Cold chain**, record a reading in `COLD-A1` outside the product's limits, such as 15 °C (F15). Review the incident and either release the batch or discard its physical units. A release cannot override an active recall.
6. Create two draft sales orders for more units than are available and visit **Scarcity**. Preview priority, order-date, or proportional fair-share allocation, then commit. Partial reservations and backorders appear on the orders (F18).
7. Visit **F3 Reorder** to ensure open suggestions exist and that their products have preferred suppliers. Open **Purchasing**, choose the supplier group, create a draft, adjust line quantities, and confirm (F19). Confirmation creates the existing F3/F6 per-product purchase-order records, which can still be received through the F3 workflow.

### Integration notes and remaining checks

- The original `Product.quantity` is physical warehouse stock; new `Batch.quantity` tracks only lots explicitly received through the batch UI. The two values are kept synchronized for new receipts, shipments and discarded batches. Existing unbatched stock has no batch trace and cannot satisfy shelf-life requirements.
- The original forecasting/reorder screens were designed before held and reserved stock existed. Their forecasts use the original product inventory fields. The new F17/F18 availability calculation deducts held and actively reserved units; a future integration task is to make every planning and risk screen use that same availability definition.
- The existing F3/F6 receipt workflow records stock at product level. It does not automatically create a tracked batch, so warehouse staff should receive a tracked batch manually only for **additional** stock, not duplicate a previous F3 receipt. Reconciling batch receipts with F3 purchase-order lines is future work.
- These additions are a development contribution, not a claim of student review. Before submission, run `mvn test`, exercise all workflows against PostgreSQL, review the code and update the sprint log. Any AI assistance should be disclosed in the SRS according to the course rulebook.

## Planned Modules

Other SRS features still awaiting implementation include supplier scoring (F5), overstock detection (F8), warehouse slotting/picking (F9–F11), FEFO pick tasks (F12), and full stock-movement traceability (F13).
