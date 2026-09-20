# Demo commands — copy blocks from THIS file in the editor (not markdown preview).
# Do not run the whole file. Repo root. Terminal A = API, terminal B = paste here.

# --- 0. Start API (terminal A; leave running) ---
# ./gradlew bootRun
# or: ./scripts/run-api.sh

# --- 0. Health ---
curl -s http://localhost:8080/health
# {"status":"UP"}

# --- 2. Upload media-type checks ---
# 415 — declared type not image/PDF
curl -s -F "file=@fixtures/task-a/receipt-clean.txt;filename=receipt-clean.png;type=text/plain" \
  http://localhost:8080/receipts

# 415 — image/png but body is not a PNG
curl -s -F "file=@fixtures/task-a/receipt-clean.txt;filename=receipt-clean.png;type=image/png" \
  http://localhost:8080/receipts

# 200 — real PNG; filename maps to receipt-clean.txt for fixture OCR
curl -s -F "file=@fixtures/task-a/upload.png;filename=receipt-clean.png;type=image/png" \
  http://localhost:8080/receipts

# --- 3. Unit tests ---
./gradlew test --console=plain

# --- 4a. Health (not audited) ---
curl -s http://localhost:8080/health

# --- 4b. Happy path ---
RECEIPT_ID=$(curl -s -F "file=@fixtures/task-a/upload.png;filename=receipt-clean.png;type=image/png" \
  http://localhost:8080/receipts | python3 -c "import sys,json; print(json.load(sys.stdin)['receipt_id'])")
echo "receipt_id=$RECEIPT_ID"

curl -s -X POST "http://localhost:8080/receipts/$RECEIPT_ID/process" | python3 -m json.tool

# paste transaction_id from process JSON:
# TRANSACTION_ID=<paste>
# curl -s "http://localhost:8080/transactions/$TRANSACTION_ID" | python3 -m json.tool

# --- 4c. Garbled OCR (400) ---
BAD_ID=$(curl -s -F "file=@fixtures/task-a/upload.png;filename=receipt-garbled.png;type=image/png" \
  http://localhost:8080/receipts | python3 -c "import sys,json; print(json.load(sys.stdin)['receipt_id'])")
echo "bad_id=$BAD_ID"

curl -s -X POST "http://localhost:8080/receipts/$BAD_ID/process" | python3 -m json.tool

# --- 4d. Prometheus ---
curl -s http://localhost:8080/actuator/prometheus | egrep 'http_server_requests_seconds_count|jvm_memory_used_bytes|hikaricp_connections' | head -n 40

curl -s http://localhost:8080/actuator/prometheus | head

# --- 6. E2E (own fat-jar process on 18080; bootRun can stay up) ---
./gradlew e2eTest --console=plain
# or: ./scripts/run-e2e.sh
