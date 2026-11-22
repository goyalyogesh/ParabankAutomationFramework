# 04 — ExcelUtil (test data in, results out)

File: `automation-core/.../core/excel/ExcelUtil.java` · Library: Apache POI (`poi-ooxml`)

## Shape of the data
Row 0 is the header. Each data row becomes a `Map<String,String>` (header → cell text), so a sheet is a
`List<Map<String,String>>`:

| UserId | FirstName | Username | Status |
|---|---|---|---|
| U01 | Ann | | |

→ `[{UserId=U01, FirstName=Ann, Username=, Status=}]`

Code asks for `row.get("FirstName")`, never "column 2", so moving columns in Excel breaks nothing.

## Methods
| Method | What it does |
|---|---|
| `readSheet(file, sheet)` | All non-blank rows, columns in order. Uses `IntStream` → `map` → `filter` → `toList()` |
| `writeSheet(file, sheet, headers, rows)` | Creates/replaces one sheet, keeps the others |
| `updateRow(file, sheet, keyColumn, keyValue, updates)` | Finds the row (e.g. `UserId=U03`) and writes values; unknown columns are added to the header |

## Why `DataFormatter`?
Excel stores `90210` as a number; plain POI returns `90210.0`. `DataFormatter` returns the text exactly
as Excel displays it — what a tester typed.

## Parallel safety (important in interviews)
Tests run in 3+ threads and all write results to the same file. Without protection two threads read the
file, each changes one row, and the second save wipes out the first ("lost update").
- One `ReentrantLock` makes read-modify-write of the file one-at-a-time.
- Saving writes `data.xlsx.tmp` first, then renames it over the real file in one atomic step — a crash
  never leaves a half-written workbook.
- Proved by `concurrentUpdatesAreAllPersisted`: 10 threads, 10 rows, no update lost.

## Limits
- The lock protects threads in **one JVM**. Two separate Maven processes writing the same file are not
  protected — we never do that (Jenkins runs one process per job).
- Close the workbook in Excel before running; Excel's file lock can make the save fail on Windows.

## Proof that the lock matters (experiment, 2026-11-21)
We removed `WRITE_LOCK.lock()/unlock()` from `updateRow` and ran `concurrentUpdatesAreAllPersisted` 3 times:
it failed **3 out of 3** with `UncheckedIOException: Cannot update Excel file` (threads overwrote each
other's temp file and read half-written workbooks). With the lock back: 10/10 tests pass. This is how you
show a concurrency test is real: it must fail when the protection is removed.
