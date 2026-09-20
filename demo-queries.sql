-- Demo SQL — paste into http://localhost:8080/h2-console
-- JDBC URL: jdbc:h2:mem:navan   User: sa   Password: (empty)
-- Run after the §4 curls (clean process + garbled upload/process).

SHOW TABLES;

SELECT id, original_filename, content_type,
       length(raw_ocr_text) AS ocr_chars
FROM receipts
ORDER BY created_at;

SELECT t.id, t.merchant, t.currency, t.grand_total, t.itemize_status, t.receipt_id
FROM expense_transactions t;

SELECT r.original_filename,
       t.id AS transaction_id,
       t.itemize_status
FROM receipts r
LEFT JOIN expense_transactions t ON t.receipt_id = r.id
ORDER BY r.created_at;

SELECT t.merchant, x.name, x.rate, x.amount
FROM tax_lines x
JOIN expense_transactions t ON t.id = x.transaction_id;

SELECT description, amount
FROM line_items
ORDER BY description;

SELECT t.grand_total,
       (SELECT sum(amount) FROM line_items i WHERE i.transaction_id = t.id) AS items,
       (SELECT sum(amount) FROM tax_lines x WHERE x.transaction_id = t.id) AS taxes
FROM expense_transactions t;
