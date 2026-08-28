import { posthog } from "posthog-js";
import {
  accepts,
  ANALYTICS_SCHEMA_VERSION,
  type AnalyticsEvent,
} from "./contract.generated.js";

export interface WebTelemetryOptions {
  projectToken?: string;
  apiHost?: string;
  production: boolean;
  analyticsConsent: boolean;
  diagnosticsConsent: boolean;
  appVersion: string;
  buildNumber: string;
  locale: "ja" | "en" | "ko" | "other";
}

interface BrowserErrorTarget {
  addEventListener(type: "error" | "unhandledrejection", listener: EventListener): void;
  removeEventListener(type: "error" | "unhandledrejection", listener: EventListener): void;
}

interface PostHogEvent {
  event: string;
  properties?: Record<string, unknown>;
}

interface PrivacyFirstPostHogConfig {
  api_host: string;
  defaults: "2026-05-30";
  person_profiles: "never";
  autocapture: false;
  capture_pageview: false;
  capture_pageleave: false;
  capture_heatmaps: false;
  capture_performance: false;
  capture_dead_clicks: false;
  disable_session_recording: true;
  disable_surveys: true;
  enable_recording_console_log: false;
  advanced_disable_feature_flags: true;
  opt_out_capturing_by_default: boolean;
  before_send: (event: PostHogEvent | null) => PostHogEvent | null;
}

export interface PostHogClient {
  init(projectToken: string, config: PrivacyFirstPostHogConfig): unknown;
  capture(event: string, properties?: Record<string, unknown>): unknown;
  captureException(error: Error, properties?: Record<string, unknown>): unknown;
  opt_in_capturing(): void;
  opt_out_capturing(): void;
}

const URL_PROPERTY = /(url|href|referrer|pathname|title)/i;
const SAFE_ERROR_NAMES = new Set([
  "Error",
  "TypeError",
  "RangeError",
  "ReferenceError",
  "SyntaxError",
  "URIError",
  "EvalError",
]);

export class PiyokeyWebTelemetry {
  private configured = false;
  private analyticsConsent = false;
  private diagnosticsConsent = false;
  private options?: WebTelemetryOptions;
  private listenersAttached = false;

  constructor(
    private readonly client: PostHogClient = posthog as unknown as PostHogClient,
    private readonly errorTarget: BrowserErrorTarget | undefined =
      typeof window === "undefined" ? undefined : window,
  ) {}

  configure(options: WebTelemetryOptions): void {
    this.options = options;
    this.analyticsConsent = options.analyticsConsent;
    this.diagnosticsConsent = options.diagnosticsConsent;
    if (!options.production || !options.projectToken) return;

    this.client.init(options.projectToken, {
      api_host: options.apiHost ?? "https://eu.i.posthog.com",
      defaults: "2026-05-30",
      person_profiles: "never",
      autocapture: false,
      capture_pageview: false,
      capture_pageleave: false,
      capture_heatmaps: false,
      capture_performance: false,
      capture_dead_clicks: false,
      disable_session_recording: true,
      disable_surveys: true,
      enable_recording_console_log: false,
      advanced_disable_feature_flags: true,
      opt_out_capturing_by_default: !options.analyticsConsent && !options.diagnosticsConsent,
      before_send: (event) => {
        if (!event) return null;
        if (event.event !== "$exception" && !acceptsEventName(event.event)) return null;
        for (const key of Object.keys(event.properties ?? {})) {
          if (URL_PROPERTY.test(key)) delete event.properties?.[key];
        }
        if (event.properties) event.properties.$geoip_disable = true;
        return event;
      },
    });
    this.configured = true;
    this.applyConsent();
  }

  setConsent(analyticsConsent: boolean, diagnosticsConsent: boolean): void {
    this.analyticsConsent = analyticsConsent;
    this.diagnosticsConsent = diagnosticsConsent;
    this.applyConsent();
  }

  capture(event: AnalyticsEvent, properties: Record<string, unknown> = {}): void {
    if (!this.configured || !this.analyticsConsent || !this.options) return;
    const payload = { ...this.commonProperties(), ...properties };
    if (!accepts(event, payload)) throw new Error(`Rejected analytics properties for ${event}`);
    this.client.capture(event, payload);
  }

  private applyConsent(): void {
    if (!this.configured) return;
    if (this.analyticsConsent || this.diagnosticsConsent) this.client.opt_in_capturing();
    else this.client.opt_out_capturing();

    if (this.diagnosticsConsent && !this.listenersAttached) {
      this.errorTarget?.addEventListener("error", this.onError);
      this.errorTarget?.addEventListener("unhandledrejection", this.onUnhandledRejection);
      this.listenersAttached = true;
    } else if (!this.diagnosticsConsent && this.listenersAttached) {
      this.errorTarget?.removeEventListener("error", this.onError);
      this.errorTarget?.removeEventListener("unhandledrejection", this.onUnhandledRejection);
      this.listenersAttached = false;
    }
  }

  private readonly onError: EventListener = (event) => {
    if (!this.diagnosticsConsent || !(event instanceof ErrorEvent)) return;
    this.captureException(event.error, "window_error");
  };

  private readonly onUnhandledRejection: EventListener = (event) => {
    if (!this.diagnosticsConsent || !(event instanceof PromiseRejectionEvent)) return;
    this.captureException(event.reason, "unhandled_rejection");
  };

  private captureException(value: unknown, boundary: string): void {
    if (!this.options) return;
    const source = value instanceof Error ? value : new Error("UnhandledError");
    const error = new Error(SAFE_ERROR_NAMES.has(source.name) ? source.name : "Error");
    error.name = SAFE_ERROR_NAMES.has(source.name) ? source.name : "Error";
    error.stack = sanitizeStack(source.stack, error.name);
    this.client.captureException(error, {
      ...this.commonProperties(),
      error_boundary: boundary,
      $geoip_disable: true,
    });
  }

  private commonProperties(): Record<string, unknown> {
    if (!this.options) return {};
    return {
      schema_version: ANALYTICS_SCHEMA_VERSION,
      platform: "web",
      app_version: this.options.appVersion,
      build_number: this.options.buildNumber,
      locale: this.options.locale,
    };
  }
}

function acceptsEventName(value: string): value is AnalyticsEvent {
  return [
    "app_opened",
    "feature_viewed",
    "onboarding_step_completed",
    "deck_downloaded",
    "session_started",
    "session_completed",
    "session_abandoned",
    "game_result",
    "review_completed",
    "deck_maker_action",
    "purchase_flow",
    "setting_changed",
    "share_completed",
  ].includes(value);
}

export function sanitizeStack(stack: string | undefined, errorName: string): string | undefined {
  if (!stack) return undefined;
  const lines = stack.split("\n");
  return [
    `${errorName}: redacted`,
    ...lines.slice(1).map((line) => line
      .replace(/https?:\/\/[^/\s)]+/g, "<origin>")
      .replace(/[?#][^\s)]*/g, "")),
  ].join("\n");
}
