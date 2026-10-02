# Consumer ProGuard/R8 rules for the FanMaker SDK.
#
# These are applied automatically to any app that consumes this AAR (wired via
# consumerProguardFiles in FanMaker/build.gradle). Only add rules here that are
# genuinely required — anything kept here is kept in every host app.

# --- AltBeacon RSSI filter (org.altbeacon:android-beacon-library) ------------
#
# RangedBeacon.getFilter() builds the filter reflectively:
#
#     BeaconManager.getRssiFilterImplClass().getConstructors()[0].newInstance()
#
# It needs a *public no-arg constructor*. R8 sees no direct caller of that
# constructor, so it strips it even though the class itself is retained
# (verified: RunningAverageRssiFilter survives as `W3.i` with zero members).
# newInstance() then throws, the exception is swallowed into
# "Could not construct RssiFilterImplClass <name>", mFilter stays null, and the
# next ranged sample NPEs on a beacon executor thread — which is uncaught, so
# it takes the whole host process down.
#
# AltBeacon ships its own proguard.txt inside the AAR, but that file is 0 bytes,
# so it protects nothing. Keeping all implementers rather than the two concrete
# classes means a future filter swap (or a host's own filter, set via
# BeaconManager.setRssiFilterImplClass) does not regress.
-keep interface org.altbeacon.beacon.service.RssiFilter
-keep class * implements org.altbeacon.beacon.service.RssiFilter {
    <init>();
}
