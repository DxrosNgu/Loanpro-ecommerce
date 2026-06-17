# CLAUDE.md — Project Instructions

This file is read by Claude Code (and any Claude session operating in this repo) before
making changes. It points to the project's skills and states the operating rules that
keep contributions — human or AI — consistent with the existing codebase.

---

## Skills available in this project

| Skill | File | Use when |
|---|---|---|
| Project Scaffolding | `.claude/skills/SCAFFOLDING.md` | Starting any new domain concept, endpoint group, or service from scratch |
| TDD Endpoint Workflow | `.claude/skills/TDD_ENDPOINT.md` | Adding a single new endpoint to an existing controller, or building any new component/page on the frontend |

**Read the relevant skill file in full before writing code.** Both skills are written
against this codebase's actual files (`ProductService`, `ProductController`,
`ProductControllerTest`, etc.) — not generic examples — so the conventions inside them
are directly copy-paste-adaptable.

---

## How to work in this repo

### Backend (`ecommerce-api/`)

1. Read `.claude/skills/SCAFFOLDING.md` if this is a new domain concept.
2. Read `.claude/skills/TDD_ENDPOINT.md` for the Red → Green → Refactor cycle on any new endpoint.
3. Write the controller test first, then the service test, then the implementation.
4. Run `./gradlew test` before considering any change complete.
5. Every new exception must be wired into `GlobalExceptionHandler` in the same change.

### Frontend (`ecommerce-ui/`)

1. Read `.claude/skills/SCAFFOLDING.md` for the API method → page → route → nav order.
2. Read `.claude/skills/TDD_ENDPOINT.md` for the component test-first pattern.
3. Mock all API calls in tests with `vi.mock('../../api/client')` — tests never hit a real server.
4. Run `npm test` before considering any change complete.

### Docker

- Any new service added to `docker-compose.yml` needs its own `.dockerignore`.
- Never `COPY . .` before installing dependencies in a Dockerfile — see the multi-stage
  pattern in `ecommerce-ui/Dockerfile` and `ecommerce-api/Dockerfile`.

---

## Subagent delegation strategy

If using Claude Code with subagents (or splitting work across separate Claude sessions),
delegate along these boundaries — they map to the dependency order in the scaffolding skill:

```
Subagent 1 — Domain & persistence
  Owns: domain/, repository/, exception/
  Reads: SCAFFOLDING.md §1–3

Subagent 2 — API contract
  Owns: dto/request/, dto/response/, controller/
  Reads: SCAFFOLDING.md §4, §6 + TDD_ENDPOINT.md controller test template
  Depends on: Subagent 1's domain classes existing first

Subagent 3 — Business logic
  Owns: service/
  Reads: SCAFFOLDING.md §5 + TDD_ENDPOINT.md service test template
  Depends on: Subagent 1 + Subagent 2's DTOs existing first

Subagent 4 — Frontend
  Owns: src/api/, src/pages/, src/components/
  Reads: SCAFFOLDING.md frontend section + TDD_ENDPOINT.md frontend TDD cycle
  Depends on: the API contract being finalised (can stub with mocked responses
  in parallel using the test-first pattern, then swap in the real client method)

Subagent 5 — Test verification
  Owns: nothing — reviews all test files against TDD_ENDPOINT.md checklist
  Runs: ./gradlew test && npm test
  Blocks merge if: any item in the "TDD checklist before opening a PR" is unchecked
```

This split exists because each subagent's output is fully verifiable by running the test
suite for its layer in isolation — domain/repository changes compile independently of the
controller layer, and the controller layer is tested with the service mocked out via
`@WebMvcTest`, so Subagent 2 never needs Subagent 3's real implementation to finish its
own tests.

---

## Non-negotiable conventions

These are enforced by the skills above, repeated here as a fast reference:

- Request DTOs: `@Data` only. Response DTOs: `@Data @Builder`.
- Every `@RequestBody` parameter has `@Valid`.
- Every repository read method returns `Page<T>`, never an unbounded `List<T>`.
- Every new custom exception is registered in `GlobalExceptionHandler` in the same change.
- Controller tests use `@WebMvcTest`, never `@SpringBootTest`.
- Integration tests (real DB) use `@SpringBootTest @ActiveProfiles("test") @Transactional`.
- Frontend tests mock the API client — no test should require a running backend.
- Soft-delete (`deleted` boolean) is used instead of hard `DELETE` wherever a foreign key
  could reference the row (orders referencing products, etc).
