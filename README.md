# WareManager

**Intelligent Warehouse and Supply Chain Optimization System**  
**Course:** CSE470 — Software Engineering  
**Section:** 12

## Overview

WareManager is a web-based application for managing warehouse inventory and supporting supply-chain decisions. The system is designed for a single distribution warehouse, with an emphasis on product availability, order fulfillment, batch traceability, and data-driven inventory planning.

The application uses the Model–View–Controller (MVC) architectural pattern with Spring Boot and Thymeleaf. This repository provides the implemented application foundation, with additional modules specified in the project's Software Requirements Specification (SRS).

## Implemented Functionality

- Login and logout using Spring Security
- Authenticated dashboard with a product count
- Product creation and listing
- Input validation and duplicate-SKU handling
- Product persistence through Spring Data JPA
- Automated Maven test workflow with GitHub Actions

## Technology Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17+ |
| Backend / MVC | Spring Boot 3, Spring MVC |
| Views | Thymeleaf, HTML, CSS |
| Authentication | Spring Security |
| Persistence | Spring Data JPA (Hibernate) |
| Local database | H2 |
| PostgreSQL integration | JDBC driver and Spring profile configured |
| Build and testing | Maven, JUnit, GitHub Actions |

## Application Architecture

The application separates HTTP request handling, business logic, persistence, and UI rendering.

```text
src/main/
├── java/com/waremanager/
│   ├── config/          # Security configuration
│   ├── controller/      # HTTP endpoints and navigation
│   ├── model/           # Domain entities and validation
│   ├── repository/      # Database access
│   └── service/         # Application business logic
└── resources/
    ├── static/css/     # Styling
    ├── templates/      # Thymeleaf views
    └── application.properties
```

For example, a product request is handled by `ProductController`, passed to `ProductService`, and persisted via `ProductRepository`. The controller then renders the appropriate Thymeleaf view.

## Local Setup

**Prerequisites:** Java 17 or later and Apache Maven.

```bash
git clone https://github.com/SakibMahmud111/warehouse_management.git
cd warehouse_management
mvn spring-boot:run
```

Open **http://localhost:8080/login** in a browser.

Development login:

- Username: `admin`
- Password: `admin123`

These credentials are defined for local development using an in-memory Spring Security user and must be replaced before production deployment.

## Database Configuration

H2 is the default database for local development. Its file-backed datasource is configured in `src/main/resources/application.properties`.

A separate Spring profile is provided in `src/main/resources/application-postgres.properties` for PostgreSQL-based development. Activating this profile requires a running PostgreSQL instance and matching connection settings.

## Testing

Run the project tests with:

```bash
mvn test
```

GitHub Actions also runs the Maven test workflow on pushes and pull requests.

## Planned Modules

The project SRS defines additional features covering demand forecasting and replenishment, supplier performance, warehouse slotting and picking, batch allocation and traceability, quality controls, and inventory reservations. These modules will be implemented incrementally as development progresses.
