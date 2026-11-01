# Android client

NOVA Android is a native Android client embedding the official WireGuard tunnel library.

## Current client behavior

- Imports a user-provided WireGuard `.conf` profile using Android's document picker.
- Parses the profile with WireGuard's official configuration parser.
- Requests Android VPN consent before starting the tunnel.
- Starts and stops the real WireGuard userspace backend.
- Does not persist imported profiles or private keys to app storage.
- Does not invent server locations, connection speed, or gateway availability.

## Requirements for a working connection

A valid profile must point to a real, reachable WireGuard server with a configured peer for this device. The server must have correct forwarding, firewall/NAT, DNS, and routing. This repository does not yet provision or operate a public VPN gateway.

## Build

GitHub Actions builds a debug APK on every relevant push and on manual workflow dispatch. Download the `nova-vpn-debug-apk` artifact from the completed workflow run.

A debug APK is suitable for device installation/testing, not Play Store distribution. A production release requires a managed signing key, release build configuration, privacy policy, gateway operations, and security review.

## Verification still required

- Test on physical Android devices and supported OS versions.
- Verify public IP and IPv4/IPv6 routing against a known test gateway.
- Verify DNS routing and leak behavior.
- Test reconnect, sleep/wake, network handover, and process death.
- Add a persistent VPN service/state recovery design before production use.
