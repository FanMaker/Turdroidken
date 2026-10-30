### Unreleased
#### Added
- `present(context, path)`, `isPresenting` and `dismiss()`. The SDK starts and closes its own screen, so a host no longer builds the intent or needs to know the `fanMakerKey` extra.
- `logout()`, `clearSessionToken()` and `clearIdentifiers()`. There was previously no way to end a session; process death was the only reset.
- `openUrl(url)`, which reports whether the SDK took a link, and `openPath(path)` for a bare path. `handleUrl` still returns `Unit`, so hosts compiled against 4.0.x keep linking.
- `getInstance(context, key)`, which rebuilds an instance from persisted state, so a cold-start push tap works before the host has initialized the SDK.
- `enableForegroundServiceScanning()` on `FanMakerSDKBeaconManager` keeps beacons scanning in the background, behind AltBeacon's foreground service, with a 1.5 s scan twice a minute. The SDK builds the notification Android requires, or the host passes its own. A host no longer has to configure AltBeacon itself. A missing manifest entry is logged instead of crashing, and scanning waits for the location and Nearby devices grants, since starting the service without them crashes on Android 14+.

#### Fixed
- Hosts that minify no longer crash as soon as beacon ranging starts. `consumer-rules.pro` was empty, and R8 stripped the constructor AltBeacon calls reflectively.
- Setting `locationEnabled = true` after `onResume` sends the ping `onResume` missed, so the host's call order no longer matters.
- A skipped location ping names the condition that caused it, instead of always blaming permissions.
- Identifiers set by a host persist across process death; values passed to the constructor still win.
- Push destinations carrying an ordinary `https://` link to the site's own host or its `allowed_domains` now route into the SDK, and the webview activity validates any destination it is handed.
- A second launch for a key that already has a webview open hands its destination to that screen instead of stacking a copy.
- Deep links no longer compose a doubled slash, and a destination nobody opened expires after ten minutes.
- The webview no longer shows the host app's name above FanMaker content.
- The SDK version now reaches device records for third-party hosts. The webview sends the display name, app version, package name, OS version and device model NUX needs to record it.
- Deep links keep their percent-encoding. `q=a%26b` used to arrive as two parameters, and an encoded redirect URL was cut at its own first `&`. A query on the configured base URL is kept, with the link's after it, as on iOS.
- A link the fan taps on one of our pages that leads off first-party hosts opens in the system browser. The SSO sign-in tap, taps on a provider's own pages and redirects stay in the webview.

#### Security
- `FanMakerSDKWebView` is no longer exported. It had been since 1.4, with no intent filter, so any app on the device could open the FanMaker screen inside the host app. Starting it with an explicit intent or `present()` is unchanged. A host that gave it an intent filter in its own manifest must route those links through its own activity instead.
