# Lab 1 Introduction to AI Assisted Engineering

- **Author:** Bilal Kameh
- **Date:** 10 November 2026
- **Repository:** [Bilalkameh/library-api](https://github.com/Bilalkameh/library-api)
- **Branch:** `main`
- **Development tool:** Codex CLI 0.167.0, signed in with ChatGPT
- **Additional support:** ChatGPT for step-by-step guidance and review discussion

This report documents the initial Library Management API scaffold and the process used to specify, review, implement, and validate it. The implementation contains the Book, Member, and Borrowing entities, three repositories, the eight required book/member operations, H2 seed data, and Swagger UI. Borrowing operations remain outside the Lab 1 scope.

## Environment

| Component | Version or configuration |
| --- | --- |
| Java used for execution | Eclipse Temurin 21.0.8 |
| Java compilation target | Java 17, configured through compiler release 17 |
| Maven | 3.10.0 |
| Spring Boot | 3.5.16 |
| SpringDoc | 2.10.0 |
| Database | H2 in memory |
| Maven coordinates | `com.library:library-api:0.0.1-SNAPSHOT` |
| Base package | `com.library.api` |

## Required reflections

### 1 Observation

Codex chose `GenerationType.IDENTITY` for the `Long` IDs, which I accepted because H2 generates identifiers and the seed inserts can omit IDs while later inserts continue the identity sequence. It introduced request DTO records with nullable `Boolean` fields, which I accepted because the controllers can preserve an explicit `false`, default an omitted flag to `true`, and keep generated IDs outside the request contract. It configured `spring.jpa.defer-datasource-initialization=true`, which I accepted because Hibernate must create the tables before `data.sql` inserts the sample records; the seeded GET responses and H2 console provided evidence that initialization worked.

### 2 Engineering judgment

I reviewed the proposed versions, Java compilation target, and verification plan with guidance from ChatGPT before approving implementation. The initial plan proposed `mvn clean verify` but contained no test classes, so I requested two automated MockMvc smoke tests for the five seeded books and three seeded members. I also requested the course-aligned package `com.library.api` and verification of the initial counts before requests that modify data. Accepting the first plan without review could have produced a successful build without automated test evidence and confused the initial seed counts with counts after creating records.

### 3 Chapter connection

Programming-in-the-small concerns individual algorithms and classes, while programming-in-the-large includes requirements, architecture, integration, testing, and the software lifecycle. Codex supported both levels by generating the scaffold and working across the repository to build, run, and check it. My work involved defining the scope, reviewing assumptions, requesting changes to the plan, and checking the resulting evidence. AI assistance accelerated construction, while engineering judgment remained necessary to interpret requirements and evaluate correctness.

### 4 Role reflection

The junior-engineer row "Prompt AI to scaffold a feature, then read and understand the generated code" best describes the activities I performed. I supplied the requirements, reviewed the plan, requested refinements, and asked Codex to explain the responsibilities and request flow of the generated components. I also ran the tests myself and exercised the API through the browser and Swagger UI. This lab therefore combined AI-supported implementation with supervised review and independent execution checks.

## Main interactions and outputs

1. I asked Codex to inspect the repository and propose a plan before modifying files. The initial request specified the entities, dependencies, endpoints, database configuration, sample records, and verification requirements.
2. I reviewed the plan with ChatGPT guidance, checked the proposed version compatibility, and sent an approval containing the package, automated-test, and seed-verification refinements.
3. Codex implemented the scaffold, updated the README, and reported build, runtime, HTTP, and packaged-JAR checks. It left all changes uncommitted for review.
4. I supplied the source archive for review and independently ran `mvn test` and browser checks. I then requested a read-only architecture explanation covering the layers, GET/POST flow, and main design decisions.

The main outputs were `pom.xml`, the application and persistence classes, controllers, request DTOs, exception handling, `application.properties`, `data.sql`, `LibraryApiSmokeTest.java`, and the updated README.

### Selected instruction excerpts

> Stop after presenting the plan and wait for my approval before implementing it.

> Add spring-boot-starter-test with test scope.

> Verify the initial database counts before performing requests that create, update, or delete records.

These excerpts illustrate scope control, a review checkpoint, and refinements made before implementation.

## Execution and verification

| Check | Evidence and result | Performed by |
| --- | --- | --- |
| `mvn test` | 2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS | Me, with terminal output supplied |
| GET `/books` | Five initial book records displayed as JSON | Me, in the browser |
| GET `/members` | Three initial member records displayed as JSON | Me, in the browser |
| Swagger overview | All eight operations displayed across two controllers | Me, in the browser |
| POST `/books` | HTTP 201; generated ID 6; `Location: /books/6` | Me, through Swagger UI |
| H2 console | Login succeeded; BOOKS, MEMBERS, and BORROWINGS tables visible | Me, in the browser |
| `mvn clean verify` | BUILD SUCCESS | Codex, as reported in its implementation summary |
| Bytecode inspection | Class version 61 reported for the application entry point | Codex, as reported in its implementation summary |
| Packaged JAR execution | Startup and restoration of initial counts reported | Codex, as reported in its implementation summary |

The two automated tests use Spring Boot and MockMvc with the separate `libraryapitest` in-memory database. They assert HTTP 200 and the expected list lengths for the two seeded GET endpoints. This is baseline smoke coverage; it does not constitute automated coverage of every CRUD or error scenario. The application was executed on Java 21; execution on an actual Java 17 runtime was not performed.

The POST screenshot documents a change after the initial seed checks. The created book receives ID 6, so the live book count increases after that request unless the record is deleted or the application is restarted.

## Problems and resolutions

- **Incomplete Codex installation:** The first npm attempt ran for several hours on a slow connection and ended with a missing Windows executable dependency despite npm returning exit code 0. After reinstalling on a better connection with optional dependencies included, `codex --version` returned 0.167.0 and login succeeded.
- **Maven command lookup:** Initially, Windows did not recognize `mvn`. After the installation/PATH setup was resolved, `mvn -version` reported Maven 3.10.0 using the installed JDK 21.
- **Verification gap in the initial plan:** The initial file list included no tests. Two automated smoke tests were added after review, and their successful execution was independently confirmed.

## Evidence index

| File | What it demonstrates |
| --- | --- |
| [books-seed.png](evidence/lab1/books-seed.png) | Five seeded books in the browser |
| [members-seed.png](evidence/lab1/members-seed.png) | Three seeded members in the browser |
| [swagger-overview.png](evidence/lab1/swagger-overview.png) | Eight documented operations |
| [create-book-201.png](evidence/lab1/create-book-201.png) | Successful POST with HTTP 201 and generated ID |
| [h2-console.png](evidence/lab1/h2-console.png) | Console access and the three application tables |
| [swagger-get-books-200.png](evidence/lab1/swagger-get-books-200.png) | GET `/books` returning HTTP 200 through Swagger |
| [test-result.txt](evidence/lab1/test-result.txt) | An excerpt of the independently executed Maven test output |

**Course references:** Reference Lab assignment, Lab 1; Chapter 1, the programming-in-the-small/programming-in-the-large distinction and the "A Day in the Life in the AI Era" table.
