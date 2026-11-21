# OrderService

Wishlist, cart, checkout (Razorpay), orders, enrolments, **webhooks, refunds and receipts** for StudentHub. Port **8085**.

## Setup
1. In STS: **File → Import → Maven → Existing Maven Projects**, choose the folder that contains `pom.xml`.
2. Windows environment variables (restart STS after changing any of them):

   | Variable | Needed | What it is |
   |---|---|---|
   | `DB_PASSWORD` | yes | MySQL password |
   | `JWT_SECRET` | yes | same value as the API gateway |
   | `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | yes | Razorpay **Test Mode** API keys |
   | `RAZORPAY_WEBHOOK_SECRET` | optional | the secret you type when you create the webhook in the Razorpay dashboard (different from the API key secret). Without it the webhook endpoint answers 503 and everything else works. |

3. The `orderdb` database and all tables are created automatically (new columns are added to existing tables).
4. Start order: Eureka, Student, Course, Notification, **OrderService**, API Gateway.

## Endpoints (through the gateway on 8083)
| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/shop/state` | signed-in user (wishlist, cart, owned course ids) |
| GET / POST / DELETE | `/api/v1/shop/wishlist`, `/wishlist/{courseId}` | signed-in user |
| POST | `/api/v1/shop/wishlist/{courseId}/move-to-cart` | signed-in user |
| GET / DELETE | `/api/v1/shop/cart` | signed-in user |
| POST / DELETE | `/api/v1/shop/cart/{courseId}` | signed-in user (refuses unpriced or owned courses) |
| GET | `/api/v1/shop/config` | signed-in user (public Razorpay key id, test/live) |
| POST | `/api/v1/shop/checkout` | creates the order from the server-side cart and prices |
| POST | `/api/v1/shop/checkout/verify` | checks Razorpay's signature, then unlocks the courses |
| GET | `/api/v1/shop/orders` | the user's own orders |
| GET | `/api/v1/shop/orders/{id}/receipt` | owner or administrator, paid orders only |
| GET | `/api/v1/shop/my-courses` | the user's owned courses |
| GET | `/api/v1/shop/admin/orders` | administrators: every order |
| POST | `/api/v1/shop/admin/orders/{id}/refund` | administrators: full refund |
| POST | `/api/v1/shop/admin/orders/{id}/refund/sync` | administrators: ask Razorpay for the refund state |
| POST | `/api/v1/webhooks/razorpay` | **public**, protected by Razorpay's signature (no JWT) |

## How a payment stays safe
1. The browser never sends a price. The server reads the cart and prices from the Course service.
2. The server creates the Razorpay order (amount in **paise**) and stores it as `CREATED`.
3. **Confirmation path 1 (browser):** after the payment window succeeds, the server checks
   `HMAC-SHA256(order_id + "|" + payment_id, key_secret)` against Razorpay's signature (constant-time compare).
4. **Confirmation path 2 (webhook):** Razorpay calls `/api/v1/webhooks/razorpay` by itself, so a purchase still unlocks if the
   customer closed the browser. The signature is `HMAC-SHA256(RAW body, webhook_secret)`. The handler also
   - ignores repeated deliveries (`X-Razorpay-Event-Id` is stored),
   - ignores an event whose amount or currency does not match the order WE created,
   - only acts on `captured` payments.
5. Both paths share one `fulfill` method, so confirming twice is harmless.

## Refunds
Administrators refund a **whole** paid order from *All orders*. The server calls Razorpay's refund API with an idempotency key
(one per order, so a retry can never refund twice). When the refund is `processed` the order becomes `REFUNDED` and the
courses are taken back. If Razorpay answers `pending`, the order shows "Refund pending" until the `refund.processed`
webhook arrives or an administrator clicks **Check refund status**.

## Receipts
`/orders/{id}/receipt` in the app is a printable page (receipt no. `SH-<year>-<order id>`). Use **Print / Save as PDF**.
It is a payment receipt, not a GST tax invoice. E-mailing receipts is not built (it needs an SMTP account).

## Webhook setup (Razorpay Dashboard, Test Mode)
Razorpay cannot call `localhost`, and its docs list `ngrok.io` and `localhost` among the blocked webhook domains. For a real
webhook you need a public HTTPS URL, for example a **zrok** tunnel (the tunnel Razorpay's docs suggest) to port 8085.
1. Start the tunnel to `http://localhost:8085`. Your webhook URL is `https://<your-tunnel-host>/api/v1/webhooks/razorpay`.
2. Dashboard (Test Mode) → **Account & Settings → Webhooks → Add New Webhook**.
3. Paste the URL, choose a secret (any long random text) and tick the events
   `payment.captured`, `order.paid`, `refund.processed`, `refund.failed`.
4. In Test Mode, when it asks for an OTP, Razorpay's docs say to enter `754081`.
5. Save the same secret as the Windows variable `RAZORPAY_WEBHOOK_SECRET` and restart STS.

### Test webhooks without any tunnel
`scripts/send-test-webhook.ps1` sends a correctly signed fake webhook straight to `localhost:8085`:
```powershell
.\send-test-webhook.ps1 -Event payment.captured -RazorpayOrderId order_XXXX -AmountPaise 499900
.\send-test-webhook.ps1 -Event payment.captured -RazorpayOrderId order_XXXX -AmountPaise 499900 -BadSignature   # expect HTTP 400
```

## Not built
- E-mailed receipts, partial refunds, GST invoices, a customer-facing "request a refund" button.
