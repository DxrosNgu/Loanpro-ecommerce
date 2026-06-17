# Skill: Project Scaffolding

> Use this skill whenever starting a new feature, module, or vertical slice from scratch.
> It encodes the conventions used across `ecommerce-api` and `ecommerce-ui` so that every
> new piece of work is consistent, testable, and ready to run on day one.

---

## When to apply this skill

- Adding a completely new domain concept (e.g. `Wishlist`, `Coupon`, `Review`)
- Adding a new vertical slice to an existing domain (e.g. bulk-delete endpoint on `Product`)
- Starting a new micro-service that follows the same stack

---

## Backend scaffolding order

Always build in dependency order. Each layer depends only on layers below it.

```
1. Domain (entity / enum)
2. Repository
3. Custom exception(s)
4. Request / Response DTOs
5. Service
6. Controller
7. Wire exception into GlobalExceptionHandler
8. Register Swagger tag in OpenApiConfig (if new controller)
```

Breaking this order causes circular import errors and forces repeated refactors.

---

## 1. Domain entity

```java
// src/main/java/com/loanpro/ecommerce/domain/Review.java
package com.loanpro.ecommerce.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "reviews")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK to products — use @ManyToOne with LAZY fetch to avoid N+1
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String reviewerName;

    @Column(nullable = false)
    private Integer rating;           // 1–5

    @Column(columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
```

**Rules:**
- Always use `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` so JPA and Lombok are both happy
- Use `@CreationTimestamp` / `@UpdateTimestamp` instead of manual `@PrePersist` hooks
- Soft-delete columns (`deleted BOOLEAN DEFAULT FALSE`) belong on the entity, not in a base class, so the column is visible in `@Query`
- Never expose the JPA entity directly from a controller — always map through a DTO

---

## 2. Repository

```java
// src/main/java/com/loanpro/ecommerce/repository/ReviewRepository.java
package com.loanpro.ecommerce.repository;

import com.loanpro.ecommerce.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Simple derived query — Spring Data generates the SQL automatically
    Page<Review> findByProductId(Long productId, Pageable pageable);

    // JPQL for anything involving joins or aggregations
    @Query("""
        SELECT AVG(r.rating) FROM Review r
        WHERE r.product.id = :productId
        """)
    Double averageRatingForProduct(@Param("productId") Long productId);
}
```

**Rules:**
- Prefer derived method names for simple single-table queries; use `@Query` for joins/aggregations
- Always return `Page<T>` (not `List<T>`) for any query that could grow unbounded
- Never use `findAll()` without a `Pageable` parameter in production code

---

## 3. Exception

```java
// src/main/java/com/loanpro/ecommerce/exception/ReviewNotFoundException.java
package com.loanpro.ecommerce.exception;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(Long id) {
        super("Review not found with id: " + id);
    }
}
```

Then wire it into `GlobalExceptionHandler`:

```java
@ExceptionHandler(ReviewNotFoundException.class)
public ResponseEntity<ApiError> handleReviewNotFound(ReviewNotFoundException ex) {
    return response(HttpStatus.NOT_FOUND, ex.getMessage(), null);
}
```

**Rules:**
- One exception class per domain concept — never reuse `ProductNotFoundException` for a Review
- All exceptions extend `RuntimeException` — Spring's `@Transactional` rolls back on unchecked exceptions
- Always add the handler to `GlobalExceptionHandler` in the same commit as the exception class

---

## 4. DTOs

```java
// src/main/java/com/loanpro/ecommerce/dto/request/ReviewRequest.java
package com.loanpro.ecommerce.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data                          // generates getters + setters — required for @WebMvcTest deserialisation
public class ReviewRequest {   // NOTE: never use @Builder on request DTOs

    @NotBlank(message = "Reviewer name is required")
    private String reviewerName;

    @NotNull
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    private String body;
}
```

```java
// src/main/java/com/loanpro/ecommerce/dto/response/ReviewResponse.java
package com.loanpro.ecommerce.dto.response;

import com.loanpro.ecommerce.domain.Review;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder                 // response DTOs use @Builder for readable test fixtures
public class ReviewResponse {
    private Long id;
    private Long productId;
    private String reviewerName;
    private Integer rating;
    private String body;
    private Instant createdAt;

    // Static factory keeps mapping logic close to the DTO, not scattered in services
    public static ReviewResponse from(Review r) {
        return ReviewResponse.builder()
            .id(r.getId())
            .productId(r.getProduct().getId())
            .reviewerName(r.getReviewerName())
            .rating(r.getRating())
            .body(r.getBody())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
```

**Critical rule — @Data vs @Builder on request DTOs:**
- Request DTOs → `@Data` only (Jackson needs a no-arg constructor + setters to deserialise)
- Response DTOs → `@Data @Builder` (builders make test fixtures readable)
- If you add `@Builder` to a request DTO, `@WebMvcTest` will silently return 400 for valid bodies

---

## 5. Service

```java
// src/main/java/com/loanpro/ecommerce/service/ReviewService.java
package com.loanpro.ecommerce.service;

import com.loanpro.ecommerce.domain.Product;
import com.loanpro.ecommerce.domain.Review;
import com.loanpro.ecommerce.dto.request.ReviewRequest;
import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ReviewResponse;
import com.loanpro.ecommerce.exception.ReviewNotFoundException;
import com.loanpro.ecommerce.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepo;
    private final ProductService productService;   // reuse findActive() — don't duplicate FK check

    @Transactional
    public ReviewResponse create(Long productId, ReviewRequest req) {
        Product product = productService.findActive(productId);  // throws 404 if deleted/missing

        Review review = Review.builder()
            .product(product)
            .reviewerName(req.getReviewerName())
            .rating(req.getRating())
            .body(req.getBody())
            .build();

        return ReviewResponse.from(reviewRepo.save(review));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForProduct(Long productId, int page, int size) {
        productService.findActive(productId);       // validate product exists first
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(
            reviewRepo.findByProductId(productId, pageable).map(ReviewResponse::from)
        );
    }

    @Transactional
    public void delete(Long id) {
        Review r = reviewRepo.findById(id)
            .orElseThrow(() -> new ReviewNotFoundException(id));
        reviewRepo.delete(r);
    }
}
```

**Rules:**
- `@Transactional(readOnly = true)` on every read method — signals Hibernate to skip dirty checking
- Services only call other services through their public methods — never call another service's repository directly
- Keep mapping (`ReviewResponse.from(...)`) in the DTO, not in the service
- Always validate FK existence (product exists) before persisting the child entity

---

## 6. Controller

```java
// src/main/java/com/loanpro/ecommerce/controller/ReviewController.java
package com.loanpro.ecommerce.controller;

import com.loanpro.ecommerce.dto.request.ReviewRequest;
import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ReviewResponse;
import com.loanpro.ecommerce.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")  // nested resource URL
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a review to a product")
    public ReviewResponse create(
        @PathVariable Long productId,
        @Valid @RequestBody ReviewRequest req) {
        return reviewService.create(productId, req);
    }

    @GetMapping
    @Operation(summary = "List reviews for a product")
    public PageResponse<ReviewResponse> list(
        @PathVariable Long productId,
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "10") int size) {
        return reviewService.listForProduct(productId, page, size);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a review")
    public void delete(@PathVariable Long id) {
        reviewService.delete(id);
    }
}
```

**Rules:**
- Nested resources go in nested URL paths: `/api/products/{productId}/reviews`
- `@ResponseStatus(HttpStatus.CREATED)` on `POST` — never return 200 for creation
- `@ResponseStatus(HttpStatus.NO_CONTENT)` on `DELETE` — never return 200 for deletion
- Controller methods are `void` or return domain objects — never `ResponseEntity<?>` unless you need custom headers
- Always annotate with `@Valid` on `@RequestBody` — without it, Bean Validation is silently skipped

---

## Frontend scaffolding order

```
1. API method in src/api/client.js
2. Page component  in src/pages/
3. Route entry     in src/App.jsx
4. Nav link        in src/components/Navbar.jsx  (if top-level)
5. Shared component in src/components/  (if reused across pages)
```

### Add API method

```js
// src/api/client.js  — add to the relevant api object
export const reviewsApi = {
  list:   (productId, page = 0, size = 10) =>
    client.get(`/products/${productId}/reviews`, { params: { page, size } }).then(r => r.data),
  create: (productId, data) =>
    client.post(`/products/${productId}/reviews`, data).then(r => r.data),
  delete: (id) =>
    client.delete(`/reviews/${id}`),
}
```

### Page component skeleton

```jsx
// src/pages/ReviewListPage.jsx
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { reviewsApi } from '../api/client'

export default function ReviewListPage() {
  const { productId } = useParams()
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['reviews', productId, page],
    queryFn: () => reviewsApi.list(productId, page),
  })

  if (isLoading) return <div className="animate-pulse h-64 bg-gray-50 rounded-xl" />
  if (isError)   return <div className="text-red-500 text-center py-16">Failed to load reviews.</div>

  return (
    // render data.content
  )
}
```

### Route entry

```jsx
// src/App.jsx — add inside <Routes>
<Route path="/products/:productId/reviews" element={<ReviewListPage />} />
```

---

## Docker checklist for new services

When adding a **new Docker service** to `docker-compose.yml`:

1. Create a `.dockerignore` next to its `Dockerfile`
2. Use multi-stage builds: `deps → build → serve`
3. Never `COPY . .` before `RUN npm install` / `RUN gradle dependencies`
4. Add `depends_on` with `condition: service_healthy` if the new service needs another to be ready
5. Use named volumes for any stateful data

---

## File naming conventions

| Layer | Convention | Example |
|-------|-----------|---------|
| Entity | `PascalCase` noun | `Review.java` |
| Repository | `<Entity>Repository` | `ReviewRepository.java` |
| Exception | `<Entity>NotFoundException` | `ReviewNotFoundException.java` |
| Request DTO | `<Action><Entity>Request` | `CreateReviewRequest.java` |
| Response DTO | `<Entity>Response` | `ReviewResponse.java` |
| Service | `<Entity>Service` | `ReviewService.java` |
| Controller | `<Entity>Controller` | `ReviewController.java` |
| Frontend page | `<Entity><Action>Page.jsx` | `ReviewListPage.jsx` |
| Frontend component | `<Entity>Card.jsx` | `ReviewCard.jsx` |
| Backend test | `<Class>Test.java` | `ReviewServiceTest.java` |
| Frontend test | `<Component>.test.jsx` | `ReviewCard.test.jsx` |
