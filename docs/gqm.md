# GQM Measurement Plan

## Goal

Analyse the Library API, including service orchestration and error responses, in order to guide one maintainability improvement with respect to control-flow complexity, dependency exposure, and error-value correctness from the point of view of a maintainer.

The structural measurements cover the service layer; the selected analyzer finding concerns the shared `ApiError` response. The goal covers both scopes. Baseline: `lab-5` at `b8dc32bbe8c8474910ebc704b34bb52d6fb92500`. Measurements use the same 32 production Java types before and after the correction.

## Questions

- **Q1:** Where do complexity and dependency indicators warrant closer code review?
- **Q2:** Does correcting one verified IMPORTANT analyzer finding remove the targeted issue while preserving the tested HTTP contracts?

## Metrics

| Metric | Answers | Definition, method, and decision rule |
| --- | --- | --- |
| **M1: manual cyclomatic complexity** | Q1 | Count the assignment's explicit decision tokens in each named `BorrowingService` method and constructor, then add one. Review any method above CC 5 and classify each with the lecture's separate risk bands. Library internals and generated lambda methods are outside this manual count. |
| **M2: service import coupling** | Q1 | Ce counts distinct explicit imported types, including project, JDK, framework, interface, enum, and annotation types. Ca counts distinct production Java types explicitly importing the service; tests are excluded. Compute `I = Ce / (Ce + Ca)`. Review the highest value in architectural context, with an internal-only sensitivity check. |
| **M3: selected finding count** | Q2 | Count SpotBugs `EQ_DOESNT_OVERRIDE_EQUALS` on `com.library.api.dto.ApiError` before and after the correction, using identical plugin/engine versions, Max effort, Low threshold, and production scope. The target is 1 to 0 with no new finding; inspect successful reports instead of treating tool errors as zero findings. |
| **M4: regression and line coverage** | Q2 | Record executed tests, failures/errors/skips, and JaCoCo project LINE covered/total counters. Require the established HTTP tests to pass and overall line coverage to remain at least 40%, the assignment floor. New tests must assert the intended value and wire contracts. Line execution alone does not establish assertion quality. |

## Results

### M1

| Named unit | Explicit decision evidence | Decision points | CC | Lecture risk level |
| --- | --- | ---: | ---: | --- |
| `BorrowingService(...)` constructor | None; field assignments only, lines 28-34. | 0 | 1 | Simple |
| `borrowBook` | `if (!book.isAvailable())`, line 42. | 1 | 2 | Simple |
| `returnBook` | `if (borrowing.getReturnDate() != null)`, line 60. | 1 | 2 | Simple |
| `getActiveBorrowings` | `if (!members.existsById(memberId))`, line 73. | 1 | 2 | Simple |
| `calculateFine` | No explicit decision; `Math.max` is a library call, lines 79-82. | 0 | 1 | Simple |
| `applyReturn` | No explicit decision; clock read and assignments, lines 84-89. | 0 | 1 | Simple |

Maximum method CC is **2** before and after; no method exceeds 5. Services were not modified by the selected fix.

### M2

| Class | Ce | Ca | Instability |
| --- | ---: | ---: | ---: |
| `BookService` | 10 | 1 | 0.9091 |
| `BorrowingService` | 14 | 1 | 0.9333 |
| `MemberService` | 11 | 1 | 0.9167 |
| `ReservationService` | 13 | 1 | 0.9286 |

These counts are unchanged. `BorrowingService` is highest at **14/15 = 0.9333**. Its sole production importer is `BorrowingController`; the other three services are each imported by their corresponding controller. When Ce includes only project types, the indices are 0.8571, 0.8750, 0.8333, and 0.8889 respectively, making `ReservationService` highest under that narrower definition.

### M3

The selected finding fell from **1 to 0**. SpotBugs total findings fell from **11 to 10**, with no new pattern/location occurrence. PMD remained at **9** findings. Both final tools completed with no missing classes or processing errors; their configurations and analyzed production scope were unchanged.

The selected finding had SpotBugs priority **2 (normal)** and rank **17**. The IMPORTANT assessment is based on demonstrated value-comparison behavior: different timestamps or validation fields compared equal because the superclass could not see the added fields. No broken HTTP endpoint was demonstrated. The correction stores these extensions in the superclass property map, preserving its equality symmetry and the existing JSON contract.

### M4

- Before the correction: **129 tests passed**, no failures/errors/skips; line coverage **322/329 = 97.87%**.
- The new regression suite first ran **14 cases: 4 assertion failures, 0 errors/skips**. Two failures expose omitted-field comparisons; two specify the chosen shared-state design. Production code was still unchanged at this checkpoint.
- Final verification: **143 tests passed** (the original 129 plus 14 new cases), with no failures/errors/skips; line coverage **324/331 = 97.89%**. The clean build also produced the application JAR.
- The 40% line-coverage floor is met. Seven executable lines remain missed both before and after; the small percentage increase comes from covering the added code, not from closing old gaps.

## Interpretation

No complexity-driven extraction is warranted in `BorrowingService`: its explicit methods are already small under this metric. The high primary import index is consistent with a service that coordinates domain objects, repositories, time, and monetary calculations while serving one controller. The ranking change under internal-only counting shows why the declared boundary matters. This class import proxy is not full semantic CBO or the lecture's package-level instability measure.

The actionable improvement is the corrected error-value state, supported by the disappearing finding and regression tests for both field differences, superclass/subclass symmetry, defensive map copying, inherited property replacement/clearing, and duplicate-free JSON. Complexity and service coupling did not decrease, and no such decrease is claimed. Remaining Spring/JPA-related analyzer findings require context-sensitive review, not automatic defensive copying of managed associations or removal of framework construction contracts.

Coverage supports the tested behavior but does not prove completeness. `LibraryApiApplication` is the lowest executable class at 1/3 lines (33.33%); its command-line `main` body is not invoked by the context-based tests. Member lookup, two reservation ID-setter lines, and one framework-error normalization line are additional uncovered paths worth prioritizing by behavioral importance.

## References

The assignment, *SE Lab Assignment 2026-2027 Codex*, PDF pp. 20-23, defines the GQM format, counting rules, and coverage floor. *Chapter 6: Measures and Metrics* (the supplied `MyChap6_MesuresEtMetriques_2627_English 1.pdf`) emphasizes precise measurement definitions on p. 24, cautious review of metrics on p. 58, method complexity and risk bands on pp. 59-63, and package instability on p. 66. Page references are PDF page numbers.
