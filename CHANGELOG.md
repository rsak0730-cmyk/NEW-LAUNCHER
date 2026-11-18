# Liquid Glass Launcher – v2.0

## Glitches fixed
- Drawer no longer lets taps fall through to home icons underneath.
- Home grid can no longer cut off apps on small screens (rows are computed from real height; extra apps flow to new pages).
- Page count was stale when apps loaded late; pager now updates correctly.
- Battery was read once; now live (with charging state).
- Swipe-up fired on every drag event; now a single clean gesture (up = drawer, down = notifications).
- Pressing Home now closes drawer/menus and returns to the main page (onNewIntent).
- Back button closes menu → theme engine → folder → drawer in order.
- Icon buttons were clipped (48dp button in 46dp pill); replaced with proper glass buttons.
- Glass rim gradient now scales with surface size (was hard-coded 400px).
- Wallpaper, theme, dock, hidden apps and usage now persist (wallpaper uses persistable URI permission).
- App list auto-refreshes on install/uninstall/update.
- Icons/colours are pre-computed off the main thread (no scroll jank); apps launch by exact activity with zoom animation.
- White flash on launch removed (dark window theme), no-recents/clearTask quirks removed, config changes handled without recreate.
- Missing launcher icon, proguard file, and uninstall permission added.

## New
- Living wallpaper (aurora blobs, dot grid, scan line, bubbles) with gyro + page parallax; custom image gets parallax too.
- Glass 2.0: gyro specular glare, sheen sweep, bevel rim; jelly squash/stretch touch, pop-in, idle bobbing.
- Widgets: rolling-digit clock with seconds ring, liquid-wave battery, RAM/Storage gauges, quick tiles (torch, Wi-Fi, Bluetooth, Settings), frequent apps.
- Control Center page left of home; 3D glass page transitions.
- Drawer: blurred/scaled home behind (Android 12+), A–Z fast scroller with magnified bubble, web-search fallback, pull-down to close, enter launches single match.
- Long-press menu: open, app info, uninstall, add/remove dock, hide app.
- Theme Engine: presets, secondary hue, icon size, 7 effect toggles, restore hidden apps, wallpaper controls.
