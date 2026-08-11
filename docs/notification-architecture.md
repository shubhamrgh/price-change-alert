# Notification and account architecture

```text
quote poller
    |
    v
AlertItemProcessor (short transaction)
    +-- pauses watch item
    +-- writes alert log
    +-- writes one NotificationDelivery per enabled channel
              |
              v
NotificationDispatchService (scheduled worker)
    +-- claims a row with a short lock lease
    +-- calls exactly one provider outside the transaction
    +-- marks SENT, FAILED, or PENDING with exponential backoff
              |
              +-- Web Push / VAPID
              +-- SMTP email
              +-- Telegram Bot API
              +-- Discord incoming webhook
```

The alert engine does not know provider protocols. Each provider implements
`NotificationSender`, reports whether it is configured, and returns whether a
failure is retryable. Delivery rows are independent, so an email outage does
not prevent mobile, Telegram, or Discord delivery.

Authentication is cookie-based: the browser receives only a random HttpOnly
token, while the database stores its SHA-256 hash and expiry. Private
controllers resolve the user from that session and never use a caller-supplied
owner ID. Session cleanup runs daily. Deployments behind a TLS proxy must
preserve forwarded HTTPS headers so the cookie receives the `Secure` attribute.
