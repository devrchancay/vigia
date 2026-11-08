import type { Connection } from "./config.js";

/** A config document; its shape is validated by the panel, so the CLI keeps it loose. */
export type PanelConfig = { schemaVersion: number; widgets?: Widget[]; [key: string]: unknown };

export interface Widget {
  id: string;
  type: string;
  pos: { x: number; y: number; w: number; h: number };
  title?: string;
  params?: Record<string, unknown>;
}

export interface PanelEvent {
  source: string;
  title: string;
  key?: string;
  kind?: string;
  body?: string;
  level?: "info" | "ok" | "warn" | "error";
  ttlSec?: number;
  url?: string;
  at?: string;
}

export interface ApiErrorDetail {
  code: string;
  message: string;
  path?: string;
}

/** An error answer from the panel, with every validation problem and its JSON path. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly errors: ApiErrorDetail[],
  ) {
    super(errors.map((e) => (e.path ? `${e.path}: ${e.message}` : e.message)).join("\n") || `HTTP ${status}`);
    this.name = "ApiError";
  }
}

type Fetch = typeof fetch;

/** Thin typed wrapper over the panel's HTTP API (docs/SPEC.md section 5). */
export class VigiaClient {
  constructor(
    private readonly connection: Connection,
    private readonly fetchImpl: Fetch = fetch,
    private readonly author = "vigia-cli",
  ) {}

  health(): Promise<{ status: string; version: string; uptimeSec: number }> {
    return this.json("GET", "/health");
  }

  getConfig(): Promise<PanelConfig> {
    return this.json("GET", "/config");
  }

  putConfig(config: PanelConfig): Promise<unknown> {
    return this.json("PUT", "/config", config);
  }

  /** JSON Merge Patch (RFC 7396). */
  patchConfig(patch: Record<string, unknown>): Promise<unknown> {
    return this.json("PATCH", "/config", patch);
  }

  validateConfig(config: PanelConfig): Promise<{ valid: boolean }> {
    return this.json("POST", "/config/validate", config);
  }

  rollback(): Promise<unknown> {
    return this.json("POST", "/config/rollback");
  }

  history(): Promise<unknown[]> {
    return this.json("GET", "/config/history");
  }

  status(): Promise<Record<string, unknown>> {
    return this.json("GET", "/status");
  }

  widgetTypes(): Promise<unknown[]> {
    return this.json("GET", "/widgets/types");
  }

  sendEvents(events: PanelEvent | PanelEvent[]): Promise<{ accepted: number }> {
    return this.json("POST", "/events", events);
  }

  listSecrets(): Promise<{ names: string[] }> {
    return this.json("GET", "/secrets");
  }

  async setSecret(name: string, value: string): Promise<void> {
    await this.request("PUT", `/secrets/${encodeURIComponent(name)}`, value, "text/plain");
  }

  async deleteSecret(name: string): Promise<void> {
    await this.request("DELETE", `/secrets/${encodeURIComponent(name)}`);
  }

  async screenshot(): Promise<Uint8Array> {
    const response = await this.request("GET", "/screenshot");
    return new Uint8Array(await response.arrayBuffer());
  }

  private async json<T>(method: string, path: string, body?: unknown): Promise<T> {
    const response = await this.request(method, path, body === undefined ? undefined : JSON.stringify(body), "application/json");
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  private async request(method: string, path: string, body?: string, contentType?: string): Promise<Response> {
    const headers: Record<string, string> = {
      Authorization: `Bearer ${this.connection.token}`,
      "X-Vigia-Author": this.author,
    };
    if (body !== undefined && contentType) headers["Content-Type"] = contentType;
    let response: Response;
    try {
      response = await this.fetchImpl(`${this.connection.url}/api/v1${path}`, {
        method,
        headers,
        ...(body === undefined ? {} : { body }),
        signal: AbortSignal.timeout(15_000),
      });
    } catch (cause) {
      throw new Error(`Cannot reach the panel at ${this.connection.url} (${(cause as Error).message})`, { cause });
    }
    if (!response.ok) throw await toApiError(response);
    return response;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { error?: ApiErrorDetail; errors?: ApiErrorDetail[] };
    return new ApiError(response.status, body.errors ?? (body.error ? [body.error] : []));
  } catch {
    return new ApiError(response.status, [{ code: "http_error", message: `HTTP ${response.status}` }]);
  }
}
