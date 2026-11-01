# NOVA VPN

A WireGuard-based VPN product. The repository starts with an Android-first client strategy and a Go control-plane API.

> **Status:** Foundation in progress. This is not yet a deployable public VPN service. A real VPN connection requires provisioned WireGuard gateways, client tunnel integration, routing, DNS handling, and end-to-end security tests.

## Product goals

- Real WireGuard encrypted tunnels; never simulate a connected state.
- Android-first client, with a path to iOS, Windows, and Linux.
- Go control plane for authentication, device registration, server discovery, and configuration delivery.
- Secure key lifecycle: private keys stay on the client/device wherever possible.
- Explicit tunnel, DNS, IPv4/IPv6, and kill-switch verification.

## Initial architecture

- **Client:** Flutter UI + Android Kotlin platform integration using Android `VpnService` and the official WireGuard userspace implementation.
- **Data plane:** WireGuard gateways on hardened Ubuntu Linux hosts.
- **Control plane:** Go HTTP API.
- **Persistence:** PostgreSQL; Redis only where caching or coordination is needed.
- **Operations:** Docker for control-plane packaging; native Linux networking for WireGuard gateways; metrics and structured logs.

## Repository layout

```
backend/       Go control-plane API
docs/          Architecture, security, and deployment notes
mobile/        Mobile client (next implementation phase)
infra/         Gateway and deployment configuration (next implementation phase)
```

## Local backend development

Requirements: Go 1.22 or newer.

```bash
cd backend
go run ./cmd/api
```

The API listens on `127.0.0.1:8080` by default.

- `GET /healthz` — process liveness only; does not claim VPN readiness.
- `GET /api/v1/servers` — configured server catalogue. Returns an empty list until real gateways are registered.

Optional environment variables are documented in `backend/.env.example`.

## Security principles

1. Do not invent or implement a proprietary VPN cryptographic protocol.
2. Do not log private keys, WireGuard configuration secrets, credentials, or user traffic.
3. Never report a tunnel as connected based only on an API response.
4. Do not advertise DNS leak protection, kill switch, or IPv6 protection until verified on supported devices.
5. Gateway provisioning must use least privilege, firewall rules, forwarding/NAT, and monitored patching.
6. Keep production secrets out of Git; use a secret manager or deployment environment.

## Roadmap

- [x] Repository foundation and architecture notes
- [x] Minimal Go API with honest health and server-catalogue endpoints
- [ ] Android WireGuard tunnel integration
- [ ] Gateway provisioning and real server registration
- [ ] Device identity, authentication, and short-lived configuration delivery
- [ ] DNS/IPv6 leak handling and kill-switch behavior
- [ ] Automated integration tests on a real gateway
- [ ] Production deployment, monitoring, and security review

See [docs/architecture.md](docs/architecture.md) for the implementation boundaries and next milestones.
