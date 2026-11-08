import { mkdtemp, readFile, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { loadConnection, parseLogin, saveConnection } from "../src/config.js";

describe("parseLogin", () => {
  it("reads url and token from the setup screen pairing URI", () => {
    const uri = "vigia://pair?url=http%3A%2F%2F100.64.0.1%3A8787&token=abc";
    expect(parseLogin(uri)).toEqual({ url: "http://100.64.0.1:8787", token: "abc" });
  });

  it("accepts a plain url and token, without trailing slash", () => {
    expect(parseLogin("http://127.0.0.1:8787/", "t")).toEqual({ url: "http://127.0.0.1:8787", token: "t" });
  });

  it("rejects missing tokens and non-http urls", () => {
    expect(() => parseLogin("http://127.0.0.1:8787")).toThrow(/Usage/);
    expect(() => parseLogin("ftp://x", "t")).toThrow(/http/);
  });
});

describe("connection file", () => {
  it("saves owner-only and loads it back; env vars win", async () => {
    const env = { XDG_CONFIG_HOME: await mkdtemp(join(tmpdir(), "vigia-")) };
    const path = await saveConnection({ url: "http://127.0.0.1:8787", token: "t" }, env);

    expect((await stat(path)).mode & 0o777).toBe(0o600);
    expect(JSON.parse(await readFile(path, "utf8"))).toEqual({ url: "http://127.0.0.1:8787", token: "t" });
    expect(await loadConnection(env)).toEqual({ url: "http://127.0.0.1:8787", token: "t" });
    expect(await loadConnection({ ...env, VIGIA_TOKEN: "other" })).toEqual({ url: "http://127.0.0.1:8787", token: "other" });
  });

  it("explains how to log in when nothing is saved", async () => {
    const env = { XDG_CONFIG_HOME: await mkdtemp(join(tmpdir(), "vigia-")) };
    await expect(loadConnection(env)).rejects.toThrow(/vigia login/);
  });
});
