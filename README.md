# WareManager — CSE470 Software Engineering

**Project:** Intelligent Warehouse and Supply Chain Optimization System  
**Section:** 12  
**Framework:** Spring Boot 3 / Spring MVC  
**Current milestone:** Basic MVC application with login and a working product demonstration.

## What works now

- Custom login form using Spring Security (in-memory **demo user**)
- Authenticated dashboard showing the number of products
- Add a product with SKU, name, and nonnegative quantity
- List saved products in a table
- Product validation and duplicate-SKU check
- H2 local database for zero-configuration testing
- Separate PostgreSQL profile for subsequent development

**Important:** This is the initial MVC foundation, **not** a claim that all 20 SRS features are implemented. The remaining features are planned for upcoming sprints. Keep the project aligned with the submitted SRS.

## Run on Windows (fastest method)

**Requirements:** Java 17+ and Apache Maven. IDE: IntelliJ IDEA or VS Code (Java extensions).

1. Open **this folder containing `pom.xml`** in your IDE.
2. Open a terminal in the project folder.
3. Run:

```powershell
mvn spring-boot:run
```

4. Open <http://localhost:8080/login>.
5. Sign in using the **local demo credentials** `admin` / `admin123`.
6. Open **Products** → **New product**, add `Rice 5kg`, SKU `RICE-005`, quantity `25`, and verify it appears in the list.

To run tests: `mvn test`.

**Security note:** The in-memory login is for coursework demonstration only. Never publish this version publicly with the hardcoded demo credentials. Replace it with secure user storage before any deployment.

## MVC architecture

| Layer | Code / responsibility |
|---|---|
| **Model** | `src/main/java/com/waremanager/model/Product.java`: JPA entity and validation rules |
| **View** | `src/main/resources/templates/`: Thymeleaf pages (`login`, `dashboard`, `products`, `product-form`) |
| **Controller** | `src/main/java/com/waremanager/controller/`: receives requests and selects the appropriate view |
| **Service** | `src/main/java/com/waremanager/service/ProductService.java`: business rules and transactions |
| **Repository** | `src/main/java/com/waremanager/repository/ProductRepository.java`: Spring Data JPA database access |
| **Configuration** | `src/main/java/com/waremanager/config/SecurityConfig.java`: login and access control |

**Request example:** Browser requests `/products` → `ProductController` calls `ProductService` → repository fetches `Product` objects → controller adds them to the Thymeleaf model → `products.html` renders the response.

## Database

- **Tonight / initial milestone:** H2 database (`./data/waremanager`). No database server needs to be installed.
- **Full project:** PostgreSQL is recommended. This project already includes a PostgreSQL driver, a `postgres` Spring profile, and an optional Docker Compose configuration.
- Do **not** activate the PostgreSQL profile unless PostgreSQL is running. H2 is the default.

Optional PostgreSQL setup:

```powershell
docker compose up -d
$env:SPRING_PROFILES_ACTIVE='postgres'
mvn spring-boot:run
```

To switch back to H2, close the terminal or run `$env:SPRING_PROFILES_ACTIVE=''`.

## GitHub submission

Repo: <https://github.com/SakibMahmud111/warehouse_management>

1. Clone the repository using **an account that can push to it**.
2. Copy the **contents** of this starter folder into the clone; do not nest an extra `warehouse_management_mvc_starter` directory inside it.
3. Commit and push all files to `main` (or create a pull request if working on a branch).
4. Verify on GitHub that `pom.xml`, `src/main/java`, and `src/main/resources/templates` are present.
5. Complete the Google Form from the faculty's earlier email before the deadline.

See `SUBMISSION_CHECKLIST.md` for exact commands and suggested form values.

## Not yet implemented

Supplier purchasing, FEFO allocation, batch tracing, recalls, full inventory reservation, demand forecasting, route optimization, and the other advanced SRS features. They require future implementation and testing.
