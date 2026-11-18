# WareManager

**Intelligent Warehouse and Supply Chain Optimization System**  
**Course:** CSE470 — Software Engineering  
**Section:** 12

## Overview

WareManager is a web-based application for managing warehouse inventory and supporting supply-chain decisions. The system is designed for a single distribution warehouse, with an emphasis on product availability, order fulfillment, batch traceability, and data-driven inventory planning.

This version contains **F1 Demand forecasting** and **F2 Dynamic safety-stock calculation**. The application uses the Model–View–Controller (MVC) architectural pattern with Spring Boot and Thymeleaf. The F1 machine-learning model (linear regression) is trained in Python with scikit-learn by a small worker script (`ml/model_worker.py`) that the Spring Boot app starts in the background; it is not a web server.

## Implemented Functionality

**Supporting work (not counted as features)**

- Login and logout with Spring Security; user accounts stored in PostgreSQL (BCrypt-hashed passwords, one role per user, disabled accounts blocked)
- First admin account created automatically when the user table is empty
- Product management (add, edit, list) with validation and duplicate-SKU handling
- Planning settings page (service level, cold-day threshold)
- Sample data loaded on first start so both features can be demonstrated immediately

**Features**

| ID | Feature | Page |
| --- | --- | --- |
| F1 | Demand forecasting: predicts this winter's length from temperatures (linear regression, trained in Python with scikit-learn), learns weekly sales per product (winter products from cold days, steady products by moving average, holiday/promotion uplift), forecasts 12 weeks, shows forecast vs actual | `/forecast` |
| F2 | Dynamic safety stock: from the F1 forecast and its error, the supplier lead time and how much it varies, and the service level (profit-optimal level for winter products), with manual override and reason; shows which products are below their safety stock | `/safety-stock` |

The features run as one pipeline (F1 → F2) at startup and after every change (sales, temperatures, events, products, settings, overrides), so both pages always agree.

## Technology Restrictions

These course rules are mandatory and are checked automatically by `mvn test` and GitHub Actions on every pull request:

- **MVC architecture is mandatory.** Requests flow Controller → Service → Repository; Thymeleaf templates are the Views and contain no business logic. `ArchitectureTests` fails the build if code skips a layer (for example a controller calling a repository).
- **MERN (MongoDB, Express, React, Node) and any combination of it is prohibited.**
- **Django, Flask, FastAPI and all other Python web frameworks are prohibited.** `StackPolicyTests` scans the whole repository and fails the build if any of these appear.
- **Python is used only for the F1 machine-learning model**, as a plain script (`ml/model_worker.py`, scikit-learn) that the Spring Boot app starts and talks to through standard input/output. It has no web server and no web framework; every page, request and database access stays in the Java MVC app.

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
| Machine learning (F1) | Python 3.10+ with scikit-learn and NumPy (`ml/model_worker.py`); the same formula also exists in Java as a backup |
| Build and testing | Maven, JUnit 5, ArchUnit, GitHub Actions |

## Application Architecture

```text
ml/
├── model_worker.py      # Python + scikit-learn: trains the F1 regressions
└── requirements.txt     # numpy, scikit-learn
src/main/
├── java/com/waremanager/
│   ├── config/          # Security, first admin, demo data, startup planning run
│   ├── controller/      # HTTP endpoints (ForecastController = F1, SafetyStockController = F2)
│   ├── model/           # JPA entities and validation
│   ├── repository/      # Database access (Spring Data JPA)
│   └── service/         # Business logic; the maths classes are plain Java with no database access
└── resources/
    ├── static/css/      # Styling
    ├── templates/       # Thymeleaf views
    └── application.properties
```

A request is handled by a controller, passed to a service, and persisted via a repository; the controller then renders a Thymeleaf view. For example, `GET /safety-stock` → `SafetyStockController.safetyStockPage()` → `SafetyStockService` → `InventoryPolicyRepository` → `safety-stock.html`.

Sign-in follows the same layering: the login form posts to Spring Security, which calls `DatabaseUserDetailsService` → `UserAccountService` → `AppUserRepository` to load the account from the `app_users` table and checks the password against its BCrypt hash. `AuthController` renders the login page and its messages.

The forecasting and safety-stock formulas live in plain-Java classes in the service package (`Statistics`, `InventoryMath`, `WinterForecaster`, `DemandForecaster`). The Spring services load data, call these classes and save the results.

**Machine learning in Python.** The F1 regressions are trained through the `ModelTrainer` interface. `PythonModelTrainer` (a Spring service) starts `ml/model_worker.py` once, sends each training request as one line of JSON and reads the answer (intercept, slope, R², error). If Python or scikit-learn is missing, or the script stops answering, `JavaModelTrainer` runs the same formula in Java, so the app always works and the numbers are identical (`PythonModelTests` checks this). The F1 page shows which one is in use. F2 is a calculation, not a prediction: it uses the F1 forecast as its input.

## Local Setup

**Prerequisites:** Java 17 or later, Apache Maven (IntelliJ IDEA includes it), PostgreSQL 15 or later, and Python 3.10 or later for the machine-learning worker.

1. Create the database once, either with Docker (`docker compose up -d`) or as the `postgres` superuser in psql / pgAdmin. This version uses its own database, `waremanager_f12`, so it never mixes with the tables of other versions:

   ```sql
   CREATE USER waremanager WITH PASSWORD 'waremanager';   -- skip if the user already exists
   CREATE DATABASE waremanager_f12 OWNER waremanager;
   ```

   Tables are created automatically by Hibernate on startup.

2. Install the Python packages once, and run from the project folder:

   ```bash
   python3 -m pip install -r ml/requirements.txt     # Windows: py -m pip install -r ml\requirements.txt
   mvn spring-boot:run
   ```

   The F1 page shows "Machine-learning models trained by: Python … with scikit-learn …". If it shows "Java backup", the reason is printed next to it (for example Python not found, or scikit-learn not installed); the app still works with the same numbers.

   The first start loads sample data (6 products, 3 years of weekly sales, 8 years of daily temperatures, holidays), which takes a few seconds.

3. Open **http://localhost:8080/login** in a browser.

Development login (created automatically when the `app_users` table is empty):

- Username: `admin`
- Password: `admin123`

Override these defaults with the `ADMIN_USERNAME`, `ADMIN_PASSWORD` and `ADMIN_FULL_NAME` environment variables before the first start, and change them before any production deployment.

## Database Configuration

PostgreSQL is the project database. The connection is configured in `src/main/resources/application.properties` and can be overridden with environment variables:

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/waremanager_f12` |
| `DB_USER` | `waremanager` |
| `DB_PASSWORD` | `waremanager` |
| `DEMO_DATA` | `true` (set to `false` to skip the sample data) |
| `ML_PYTHON_ENABLED` | `true` (set to `false` to use only the Java version of the model) |
| `ML_PYTHON_COMMAND` | empty: tries `python3`, then `python` (Windows: `py -3`, `python`, `python3`); or a full path such as `C:\Python312\python.exe` |
| `ML_PYTHON_SCRIPT` | `ml/model_worker.py` (relative to the folder the app is started from) |

Sample sales, temperatures and products are added only when no sales history exists. Products whose SKU already exists are never changed.

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
| `PlanningMathTests` | Formulas (safety stock, profit-optimal service level, normal distribution, regression), winter prediction and the weekly sales models, with a fixed date |
| `PlanningFeatureTests` | Every page renders; F1 and F2 flows end to end (sales, temperatures and events entry, overrides, below-safety-stock count, settings, new products, validation messages) |
| `FeatureChainTests` | F1 hands its results to F2 correctly: the saved forecast and its error give exactly the safety stock of the formula; a longer lead time needs more safety stock; overrides are used and removed; every plan is consistent |
| `PythonModelTests` | The Python worker and the Java backup give the same regressions and winter predictions; the app falls back to Java when Python, scikit-learn or the script is missing (the Python comparisons are skipped on a PC without Python) |
| `LoginFlowTests` | Database login: correct/wrong password, case-insensitive username, disabled account, BCrypt storage |
| `ArchitectureTests` | MVC layering rules |
| `StackPolicyTests` | No MERN and no Python web frameworks |

Tests use the `test` profile (`src/test/resources/application-test.properties`) with an in-memory H2 database, so they run without PostgreSQL. GitHub Actions also runs the Maven test workflow on pushes and pull requests, after installing Python and `ml/requirements.txt`, so the Python model is tested there too.

## Third-party Packages

Java, all part of or managed by Spring Boot: Spring Web MVC, Thymeleaf, Spring Data JPA (Hibernate), Spring Security, Bean Validation, Jackson (JSON for the Python worker), PostgreSQL JDBC driver; for tests only: H2, JUnit 5, Spring Security Test, ArchUnit.

Python (`ml/requirements.txt`): scikit-learn (`LinearRegression` fits the straight lines of F1) and NumPy (arrays). No package implements a feature: what to learn from, the winter and demand models and the safety-stock formulas are written in this project.

## Planned Modules

The SRS defines further features: reorder points (F3), order quantities (F4), supplier lead-time prediction (F6), stockout probability (F7), demand anomaly detection (F20), supplier performance, warehouse slotting and picking, batch allocation and traceability, quality controls, and inventory reservations. These modules will be implemented incrementally in later sprints.
