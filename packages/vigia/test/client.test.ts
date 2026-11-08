import { describe, expect, it, vi } from "vitest";
import { ApiError, VigiaClient } from "../src/client.js";

const connection = { url: "http://127.0.0.1:8787", token: "t0ken" };

function fakeFetch(status: number, body: unknown, contentType = "application/json") {
  return vi.fn(async () => new Response(typeof body === "string" ? body : JSON.stringify(body), { status, headers: { "Content-Type": contentType } }));
}

describe("VigiaClient", () => {
  it("sends the token, author and JSON body to the API path", async () => {
    const fetchMock = fakeFetch(200, { config: {} });
    await new VigiaClient(connection, fetchMock as typeof fetch).patchConfig({ theme: { palette: "teal" } });

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://127.0.0.1:8787/api/v1/config");
    expect(init.method).toBe("PATCH");
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer t0ken");
    expect((init.headers as Record<string, string>)["X-Vigia-Author"]).toBe("vigia-cli");
    expect(init.body).toBe('{"theme":{"palette":"teal"}}');
  });

  it("turns 422 bodies into ApiError with every path", async () => {
    const fetchMock = fakeFetch(422, {
      error: { code: "out_of_range", message: "1-24", path: "grid.columns" },
      errors: [
        { code: "out_of_range", message: "1-24", path: "grid.columns" },
        { code: "overlap", message: "Overlaps 'clock'", path: "widgets[1].pos" },
      ],
    });

    const error = await new VigiaClient(connection, fetchMock as typeof fetch).putConfig({ schemaVersion: 1 }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toBe("grid.columns: 1-24\nwidgets[1].pos: Overlaps 'clock'");
  });

  it("sends secrets as plain text and returns screenshots as bytes", async () => {
    const fetchMock = vi.fn(async () => new Response(new Uint8Array([0x89, 0x50]), { status: 200 }));
    const client = new VigiaClient(connection, fetchMock as typeof fetch);

    await client.setSecret("x", "value");
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(init.body).toBe("value");
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("text/plain");
    expect(Array.from(await client.screenshot())).toEqual([0x89, 0x50]);
  });

  it("explains unreachable panels", async () => {
    const fetchMock = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    await expect(new VigiaClient(connection, fetchMock as typeof fetch).health()).rejects.toThrow(/Cannot reach the panel/);
  });
});
