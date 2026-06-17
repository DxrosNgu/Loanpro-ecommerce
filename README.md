# LoanPro E-Commerce

Enterprise-grade e-commerce application built with Spring Boot 3 (Java 21) and React 18.

**CSV downloaded:** June 13, 2026

---

## Quick start

```bash
git clone <repo-url>
cd ecommerce
docker compose up --build
```

| Service  | URL                              |
|----------|----------------------------------|
| UI       | http://localhost:5173            |
| API      | http://localhost:8080/api        |
| Swagger  | http://localhost:8080/swagger-ui.html |

---

## Run locally (without Docker)

### Prerequisites
- Java 21
- Node 20
- PostgreSQL 16 running locally

### Backend
```bash
cd ecommerce-api
./gradlew bootRun
# API available at http://localhost:8080
```

### Frontend
```bash
cd ecommerce-ui
npm install
npm run dev
# UI available at http://localhost:5173
```

### Tests
```bash
cd ecommerce-api
./gradlew test
```

---

## Architecture

```
ecommerce/
├── ecommerce-api/     Spring Boot 3 REST API  (port 8080)
├── ecommerce-ui/      React 18 + Vite SPA     (port 5173 / 80)
└── docker-compose.yml Orchestrates api + ui + postgres
```

**Backend layers:**
```
Controller → Service → Repository → PostgreSQL
                 ↕
          CsvImportService (OpenCSV + OWASP sanitizer)
          PaymentFacade    (fake gateway)
          GlobalExceptionHandler
```

**Frontend pages:**
- `/`                  — Product list with search, category, and price filters
- `/products/new`      — Create product form
- `/products/:id`      — Product detail + buy now
- `/products/:id/edit` — Edit product form
- `/products/import`   — CSV drag-and-drop import with per-row result report
- `/orders/:id`        — Order confirmation / receipt

---

## API reference

| Method | Path                    | Description                     |
|--------|-------------------------|---------------------------------|
| GET    | /api/products           | Paginated product list          |
| GET    | /api/products/:id       | Single product                  |
| POST   | /api/products           | Create product                  |
| PUT    | /api/products/:id       | Update product                  |
| DELETE | /api/products/:id       | Soft-delete product             |
| POST   | /api/products/import    | CSV bulk import (multipart)     |
| GET    | /api/search             | Search: ?q= &category= &minPrice= &maxPrice= |
| POST   | /api/orders             | Place order (fake payment)      |
| GET    | /api/orders/:id         | Order receipt                   |

---

## CSV import

Upload any CSV with these columns:

```
name, sku, description, category, price, stock, weight_kg
```

Edge cases handled automatically:

| Problem              | Handling                              |
|----------------------|---------------------------------------|
| `$29.99` price       | `$` stripped → imported as `29.99`    |
| `"free"` price       | Mapped to `0.00`                      |
| Negative stock       | Row rejected with reason              |
| Blank name           | Row rejected with reason              |
| Whitespace-only name | Row rejected after trim               |
| Duplicate SKU        | Existing record updated (upsert)      |
| XSS in name/desc     | Sanitised by OWASP HTML sanitizer     |
| SQL injection        | Handled by JPA parameterised queries  |
| Missing weight       | Stored as `null` (nullable column)    |
| Missing category     | Stored as `UNCATEGORIZED`             |
| Fully blank rows     | Silently skipped                      |

---

## Fake payment

Cards ending in **0000** are always declined.
All other valid card numbers (13–19 digits) succeed and return a `TXN-XXXXXXXX` reference.

---

## Decisions

**Why Spring Boot 3 + Java 21?**
LTS release with virtual threads available for future I/O-heavy work. Spring Boot 3 requires Jakarta EE 10, which aligns with modern ecosystem standards.

**Why PostgreSQL over H2?**
The challenge requires a "local DB" that persists across container restarts. H2 in-memory resets on restart. PostgreSQL 16-alpine keeps the image small while being production-representative.

**Why React 18 + Vite over Next.js?**
A CSR SPA is sufficient for this scope. Next.js SSR adds a Node.js runtime to the Docker setup without meaningful benefit when there are no SEO or hydration requirements.

**Why TanStack Query?**
Handles loading/error states, caching, and cache invalidation after mutations with minimal boilerplate — critical for keeping the product list fresh after create/edit/delete/import.

**Why soft-delete?**
Deleting a product with existing orders would violate the FK constraint or corrupt order history. The `deleted` boolean hides products from the catalogue while preserving referential integrity.

**Why `unit_price` snapshotted on order_items?**
Product prices can change after an order is placed. Snapshotting the price at purchase time keeps receipts accurate regardless of future price edits.

**Alternatives considered:**
- Elasticsearch for search → overkill for this scale; JPQL LIKE covers the requirement
- Flyway migrations → preferred for production; `ddl-auto: update` chosen for challenge simplicity
- Redis for caching → no persistent load to justify it here
- Spring Security + JWT → challenge doesn't specify auth; added complexity without clear requirement


---

## Running tests

### Backend
```bash
cd ecommerce-api
./gradlew test

# With HTML report (opens in browser)
./gradlew test jacocoTestReport
open build/reports/tests/test/index.html
```

**32 backend tests across 8 test classes:**

| Class | Type | What it tests |
|-------|------|--------------|
| `ProductServiceTest` | Unit (Mockito) | list, getById, create, update, delete, search — 12 cases |
| `OrderServiceTest` | Unit (Mockito) | placeOrder (paid/failed/stock/total), getById — 8 cases |
| `PaymentFacadeTest` | Unit (pure) | valid cards, declined cards, invalid input — 11 cases (parameterised) |
| `CsvRowValidatorTest` | Unit (pure) | all CSV edge cases: price, stock, name, XSS, SQL, structure — 22 cases |
| `CsvImportServiceTest` | Integration (H2) | end-to-end import with real DB: valid, security, upsert, mixed — 14 cases |
| `ProductControllerTest` | Slice (@WebMvcTest) | REST contract: list, get, create (valid + invalid), delete |
| `OrderControllerTest` | Slice (@WebMvcTest) | REST contract: place (success/failure/validation), get, 404 |
| `SearchControllerTest` | Slice (@WebMvcTest) | keyword, category, price range, combined, default pagination |
| `CsvImportControllerTest` | Slice (@WebMvcTest) | upload result, empty file, header-only file |

### Frontend
```bash
cd ecommerce-ui
npm install
npm test          # single run
npm run test:watch        # watch mode
npm run test:coverage     # with coverage report
```

**35 frontend tests across 5 test files:**

| File | What it tests |
|------|--------------|
| `ProductCard.test.jsx` | Rendering, Buy button (enabled/disabled/callback), price formatting — 12 cases |
| `CheckoutModal.test.jsx` | Rendering, successful checkout, failed checkout, close behaviour — 9 cases |
| `client.test.js` | All API methods (productsApi, searchApi, ordersApi) call correct URLs and params — 8 cases |
| `ProductFormPage.test.jsx` | Create mode (fields, validation, submit, API error), edit mode (pre-fill, update) — 9 cases |
| `CsvImportPage.test.jsx` | File selection, non-CSV rejection, import results, row errors, API failure — 7 cases |
| `OrderConfirmPage.test.jsx` | PAID order (all fields), FAILED order (Try again), loading/error states — 10 cases |

---

## Skills and subagents used

This project was built using a deliberate combination of Claude capabilities, each mapped to a specific layer of the challenge.

### Architectural planning (Claude chat)
Used to design the three-option architecture comparison (Monolith, REST+SPA, Hexagonal), evaluate trade-offs (PostgreSQL vs H2, React vs Thymeleaf, TanStack Query vs SWR), and lock in the system diagram before writing a single file. The challenge brief explicitly values "foreseeing skills", so this planning phase was treated as a deliverable in itself.

### Backend generation (Claude computer use)
The full Spring Boot 3 / Java 21 backend was generated iteratively using Claude's bash and file-creation tools. Each layer was built in dependency order — domain → repository → service → controller — ensuring each piece compiled before the next was added. Key decisions verified at generation time:
- `ProductRequest` uses `@Data` (not `@Builder`) so `@WebMvcTest` can deserialise it without an all-args constructor
- `CsvImportService` imports are cleaned to remove a leftover `htmlparser` import that would cause a compile error
- `GlobalExceptionHandler` wires all four custom exception types in one pass to avoid partial coverage

### CSV edge-case analysis (Claude reasoning)
The 96-row CSV was analysed before writing the validator to enumerate exactly 14 distinct data quality problems. This prevented the common mistake of writing a validator for the happy path and discovering dirty data only at runtime. The `CsvRowValidator` class handles each case explicitly and independently — meaning new edge cases can be added as test cases without changing existing logic.

### Frontend scaffolding (Claude computer use + frontend-design skill)
The React 18 + Vite + Tailwind setup was generated using the `frontend-design` skill to ensure consistent token usage (CSS variables, border-radius, colour palette) that matches the Claude UI system rather than defaulting to raw Tailwind utility classes. The five-page structure was mocked up interactively first so the component hierarchy was clear before implementation.

### Test generation (Claude reasoning + computer use)
Tests were written after reading the actual compiled source — not generated in parallel with the implementation. This catches the most common AI testing failure: tests that are correct for an imagined API signature, not the real one. Specific patterns applied:
- `@WebMvcTest` slices for controllers (fast, no DB required)
- `@SpringBootTest + @ActiveProfiles("test") + @Transactional` for integration tests (real H2, rolled back after each test)
- Pure JUnit 5 for `PaymentFacadeTest` and `CsvRowValidatorTest` (no Spring context needed)
- `@ParameterizedTest + @ValueSource` for declined-card variants and invalid stock/price inputs
- Vitest with `@testing-library/react` and `msw`-style `vi.mock` for frontend — all API calls mocked so tests run without a server
