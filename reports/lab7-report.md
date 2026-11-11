# Lab 7 - Software Testing

The final clean build passes **163 tests**, with **100% service-package line coverage (109/109)** and **100% service branch coverage (32/32)**. Overall production line coverage is **98.49% (326/331)**. Both deliberate fine-calculation bugs were detected, and the original production source was restored before the final build.

Baseline: `lab-6` at `bb5512716ad2cbdd6af5ff4eacdb6fabfa556edc`. This lab adds [BorrowingServiceUnitTest](../src/test/java/com/library/api/service/BorrowingServiceUnitTest.java) and [BorrowingEndToEndTest](../src/test/java/com/library/api/controller/BorrowingEndToEndTest.java). All 16 pre-existing test/support files, production code, Maven configuration, seed data, and the reservation specification are unchanged in the final version. The 20 added cases comprise 17 isolated unit tests and three real HTTP flows.

## 1. Coverage before and after

These tables use the JaCoCo XML counters from the complete baseline and restored final suite. A cell is **covered/total (percentage)**. Line and branch coverage are separate metrics; N/A means there are no executable lines or branches in that scope. The comparison excludes intermediate runs that selected only one test class and excludes both deliberately broken versions.

| Scope/package | Before: lines | After: lines | Before: branches | After: branches |
| --- | ---: | ---: | ---: | ---: |
| Overall | 324/331 (97.89%) | 326/331 (98.49%) | 44/48 (91.67%) | 46/48 (95.83%) |
| com/library/api | 1/3 (33.33%) | 1/3 (33.33%) | N/A | N/A |
| config | 2/2 (100.00%) | 2/2 (100.00%) | N/A | N/A |
| controller | 35/36 (97.22%) | 36/36 (100.00%) | N/A | N/A |
| dto | 22/22 (100.00%) | 22/22 (100.00%) | 2/2 (100.00%) | 2/2 (100.00%) |
| entity | 87/89 (97.75%) | 87/89 (97.75%) | N/A | N/A |
| exception | 24/25 (96.00%) | 24/25 (96.00%) | 4/6 (66.67%) | 4/6 (66.67%) |
| mapper | 45/45 (100.00%) | 45/45 (100.00%) | 8/8 (100.00%) | 8/8 (100.00%) |
| repository | N/A | N/A | N/A | N/A |
| service | 108/109 (99.08%) | 109/109 (100.00%) | 30/32 (93.75%) | 32/32 (100.00%) |

### Service classes

| Class | Before: lines | After: lines | Before: branches | After: branches |
| --- | ---: | ---: | ---: | ---: |
| `BookService` | 23/23 (100.00%) | 23/23 (100.00%) | 11/12 (91.67%) | 12/12 (100.00%) |
| `BorrowingService` | 40/40 (100.00%) | 40/40 (100.00%) | 6/6 (100.00%) | 6/6 (100.00%) |
| `MemberService` | 11/12 (91.67%) | 12/12 (100.00%) | 5/6 (83.33%) | 6/6 (100.00%) |
| `ReservationService` | 34/34 (100.00%) | 34/34 (100.00%) | 8/8 (100.00%) | 8/8 (100.00%) |

The baseline already exceeded the assignment's 80% target: service line coverage was **108/109 = 99.08%**, and every service class exceeded 80%. The completed suite closes the remaining service line in `MemberService.getById` by fetching the member registered through HTTP. Supplying explicit `available=true` and `active=true` in valid creation requests also exercises the remaining creation branches in `BookService` and `MemberService`. Service line coverage rises by **0.92 percentage points**, and branch coverage by **6.25 points**.

Overall line coverage rises from **97.89% to 98.49%**: member lookup also executes the corresponding `MemberController` line. Five lines remain missed: `LibraryApiApplication` lines 13-14 (command-line bootstrap), `Reservation` lines 48-49 (ID setter), and `GlobalExceptionHandler` line 88 (framework 5xx-detail normalization). The two remaining uncovered branches are in the exception handler. No source exclusions or changed coverage configuration were used.

The final HTML summary at `target/site/jacoco/index.html` was rendered in a browser and checked against its XML line counters; the tables above provide the required before/after summary. Generated HTML/XML/CSV remain ignored build outputs. To regenerate them, run `mvn -B -ntp clean verify`; the existing JaCoCo configuration emits the report during the test phase.

## 2. Generated unit suite and initial audit

The initial generated suite was run before the audit and passed **15 named tests**. It uses `@ExtendWith(MockitoExtension.class)` and `@Mock` for `BookRepository`, `MemberRepository`, and `BorrowingRepository`, with a real fixed `Clock` and no Spring context. It covers all four public operations: `borrowBook`, `returnBook`, `calculateFine`, and `getActiveBorrowings`. The last operation is included because Step B says every public method, even though its example list omits it.

Each initial test and its invoked helpers were reviewed against required behavior. **Initial classification: 15 Meaningful, 0 Weak, 0 Wrong oracle.** Missing scenarios and fixture blind spots are listed separately below; they are not retroactively counted as weak or failing test methods. The table describes the initial version, before the two new methods and clock correction.

| Initial test method | Classification | Reason |
| --- | --- | --- |
| `borrowAvailableBookCreatesLoanAndMarksBookUnavailable` | Meaningful | Checks generated ID returned by the repository, exact book/member identities, literal borrow/due dates, null return date, scale-two zero fine, availability change, and saving the returned loan. Expectations come from the contract rather than the tested method's own result. |
| `borrowMissingBookRejectsBeforeMemberLookupOrSave` | Meaningful | Checks the specific resource exception/message and prevents member lookup or loan persistence after a missing book. |
| `borrowMissingMemberLeavesBookAvailableAndDoesNotSave` | Meaningful | Checks the member-specific failure, unchanged book availability, and no borrowing-repository interaction. |
| `borrowUnavailableBookRejectsWithoutSaving` | Meaningful | Checks the exact conflict message, preserved unavailable state, and no borrowing save; this is the unit-level domain counterpart of HTTP 409. |
| `returnBeforeDueDateRestoresAvailabilityWithZeroFine` | Meaningful | The reviewed return helper checks the literal zero fine, clock date, restored availability, preserved identity/dates/relationships, and save. It exercises the early-return boundary. |
| `returnThreeDaysLateChargesOneEuroFifty` | Meaningful | The same helper checks a three-day-late return against an independent literal 1.50 value and verifies settlement state and persistence interaction. |
| `returnMissingBorrowingRejectsWithoutBookLookupOrSave` | Meaningful | Checks the correct missing-loan exception/message, no book/member access, and no save. |
| `returnExactlyOnDueDateRestoresAvailabilityWithZeroFine` | Meaningful | Checks the exact due-date boundary through the service return operation, including non-null scale-two zero fine, date, availability, and save. |
| `fineOnDueDateIsZero` | Meaningful | Compares pure calculation against literal 0.00 and verifies no repository interaction. BigDecimal equality also checks monetary scale. |
| `fineOneDayLateIsFiftyCents` | Meaningful | Compares the first positive late-day boundary against literal 0.50 without deriving that expectation from production code. |
| `fineSevenDaysLateIsThreeEurosFifty` | Meaningful | Checks the seven-day calculation against literal 3.50; a changed rate is observable. |
| `fineBeforeDueDateIsZero` | Meaningful | Checks negative lateness clamps to literal 0.00 rather than producing a negative fine. |
| `activeBorrowingsForExistingMemberReturnsRepositoryResults` | Meaningful | Checks the returned IDs in order and verifies the member-specific ordered/unreturned query. This tests service delegation; a mocked query does not prove database filtering, which existing integration tests address. |
| `activeBorrowingsForExistingMemberCanBeEmpty` | Meaningful | Distinguishes an existing member with no loans from a missing member; verifies the expected query and empty result. |
| `activeBorrowingsRejectsMissingMemberBeforeQuery` | Meaningful | Checks the member-specific exception and prevents querying borrowings when the member does not exist. |

The repository mocks establish service decisions and collaborator calls; they do not prove a database transaction or query's filtering. The existing persistence tests check member scoping, return-date filtering, ordering, and committed state, and the existing contention tests check overlapping transactions. Literal monetary expectations are independent of the production constant. `BigDecimal` equality checks both value and scale, so neither null nor a scale-incompatible zero silently passes those unit assertions.

### Three audit corrections

| Issue in the initial generated suite | Correction | Final evidence |
| --- | --- | --- |
| **A1 - Missing:** no isolated case for rejecting an already returned loan. | Added `returnAlreadyReturnedLoanPreservesSettlementWithoutSaving`, using a prior return date, assessed 1.00 fine, and an unavailable book that may be on a later loan. | Asserts the exact conflict, unchanged ID, borrow/due/return dates, fine, relationships and availability; no save, book lookup, or member access. |
| **A2 - Missing:** no isolated case for an existing loan whose required book lookup fails. | Added `returnMissingBookLeavesLoanUnsettledWithoutSaving`. | Asserts the Book-specific exception and preserved unsettled state, original ID/dates/relationships, zero fine and unavailable flag; no save or member access. |
| **A3 - Fixture blind spot:** the initial noon-UTC clock could not distinguish using the configured zone from always using UTC. | Changed the shared clock to instant `2024-02-28T22:30:00Z` in `Asia/Beirut`, retaining literal local date `2024-02-29` and due date `2024-03-14`. | Successful borrowing and return tests now distinguish the injected local calendar date from the previous UTC date. |

A1 and A3 strengthen the new isolated suite; existing integration tests already covered repeated returns and the configured clock zone. A2 exercises a defensive collaborator failure: database foreign keys normally prevent a valid persisted loan from referencing a missing book. The test does not claim that an ordinary valid database can contain an orphan. The final suite contains **17 meaningful unit methods**; the original 15 were retained. A follow-up review added explicit preservation assertions for original identities and relationships before the mutation runs.

## 3. Real HTTP integration flows

Step D's heading mentions MockMvc, while its concrete instructions require `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `TestRestTemplate`. The three new tests follow those explicit instructions; the existing MockMvc controller tests remain unchanged. Each request passes through the real HTTP server, controllers, services, repositories, and H2 database.

| New test | Flow and observable result |
| --- | --- |
| `registeringResourcesAndBorrowingPersistsUnavailableBook` | POST book and member, GET the registered member, POST borrowing, then GET book with `available=false`. Also checks a fresh stored loan's related IDs, dates, null return date, zero fine, and exactly one row. |
| `fullBorrowingCyclePersistsReturnAndRestoresBookAvailability` | Register resources, borrow, confirm unavailable, PUT return, then GET book with `available=true`. Checks retained borrowing identity/dates, non-null numeric zero fine, persisted return date/fine, one retained row, and an empty active-loan list. |
| `borrowingSameBookTwiceReturnsConflictWithoutAnotherLoan` | Register resources, borrow twice, assert the second HTTP 409 and exact problem detail. Verify one retained loan, unchanged first-loan values, and the book still unavailable. |

The test server binds to loopback on a random port. A dedicated empty H2 database and fixed clock isolate fixtures from application seed data and wall-clock time. Cleanup deletes dependent loans before books and members, and the test context closes after the class. This is explicit cleanup because server-side HTTP transactions do not roll back with a test-thread transaction. [Spring Boot 3.5 testing documentation](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html).

Assertions check statuses, identities, values, persisted state, and JSON types. In particular, the fine must be a JSON number before its value is compared; calling a permissive numeric accessor alone could turn a missing/null field into a misleading zero. These tests add HTTP transport coverage without replacing the more focused unit or existing contention tests.

## 4. Deliberate bug experiments

Both mutations were applied independently to the same green **163-case** suite. Each version compiled and executed all cases. Test/source/build hashes verified that only the intended `BorrowingService` edit changed, and the original file bytes were restored in a `finally` block after each run. No mutation is retained in the delivered source. The final clean build recompiles restored code rather than relying on bytecode left by a failing run.

| Version | Executed | Passed | Assertion failures | Runtime errors | Skipped | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Before mutations | 163 | 163 | 0 | 0 | 0 | 0 |
| Fine rate 1.00 | 163 | 155 | 8 | 0 | 0 | 1 |
| Zero fine replaced by null | 163 | 148 | 11 | 4 | 0 | 1 |
| Restored clean build | 163 | 163 | 0 | 0 | 0 | 0 |

### Bug 1 - Double the daily fine rate

The existing constant was temporarily changed from `new BigDecimal("0.50")` to `new BigDecimal("1.00")`. No second arithmetic change was applied. Eight assertions detected the changed positive fine:

| Test invocation | Outcome | Observed signal |
| --- | --- | --- |
| `BorrowingControllerTest.returnPersistsOverdueFine` | Failure | `JSON path "$.fineAmount" expected:<1.5> but was:<3.0>` |
| `BorrowingConcurrencyTest.concurrentReturn_shouldPreserveTheFirstReturnAndFine` | Failure | `expected: <1.50> but was: <3.00>` |
| `BorrowingServiceUnitTest.returnThreeDaysLateChargesOneEuroFifty` | Failure | `expected: <1.50> but was: <3.00>` |
| `BorrowingServiceUnitTest.fineSevenDaysLateIsThreeEurosFifty` | Failure | `expected: <3.50> but was: <7.00>` |
| `BorrowingServiceUnitTest.fineOneDayLateIsFiftyCents` | Failure | `expected: <0.50> but was: <1.00>` |
| `FineCalculationTest.calculateFine_shouldChargeOnlyOverdueDays(long, String)[3]` | Failure | `expected: <0.50> but was: <1.00>` |
| `FineCalculationTest.calculateFine_shouldChargeOnlyOverdueDays(long, String)[4]` | Failure | `expected: <3.50> but was: <7.00>` |
| `ReturnServiceTest.returnBook_shouldPersistLocalDateFineAndAvailability(int, String)[3]` | Failure | `expected: <1.50> but was: <3.00>` |

For parameterized `FineCalculationTest`, invocations `[3]` and `[4]` mean one and seven days late. `ReturnServiceTest` invocation `[3]` is a three-day-late return. Their expected 0.50, 3.50, and 1.50 values are literal oracles, so doubling the production constant cannot change the expectation to match the defect.

All other **155** invocations passed. Early and exactly-due returns still multiply by zero, so their survival is expected. The new full-cycle HTTP test returns a newly created loan before its due date, while the other two HTTP tests do not return a loan. All three therefore survive this rate mutation; the existing overdue HTTP test and new isolated late-return test cover the positive-fine case. A passing non-overdue flow does not validate the overdue rate.

### Bug 2 - Return null for a zero fine

The original 0.50 rate was restored first. The following temporary condition was inserted after calculating `overdueDays` and before the unchanged multiplication:

```java
if (overdueDays == 0) {
    return null;
}
```

Because the existing calculation clamps negative lateness to zero, this affects both early and exactly-on-time returns. The complete set of non-passing invocations is:

| Test invocation | Outcome | Observed signal |
| --- | --- | --- |
| `BorrowingControllerTest.activeListIsScopedToMemberAndExcludesReturnedBorrowings` | Failure | `Status expected:<200> but was:<409>` |
| `BorrowingControllerTest.returnRestoresAvailabilityAndRejectsRepeatedReturn` | Failure | `Status expected:<200> but was:<409>` |
| `BorrowingEndToEndTest.fullBorrowingCyclePersistsReturnAndRestoresBookAvailability` | Failure | `expected: <200 OK> but was: <409 CONFLICT>` |
| `ReservationControllerTest.ac9_currentBorrowerCanReserveAndReturnWithoutChangingReservationState` | Failure | `Status expected:<200> but was:<409>` |
| `BorrowingServiceUnitTest.returnBeforeDueDateRestoresAvailabilityWithZeroFine` | Failure | `expected: <0.00> but was: <null>` |
| `BorrowingServiceUnitTest.fineOnDueDateIsZero` | Failure | `expected: <0.00> but was: <null>` |
| `BorrowingServiceUnitTest.fineBeforeDueDateIsZero` | Failure | `expected: <0.00> but was: <null>` |
| `BorrowingServiceUnitTest.returnExactlyOnDueDateRestoresAvailabilityWithZeroFine` | Failure | `expected: <0.00> but was: <null>` |
| `FineCalculationTest.calculateFine_shouldChargeOnlyOverdueDays(long, String)[1]` | Failure | `expected: <0.00> but was: <null>` |
| `FineCalculationTest.calculateFine_shouldChargeOnlyOverdueDays(long, String)[2]` | Failure | `expected: <0.00> but was: <null>` |
| `ReservationConcurrencyTest.ac10_returnBeforeCreation_rejectsTheNowAvailableBook` | Failure | `Completion-latch assertion times out; the same test logs NULL FINE_AMOUNT before reaching the latch.` |
| `ReservationConcurrencyTest.ac10_creationBeforeReturn_keepsTheReservationActiveAfterTheReturn` | Error | `Worker future propagates a DataIntegrityViolationException: FINE_AMOUNT cannot be NULL.` |
| `ReservationServiceTest.ac9_bookReturn_preservesActiveReservationAndAllowsCancellation` | Error | `DataIntegrityViolationException: the return attempts to persist NULL FINE_AMOUNT.` |
| `ReturnServiceTest.returnBook_shouldPersistLocalDateFineAndAvailability(int, String)[1]` | Error | `DataIntegrityViolationException: the return attempts to persist NULL FINE_AMOUNT.` |
| `ReturnServiceTest.returnBook_shouldPersistLocalDateFineAndAvailability(int, String)[2]` | Error | `DataIntegrityViolationException: the return attempts to persist NULL FINE_AMOUNT.` |

For `FineCalculationTest`, `[1]` is one day early and `[2]` is exactly due. For `ReturnServiceTest`, `[1]` returns one day before the due date and `[2]` returns exactly on it. Four new unit assertions and two existing pure-fine assertions directly report **expected 0.00, actual null**.

The four runtime errors are consequential persistence failures: `Borrowing.fineAmount` maps to a non-null database column. One is propagated through a worker future. HTTP return paths translate the data-integrity exception to 409, so their expected-200 assertions fail. The return-before-reservation contention test fails at a completion latch; its own captured output records the null-column error, and the helper flush occurs before the latch is released. That is an indirect and less diagnostic signal, not a monetary oracle or evidence of compilation failure. Direct unit assertions independently establish detection.

The remaining **148** invocations passed. Positive late fines remain unchanged, so the one-day, seven-day, and three-day-late tests survive. Creation/lookup operations and rejected-return guards that never settle a loan also survive. This distinction shows why both zero and positive boundary cases are necessary.

### Complete class outcome summary

Each cell is **passed / assertion failures / runtime errors**; there were no skipped cases. Every non-passing invocation is named in the two tables above, and all other invocations in its class passed. The full 163-case identity set was identical in both experiments and the restored run.

| Test class | Rate mutation | Null mutation |
| --- | ---: | ---: |
| `LibraryApiSmokeTest` | 2 / 0 / 0 | 2 / 0 / 0 |
| `BorrowingControllerTest` | 13 / 1 / 0 | 12 / 2 / 0 |
| `BorrowingEndToEndTest` | 3 / 0 / 0 | 2 / 1 / 0 |
| `CatalogueContractTest` | 9 / 0 / 0 | 9 / 0 / 0 |
| `OpenApiDocumentationTest` | 18 / 0 / 0 | 18 / 0 / 0 |
| `ReservationControllerTest` | 33 / 0 / 0 | 32 / 1 / 0 |
| `ApiErrorTest` | 14 / 0 / 0 | 14 / 0 / 0 |
| `GlobalExceptionHandlerTest` | 10 / 0 / 0 | 10 / 0 / 0 |
| `BorrowingMapperTest` | 2 / 0 / 0 | 2 / 0 / 0 |
| `ActiveBorrowingServiceTest` | 3 / 0 / 0 | 3 / 0 / 0 |
| `BorrowingConcurrencyTest` | 1 / 1 / 0 | 2 / 0 / 0 |
| `BorrowingServiceTest` | 4 / 0 / 0 | 4 / 0 / 0 |
| `BorrowingServiceUnitTest` | 14 / 3 / 0 | 13 / 4 / 0 |
| `FineCalculationTest` | 2 / 2 / 0 | 2 / 2 / 0 |
| `ReservationConcurrencyTest` | 6 / 0 / 0 | 4 / 1 / 1 |
| `ReservationServiceTest` | 17 / 0 / 0 | 16 / 0 / 1 |
| `ReturnServiceTest` | 4 / 1 / 0 | 3 / 0 / 2 |

These results establish detection of the **two specified injected faults**. They are not an exhaustive mutation score or proof that every possible regression is detected. The different direct-value, HTTP-status, persistence, and synchronization signals also illustrate that a failing test can be useful while offering very different diagnostic precision.

## 5. Acceptance criteria as evals

The Lab 3 criteria in [docs/AGENTS.md](../docs/AGENTS.md) define controlled inputs and independent expected outcomes, so their executable acceptance tests function as specification-based evals: they assess whether an implementation satisfies the intended behavior, regardless of how it was produced. For AC-1, an existing member reserving an available book must receive HTTP 409 with detail `Book is available for direct borrowing.`, create no reservation, and leave availability unchanged; `ReservationControllerTest.ac1_availableBookReturnsExactConflictWithoutChanges` checks those outcomes. A 201 response, incorrect detail, extra row, or changed book flag would be a failing eval even if compilation and unrelated tests passed. The written criterion is the evaluation specification, while the executable assertion and observed run supply the verdict. Chapter 1's distinction between running tests and validating output applies directly: a precise oracle connects the feature requirement to a measurable result.

## 6. Can generated tests be trusted without review?

Passing generated tests provide evidence only for the behaviors and data they actually assert. Two concrete audit examples show why review remains necessary. First, the initial suite had useful happy-path and missing-record tests but omitted isolated repeated-return rejection and preservation; it could pass while that guard was absent from this suite's coverage. Second, its UTC-noon fixture could not distinguish the configured clock zone from UTC even though it asserted literal dates. Adding the rejection case and moving the instant across a local-date boundary strengthened the useful initial suite. The defensive missing-book return test addresses a third gap, with its persistence limitation stated explicitly.

The deliberate bugs give execution evidence beyond review: fixed expected money values detect the doubled rate, and exact zero assertions reject null. Conversely, the new on-time HTTP cycle survives the rate bug because its data never creates a positive fine. High coverage alone cannot establish the adequacy of expected values or input partitions; the initial service coverage was already 99.08%. Review, independent expectations, boundary data, and observed defect detection complement each other. The experiment does not establish trustworthiness for arbitrary generated tests or substitute one reviewer's confidence for test execution.

## 7. Reproducible execution and references

All runs used Java **17.0.21.0.3** and Maven **3.10.0**. The table gives actual completion times in UTC on 11 November 2026; totals are **executed / assertion failures / runtime errors / skipped**.

| Checkpoint | Command | Completed UTC | Exit | Test totals |
| --- | --- | --- | ---: | ---: |
| Unchanged Lab 6 baseline | `mvn -B -ntp test jacoco:report` | 20:04:15 | 0 | 143 / 0 / 0 / 0 |
| Initial generated unit suite | `mvn -B -ntp -Dtest=BorrowingServiceUnitTest test` | 20:05:48 | 0 | 15 / 0 / 0 / 0 |
| Three HTTP flows | `mvn -B -ntp -Dtest=BorrowingEndToEndTest test` | 20:07:52 | 0 | 3 / 0 / 0 / 0 |
| Audit fixes and complete suite | `mvn -B -ntp test jacoco:report` | 20:10:14 | 0 | 163 / 0 / 0 / 0 |
| Additional preservation assertions | `mvn -B -ntp test jacoco:report` | 20:12:21 | 0 | 163 / 0 / 0 / 0 |
| Independent mutation: rate 1.00 | `mvn -B -ntp test` | 20:12:46 | 1 | 163 / 8 / 0 / 0 |
| Independent mutation: zero fine becomes null | `mvn -B -ntp test` | 20:13:20 | 1 | 163 / 11 / 4 / 0 |
| Restored code, clean build | `mvn -B -ntp clean verify` | 20:14:02 | 0 | 163 / 0 / 0 / 0 |

The final `mvn -B -ntp clean verify` built the executable JAR and regenerated coverage. All **56** captured source/build/specification inputs match the final tested state. The application test context and temporary browser profile have closed. The required tests and this report are tracked; raw logs, snapshots, copied Surefire XML, review notes, and browser evidence remain ignored local support.

Course references use PDF page numbers: *SE Lab Assignment 2026-2027 Codex*, pp. 23-27; *Chapter 7: Test Logiciel* (`MonChap7_TestLogiciel_2324.pdf`), p. 31 on injected-fault testing, p. 32 on unit/integration/regression levels, pp. 56-57 on partial test oracles, pp. 64-65 on instruction/branch coverage limits, and pp. 77-80 on structural versus specification-based testing; *Chapter 1: Introduction to Software Engineering* (`MyChap1_IntroToSoftwareEngineering2627English 1.pdf`), pp. 15 and 18 on evals, specifications, and verifying output.
