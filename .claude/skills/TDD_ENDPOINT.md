# Skill: Building a New Endpoint with TDD

> Use this skill every time you add a new endpoint.
> The TDD cycle in this project is: **Red → Green → Refactor**.
> Tests are written against the real method signatures of the production code —
> never against imagined signatures.

---

## The rule: tests come first

Writing tests after implementation is not TDD. In this project the order is fixed:

```
1. Write the controller test  → it fails (Red) — no controller exists yet
2. Write the service test     → it fails (Red) — no service method exists yet
3. Write the controller       → controller test passes (Green)
4. Write the service          → service test passes (Green)
5. Refactor                   → both tests still pass
```

This means you discover the API contract through the test, not through the implementation.

---

## Worked example: PATCH /api/products/{id}/stock

We need an endpoint to adjust stock by a delta (positive = restock, negative = sale adjustment).

### Step 0 — Define the contract in writing first

Before touching any file, answer these four questions:

```
Method:     PATCH
URL:        /api/products/{id}/stock
Request:    { "delta": 25 }        ← positive or negative integer
Response:   200 ProductResponse    ← the updated product
Errors:     404 if product missing
            409 if adjustment would make stock negative
```

---

### Step 1 — Write the controller test (Red)

No `ProductController` change yet. This test will fail to compile, which is intentional.

```java
// src/test/java/com/loanpro/ecommerce/controller/ProductControllerTest.java
// (add these tests to the existing class)

@Test
@DisplayName("PATCH /api/products/{id}/stock returns 200 with updated product")
void patchStockReturnsUpdatedProduct() throws Exception {
    // Arrange — define exactly what the service method will return
    ProductResponse updated = ProductResponse.builder()
        .id(1L).name("Running Shoes").sku("RS-001")
        .price(BigDecimal.valueOf(89.99)).stock(175)   // was 150, delta +25
        .category(Category.FOOTWEAR).build();

    when(productService.adjustStock(eq(1L), eq(25))).thenReturn(updated);

    // Act + Assert
    mockMvc.perform(patch("/api/products/1/stock")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"delta\": 25}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(175));
}

@Test
@DisplayName("PATCH /api/products/{id}/stock returns 409 when stock would go negative")
void patchStockReturns409OnNegativeResult() throws Exception {
    when(productService.adjustStock(eq(1L), eq(-999)))
        .thenThrow(new InsufficientStockException("RS-001", 999, 150));

    mockMvc.perform(patch("/api/products/1/stock")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"delta\": -999}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").exists());
}

@Test
@DisplayName("PATCH /api/products/{id}/stock returns 404 when product not found")
void patchStockReturns404WhenNotFound() throws Exception {
    when(productService.adjustStock(eq(99L), anyInt()))
        .thenThrow(new ProductNotFoundException(99L));

    mockMvc.perform(patch("/api/products/99/stock")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"delta\": 10}"))
        .andExpect(status().isNotFound());
}

@Test
@DisplayName("PATCH /api/products/{id}/stock returns 400 when delta is missing")
void patchStockReturns400WhenDeltaMissing() throws Exception {
    mockMvc.perform(patch("/api/products/1/stock")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
}
```

**Run the tests → they fail to compile** because `productService.adjustStock` does not exist. That is correct. You now have a specification.

---

### Step 2 — Write the service test (Red)

```java
// src/test/java/com/loanpro/ecommerce/service/ProductServiceTest.java
// (add to the existing Nested class or create a new one)

@Nested
@DisplayName("adjustStock()")
class AdjustStock {

    @Test
    @DisplayName("increases stock by positive delta")
    void increasesStock() {
        shoe.setStock(150);
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
        when(repo.save(shoe)).thenReturn(shoe);

        ProductResponse result = service.adjustStock(1L, 25);

        assertThat(result.getStock()).isEqualTo(175);
        verify(repo).save(argThat(p -> p.getStock() == 175));
    }

    @Test
    @DisplayName("decreases stock by negative delta")
    void decreasesStock() {
        shoe.setStock(150);
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
        when(repo.save(shoe)).thenReturn(shoe);

        ProductResponse result = service.adjustStock(1L, -50);

        assertThat(result.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("throws InsufficientStockException when delta would make stock negative")
    void throwsWhenStockGoesNegative() {
        shoe.setStock(10);
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));

        assertThatThrownBy(() -> service.adjustStock(1L, -50))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessageContaining("RS-001");

        verify(repo, never()).save(any());   // must NOT persist on failure
    }

    @Test
    @DisplayName("throws ProductNotFoundException when product does not exist")
    void throwsWhenProductMissing() {
        when(repo.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjustStock(99L, 10))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("zero delta is valid — saves unchanged stock")
    void zeroDeltaIsValid() {
        shoe.setStock(100);
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
        when(repo.save(shoe)).thenReturn(shoe);

        service.adjustStock(1L, 0);

        verify(repo).save(argThat(p -> p.getStock() == 100));
    }
}
```

**Run the tests → they fail to compile.** Correct. Now write the production code.

---

### Step 3 — Write the request DTO

```java
// src/main/java/com/loanpro/ecommerce/dto/request/StockAdjustRequest.java
package com.loanpro.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockAdjustRequest {

    @NotNull(message = "delta is required")
    private Integer delta;
}
```

---

### Step 4 — Implement the service method (Green)

Add `adjustStock` to `ProductService`:

```java
@Transactional
public ProductResponse adjustStock(Long id, int delta) {
    Product p = findActive(id);                            // throws 404 if missing

    int newStock = p.getStock() + delta;
    if (newStock < 0) {
        throw new InsufficientStockException(p.getSku(), Math.abs(delta), p.getStock());
    }

    p.setStock(newStock);
    return ProductResponse.from(repo.save(p));
}
```

**Run the service tests → they pass (Green).**

---

### Step 5 — Implement the controller method (Green)

Add the PATCH method to `ProductController`:

```java
@PatchMapping("/{id}/stock")
@Operation(summary = "Adjust product stock by a delta")
public ProductResponse adjustStock(
    @PathVariable Long id,
    @Valid @RequestBody StockAdjustRequest req) {
    return productService.adjustStock(id, req.getDelta());
}
```

**Run the controller tests → they pass (Green).**

---

### Step 6 — Refactor

Now that all tests are green, look for duplication:

- Is `InsufficientStockException` being built the same way in `OrderService` and now `ProductService`? Extract a factory method.
- Does `findActive()` need a cleaner name now that more methods call it? Rename and let the tests catch any missed usages.
- Does `StockAdjustRequest` need a `@Min`/`@Max` on `delta` to prevent extreme values? Add it, write a test for the 400 first.

---

## Choosing the right test type

| What you're testing | Test type | Annotation | Speed |
|---|---|---|---|
| Service logic, pure calculations | Unit | `@ExtendWith(MockitoExtension.class)` | ~5ms |
| Controller HTTP contract (status, JSON shape, validation) | Slice | `@WebMvcTest(XController.class)` | ~200ms |
| Database queries, CSV import pipeline | Integration | `@SpringBootTest @ActiveProfiles("test")` | ~2s |
| External call simulation | Contract | Mockito + `when().thenReturn()` | ~5ms |

**Never use `@SpringBootTest` for a controller test.** It starts the full context and a real database, making the test 10× slower and harder to isolate failures. `@WebMvcTest` mocks the service layer and only loads the web layer.

---

## Controller test template (`@WebMvcTest`)

```java
@WebMvcTest(ProductController.class)        // only loads ProductController + MVC infrastructure
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ProductService productService;  // replaces the real service with a Mockito mock

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void createReturns201() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes");
        req.setSku("RS-001");
        req.setPrice(BigDecimal.valueOf(89.99));
        req.setStock(150);

        when(productService.create(any())).thenReturn(
            ProductResponse.builder().id(1L).name("Running Shoes").sku("RS-001")
                .price(BigDecimal.valueOf(89.99)).stock(150).build());

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
            .andExpect(status().isCreated())              // 201, not 200
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.sku").value("RS-001"));
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void createReturns400WhenNameMissing() throws Exception {
        ProductRequest req = new ProductRequest();
        // name intentionally omitted
        req.setSku("RS-001");
        req.setPrice(BigDecimal.TEN);
        req.setStock(10);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));
    }

    // ── not found ────────────────────────────────────────────────────────────

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(productService.getById(99L))
            .thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/products/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }
}
```

---

## Service test template (`@ExtendWith(MockitoExtension.class)`)

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock ProductRepository repo;
    @InjectMocks ProductService service;

    private Product shoe;

    @BeforeEach
    void setUp() {
        shoe = Product.builder()
            .id(1L).name("Running Shoes").sku("RS-001")
            .price(BigDecimal.valueOf(89.99)).stock(150)
            .category(Category.FOOTWEAR).deleted(false).build();
    }

    @Test
    @DisplayName("create() uppercases the SKU before saving")
    void createUppercasesSku() {
        ProductRequest req = new ProductRequest();
        req.setName("Shoe"); req.setSku("rs-001");
        req.setPrice(BigDecimal.TEN); req.setStock(5);

        when(repo.save(any(Product.class))).thenReturn(shoe);

        service.create(req);

        // argThat lets you verify a property of the saved entity
        verify(repo).save(argThat(p -> p.getSku().equals("RS-001")));
    }

    @Test
    @DisplayName("delete() sets deleted=true and never creates a new entity")
    void deleteSetsSoftDeleteFlag() {
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
        when(repo.save(shoe)).thenReturn(shoe);

        service.delete(1L);

        assertThat(shoe.getDeleted()).isTrue();
        verify(repo).save(shoe);           // same object, not a new one
        verify(repo, never()).delete(any()); // hard delete must never be called
    }
}
```

---

## Integration test template (`@SpringBootTest`)

Use only for tests that need a real database interaction: CSV import, complex JPQL queries, cascading saves.

```java
@SpringBootTest
@ActiveProfiles("test")   // loads application-test.yml → H2 in-memory
@Transactional            // rolls back after each test — no cleanup code needed
@DisplayName("CsvImportService (integration)")
class CsvImportServiceTest {

    @Autowired CsvImportService csvImportService;
    @Autowired ProductRepository productRepository;

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv",
            content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("upserts existing SKU instead of inserting a duplicate")
    void upsertsExistingSku() throws Exception {
        csvImportService.importFile(csv(
            "name,sku,description,category,price,stock,weight_kg\n"
            + "Original,DUP-001,first,Footwear,10.00,10,0.1\n"));

        CsvImportResult r = csvImportService.importFile(csv(
            "name,sku,description,category,price,stock,weight_kg\n"
            + "Updated,DUP-001,second,Footwear,20.00,20,0.1\n"));

        assertThat(r.getUpdated()).isEqualTo(1);
        assertThat(r.getImported()).isZero();

        // Verify the DB state directly
        var saved = productRepository.findBySku("DUP-001");
        assertThat(saved).isPresent();
        assertThat(saved.get().getName()).isEqualTo("Updated");
        assertThat(saved.get().getPrice()).isEqualByComparingTo("20.00");
    }
}
```

---

## Frontend TDD cycle

The same Red → Green → Refactor cycle applies. Write the test against the component's *expected* behaviour before implementing the component.

### Step 1 — Write the test (Red)

```jsx
// src/__tests__/components/ReviewCard.test.jsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ReviewCard from '../../components/ReviewCard'

// This import will fail until ReviewCard.jsx exists — that is correct
describe('ReviewCard', () => {
  const review = {
    id: 1, reviewerName: 'Diego', rating: 5,
    body: 'Great shoes!', createdAt: '2026-06-13T10:00:00Z'
  }

  it('displays the reviewer name', () => {
    render(<ReviewCard review={review} />)
    expect(screen.getByText('Diego')).toBeInTheDocument()
  })

  it('displays the correct number of filled stars', () => {
    render(<ReviewCard review={review} />)
    // 5 filled stars for rating 5
    expect(screen.getAllByTestId('star-filled')).toHaveLength(5)
  })

  it('calls onDelete with the review id when Delete is clicked', async () => {
    const onDelete = vi.fn()
    render(<ReviewCard review={review} onDelete={onDelete} />)
    await userEvent.click(screen.getByRole('button', { name: /delete/i }))
    expect(onDelete).toHaveBeenCalledWith(1)
  })
})
```

### Step 2 — Implement the component (Green)

```jsx
// src/components/ReviewCard.jsx
export default function ReviewCard({ review, onDelete }) {
  return (
    <div className="card p-4">
      <div className="flex items-center justify-between mb-2">
        <span className="font-medium text-gray-900">{review.reviewerName}</span>
        <div className="flex gap-0.5">
          {Array.from({ length: 5 }).map((_, i) => (
            <span key={i} data-testid={i < review.rating ? 'star-filled' : 'star-empty'}>
              {i < review.rating ? '★' : '☆'}
            </span>
          ))}
        </div>
      </div>
      {review.body && <p className="text-sm text-gray-600">{review.body}</p>}
      {onDelete && (
        <button
          onClick={() => onDelete(review.id)}
          className="btn-danger text-xs mt-3"
        >
          Delete
        </button>
      )}
    </div>
  )
}
```

**Run tests → Green.**

---

## Common mistakes and how to avoid them

### Mistake 1 — Testing implementation details instead of behaviour

```java
// BAD — tests that repo.save() was called exactly once (implementation)
verify(repo, times(1)).save(any());

// GOOD — tests the observable outcome (behaviour)
assertThat(result.getSku()).isEqualTo("RS-001");
verify(repo).save(argThat(p -> p.getSku().equals("RS-001")));
```

### Mistake 2 — Using `@SpringBootTest` for controller tests

```java
// BAD — loads full context, needs a running database
@SpringBootTest
class ProductControllerTest { ... }

// GOOD — loads only the web layer, services are mocked
@WebMvcTest(ProductController.class)
class ProductControllerTest { ... }
```

### Mistake 3 — Not adding `@Valid` on `@RequestBody`

```java
// BAD — Bean Validation is silently skipped
@PostMapping
public ProductResponse create(@RequestBody ProductRequest req) { ... }

// GOOD — 400 is returned automatically when constraints fail
@PostMapping
public ProductResponse create(@Valid @RequestBody ProductRequest req) { ... }
```

### Mistake 4 — Adding `@Builder` to a request DTO

```java
// BAD — Jackson cannot deserialise without a no-arg constructor
@Data @Builder
public class ProductRequest { ... }

// GOOD — @Data provides the no-arg constructor Jackson needs
@Data
public class ProductRequest { ... }
```

### Mistake 5 — Calling `repo.findAll()` without pagination

```java
// BAD — loads every row from the database into memory
List<Product> all = repo.findAll();

// GOOD — always paginate
Page<Product> page = repo.findByDeletedFalse(PageRequest.of(0, 12));
```

### Mistake 6 — Forgetting `@Transactional` on write methods

```java
// BAD — if an exception is thrown after the first save, partial data remains
public void processOrder(OrderRequest req) {
    orderRepo.save(order);        // saved
    stockRepo.decrement(id);      // throws → order saved, stock not decremented
}

// GOOD — both saves roll back together if anything fails
@Transactional
public void processOrder(OrderRequest req) {
    orderRepo.save(order);
    stockRepo.decrement(id);
}
```

---

## TDD checklist before opening a PR

- [ ] Controller test written **before** the controller method
- [ ] Service test written **before** the service method
- [ ] Every new exception is handled in `GlobalExceptionHandler`
- [ ] Happy path covered (2xx response, correct JSON shape)
- [ ] Validation path covered (400 with `fieldErrors`)
- [ ] Not-found path covered (404 with `message`)
- [ ] Conflict path covered if applicable (409)
- [ ] No `@SpringBootTest` on controller tests
- [ ] No `repo.findAll()` without `Pageable`
- [ ] Request DTO uses `@Data` (not `@Builder`)
- [ ] `@Valid` present on every `@RequestBody`
- [ ] Frontend component test written before the component
- [ ] All API calls in frontend tests are mocked with `vi.mock`
