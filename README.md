# navan-expense

Receipt upload, tax capture, and auto-itemize HTTP API.

OCR is **stubbed**: uploaded filenames map to the fixture text in `fixtures/task-a/`. No OCR vendor or LLM API is called.

## Setup

1. **Java 21** on the `PATH` (Temurin / Oracle / OpenJDK). Confirm:

   ```bash
   java -version
   ```

   You want `21`. macOS with Homebrew: `brew install --cask temurin@21`.

2. Clone this repo. **Gradle is not installed separately** — use the wrapper (`./gradlew`).

3. From the repo root, you can check the wrapper:

   ```bash
   ./gradlew --version
   ```

Nothing else to install: H2 is in-process, fixtures are in the repo, no Docker, no OCR key.

## Run (dev)

```bash
./gradlew bootRun
```

The API listens on `http://localhost:8080`. H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:navan`, user `sa`, empty password).

## Fat jar (cmdline)

Spring Boot `bootJar` is a **fat / executable jar** (parser module + Spring + H2 inside).

```bash
./gradlew bootJar
java -jar api/build/libs/navan-expense.jar
```

Same thing via script (builds the jar if missing):

```bash
./scripts/run-api.sh
```

Pass Spring args through:

```bash
java -jar api/build/libs/navan-expense.jar --server.port=8080
./scripts/run-api.sh --server.port=8080
```

Still `http://localhost:8080` unless you change the port.

## Tests

Unit + WebMvc tests:

```bash
./gradlew test
```

**E2E runner** (builds the fat jar, starts it on port **18080**, runs HTTP contract tests against that process, then stops it):

```bash
./gradlew e2eTest
```

or:

```bash
./scripts/run-e2e.sh
```

Point the same tests at an API that is already running (for example the fat jar or `bootRun` on 8080):

```bash
E2E_BASE_URL=http://localhost:8080 ./scripts/run-e2e.sh
# or
./gradlew e2eTest -Pe2e.baseUrl=http://localhost:8080
```

`./gradlew check` runs `test` then `e2eTest`.

Default e2e uses port 18080 so it does not collide with a demo `bootRun` on 8080. Logs for the jar the runner starts: `api/build/e2e-app.log`.

## Filename mapping (OCR stub)

| Upload name | Fixture text |
|---|---|
| `receipt-clean.png` | `receipt-clean.txt` |
| `receipt-tax-only.jpg` | `receipt-tax-only.txt` |
| `receipt-mismatch.pdf` | `receipt-mismatch.txt` |
| `receipt-garbled.png` | `receipt-garbled.txt` |
| `receipt-non-numeric.png` | `receipt-non-numeric.txt` |
| `receipt-unlabeled.png` | `receipt-unlabeled.txt` |
| `receipt-missing-merchant.png` (and other `receipt-missing-*.png`) | matching `.txt` |

Unknown filenames: `POST /receipts/{id}/process` returns `400` `OCR_TEXT_NOT_FOUND`.

## Example curls

Health:

```bash
curl -s http://localhost:8080/health
```

Prometheus scrape:

```bash
curl -s http://localhost:8080/actuator/prometheus | head
```

Upload and process a clean receipt:

```bash
RECEIPT_ID=$(curl -s -F "file=@fixtures/task-a/receipt-clean.txt;filename=receipt-clean.png" \
  http://localhost:8080/receipts | python3 -c "import sys,json; print(json.load(sys.stdin)['receipt_id'])")

curl -s -X POST http://localhost:8080/receipts/$RECEIPT_ID/process
```

Fetch the transaction (copy `transaction_id` from process):

```bash
curl -s http://localhost:8080/transactions/$TRANSACTION_ID
```

Re-itemize from stored OCR:

```bash
curl -s -X POST http://localhost:8080/transactions/$TRANSACTION_ID/itemize
```

User override (must still reconcile with stored total + taxes):

```bash
curl -s -X PATCH http://localhost:8080/transactions/$TRANSACTION_ID/items \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"description":"Espresso","amount":3.50},{"description":"Sandwich","amount":8.90},{"description":"Mineral water","amount":2.60}]}'
```

Parse failure (400):

```bash
BAD_ID=$(curl -s -F "file=@fixtures/task-a/receipt-garbled.txt;filename=receipt-garbled.png" \
  http://localhost:8080/receipts | python3 -c "import sys,json; print(json.load(sys.stdin)['receipt_id'])")
curl -s -X POST http://localhost:8080/receipts/$BAD_ID/process
```

Invalid transaction id (400):

```bash
curl -s -X POST http://localhost:8080/transactions/not-a-uuid/itemize
curl -s -X PATCH http://localhost:8080/transactions/not-a-uuid/items \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"description":"Water","amount":4.00}]}'
```
