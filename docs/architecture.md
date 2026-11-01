# NOVA VPN architecture

## Two separate planes

### Data plane

The data plane carries user packets through a WireGuard tunnel:

1. The Android client creates a WireGuard tunnel through the platform VPN API.
2. WireGuard encrypts packets for the selected gateway.
3. The Linux gateway decrypts packets, applies forwarding/firewall policy, and routes approved traffic to the internet.
4. Return traffic is routed back through the tunnel.

The control API does not carry user browsing traffic.

### Control plane

The Go API will manage:

- Account authentication and authorization.
- Device registration and revocation.
- Gateway catalogue and health state.
- Per-device WireGuard peer provisioning.
- Short-lived configuration delivery and key rotation metadata.
- Abuse controls, quotas, and audit events that exclude sensitive traffic data.

The initial API intentionally exposes only liveness and an empty server catalogue. It does not provision peers or claim that a gateway is online.

## Android client boundary

Use Android `VpnService` and a maintained WireGuard implementation. The Flutter layer owns presentation and user workflows; a Kotlin platform layer owns Android VPN lifecycle, permission consent, foreground-service requirements, and tunnel state callbacks.

The app must distinguish at least: disconnected, permission-required, connecting, connected, reconnecting, and error. "Connected" must come from the tunnel engine and verified routing state, not a successful HTTP response.

## Gateway requirements before public launch

- Dedicated supported Linux VPS with a static public endpoint.
- WireGuard interface and unique peer keys.
- IPv4 forwarding and carefully scoped NAT/firewall policy.
- IPv6 policy that either routes IPv6 through the tunnel or blocks it safely; never silently leak it.
- DNS resolver reachable only through the tunnel, with leak tests.
- MTU validation, rate limits, monitoring, patching, and abuse response.
- No default route to a gateway until end-to-end tests pass.

## Security boundaries

- Generate client private keys on the client where feasible; send only public keys to the control plane.
- Protect configuration delivery with authenticated TLS and device authorization.
- Do not put private keys in logs, analytics, crash reports, or source control.
- Revoke peers when devices are removed or credentials are compromised.
- A kill switch is platform- and OS-version-dependent and must be validated on physical devices.
- Conduct independent security review before handling real users or sensitive traffic.

## Delivery sequence

1. Implement Android WireGuard integration and tunnel state reporting.
2. Provision one test gateway and document reproducible setup.
3. Establish a test peer and verify full-tunnel IPv4/IPv6/DNS behavior.
4. Add authenticated control-plane operations and device lifecycle.
5. Add multi-gateway discovery, health checks, and operational observability.
6. Run automated and physical-device leak, reconnect, sleep/wake, and failure tests.
7. Only then consider public beta and additional platforms.
