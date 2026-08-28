# PIYOKEY web analytics adapter

This package is ready to be imported by the future web application. The repository currently has no web product source, so it intentionally does not create or deploy a website.

Initialize `PiyokeyWebTelemetry` only with production environment variables. Consent must come from the web privacy settings or consent UI; both values default to `false`. The adapter disables autocapture, page views, heatmaps, session recording, performance capture, surveys, feature flags, and person profiles. It manually captures sanitized browser exceptions only while diagnostics consent is on.

Production builds must upload source maps with the PostHog CLI and then remove public `.map` files from deployed assets. See `docs/ANALYTICS.md` for the release gate.
