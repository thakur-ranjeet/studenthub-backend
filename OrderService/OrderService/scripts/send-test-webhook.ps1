<#
  Sends a correctly SIGNED fake Razorpay webhook to your local OrderService, so you can test webhooks
  without a public URL or ngrok.

  Needs the RAZORPAY_WEBHOOK_SECRET environment variable (the same value OrderService uses).
  Open a NEW PowerShell window after setting it, and run this from the OrderService\scripts folder.

  Examples
    # a customer paid, but their browser closed before the order was confirmed
    .\send-test-webhook.ps1 -Event payment.captured -RazorpayOrderId order_XXXXXXXX -AmountPaise 499900

    # a refund finished
    .\send-test-webhook.ps1 -Event refund.processed -PaymentId pay_XXXXXXXX -RefundId rfnd_XXXXXXXX -AmountPaise 499900

    # prove a forged webhook is rejected (expect HTTP 400)
    .\send-test-webhook.ps1 -Event payment.captured -RazorpayOrderId order_XXXXXXXX -AmountPaise 499900 -BadSignature
#>
param(
  [ValidateSet("payment.captured", "order.paid", "refund.processed", "refund.failed")] [string]$Event = "payment.captured",
  [string]$RazorpayOrderId = "",
  [string]$PaymentId = "pay_TESTWEBHOOK0001",
  [string]$RefundId = "rfnd_TESTWEBHOOK0001",
  [Parameter(Mandatory = $true)] [long]$AmountPaise,
  [string]$EventId = "",
  [string]$Url = "http://localhost:8085/api/v1/webhooks/razorpay",
  [switch]$BadSignature
)

$secret = $env:RAZORPAY_WEBHOOK_SECRET
if ([string]::IsNullOrEmpty($secret)) {
  throw "Set the RAZORPAY_WEBHOOK_SECRET environment variable first, then open a NEW PowerShell window."
}
if ([string]::IsNullOrEmpty($EventId)) { $EventId = "evt_" + [guid]::NewGuid().ToString("N") }

$payment = @{ entity = @{ id = $PaymentId; entity = "payment"; amount = $AmountPaise; currency = "INR"; status = "captured"; order_id = $RazorpayOrderId } }

if ($Event -like "refund.*") {
  $refundStatus = "failed"
  if ($Event -eq "refund.processed") { $refundStatus = "processed" }
  $payload = @{
    refund  = @{ entity = @{ id = $RefundId; entity = "refund"; amount = $AmountPaise; currency = "INR"; payment_id = $PaymentId; status = $refundStatus } }
    payment = $payment
  }
}
elseif ($Event -eq "order.paid") {
  $payload = @{ payment = $payment; order = @{ entity = @{ id = $RazorpayOrderId; amount = $AmountPaise; status = "paid" } } }
}
else {
  $payload = @{ payment = $payment }
}

$body = @{
  entity     = "event"
  event      = $Event
  payload    = $payload
  created_at = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
} | ConvertTo-Json -Depth 10 -Compress

# the signature is computed over the exact bytes that are sent (this is why OrderService reads the RAW body)
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$keyBytes = [System.Text.Encoding]::UTF8.GetBytes($secret)
$hmac = New-Object -TypeName System.Security.Cryptography.HMACSHA256 -ArgumentList (, $keyBytes)
$signature = ($hmac.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
if ($BadSignature) { $signature = "0" * 64 }

Write-Host "Sending $Event (event id $EventId) to $Url"
try {
  $response = Invoke-WebRequest -Uri $Url -Method Post -Body $bytes -ContentType "application/json" `
    -Headers @{ "X-Razorpay-Signature" = $signature; "X-Razorpay-Event-Id" = $EventId } -UseBasicParsing
  Write-Host "HTTP $($response.StatusCode): $($response.Content)"
}
catch {
  if ($_.Exception.Response) { Write-Host ("HTTP " + [int]$_.Exception.Response.StatusCode + " (rejected)") }
  else { Write-Host $_.Exception.Message }
}
