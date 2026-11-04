# CSE470 — Initial MVC Repository Deadline Checklist

## Step 1 — Run and verify locally

- [ ] Java 17+ installed: `java -version`
- [ ] Maven installed: `mvn -version`
- [ ] Inside the folder with `pom.xml`, run `mvn spring-boot:run`
- [ ] Visit `http://localhost:8080/login`
- [ ] Demo login works: `admin` / `admin123`
- [ ] Dashboard loads
- [ ] Products → New product → save a test product → see it in the product list
- [ ] Stop the server with Ctrl+C

## Step 2 — Push to GitHub

> Use the GitHub account with write permission to `SakibMahmud111/warehouse_management`.
> If you cannot push, ask the repository owner to upload the starter or grant you collaborator access.

1. Clone the repo:

   `git clone https://github.com/SakibMahmud111/warehouse_management.git`

2. Copy **everything inside** this starter folder into the `warehouse_management` folder, preserving paths and replacing `README.md` if asked.
3. Open a terminal inside the cloned `warehouse_management` folder:

   ```powershell
   git status
   git add .
   git commit -m "Initialize Spring Boot MVC structure with login and product demo"
   git push origin main
   ```

4. Refresh <https://github.com/SakibMahmud111/warehouse_management> and check for `pom.xml`, `src/`, and the new commit.

## Step 3 — Submit the form

The form URL is **not present in the screenshot**. Find the faculty's preceding email and use that form link.

Suggested values (adapt to the exact question text):

| Field | Suggested value |
|---|---|
| Course | CSE470: Software Engineering |
| Section | 12 |
| Faculty | Soumya Swagata Biswas |
| Project title | WareManager — Intelligent Warehouse and Supply Chain Optimization System |
| GitHub URL | https://github.com/SakibMahmud111/warehouse_management |
| Primary language | Java |
| Main framework / architecture | Spring Boot 3 / Spring MVC (Model–View–Controller) |
| View technology | Thymeleaf, HTML, CSS |
| Data access | Spring Data JPA / Hibernate |
| Initial demo database | H2 (local) |
| Planned project database | PostgreSQL |
| Scope completed today | MVC structure, login page, dashboard, product creation and listing |

> Fill in group members, student IDs, and any extra fields exactly as required by the actual form.

## Before submission

- [ ] GitHub content is visibly updated on `main`.
- [ ] Do not claim all 20 features are complete.
- [ ] The Google Form is submitted before 11:59 PM (Bangladesh time).
- [ ] Save a screenshot or confirmation of the successful submission.
