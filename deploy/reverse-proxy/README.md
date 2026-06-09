# Public-exposure reverse proxy (pdfbook-web)

ADR-0009 keeps the Spring app minimal and puts the public-internet hardening at the **perimeter**.
This directory holds a version-controlled reference for that perimeter.

## Split of responsibility

| Concern | Owner | Where |
| --- | --- | --- |
| TLS termination | Reverse proxy | [`Caddyfile`](./Caddyfile) `tls` |
| Authentication | Reverse proxy | `basic_auth` (or forward-auth) |
| Rate / connection / body limits | Reverse proxy | `rate_limit`, `request_body` |
| No failure detail to clients | **App** | `SseProgressPublisher` (SSE) + `JobStatusResponse` (REST) send `kind` only |
| Cross-origin POST rejection (CSRF) | **App** | `SameOriginCsrfFilter` |
| Actuator off the public surface | **App** | prod profile: `management.server.port: 8081`, `address: 127.0.0.1` |
| Upload size cap | App + proxy | `spring.servlet.multipart.max-file-size: 512MB` ↔ proxy `max_size 512MB` |

The app changes are essential because a proxy cannot fix them: failure detail and CSRF reachability
are properties of the app's own responses, not of the transport.

## Run

```sh
# 1) Prod container, bound to loopback (actuator is on its own loopback :8081 inside the container).
docker run --rm -p 127.0.0.1:8080:8080 pdfbook-web

# 2) Set domain, ACME email, and a Basic-auth hash in the Caddyfile:
caddy hash-password            # paste the output into basic_auth

# 3) Proxy in front:
caddy run --config deploy/reverse-proxy/Caddyfile
```

## Notes

- **Rate limiting** is a Caddy plugin (`caddy-ratelimit`), so it ships commented out; build a binary
  that includes it with `xcaddy build --with github.com/mholt/caddy-ratelimit`. With **nginx**
  instead, use native `limit_req`/`limit_conn` zones plus `client_max_body_size 512m;` and
  `proxy_buffering off;` on the SSE location. **Important for nginx:** add
  `proxy_set_header Host $host;` — a bare `proxy_pass` rewrites `Host` to the upstream
  (`127.0.0.1:8080`), which the app's `SameOriginCsrfFilter` then sees as a host mismatch and
  **rejects every POST with 403**. Caddy's `reverse_proxy` preserves the original `Host` by default,
  so the `Caddyfile` here needs no such line.
- **Actuator** is never proxied (the prod profile puts it on a separate port, 8081). The prod
  profile binds it to the container's **loopback** (`management.server.address: 127.0.0.1`), so it
  is reachable only from *inside* the container — by design it is NOT exposed through the published
  app port (`-p 127.0.0.1:8080:8080` maps only 8080) and a host/proxy curl to `:8081` will fail.
  Health-check it from within the container, e.g.
  `docker exec <container> curl -fsS http://localhost:8081/actuator/health`, or add a Dockerfile
  `HEALTHCHECK` that does the same. For a monitoring sidecar on the Docker network, drop the
  loopback binding (`management.server.address: 0.0.0.0`) and leave port 8081 **unpublished** —
  reachable within the network, never public.
- The **self-contained jpackage app-image** is out of scope here: it has no proxy and runs the
  default (loopback) profile, for LAN/local desktop use only.
