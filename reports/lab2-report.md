# Lab 2 - Agile borrowing sprint

## Sprint results and estimates

The sprint implements all four borrowing stories. The original estimates from the test-first checkpoint are retained below; they cover story implementation and testing, excluding environment setup, report preparation, and the final review.

| Story | Initial estimate (h) | Actual (h) | Actual / estimate | Result |
| --- | ---: | ---: | ---: | --- |
| US-01 - Record a borrowing | 0.75 | 1.00 | 1.3333 | DONE: saved borrowing, unavailable book, current local date, 14-day due date, and validated API request. |
| US-02 - Record a return | 0.50 | 0.75 | 1.5000 | DONE: saved return date and fine, restored availability, and duplicate-return protection. |
| US-03 - List active borrowings | 0.50 | 0.50 | 1.0000 | DONE: only the requested member's unreturned records, ordered by ID. |
| US-04 - Calculate an overdue fine | 0.25 | 0.25 | 1.0000 | DONE: exact decimal amounts, EUR 0.50 per overdue day, with a zero minimum. |
| **Total** | **2.00** | **2.50** | **1.2500** | **Four completed stories** |

Actual hours are reported active human effort, recorded retrospectively at completion, with unattended execution time excluded. Story implementation and testing total **2.50 hours** against the original **2.00-hour** estimate, so the assignment's actual/estimated ratio is **1.25**: reported effort exceeded the estimate by 25%. The reciprocal estimate-to-actual multiplier is **0.80**, which describes this comparison rather than an independently measured AI speedup. A separate **0.25 hour** for setup, review, and reporting gives **2.75 hours** for the whole lab; that overhead is outside both story totals. The completed-story count is four and remains distinct from either effort ratio or story-point velocity.

The detailed acceptance criteria and completion status are in [the sprint backlog](../docs/sprint-backlog.md).

## Test-first observation

The unchanged Lab 1 application passed its two smoke tests. Adding `BorrowingServiceTest` then caused Maven to fail at `testCompile` because `BorrowingService` did not exist. This was compilation-stage RED: no test methods or assertions executed. Commit `cdb4624` contains that first test and precedes the production implementation in Git history.

Writing the test first established the public `borrowBook(Long, Long)` contract and the observable outcome: a persisted borrowing and an unavailable book with a due date fourteen days after the borrow date. Flushing and clearing the persistence context made the first acceptance test check database state rather than only a modified Java object. The fine, return, and query tests likewise preceded their respective methods. The implementation order was US-01, US-04, US-02, US-03 because returns depend on fine calculation.

| Stage | Observed RED | Observed GREEN |
| --- | --- | --- |
| US-01 | Missing `BorrowingService`; test compilation failed. | 13 tests passed, including the original smoke tests and the borrowing service/API cases. |
| US-04 | Missing `calculateFine`; test compilation failed. | 4 pure unit-test cases passed: early 0.00, on due date 0.00, one day 0.50, seven days 3.50. |
| US-02 | Missing `returnBook`; test compilation failed. | The complete suite passed 25 tests after returns were implemented. |
| US-03 | Missing `getActiveBorrowings`; test compilation failed. | The complete suite passed 30 tests after the query was implemented. |
| Review: fractional identifiers | Both executed test cases failed: expected HTTP 400, received 201. | After the correction, all 19 borrowing-controller and return-service cases passed. |
| Review: competing transactions | Added coverage for existing locking behavior; no artificial failure introduced. | Both contention tests passed. |
| Final verification | All corrections included. | `mvn -B -ntp clean verify` passed all 34 tests and built the executable JAR. |

The fractional-ID result was an assertion failure, unlike the earlier compilation failures. It exposed a real input bug: Jackson converted a fractional JSON number into a `Long` before `@Positive` validation, allowing a request to act on the truncated identifier. The correction disables `ACCEPT_FLOAT_AS_INT` through application configuration. The regression tests verify HTTP 400 and unchanged borrowing/book state for both identifier fields.

## Review output

The following is the review captured before its recommendations were applied. Its line references and status statements describe that earlier snapshot; the decisions and final results follow it.

<details>
<summary>Full implementation review</summary>

# Lab 2 implementation review

Date: 11 November 2026. Scope: borrowing and return services, borrowing HTTP endpoints, book-availability updates, repositories, request/response DTOs, exception handling, and the current tests. This review used source inspection and existing execution evidence; it did not run another Maven build.

## Recommended changes

### R1 - Reject fractional identifiers before they can select a different record (P2)

`BorrowingRequest.java:7-8` declares positive `Long` identifiers. Bean validation runs after JSON deserialization, so it cannot detect a fractional input that has already been converted into an integer. No Jackson coercion override was present at review time. Inspection of the installed Jackson Databind 2.21.4 `DeserializationFeature` bytecode confirmed that `ACCEPT_FLOAT_AS_INT` defaults to true.

A request such as `{"bookId":6.9,"memberId":4}` must not silently target book 6. The same issue applies to `memberId`. The existing missing/nonpositive/malformed-body tests do not cover this case.

Recommendation: add separate HTTP tests for fractional book and member identifiers using valid fixtures, require HTTP 400, and verify that no borrowing or availability change is persisted. Capture the observed failing result before disabling float-to-integer deserialization. Keep ordinary integer identifiers working. A broader decision about accepting quoted integer strings is separate; no incompatible string-coercion policy is required to fix truncation.

Status at review: configuration concern verified; endpoint reproduction and correction still require execution evidence.

### R2 - Exercise overlapping transactions before claiming concurrency protection (P2)

`BorrowingService.java:33-68`, `BookRepository.java:12-14`, and `BorrowingRepository.java:17-19` use transactions and pessimistic write locks. The book update path also takes the book lock before checking active loans. This is a reasonable implementation for the current operations.

However, `BorrowingControllerTest.java:98-109` performs two borrowing requests sequentially. Repeated-return tests are also sequential. No current test starts competing transactions, so the suite verifies duplicate rejection but does not demonstrate behavior under contention.

Recommendation: add bounded integration tests for two attempts to borrow the same available book and two attempts to return the same loan. Each worker must invoke the proxied service in its own transaction. Arrange actual overlap by holding a transaction's lock until a contender has attempted the competing operation; a common start signal alone may still permit sequential execution. Require exactly one success, one domain conflict, a single borrowing record, correct final availability, and an unchanged first return date/fine. Release workers and shut down executors in `finally`, including on assertion failure.

Report the result as evidence for the tested H2 configuration, not a proof covering every database or interleaving. Do not manufacture a production defect or require an artificial RED run for a test that correctly passes the existing locks.

### R3 - Assert the fixed date in the borrowing HTTP success test (P2)

`BorrowingControllerTest.java:78-94` imports a fixed clock but only checks that the returned due date is fourteen days after the returned borrow date. It does not assert that the borrow date is the known local date. The original service acceptance test intentionally uses bounds around the machine's current date.

Consequently, replacing `LocalDate.now(clock)` with `LocalDate.now()` inside borrowing could pass both borrowing tests. Return-date tests exercise a different method and cannot establish this contract for borrowing.

Recommendation: assert the HTTP borrow date equals `2026-11-11` and the due date equals `2026-11-25`, then assert those values through a fresh borrowing repository read. The fixed instant is `2026-11-10T22:30:00Z` in `Asia/Beirut`, making the expected library date different from the UTC date and providing a useful timezone boundary.

## Findings that do not require immediate changes

- Keep `BookController`'s existing PUT replacement semantics: omitting `available` means true. Reject that request, or explicit true, while an active loan exists; permit a metadata update with explicit false. The implementation and HTTP test already cover this invariant. Do not silently turn PUT into a partial update.
- Inactive members are representable, but the Lab 2 story does not define inactive-member borrowing policy. Adding a rejection now would introduce an unagreed business rule.
- Mapping every `IllegalStateException` to 409 is broader than the known domain conflicts. During the planned custom-exception work, introduce a narrow conflict exception that can extend `IllegalStateException` to preserve the handout contract. No demonstrated internal exception is currently being misclassified, so this is a design refinement rather than an observed failure.
- Null fine inputs and direct null service IDs have no documented contract. HTTP request validation already protects the borrowing body. Do not invent new public behavior solely to increase test counts.
- Lock timeouts, transient database failures, and portability across production databases need explicit policy and environment-specific tests when deployment requirements are introduced. A global retry loop or database-specific uniqueness mechanism is premature here.
- Pagination and indexes for large loan histories are reasonable later performance work. No performance measurement currently justifies adding them to this lab.

## Test quality and XP assessment

The fine calculation tests are pure unit tests with exact decimal expectations and no repository or clock interactions. The service and HTTP tests are integration tests using real persistence; describing all of them as unit tests would be inaccurate. Rejection and return cases read state after service transactions complete, while query fixtures are flushed and cleared before reads. Tests cover missing resources, no-active-loan results, returned/other-member filtering, monetary boundaries, repeated returns, and persisted availability.

The implementation keeps service methods small, uses constructor injection, isolates time through `Clock`, and returns flat borrowing DTOs rather than serializing lazy relationships. Query ordering is explicit. Existing tests include real behavior and persistence checks rather than asserting private implementation details.

The original compilation-stage RED checkpoint precedes production behavior in Git history. Later story checks also recorded missing-method compilation failures before implementation. These demonstrate test-first sequence, but a compilation failure must not be reported as an executed assertion failure. The review recommendations above arise from the implemented behavior and observed test coverage; adding an arbitrary refactoring or deliberate defect is unnecessary.

</details>

## Applied and deferred review decisions

| Recommendation | Decision and observed result |
| --- | --- |
| R1 - Reject fractional identifiers | Applied. Two HTTP regression tests first reproduced 201 responses for fractional IDs. Disabling float-to-integer coercion changed both to 400; normal integer requests still pass. |
| R2 - Test real transaction contention | Applied. Two independent transactions compete to borrow the same book or return the same loan. H2 session metadata confirms that the contender waits on the first transaction's `SELECT ... FOR UPDATE` lock before commit is released. Each case produces one success and one domain conflict, with fresh reads confirming the final borrowing and book state. |
| R3 - Assert exact dates | Applied and strengthened. The fixed clock uses `2024-02-28T22:30:00Z` in `Asia/Beirut`, giving local date 2024-02-29 and due date 2024-03-14. Both response fields and saved dates are checked. Using a past leap day avoids the earlier fixture's coincidence with the execution date. |
| Earlier design alternative: change PUT to preserve omitted availability | Rejected before the final review, which confirmed the existing replacement contract. Omitted `available` defaults to true. Requests that would reopen an active loan return 409; explicit false permits metadata edits. |
| Reject inactive members | Deferred. Lab 2 does not specify that borrowing policy; introducing it would add a business rule beyond the stated stories. |
| Narrow conflict exceptions, database retries, pagination, and indexes | Deferred. These are design, deployment, or measured-performance decisions. No current acceptance failure justifies adding them to this sprint. |

The review improved correctness and the strength of the evidence. Input validation originally looked adequate because the request fields had validation annotations, but the failing fractional-ID tests showed why deserialization must also be checked. Similarly, sequential duplicate requests did not establish behavior under contention; the added tests explicitly observe the blocking relationship between two database sessions. The date assertions now check the known calendar date instead of only checking that two returned values are mutually consistent.

The workflow follows the XP test-first loop and incremental delivery: each story gained a test before its production method, relevant tests ran after each change, and the full regression suite passed before the final build. Fine calculation remains a small pure function. Transaction boundaries keep borrowing and book updates atomic, constructor injection makes dependencies explicit, and a clock isolates time-dependent behavior. Flat response DTOs keep lazy JPA relationships out of borrowing JSON.

## Verification details

Verification ran on 11 November 2026 using Java 17.0.21.0.3, Maven 3.10.0, Spring Boot 3.5.16, and H2 2.3.232.

| Test class | Cases | Purpose |
| --- | ---: | --- |
| `LibraryApiSmokeTest` | 2 | Preserve the original five-book and three-member seed responses. |
| `BorrowingServiceTest` | 4 | Persisted borrowing state and rejection of missing or unavailable resources. |
| `FineCalculationTest` | 4 | Pure unit tests for exact decimal fine boundaries, without repository or clock interactions. |
| `ReturnServiceTest` | 5 | Early/exact-due/overdue returns, missing borrowing, and unchanged repeated returns. |
| `ActiveBorrowingServiceTest` | 3 | Member/status filtering, ordering, empty results, and a missing member. |
| `BorrowingControllerTest` | 14 | HTTP contracts, validation, serialization, dates, availability, returns, and active lists. |
| `BorrowingConcurrencyTest` | 2 | Actual row-lock contention for borrowing and returning. |
| **Total** | **34** | **0 failures, 0 errors, 0 skipped** |

The four fine cases are unit tests; the remaining thirty cases are integration tests using Spring and real persistence. Test databases are separate from the running application and from each other. Tests either roll back their fixtures or remove them after assertions on committed state.

The executable JAR was also started on an ephemeral loopback port and passed 24 HTTP checks. The run verified initial seed counts, book/member creation, borrowing and return transitions, active-list filtering, duplicate operations, book-edit protection, fractional/missing/nonpositive identifiers, missing resources, the OpenAPI document, and Swagger UI. All expected statuses and response checks passed. The verification script asserted that the OpenAPI document exposed eleven operations, and the application process was stopped after verification. These checks are separate from the 34 JUnit cases; the Swagger check follows HTTP redirects.

The concurrency evidence covers the tested H2 configuration and controlled interleavings. It is not a guarantee for every database or schedule; cross-database contention, timeout policy, and exhaustive deadlock testing are outside this run. Coverage percentages have not been measured in this lab, so no coverage target is claimed.

## AI assistance and the next sprint

The reported retrospective effort supports the estimate comparison above. Without an equivalent unassisted baseline, it does not establish that AI assistance made this sprint faster or slower. The concrete outcome is a working four-story feature with repeatable tests, including a defect discovered and corrected during review. Successful code generation alone would not have detected the fractional-ID behavior or established the concurrency claims; focused tests and review were still necessary.

For Sprint 2, the expected improvement is lower environment and framework setup overhead because the build, persistence configuration, fixtures, and error-handling conventions already exist. Reservation requirements and acceptance criteria introduce separate specification work, so lower setup overhead does not guarantee a lower total effort ratio. That is a qualitative prediction rather than a measured productivity result.

## Course references

- *SE Lab Assignment 2026-2027 Codex*, PDF pp. 7-10: story estimates, test-first implementation, review, and sprint reporting.
- *Chapter 2: XP Continued*, PDF pp. 9-10: initial estimates and historical velocity; pp. 16-17: tests before code and regression; p. 22: review at a higher level; pp. 40-41: the test-first development loop.
