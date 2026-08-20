# Security Model

## Fail-closed decisions

- Invalid TLS certificates are canceled.
- Client certificate requests are canceled.
- HTTP authentication prompts are canceled.
- Web permission requests are denied.
- Geolocation requests are denied.
- File chooser requests are canceled.
- Downloads are disabled.
- Custom URI schemes are canceled.
- Mixed HTTP/HTTPS content is prohibited.
- Cleartext traffic is prohibited by both manifest and Network Security Config.

## Android capability boundary

The app requests only `android.permission.INTERNET` and intentionally does not request device-sensitive runtime permissions.

## Reporting

Do not include real secrets, credentials, private browsing data, or personal identifiers in security reports.
