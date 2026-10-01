# Changelog

## 0.0.7

- Balancer now tracks real two-way traffic instead of just "VPN interface came up," and fails over automatically when a profile serves one-way traffic only. Same health tracking added to the SOCKS5 path, which had none before.
- One-way-traffic now confirms in ~7-10s (was ~20-25s), with a notification and a home-screen status for it.
- Fixed "stop tunnel first" error from switching profiles quickly.
- VPN and SOCKS5 are now separate modes in Settings; removed "start on boot."
- Balancer moved out of Settings into the profile picker as its own entry, showing which profile it's actually connected through.
- Animated screen transitions and list items; full visual pass on the Deploy screens and the split-tunnel screens.
- Fixed profile rows stretching vertically when auto-reconnect and the cookie badge were both shown.
- Cookie push failures now show the real reason (e.g. "could not reach the controlplane") instead of a generic "failed."
- Smaller profile row action buttons; dropped the crowding "auto-reconnect" label.
- Removed the MTS transport option from "Original" mode (still available under "Fork").
