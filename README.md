# LoanPro E-Commerce

Enterprise-grade e-commerce application built with Spring Boot 3 (Java 21) and React 18.

---

## Quick start with Docker Compose

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine + Compose plugin)
- Ports **5432**, **8080**, and **5173** free on your machine

### Start everything

```bash
git clone <repo-url>
cd ecommerce
docker compose up --build
```

`--build` compiles the Spring Boot JAR and the React bundle inside Docker; omit it on subsequent runs if the source hasn't changed.

The first build takes ~2–3 minutes (Gradle downloads dependencies, npm installs packages). Subsequent `docker compose up` calls are much faster.

| Service    | URL                                        | Notes                          |
|------------|--------------------------------------------|--------------------------------|
| UI         | http://localhost:5173                      | React SPA (served by nginx)    |
| API        | http://localhost:8080/api                  | Spring Boot REST API           |
| Swagger UI | http://localhost:8080/swagger-ui.html      | Interactive API docs           |
| OpenAPI    | http://localhost:8080/api-docs             | Raw OpenAPI JSON               |
| PostgreSQL | localhost:5432 (db: `ecommerce`, user: `app`, pw: `secret`) | Exposed for local tooling |

### Useful commands

```bash
# Start in the background
docker compose up --build -d

# Stream logs for a specific service
docker compose logs -f api
docker compose logs -f ui

# Stop and remove containers (keeps the pg_data volume)
docker compose down

# Stop and wipe all data (full reset)
docker compose down -v

# Rebuild only the API after a backend change
docker compose up --build api
```

### How services depend on each other

```
db (postgres:16-alpine)
  └── api (Spring Boot)  — waits for db healthcheck before starting
        └── ui (nginx)   — waits for api to be up
```

The `db` service uses a healthcheck (`pg_isready`) so the API never starts before PostgreSQL is ready to accept connections.

---

## Run locally (without Docker)

### Prerequisites

- Java 21 (e.g. via [SDKMAN](https://sdkman.io/): `sdk install java 21-tem`)
- Node 20+ (e.g. via [nvm](https://github.com/nvm-sh/nvm): `nvm use 20`)
- PostgreSQL 16 running locally with a database named `ecommerce`, user `app`, password `secret`

### Backend

```bash
cd ecommerce-api
./gradlew bootRun
# API available at http://localhost:8080
```

To override the database URL:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mydb ./gradlew bootRun
```

### Frontend

```bash
cd ecommerce-ui
npm install
npm run dev
# UI available at http://localhost:5173
# API calls are proxied to http://localhost:8080 via Vite's dev server
```

---

## Using the application

### Product catalogue

Open http://localhost:5173. The home page shows all products with **search**, **category**, and **price range** filters. Results are paginated (12 per page).

### Creating a product

Click **New product** (top-right). Fill in Name, SKU, Price, and Stock (required), then optionally Category, Weight, and Description. SKUs must be unique.

### Editing a product

Click any product card to open its detail page, then click **Edit**. Saving immediately updates the price for any in-progress cart session too.

### Adding to cart and checking out

Click **Buy now** on the product detail page. Open the cart (top-right icon) and click **Checkout**. The payment form requires:

- A 16-digit card number (spaces and dashes are stripped automatically)
- Expiry in **MM/YYYY** format (e.g. `12/2030`)
- A 3-digit CVV
- Name on card

**Test cards:**

| Card number          | Result   |
|----------------------|----------|
| `4111 1111 1111 1111` | ✅ Paid  |
| Any ending in `0000` | ❌ Declined |

### Bulk CSV import

Navigate to **Import** (`/products/import`). Drag and drop a CSV file or click to browse. Expected columns:

```
name, sku, description, category, price, stock, weight_kg
```

The import handles dirty data automatically — see the [CSV import](#csv-import) section below.

---

## Postman collection

A ready-to-use Postman collection is included at `ecommerce-api.postman_collection.json`.

**Import steps:**

1. Open Postman → **Import** → drag in `ecommerce-api.postman_collection.json`
2. The collection uses a `baseUrl` variable defaulting to `http://localhost:8080` — change it in *Collection → Variables* to point at any environment
3. Run **Create product** first; the test script auto-sets the `productId` variable so subsequent requests (Update, Delete, Place order) work immediately
4. Run **Place order — success** next; its test script sets `orderId` for the **Get order** request

The collection covers every endpoint including edge cases (validation errors, declined cards, insufficient stock, 404s).

---

## Architecture

```
ecommerce/
├── ecommerce-api/     Spring Boot 3 REST API  (port 8080)
├── ecommerce-ui/      React 18 + Vite SPA     (port 5173 / 80 in Docker)
└── docker-compose.yml Orchestrates api + ui + postgres
```

### Backend layers

```
Controller → Service → Repository → PostgreSQL
                 ↕
          CsvImportService  (OpenCSV + OWASP HTML sanitizer)
          PaymentFacade     (simulated payment gateway)
          GlobalExceptionHandler
```

### Frontend pages

| Path                   | Page                                              |
|------------------------|---------------------------------------------------|
| `/`                    | Product list — search, category, price filters    |
| `/products/new`        | Create product form                               |
| `/products/:id`        | Product detail + Add to cart                      |
| `/products/:id/edit`   | Edit product form                                 |
| `/products/import`     | CSV drag-and-drop import with per-row result      |
| `/orders/:id`          | Order confirmation / receipt                      |

---

## API reference

| Method | Path                      | Description                                  |
|--------|---------------------------|----------------------------------------------|
| GET    | /api/products             | Paginated product list (`page`, `size`, `sort`) |
| GET    | /api/products/:id         | Single product                               |
| POST   | /api/products             | Create product                               |
| PUT    | /api/products/:id         | Update product (full replacement)            |
| DELETE | /api/products/:id         | Soft-delete product (returns 204)            |
| POST   | /api/products/import      | CSV bulk import (multipart, key=`file`)      |
| GET    | /api/search               | Search: `?q=` `&category=` `&minPrice=` `&maxPrice=` `&page=` `&size=` |
| POST   | /api/orders               | Place order (simulated payment)              |
| GET    | /api/orders/:id           | Order receipt                                |

Full request/response schemas are available in Swagger UI at http://localhost:8080/swagger-ui.html.

---

## Running tests

### Backend

```bash
cd ecommerce-api
./gradlew test

# With HTML report
./gradlew test jacocoTestReport
open build/reports/tests/test/index.html
```

> **Note:** backend tests use H2 in-memory — no running PostgreSQL required. The `application-test.yml` profile is picked up automatically from `src/test/resources/`.

**32 backend tests across 8 test classes:**

| Class                    | Type                          | What it tests                                                                 |
|--------------------------|-------------------------------|-------------------------------------------------------------------------------|
| `ProductServiceTest`     | Unit (Mockito)                | list, getById, create, update, delete, search — 12 cases                      |
| `OrderServiceTest`       | Unit (Mockito)                | placeOrder (paid/failed/stock/total), getById — 8 cases                       |
| `PaymentFacadeTest`      | Unit (pure)                   | valid cards, declined cards, invalid input — 11 cases (parameterised)         |
| `CsvRowValidatorTest`    | Unit (pure)                   | all CSV edge cases: price, stock, name, XSS, SQL, structure — 22 cases        |
| `CsvImportServiceTest`   | Integration (H2)              | end-to-end import: valid, security, upsert, mixed — 14 cases                  |
| `ProductControllerTest`  | Slice (`@WebMvcTest`)         | REST contract: list, get, create (valid + invalid), delete                    |
| `OrderControllerTest`    | Slice (`@WebMvcTest`)         | REST contract: place (success/failure/validation), get, 404                   |
| `SearchControllerTest`   | Slice (`@WebMvcTest`)         | keyword, category, price range, combined, default pagination                  |
| `CsvImportControllerTest`| Slice (`@WebMvcTest`)         | upload result, empty file, header-only file                                   |

### Frontend

```bash
cd ecommerce-ui
npm install
npm test              # single run
npm run test:watch    # watch mode
npm run test:coverage # with coverage report
```

**35 frontend tests across 5 test files:**

| File                          | What it tests                                                                 |
|-------------------------------|-------------------------------------------------------------------------------|
| `ProductCard.test.jsx`        | Rendering, Buy button (enabled/disabled/callback), price formatting — 12 cases |
| `CheckoutModal.test.jsx`      | Rendering, expiry/CVV/card validation, success/failure/close — 9 cases        |
| `client.test.js`              | All API methods (productsApi, searchApi, ordersApi) call correct URLs — 8 cases |
| `ProductFormPage.test.jsx`    | Create mode (fields, validation, submit, API error), edit mode, cart sync — 9 cases |
| `CsvImportPage.test.jsx`      | File selection, non-CSV rejection, import results, row errors, API failure — 7 cases |

---

## CSV import

Upload any CSV with these columns:

```
name, sku, description, category, price, stock, weight_kg
```

Edge cases handled automatically:

| Problem               | Handling                                   |
|-----------------------|--------------------------------------------|
| `$29.99` price        | `$` stripped → imported as `29.99`         |
| `"free"` price        | Mapped to `0.00`                           |
| Negative stock        | Row rejected with reason                   |
| Blank name            | Row rejected with reason                   |
| Whitespace-only name  | Row rejected after trim                    |
| Duplicate SKU         | Existing record updated (upsert)           |
| XSS in name/desc      | Sanitised by OWASP HTML sanitizer          |
| SQL injection         | Handled by JPA parameterised queries       |
| Missing weight        | Stored as `null` (nullable column)         |
| Missing category      | Stored as `UNCATEGORIZED`                  |
| Fully blank rows      | Silently skipped                           |

---

## Fixed incidents

### `function lower(bytea) does not exist` on `GET /api/search` with no keyword

**Symptom:** `GET /api/search?minPrice=30&page=0&size=12` (any search where `q` is omitted) returned HTTP 500 against PostgreSQL, while working fine in H2-backed tests.

**Root cause:** the original JPQL wrapped `:q` inside `LOWER(CONCAT('%', :q, '%'))`. When `q` is `null` and there is no surrounding string literal for Hibernate to infer a type from, PostgreSQL's JDBC driver falls back to binding the untyped null as `bytea`. `LOWER(bytea)` has no matching overload, so Postgres rejects the query — even though the surrounding `:q IS NULL OR …` clause should have made the `LOWER()` call irrelevant. SQL does not short-circuit `OR` branches during type-checking, so the broken branch is evaluated regardless.

**Fix:** the `%pattern%` wildcard string is now built in Java (`ProductRepository.search()`) and passed to the underlying query as either a concrete lowercased `String` or a typed `null` — never a null routed through a SQL string function.

**Lesson:** any JPQL that wraps a nullable bound parameter in a database function (`LOWER`, `CONCAT`, `UPPER`, `TRIM`, etc.) is a latent cross-database portability bug, even when guarded by an `IS NULL OR` clause. Prefer building the final string value in Java.

---

## Decisions

**Why Spring Boot 3 + Java 21?**
LTS release with virtual threads available for future I/O-heavy work. Spring Boot 3 requires Jakarta EE 10, which aligns with modern ecosystem standards.

**Why PostgreSQL over H2?**
PostgreSQL 16 persists data across container restarts (via the `pg_data` Docker volume) and is production-representative. H2 in-memory resets on restart and hides real cross-database portability bugs like the `lower(bytea)` incident above.

**Why React 18 + Vite over Next.js?**
A CSR SPA is sufficient for this scope. Next.js SSR adds a Node.js runtime to the Docker setup without meaningful benefit when there are no SEO or hydration requirements.

**Why TanStack Query?**
Handles loading/error states, caching, and cache invalidation after mutations with minimal boilerplate — critical for keeping the product list fresh after create/edit/delete/import operations.

**Why soft-delete?**
Deleting a product with existing orders would violate the FK constraint or corrupt order history. The `deleted` boolean hides products from the catalogue while preserving referential integrity.

**Why `unit_price` snapshotted on `order_items`?**
Product prices can change after an order is placed. Snapshotting the price at purchase time keeps receipts accurate regardless of future price edits.

**Alternatives considered:**
- Elasticsearch for search → overkill for this scale; JPQL LIKE covers the requirement
- Flyway migrations → preferred for production; `ddl-auto: update` chosen here for simplicity
- Redis for caching → no persistent load to justify it here
- Spring Security + JWT → challenge doesn't specify auth; added complexity without a clear requirement