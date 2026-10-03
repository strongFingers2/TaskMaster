# TaskMaster REST API

A Spring Boot REST API for managing personal tasks, built from scratch with JWT authentication, per-user ownership scoping, and a PostgreSQL backend. Built as a learning project to go deep on Spring Security, JPA relationships, and production-adjacent concerns like containerization and secret management — not just CRUD.

## Features

- JWT-based stateless authentication (register/login)
- Ownership-scoped tasks — users can only ever see, edit, or delete their own tasks, enforced at the database query level
- Full CRUD on tasks with proper 404/403 semantics
- Pagination on the task list endpoint
- Centralized exception handling via `@ControllerAdvice`
- Request/response DTOs to prevent entity over-exposure and field-injection attacks
- PostgreSQL persistence (migrated from an in-memory H2 setup)
- Dockerized app + database, with secrets injected via environment variables

## Tech Stack

- Java 17, Spring Boot 3.x
- Spring Security + JWT (jjwt)
- Spring Data JPA / Hibernate
- PostgreSQL
- Docker & Docker Compose
- Maven

## Architecture Decisions

**JWT over sessions.** The API is stateless — no server-side session store. Every request carries a signed token; the server verifies the signature and expiry rather than looking up session state. Simpler to scale horizontally, at the cost of not being able to revoke a single token early without extra infrastructure (a known, accepted tradeoff for this project's scope).

**Ownership enforced at the query layer, not just in controller logic.** Task retrieval uses `findByOwnerId`-style derived queries, so a user's data is filtered by the database itself — not fetched in bulk and filtered in Java afterward. This matters for both correctness and performance as data grows.

**DTOs on both directions.** Response DTOs strip out the full `owner` entity (and avoid a Hibernate lazy-proxy serialization crash — see below). Request DTOs exclude fields like `owner` and `id` entirely, closing off a field-injection attack where a client could otherwise smuggle an `owner` value into a request body and hijack a task's ownership.

**Secrets out of source control.** The JWT signing key and database password are read from environment variables (`JWT_SECRET`, `DB_PASSWORD`), never hardcoded or committed.

## Getting Started

### Option 1: Docker (recommended)

```bash
git clone <repo-url>
cd taskmaster
cp .env.example .env   # fill in DB_PASSWORD and JWT_SECRET
docker-compose up --build
```

The API will be available at `http://localhost:8080`.

### Option 2: Manual

Requires Java 17, Maven, and a running PostgreSQL instance.

```bash
createdb taskmanager
export DB_PASSWORD=your_postgres_password
export JWT_SECRET=a_random_32plus_character_string
mvn spring-boot:run
```

## API Endpoints

| Method | Endpoint             | Auth required | Description                          |
|--------|-----------------------|----------------|----------------------------------------|
| POST   | `/auth/register`      | No             | Register a new user                    |
| POST   | `/auth/login`         | No             | Log in, returns a JWT                  |
| POST   | `/tasks`               | Yes            | Create a task                          |
| GET    | `/tasks`               | Yes            | List current user's tasks (paginated: `?page=0&size=10`) |
| GET    | `/tasks/{id}`          | Yes            | Get a single task (must be owner)      |
| PUT    | `/tasks/{id}`          | Yes            | Update a task (must be owner)          |
| DELETE | `/tasks/{id}`          | Yes            | Delete a task (must be owner)          |

Protected endpoints require `Authorization: Bearer <token>`.

## Bugs I Hit and Fixed

**Hibernate lazy-proxy serialization crash.** Returning a `Task` entity directly from `getTaskById` threw a Jackson `InvalidDefinitionException` on the `owner` field. The root cause: a `LAZY @ManyToOne` field isn't a real `User` object until accessed — it's a Hibernate-generated proxy carrying internal fields like `hibernateLazyInitializer` that Jackson has no idea how to serialize. Fixed by introducing a `TaskResponseDTO` that extracts only `owner.getId()` instead of serializing the whole proxy — which also closed a second problem: the raw entity was leaking the full `User` object, password hash included, in every task response.

**Signing key regenerated on every restart, silently invalidating all tokens.** `JwtUtil` originally generated its `SecretKey` as a field initializer (`Jwts.SIG.HS256.key().build()`), which runs fresh on every app startup. Every restart produced a new key, so every previously issued JWT failed signature verification — users were force-logged-out on every deploy, even though their expiry hadn't passed and the data was fine. Fixed by reading a fixed secret from an environment variable and building the key once via `@PostConstruct`, so the signing key is stable across restarts.

**Missing `return` statements causing incorrect status codes.** In `getTaskById`, `updateTask`, and `deleteTask`, an early `if` branch handling the 403 case was missing a `return`, so execution fell through to the 404 branch below it regardless of which case actually applied. This isn't just a bug — a 404 instead of a 403 on an ownership check is an information-leak difference (404 tells an attacker "this ID doesn't exist," 403 tells them "it exists but isn't yours"), so the wrong status code has real security implications, not just correctness ones.

**Operation-ordering bug: saving before setting required state.** `createTask` originally called `taskService.createTask(task)` — saving the task with `owner = null` — before calling `task.setOwner(user)`, then returned the stale pre-owner object. Fixed by reordering to set `owner` before the single save call, and returning what the service actually persisted rather than a manually reconstructed object.

**Unsafe `Optional` access before presence check.** `deleteTask` called `.get()` on an `Optional<Task>` before checking `.isPresent()`, which threw on any nonexistent ID instead of returning a clean 404. Reordered to check presence first.

**Unfiltered list endpoint leaking all users' data.** The original `GET /tasks` called `taskService.getAllTasks()` with no ownership filter at all — returning every task from every user in the database. Fixed by replacing it with a `findByOwnerId` derived query, filtering at the database level instead of fetching everything and discarding most of it in the controller (a fix that solved both the security leak and an unnecessary full-table-scan performance problem in one change).

## What I'd Do Differently / Next Steps

- Extract the duplicated ownership check (`task.getOwner().getId() == currentUser.getId()`) into a single `isOwner()` helper — currently repeated across three controller methods
- Add Swagger/OpenAPI for a browsable API UI
- Add a refresh-token mechanism to allow actual token revocation (currently a stolen token is only neutralized by its 1-hour expiry)
- Migrate schema management from `ddl-auto=update` to a proper migration tool like Flyway for production use
