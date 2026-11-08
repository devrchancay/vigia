import { mkdir, readFile, writeFile, chmod } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

/** Where the CLI keeps the panel address and token (written by `vigia login`). */
export interface Connection {
  url: string;
  token: string;
}

export function configPath(env: NodeJS.ProcessEnv = process.env): string {
  const base = env.XDG_CONFIG_HOME ?? join(homedir(), ".config");
  return join(base, "vigia", "config.json");
}

/**
 * Accepts the pairing URI from the setup screen QR (`vigia://pair?url=...&token=...`) or a plain base URL,
 * and returns the API base without a trailing slash.
 */
export function parseLogin(target: string, token?: string): Connection {
  if (target.startsWith("vigia://")) {
    const uri = new URL(target);
    const url = uri.searchParams.get("url");
    const paired = uri.searchParams.get("token");
    if (!url || !paired) throw new Error("Pairing URI must contain url and token");
    return { url: normalizeUrl(url), token: paired };
  }
  if (!token) throw new Error("Usage: vigia login <url> <token>, or vigia login 'vigia://pair?...'");
  return { url: normalizeUrl(target), token };
}

function normalizeUrl(url: string): string {
  const parsed = new URL(url);
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") throw new Error(`Not an http(s) URL: ${url}`);
  return url.replace(/\/+$/, "");
}

/** VIGIA_URL and VIGIA_TOKEN override the saved file, which is handy in CI and agent sandboxes. */
export async function loadConnection(env: NodeJS.ProcessEnv = process.env): Promise<Connection> {
  if (env.VIGIA_URL && env.VIGIA_TOKEN) return { url: normalizeUrl(env.VIGIA_URL), token: env.VIGIA_TOKEN };
  let saved: Partial<Connection> = {};
  try {
    saved = JSON.parse(await readFile(configPath(env), "utf8")) as Partial<Connection>;
  } catch {
    throw new Error("Not logged in. Run: vigia login <url> <token> (or set VIGIA_URL and VIGIA_TOKEN)");
  }
  if (!saved.url || !saved.token) throw new Error(`Incomplete config at ${configPath(env)}; run vigia login again`);
  return { url: env.VIGIA_URL ? normalizeUrl(env.VIGIA_URL) : saved.url, token: env.VIGIA_TOKEN ?? saved.token };
}

/** Saves with owner-only permissions: the file holds the bearer token. */
export async function saveConnection(connection: Connection, env: NodeJS.ProcessEnv = process.env): Promise<string> {
  const path = configPath(env);
  await mkdir(dirname(path), { recursive: true, mode: 0o700 });
  await writeFile(path, JSON.stringify(connection, null, 2) + "\n", { mode: 0o600 });
  await chmod(path, 0o600);
  return path;
}
