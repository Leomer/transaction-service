# transaction-service

Transactions microservice: deposits, withdrawals, credit payments, credit card
charges, balance queries and movement history.
Spring Boot, WebFlux, RxJava 3, MongoDB.

## Requirements
- JDK 17, Maven, MongoDB
- config-server running on :8888
- account-service on :8082 and credit-service on :8083

## Run
1. Start MongoDB, config-server, customer-service, account-service and credit-service
2. `mvn spring-boot:run`
- Port: 8084 (from config-repo)
- Database: `transaction_db`
- Swagger UI: http://localhost:8084/swagger-ui.html

## Business rules
- Withdrawals require sufficient funds (validated atomically in account-service).
- Savings accounts have a monthly movement limit; checking accounts are unlimited.
- Fixed-term accounts allow a single movement per month, only on the day set in
  `business.fixed-term-day`.
- Charges are only allowed on credit cards and cannot exceed the credit limit.
- Payments cannot exceed the outstanding debt.

## Endpoints
| Method | Path | Description |
|---|---|---|
| POST | /transactions/deposits | Deposit into an account |
| POST | /transactions/withdrawals | Withdraw from an account |
| POST | /transactions/payments | Pay a credit product |
| POST | /transactions/charges | Charge a credit card |
| GET | /transactions/balance/accounts/{accountNumber} | Account balance |
| GET | /transactions/balance/credits/{creditNumber} | Credit balance and available credit |
| GET | /transactions | List all transactions |
| GET | /transactions/{id} | Find a transaction |
| GET | /transactions/product/{productNumber} | Movements of a product (latest first) |
| POST | /transactions/transfers | Transfer between accounts (own or third party) |
| GET | /transactions/product/{n}/between?from=&to= | Movements in a date range |
| GET | /transactions/product/{n}/last | Latest 10 movements |

Request body for operations:
{ "productNumber": "...", "amount": 100.00 }

## Transfers
- Same endpoint for own and third-party accounts; the response says `OWN` or `THIRD_PARTY`.
- The target is checked before debiting the source.
- If the credit to the target fails, the source is refunded (compensation).
  If the refund also fails, a CRITICAL log is written.
- Two movements are stored: `TRANSFER_OUT` and `TRANSFER_IN`.

## Fees
Each account has a number of free transactions per month. Beyond it, each
deposit or withdrawal pays a fee, applied in the same atomic balance update.
The fee is stored in the movement (`fee`), not as a separate row.

## Kafka
| Topic | Direction | Purpose |
|---|---|---|
| debit-payment-requests | consumes | Each request is processed as a withdrawal on the card's primary account |
| debit-payment-results | publishes | `COMPLETED\|paymentId\|balanceAfter` or `FAILED\|paymentId\|reason` |

## Third-party payments
Any credit product can be paid by anyone with its number; the payer is not
validated by design (Part III requirement).

## Design patterns
- **Clock injection:** time is a dependency, so time-based rules are testable.

## Documentation
See the `docs/` folder: OpenAPI contract and sequence diagram.
