import assert from "node:assert/strict";
import test from "node:test";
import { PiyokeyWebTelemetry, sanitizeStack, type PostHogClient } from "../src/index.js";

class FakePostHog {
  initCalls: unknown[][] = [];
  captures: unknown[][] = [];
  exceptions: unknown[][] = [];
  optedIn = 0;
  optedOut = 0;

  init(...args: unknown[]) { this.initCalls.push(args); return this; }
  capture(...args: unknown[]) { this.captures.push(args); }
  captureException(...args: unknown[]) { this.exceptions.push(args); }
  opt_in_capturing() { this.optedIn += 1; }
  opt_out_capturing() { this.optedOut += 1; }
}

const base = {
  projectToken: "phc_test",
  production: true,
  analyticsConsent: true,
  diagnosticsConsent: false,
  appVersion: "1.1.0",
  buildNumber: "8",
  locale: "en" as const,
};

test("configures privacy-first manual capture and validates the allowlist", () => {
  const fake = new FakePostHog();
  const telemetry = new PiyokeyWebTelemetry(fake as unknown as PostHogClient, undefined);
  telemetry.configure(base);
  const config = fake.initCalls[0]?.[1] as Record<string, unknown>;
  assert.equal(config.autocapture, false);
  assert.equal(config.disable_session_recording, true);
  assert.equal(config.advanced_disable_feature_flags, true);
  assert.equal(config.person_profiles, "never");
  telemetry.capture("feature_viewed", { feature: "web_landing" });
  assert.equal(fake.captures.length, 1);
  assert.throws(() => telemetry.capture("feature_viewed", { text: "typed content" }));
  assert.throws(() => telemetry.capture("feature_viewed", {}));
  assert.throws(() => telemetry.capture("feature_viewed", { feature: "typed free text" }));
});

test("missing token and development builds are no-op", () => {
  const fake = new FakePostHog();
  const telemetry = new PiyokeyWebTelemetry(fake as unknown as PostHogClient, undefined);
  telemetry.configure({ ...base, projectToken: undefined });
  telemetry.capture("app_opened", { entry_point: "cold_start" });
  assert.equal(fake.initCalls.length, 0);
  assert.equal(fake.captures.length, 0);
});

test("stack sanitizer removes messages, origins, query strings, and fragments", () => {
  const sanitized = sanitizeStack(
    "TypeError: secret typed text\n    at submit (https://example.com/app.js?email=a@b.com#x:12:3)",
    "TypeError",
  );
  assert.equal(sanitized?.includes("secret typed text"), false);
  assert.equal(sanitized?.includes("example.com"), false);
  assert.equal(sanitized?.includes("email"), false);
  assert.match(sanitized ?? "", /<origin>\/app\.js/);
});
