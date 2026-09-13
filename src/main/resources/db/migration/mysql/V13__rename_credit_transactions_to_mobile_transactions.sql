-- credit_transactions has only ever recorded mobile/web-originated activity (Braintree purchases
-- and web-UI queue spends) -- UserServiceImpl.deductCredits explicitly skips the local walk-up
-- (JFC/Swing) user, so no row here has ever come from the physical kiosk. Sitting right next to
-- the local_credit_transactions table (see V12__init_financial_ledger_schema.sql; split into
-- local_cash_transactions/local_credit_transactions by V14), that name was easy to confuse with
-- the local tables it has nothing to do with. Renamed to reflect what it actually holds; no
-- column changes, and no application code references the table name directly
-- (CreditTransactionEntity's own persistence goes through entityManager.persist()/merge(), not
-- native SQL, so only its @Table annotation needed updating alongside this rename).
RENAME TABLE credit_transactions TO mobile_transactions;
