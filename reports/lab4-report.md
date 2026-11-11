# Lab 4 — Design, Implementation, and Deployment

The catalogue controllers now delegate persistence and business rules to services and expose explicit DTO contracts. All fourteen operations have OpenAPI documentation, the shared error handler provides a consistent envelope, and the application builds and runs in the required multi-stage Java 17 container. Verification passed 129 tests, 60 HTTP checks against the container, and a rendered Swagger UI check covering all fourteen operations.

The baseline was the completed `lab-3` revision, `67c05c1bf221071c4ddace1da67fe236053a1b23`. The design review below was recorded before source changes. Existing business behavior, entity mappings, dependencies, seed data, borrowing/reservation services, and prior tests were preserved. Lab 4's requested controller changes apply beyond the earlier reservation-implementation constraints; the original Lab 3 specification remains unchanged.

## 1. Design review findings

Commit references in the quoted review have been updated to the equivalent rewritten history; its findings and other wording are unchanged.

<details>
<summary>Initial Step A design review output</summary>

# Lab 4 initial design review

Baseline: the completed Lab 3 application at `67c05c1`. Review scope: the `src/` production classes, resources, existing DTOs, and test contracts. This review is recorded before the Lab 4 implementation. Severity uses the assignment's design categories: CRITICAL means a design-principle violation, not a claim of a production incident.

## 1. Separation of concerns - CRITICAL

`BookController` combines HTTP routing with entity construction, persistence, default-field mapping, transaction management, and the rule preventing an actively borrowed book from becoming available. Its `update` method both takes a database lock and enforces that business invariant. `MemberController.create` similarly creates and saves an entity while choosing membership-date and active-state defaults.

These responsibilities reduce controller cohesion: a change in persistence or library policy changes the HTTP layer. Extract `BookService` and `MemberService`, preserve all current defaults and conflicts, and place transactional operations and policy decisions there. Controllers should bind and validate DTOs, delegate, map results, and choose HTTP statuses/headers. The existing `BorrowingController` and `ReservationController` already delegate business behavior to services; they do not require a fictitious business-logic cleanup.

## 2. Layer coupling - IMPORTANT

`BookController` injects `BookRepository` and `BorrowingRepository`; `MemberController` injects `MemberRepository`. Both bypass the service layer and directly depend on Spring Data operations. This is the dependency aspect of the separation-of-concerns issue above, not an additional runtime defect.

Move those repository dependencies behind the new services. Preserve the shared book-row lock for availability-changing updates, active-loan protection, sorted lists, missing-record details, and foreign-key conflicts. Borrowing and reservation controllers already depend on services, while all repositories already use `JpaRepository`; no missing Repository pattern should be claimed for them.

## 3. API contract - CRITICAL

Book and member endpoints return `Book`, `List<Book>`, `ResponseEntity<Book>`, `Member`, and equivalent entity lists/responses directly. Although their inputs already use request DTO records, their public response contract is still tied to the JPA classes.

This weakens abstraction between persistence and transport: adding a persisted field or relationship can inadvertently alter JSON or introduce serialization concerns. The current Book and Member entities are scalar-only, so this review does not claim an existing recursive-JSON failure or exposed secret. Introduce the required DTOs and mappers, keep the established JSON fields, and remove entity types from controller method signatures. Borrowing and reservation responses already use flat DTOs with related-resource IDs; retain those benefits while applying the lab's naming/mapping requirements.

## 4. Error handling - IMPORTANT

`GlobalExceptionHandler` already exists as `@RestControllerAdvice`. It correctly maps `ResourceNotFoundException` to 404, `IllegalStateException` to 409, validation and malformed inputs to 400, and integrity conflicts to 409. Existing tests verify these behaviors. A service `IllegalStateException` therefore already produces a controlled 409 response; the missing-handler scenario does not apply.

The remaining Lab 4 gaps are the required consistent `error` and `timestamp` fields, explicit JPA `EntityNotFoundException` or `NoSuchElementException` handling, and a controlled generic 500 response. Add the new fields while preserving existing ProblemDetail `status`, `detail`, and validation `errors` compatibility. Unexpected 500 responses must use a stable public message, rather than exposing exception internals. Preserve Spring's normal statuses and headers for framework failures such as unsupported methods or content types; a broad fallback must not convert those into 500 errors.

## 5. OpenAPI documentation - IMPORTANT

SpringDoc is installed and the generated description already exposes the fourteen operations. However, controllers have no `@Tag`, `@Operation`, or explicit `@ApiResponse` annotations, and DTO fields lack the required `@Schema` examples. Generated availability is not equivalent to the documented success/error contracts requested by this lab.

Document every existing route and its applicable responses, including validation, missing-resource, conflict, and server-error cases. Describe arrays, flat IDs, read-only/generated fields, dates, defaults, and cancellation/history semantics accurately. Keep `/swagger-ui.html` and configure the required JSON path `/api-docs`; the baseline currently uses `/v3/api-docs`.

## Scope and verification implications

The two principal design problems are mixed controller responsibilities and entity exposure in the API. Service extraction reduces coupling and improves cohesion; DTOs provide a transport abstraction independent of persistence shape. These changes should preserve business behavior and the existing tests, rather than introduce new membership, reservation-allocation, or borrowing policies.

The Lab 4 controller/DTO work supersedes the earlier Lab 3 prohibition on editing existing controllers for reservation implementation. The frozen reservation specification remains the historical requirements artifact; its lifecycle and acceptance behavior still apply.

Docker is a separate required deployment deliverable, not an invented defect in the existing source design. Add the specified multi-stage Java 17 image, `.dockerignore`, port 8080, and JVM heap options, then record an actual image build and application check. Code inspection alone cannot satisfy the container-start criterion.

The existing tests provide substantial behavioral coverage, but not the new DTO ownership/mapper, complete error-envelope, explicit OpenAPI-schema, or container-execution evidence. Add checks targeted at those changes and run the regression suite. No cosmetic issue is elevated to CRITICAL, and no already-implemented service, DTO, exception handler, or locking behavior is reported as absent.

</details>

### Two principal design problems

**Mixed controller responsibilities reduced cohesion.** At the baseline, `BookController.update` handled HTTP binding, acquired a database lock, checked the active-borrowing rule, changed entity fields, and flushed persistence changes. A change in library policy therefore required editing the HTTP component, and another caller could not reuse that operation without depending on a controller. [BookService](../src/main/java/com/library/api/service/BookService.java) now owns the transaction and availability invariant. [BookController](../src/main/java/com/library/api/controller/BookController.java) handles the transport boundary. This separates concerns and concentrates related business work in one reusable operation.

**Entity exposure coupled the API to persistence representation.** Book and member responses used JPA classes as their public return types. The new [BookDTO](../src/main/java/com/library/api/dto/BookDTO.java) and [MemberDTO](../src/main/java/com/library/api/dto/MemberDTO.java) define explicit transport abstractions. Controllers no longer depend directly on repositories or declare entity return types. The DTO field lists and mapper methods now determine the API shape, reducing the effect of a persistence-model change on HTTP consumers.

These are design findings, not claims that the old application failed to execute. The baseline's 90 tests passed. Borrowing and reservation already had services and flat response DTOs, so those existing strengths were retained rather than reported as missing.

## 2. DTO decision and implementation

Returning a JPA object makes its serializable properties part of the API implicitly. Persistence annotations do not by themselves define a stable external contract, and future relationships can introduce nested graphs or lazy-loading concerns. The baseline Book and Member classes were scalar-only; there was no demonstrated recursive-response defect or secret-field leak.

A concrete maintainability improvement is `GET /books/{id}`: it now returns the seven declared `BookDTO` fields through [BookMapper](../src/main/java/com/library/api/mapper/BookMapper.java), instead of returning `Book`. A future persistence-only field on `Book` would require a deliberate DTO and mapping change before appearing in that response. This separates API versioning decisions from database-model evolution. The catalogue contract tests assert the exact public field set, preserving the present JSON while making an accidental exposure detectable.

| Area | Resulting design and preserved behavior |
| --- | --- |
| Books | `BookDTO` is the requested shared request/response record. `BookMapper.toDTO(Book)` and `toEntity(BookDTO)` provide both directions; `updateEntity` copies editable fields without replacing identity. `BookService` owns repository access, default availability, transactions, and the active-loan guard. |
| Members | `MemberDTO`, [MemberMapper](../src/main/java/com/library/api/mapper/MemberMapper.java), and [MemberService](../src/main/java/com/library/api/service/MemberService.java) apply the same separation. Missing membership dates use the injected clock's local date; omitted flags remain true and explicit false remains false. |
| Borrowings | The existing narrow `BorrowingRequest(bookId, memberId)` remains the creation contract. [BorrowingDTO](../src/main/java/com/library/api/dto/BorrowingDTO.java) replaces the old response record, and [BorrowingMapper](../src/main/java/com/library/api/mapper/BorrowingMapper.java) owns conversion. Dates, fines, and persistence remain controlled by the unchanged borrowing service. |
| Reservations | Existing request/response DTOs remain flat and receive field examples and API documentation. Reservation lifecycle and contention behavior are unchanged. |

Generated book/member IDs are read-only for JSON input. Creation services also clear mapped IDs, so a direct service caller cannot turn a create operation into an update of an existing record. PUT uses the path ID. These protections preserve the previous request DTOs' exclusion of resource identity; they are not presented as repairing an identity-injection vulnerability demonstrated in Lab 3.

Borrowing's reverse mapper takes `BorrowingDTO`, an already-resolved `Book`, and an already-resolved `Member`. It checks the related IDs and reuses those instances. This adapts the handout's simple Book mapper to an entity with relationships: the mapper neither queries repositories nor invents detached relationship objects. The public borrow endpoint still accepts only the narrow request DTO and resolves its records through the service. Internal reverse mapping is not a second client-controlled borrowing workflow.

Catalogue GET/POST/PUT response fields, sorted lists, Location headers, validation, unique ISBN/email conflicts, and bodyless deletion remain intact. Book updates retain the shared row lock and active-loan protection; foreign keys still prevent deletion of referenced books.

## 3. Pattern identification

| Pattern | Concrete implementation | Design problem solved |
| --- | --- | --- |
| Repository | [BookRepository](../src/main/java/com/library/api/repository/BookRepository.java), extending `JpaRepository<Book, Long>`, including `findByIdForUpdate` | Provides a collection-like persistence abstraction and a named locking operation. Services do not embed SQL or manage an EntityManager for ordinary catalogue access. Controllers now reach persistence through services. |
| Service Layer acting as an application facade | [BorrowingService](../src/main/java/com/library/api/service/BorrowingService.java), and the new `BookService`/`MemberService` | Presents use-case operations such as `borrowBook`, `returnBook`, and `update`, coordinating repositories, state rules, time, and transaction boundaries behind a smaller interface. HTTP controllers do not need to orchestrate those collaborators. |

The Repository pattern already existed; the change removes controller bypasses around the service layer. The service facade is an application-level use of the Facade idea, not a claim that a separate GoF facade hierarchy was added. DTOs additionally provide the transport-boundary pattern requested by the lab. No unused Factory or Strategy abstraction was introduced solely to increase the pattern count.

Chapter 4 connects low coupling with making components easier to understand and change, and high cohesion with grouping related responsibilities. Those ideas explain the service extraction and explicit DTO boundary more directly than a count of classes or annotations. The lecture also distinguishes patterns by the design problem they solve; naming a pattern without that connection would not justify it.

## 4. Error handling and OpenAPI contracts

[GlobalExceptionHandler](../src/main/java/com/library/api/exception/GlobalExceptionHandler.java) remains a single `@RestControllerAdvice`, which includes the behavior of `@ControllerAdvice`. It now extends `ResponseEntityExceptionHandler` and returns [ApiError](../src/main/java/com/library/api/dto/ApiError.java), a ProblemDetail subclass. Every mapped error includes `error` and an ISO UTC `timestamp`, while retaining `detail`, `status`, and standard problem fields. Validation adds field messages in `errors`. This is an additive interpretation of the lab's error envelope that preserves the existing API contract. Spring supports both ProblemDetail extensions and centralized MVC exception handling. [Spring Framework 6.2 error responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html).

| Failure | Response |
| --- | --- |
| Domain `IllegalStateException` | 409 with the existing domain message. |
| `ResourceNotFoundException`, JPA `EntityNotFoundException`, or `NoSuchElementException` | 404 with a missing-resource message. |
| Invalid fields or malformed input | 400; validation failures retain the field-error map. |
| Uniqueness or foreign-key integrity conflict | 409 with the existing stable conflict message. |
| Unexpected exception | 500 with `An unexpected error occurred.`; diagnostic details are logged server-side. |
| Framework routing/media failures | Preserve 404, 405, 406, and 415 as applicable, including the 405 Allow header. |

[GlobalExceptionHandlerTest](../src/test/java/com/library/api/exception/GlobalExceptionHandlerTest.java) exercises the two requested real borrowing scenarios: a nonexistent book returns 404, and a second borrowing of the same book returns 409. Its ten cases also cover validation, standard missing-resource exceptions, controlled 500 output, and framework responses. Throwing test endpoints are isolated test components and do not enter the packaged application.

All four controllers now have tags; every operation has a summary and explicit applicable success/error responses. DTO fields have descriptions and examples, with read-only metadata for server-controlled fields. Swagger UI remains at `/swagger-ui.html`; its configured document is now `/api-docs`, as required. [SpringDoc v2 documentation](https://springdoc.org/v2/).

[OpenApiDocumentationTest](../src/test/java/com/library/api/controller/OpenApiDocumentationTest.java) checks the generated document rather than just searching Java annotations. It verifies all fourteen operations, DTO references, list schemas, positive identifier bounds, field examples, shared errors, bodyless 204, and Swagger configuration. One real mismatch appeared during this check: the inherited `ApiError.properties` storage map was advertised as a public schema field. Marking that accessor `@Schema(hidden = true)` removed the incorrect documentation while preserving the inherited runtime behavior. The unchanged failing assertion then passed in the full build.

## 5. Deployment reflection

### Multi-stage build

The [Dockerfile](../Dockerfile) compiles with `maven:3.9-eclipse-temurin-17` and `mvn -B -ntp package -DskipTests`, then copies only the executable JAR into `eclipse-temurin:17-jre-alpine`. BuildKit caches Maven downloads between builds. Maven, test sources, and compiler tooling are excluded from the runtime stage. This reduces the runtime artifact's contents and separates build requirements from execution requirements; the host does not supply the JAR. [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/).

The final process runs as the non-root `library` user, exposes 8080, and uses exactly `-Xmx256m -Xms128m`. The [.dockerignore](../.dockerignore) allowlist excludes Git metadata, reports, local notes, and host build output. Tests run separately before the image build because the requested container build skips their execution.

The required Java 17 Alpine image was available for `amd64` at verification time. Therefore the image was built and run with `--platform linux/amd64` on the Apple Silicon host, using emulation for the runtime. The Maven stage uses the builder's native platform and generates a Java 17 JAR. This follows the actual published platform rather than silently substituting a different runtime image. [Official Temurin image manifest](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin).

The image was exercised through a local Colima Docker engine. Docker was initially absent; the CLI, Buildx, Colima, and its Lima dependency were installed for this verification. A separate `library-lab4` profile provided the engine without replacing the active Docker context. The application container was stopped and removed after verification, and the local profile was shut down. The built image remains available for reuse.

### Moving from H2 to PostgreSQL

A PostgreSQL deployment would require the following changes; they are a deployment analysis, not changes implemented in this lab:

1. Add the PostgreSQL JDBC driver to the runtime dependencies and provide a reachable database, database name, and credentials through deployment configuration. A connection URL could be `jdbc:postgresql://db:5432/library`, where `db` is the database service's network name. [pgJDBC connection configuration](https://jdbc.postgresql.org/documentation/use/).
2. Replace the current H2 URL through `SPRING_DATASOURCE_URL`, with the corresponding username/password configuration. Inside a container, `localhost` identifies that container, so a separate database needs its own reachable address. Credentials belong in environment/secret configuration rather than the image or repository.
3. Replace `create-drop` with a controlled schema lifecycle. Provision the schema through one migration mechanism and use `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` for startup validation. Set `SPRING_SQL_INIT_MODE=never` so the lab's seed script is not replayed into the operational database. [Spring Boot 3.5 database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html).
4. Provide persistent storage and database backup/restore, and verify the schema, constraints, date/decimal handling, and transaction behavior against PostgreSQL. The contention tests inspect H2 session metadata, so their observation mechanism needs a PostgreSQL-specific adaptation; successful H2 tests alone do not establish PostgreSQL behavior.

The present container retains in-memory H2 and loses its data on application restart. Its heap options bound the Java heap, not the complete container's memory usage. No PostgreSQL deployment or performance benchmark is claimed.

## Verification results

Checks ran on 11 November 2026. Local verification used Java 17 and Maven 3.10.0; the container was built with the required Maven/Temurin 17 image and ran Temurin 17.0.21.

| Check | Observed result |
| --- | --- |
| Baseline `mvn -B -ntp test` | 90 tests passed before source changes. |
| Existing smoke/API tests plus expanded error tests | 59 tests passed. |
| Catalogue, mapper, and generated OpenAPI checks | 29 tests executed; one OpenAPI assertion exposed the inherited storage-map documentation issue. |
| Final `mvn -B -ntp clean verify` | 129 tests passed, zero failures, errors, or skips; executable JAR built. |
| Docker build from source | `docker build --platform linux/amd64 -t library-api:lab4 .` completed successfully. |
| Actual container HTTP exercise | 60 checks passed, covering catalogue operations, required 404/409 borrowing errors, returns, reservation history, invalid input, and the API/UI routes. |
| Rendered Swagger UI | Isolated headless Chrome loaded `/swagger-ui.html`, rendered all fourteen operations under four tags, and used `/api-docs`. The rendered page was also visually inspected. |
| Runtime image inspection | Non-root UID 100; `/app` contains `app.jar`; Maven and workspace source directories are absent; port and JVM options match the Dockerfile. |
| Cleanup | Application container stopped and removed; temporary browser profile removed; local Colima profile stopped. |

The first container readiness probe encountered `RemoteDisconnected`: the connection closed without a response while the application was still starting. That probe was corrected to retry transient startup connection failures within its existing deadline; the subsequent complete HTTP and browser checks passed. This was a verification-harness issue, and no application change or image rebuild was needed.

The 39 added test executions comprise nine catalogue contract cases, two mapper unit cases, ten exception cases, and eighteen OpenAPI checks. All 90 earlier cases remain intact. The complete suite contains six isolated unit cases and 123 integration cases; it is not a coverage-percentage claim.

Locally verified image: `library-api:lab4`, `linux/amd64`, ID `sha256:f7f37a5530dce0316f87e258ecdc121a83fc7920e7d35849b00a7d127ceeab35`. Docker reported a local image size of 120,896,406 bytes. The verification source hashes matched the build inputs; raw command logs and browser captures remain local.

**Course references:** *SE Lab Assignment 2026–2027 Codex*, Lab 4, PDF pp. 14–17; *Chapter 4: Design, Implementation and Deployment*, PDF pp. 32–34 (design documentation and collaboration), pp. 44–51 (patterns, coupling and cohesion), pp. 95–97 (error handling and boundary protection), and pp. 101–104 (containers and deployment). Page references use PDF page numbers rather than the slides' printed counters.
