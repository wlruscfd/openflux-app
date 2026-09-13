# OpenFlux Android app

**English** | [Русский](README.ru.md)

A fork of [p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux). A standalone
Android client with multiple switchable profiles, built on top of the same Go core
(`transport`, `tunnel`) as the desktop client/exit-node — via [`gomobile bind`](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)
instead of shelling out to the CLI binary. See [openflux-server](https://github.com/wlruscfd/openflux-server)'s
`mobile/` (the gomobile entry point) and `gateway/` (turns the Android `VpnService` TUN file
descriptor into TCP connections dialed through the tunnel — the system-wide-capture counterpart to
that repo's `socks5/` on desktop).

## One-time setup

You need an Android SDK + NDK (NDK version 27.x; see
[openflux-server](https://github.com/wlruscfd/openflux-server)'s README for the exact version).

```bash
export ANDROID_HOME="/path/to/your/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/<version>"
```

## Build

```bash
# 1. In your openflux-server checkout: builds mobile/ into an .aar.
#    If openflux-server and this repo are checked out side by side with
#    these exact folder names, the default output path just works; pass
#    this repo's app/libs directory explicitly otherwise:
#      ./build_android_aar.sh /path/to/openflux-app/app/libs
cd ../openflux-server && ./build_android_aar.sh

# 2. From here (this repo's root): builds the APK
cd ../openflux-app
gradle assembleDebug        # or ./gradlew if its wrapper zip can be downloaded here
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## What's not supported yet

- Only the Yandex Docs transport (the MAX/"oneme" transport pulls in a dependency —
  `github.com/wlynxg/anet` — whose `go:linkname` hook into the standard library doesn't build
  against every Go toolchain; there's no newer release fixing it, and the controlplane-managed
  "key" flow is Yandex-only anyway, so it isn't missed here).
- Generic UDP/QUIC passthrough — only TCP and DNS-over-TCP (for UDP/53) are relayed. Most
  software still works because it falls back to TCP, but HTTP/3 and UDP-only apps/games won't
  connect over the tunnel yet.
- IPv6 data path — IPv6 traffic is routed into the tunnel (so it doesn't leak outside it) but is
  then simply dropped rather than relayed.

## Split tunneling

Both kinds are configured globally (Settings) and apply to every profile:

- **Per-app** — the system VPN filters whole apps (Android's `VpnService` per-app routing).
- **Per-site** — the gateway routes by website instead: exclude a domain from the tunnel, or tunnel
  only the listed domains. Sites are identified by their TLS SNI, with the gateway's DNS cache
  covering SNI-less connections.

## Profiles

Each profile is either:
- **Access key** — a [controlplane](https://github.com/wlruscfd/openflux-server/tree/main/controlplane)
  base URL + key token. The app resolves this to a Yandex Docs URL via `POST /v1/resolve` (through
  `mobile.Mobile.resolveKey`) each time it connects, so the actual doc URL is never stored on-device.
- **Manual** — a raw Yandex Docs URL, for testing against your own exit node the way the desktop
  CLI's `--url` flag works.

Only one profile can be connected at a time (matches how a device has one active VPN connection).
Secrets (key token / doc URL) are stored in `EncryptedSharedPreferences`, backed by the Android
Keystore — never in the plain Room database alongside the rest of a profile's settings.

## Importing a profile from a link

`ProfileDeepLink.kt` implements `openflux://import?data=<base64url-json>` — tapping such a link
(from a browser, another app, or a QR code that encodes it) opens the app straight to the profile
editor, pre-filled from the link, for the user to review and save. Nothing is saved or connected
automatically, since the link is BROWSABLE and could come from an untrusted source. The JSON
payload (before base64url-encoding, no padding):

```json
{
  "name": "My profile",
  "mode": "key",
  "control_url": "https://control.example.com",
  "key_token": "key_...",
  "transport": "yandex",
  "doc_url": "",
  "max_token": "",
  "max_uid": 0,
  "mtu": 1400,
  "dns_upstream": "77.88.8.8",
  "auto_reconnect": true
}
```

`transport` (`"yandex"` or `"max"`) and `max_token`/`max_uid` only matter when `mode` is `"manual"` - see
`ManualTransport` in `data/Profile.kt`. A `"key"`-mode profile's transport comes from controlplane
itself, not the link.

`ProfileDeepLink.buildUri(profile)` builds the matching URI from a `Profile`, for whatever
generates these links (e.g. the controlplane admin panel or the ingest API's response).

## Contributors

- [FLAT447](https://github.com/FLAT447) — fixed split tunneling only listing system apps
  ([#1](https://github.com/wlruscfd/openflux-app/pull/1)); added Material You dynamic color and
  reworked the home screen UI ([#2](https://github.com/wlruscfd/openflux-app/pull/2)).
