import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { describe, expect, it, vi } from "vitest";
import { ApiError, type VigiaClient } from "../src/client.js";
import { createMcpServer } from "../src/mcp.js";

async function connect(api: Partial<VigiaClient>) {
  const server = createMcpServer(api as VigiaClient, "test");
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  await server.connect(serverTransport);
  const client = new Client({ name: "test", version: "1" });
  await client.connect(clientTransport);
  return client;
}

describe("vigia MCP server", () => {
  it("exposes the panel tools and asks agents to validate and screenshot", async () => {
    const client = await connect({});
    const { tools } = await client.listTools();

    expect(tools.map((t) => t.name).sort()).toEqual(
      [
        "add_widget",
        "get_config",
        "get_status",
        "list_widget_types",
        "patch_config",
        "remove_widget",
        "rollback",
        "screenshot",
        "send_event",
        "validate_config",
      ].sort(),
    );
    expect(tools.find((t) => t.name === "patch_config")?.description).toMatch(/validate_config.*screenshot/);
  });

  it("add_widget validates the new config before saving it", async () => {
    const api = {
      getConfig: vi.fn(async () => ({ schemaVersion: 1, widgets: [] })),
      validateConfig: vi.fn(async () => ({ valid: true })),
      putConfig: vi.fn(async () => ({ author: "vigia-mcp" })),
    };
    const client = await connect(api);

    await client.callTool({
      name: "add_widget",
      arguments: { id: "w", type: "weather", pos: { x: 0, y: 1, w: 4, h: 3 }, params: { name: "Daule", latitude: -2.07, longitude: -79.88 } },
    });

    const saved = api.putConfig.mock.calls[0]?.[0] as unknown as { widgets: Array<{ id: string }> };
    expect(api.validateConfig).toHaveBeenCalledBefore(api.putConfig);
    expect(saved.widgets.map((w) => w.id)).toEqual(["w"]);
  });

  it("returns panel rejections as readable tool errors", async () => {
    const api = {
      patchConfig: vi.fn(async () => {
        throw new ApiError(422, [{ code: "invalid_format", message: "Unknown palette 'neon'", path: "theme.palette" }]);
      }),
    };
    const client = await connect(api);

    const result = (await client.callTool({ name: "patch_config", arguments: { patch: { theme: { palette: "neon" } } } })) as {
      isError: boolean;
      content: Array<{ text: string }>;
    };

    expect(result.isError).toBe(true);
    expect(result.content[0]?.text).toContain("theme.palette: Unknown palette 'neon'");
  });

  it("returns screenshots as images", async () => {
    const client = await connect({ screenshot: vi.fn(async () => new Uint8Array([1, 2, 3])) });

    const result = (await client.callTool({ name: "screenshot", arguments: {} })) as { content: Array<{ type: string; data: string; mimeType: string }> };

    expect(result.content[0]).toEqual({ type: "image", data: "AQID", mimeType: "image/png" });
  });
});
