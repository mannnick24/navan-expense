# Submission — Task A (receipt upload, taxes, auto-itemize)

Nick Mann. Checklist from `task-a/task-a-receipt-autoitemize-CANDIDATE.md` § Submit.

## 1. Git repo URL + commit SHA

| | |
|---|---|
| **Repo** | https://github.com/mannnick24/navan-expense |
| **SHA** | `56245472d1ee2cb4b79045615539d2ec4cafc08f` |

If you commit after this file, replace the SHA with `git rev-parse HEAD`.

## 2. README — run + curls

[`README.md`](README.md) is the run book.

**One command (dev):**

```bash
./scripts/run-api.sh
```

(or `./gradlew bootRun`). Java 21. Gradle wrapper. Listens on `http://localhost:8080`.

**Example curls for every endpoint** are in the README:

| Method | Path | README section |
|---|---|---|
| `GET` | `/health` | Health |
| `GET` | `/actuator/prometheus` | Prometheus scrape |
| `POST` | `/receipts` | Upload and process |
| `POST` | `/receipts/{id}/process` | Upload and process |
| `GET` | `/transactions/{id}` | Fetch the transaction |
| `POST` | `/transactions/{id}/itemize` | Re-itemize from stored OCR |
| `PATCH` | `/transactions/{id}/items` | User override |
| `POST` | `/receipts/{id}/process` (bad OCR) | Parse failure (400) |
| `POST`/`PATCH` | `/transactions/{id}/itemize` and `/items` | Invalid transaction id (400) |

Upload bodies must be image or PDF (`fixtures/task-a/upload.png`) with `filename=` set to the fixture stem (OCR stub).

```bash
./gradlew test
./gradlew e2eTest
./gradlew check
```

## 3. OCR / model

**OCR is stubbed.** No OCR vendor, Vision API, or LLM is called at runtime. No API keys.

- Upload `filename` stem → classpath `fixtures/task-a/{stem}.txt` (`ClasspathReceiptTextResolver`, `app.ocr.kind=fixture`).
- Extraction is a deterministic regex parser (`app.parser.kind=regex` → `RegexReceiptParser`).
- `app.ocr.kind=ocr` is a non-functional vendor placeholder (`StubOcrReceiptTextResolver`).

The three brief fixtures (`receipt-clean`, `receipt-tax-only`, `receipt-mismatch`) match `fixtures/task-a/gold.json`.

## 4. Architecture (optional half-page)

Gradle modules: **`receipt-parser`** (no Spring) and **`api`** (Spring Boot 4.1, JPA, H2).

`POST /receipts` stores the file and a `receipts` row. `POST /receipts/{id}/process` resolves stub OCR text, persists it, then creates one **transaction**, **tax lines as their own rows**, and **line items**. Itemize replaces items only. PATCH items returns **409** if amounts no longer reconcile; we do **not** invent a balancing line. Status is `COMPLETE` or `NEEDS_REVIEW` (zero/negative amounts stay stored but review). Unparseable OCR is **400** with no transaction.

Parser and OCR implementations are injected (`app.parser.kind`, `app.ocr.kind`). Cross-cutting: AOP JSON audit (not health), Prometheus scrape, `/health` with `SELECT 1`. Nginx rate-limit/TLS was explored and left document-only.

More talk track: [`demo-script.md`](demo-script.md).
