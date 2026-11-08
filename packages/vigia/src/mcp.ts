import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { ApiError, type PanelConfig, type VigiaClient } from "./client.js";
import { withWidget, withoutWidget } from "./widgets.js";

type ToolResult = { content: Array<{ type: "text"; text: string } | { type: "image"; data: string; mimeType: string }>; isError?: boolean };

const text = (value: unknown): ToolResult => ({
  content: [{ type: "text", text: typeof value === "string" ? value : JSON.stringify(value, null, 2) }],
});

/** Turns panel errors into tool results the agent can read and fix, instead of failing the call. */
async function run(action: () => Promise<ToolResult>): Promise<ToolResult> {
  try {
    return await action();
  } catch (error) {
    const message = error instanceof ApiError ? `Panel rejected the request (HTTP ${error.status}):\n${error.message}` : (error as Error).message;
    return { content: [{ type: "text", text: message }], isError: true };
  }
}

const WORKFLOW =
  "Workflow: read get_config and list_widget_types first, call validate_config before applying a change, " +
  "then take a screenshot to check the result. Positions are grid cells {x, y, w, h}; widgets must not overlap.";

const widgetShape = {
  id: z.string().describe("Unique id, a-z 0-9 - _"),
  type: z.string().describe("A type from list_widget_types"),
  pos: z.object({ x: z.number().int(), y: z.number().int(), w: z.number().int(), h: z.number().int() }).describe("Grid cells"),
  title: z.string().optional(),
  params: z.record(z.string(), z.unknown()).optional().describe("Params as list_widget_types describes them"),
};

/** MCP server exposing the panel to agents such as Claude Code (`vigia mcp`, stdio). */
export function createMcpServer(client: VigiaClient, version: string): McpServer {
  const server = new McpServer({ name: "vigia", version }, { instructions: `Controls a Vigía status panel. ${WORKFLOW}` });

  server.registerTool(
    "get_config",
    { description: `Current panel config (theme, grid, widgets). ${WORKFLOW}` },
    () => run(async () => text(await client.getConfig())),
  );

  server.registerTool(
    "list_widget_types",
    { description: "Widget types this panel can render, each with its params (type, required, default, rules)." },
    () => run(async () => text(await client.widgetTypes())),
  );

  server.registerTool(
    "validate_config",
    {
      description: "Dry run: checks a full config without applying it. Returns every error with its JSON path.",
      inputSchema: { config: z.record(z.string(), z.unknown()).describe("A full config document") },
    },
    ({ config }) => run(async () => text(await client.validateConfig(config as PanelConfig))),
  );

  server.registerTool(
    "patch_config",
    {
      description: `Applies a JSON Merge Patch (RFC 7396) to the config; arrays such as widgets are replaced whole. ${WORKFLOW}`,
      inputSchema: { patch: z.record(z.string(), z.unknown()) },
    },
    ({ patch }) => run(async () => text(await client.patchConfig(patch))),
  );

  server.registerTool(
    "add_widget",
    { description: `Adds a widget, or replaces the one with the same id. ${WORKFLOW}`, inputSchema: widgetShape },
    (widget) =>
      run(async () => {
        const next = withWidget(await client.getConfig(), {
          id: widget.id,
          type: widget.type,
          pos: widget.pos,
          ...(widget.title === undefined ? {} : { title: widget.title }),
          ...(widget.params === undefined ? {} : { params: widget.params }),
        });
        await client.validateConfig(next);
        return text(await client.putConfig(next));
      }),
  );

  server.registerTool(
    "remove_widget",
    { description: "Removes a widget by id.", inputSchema: { id: z.string() } },
    ({ id }) => run(async () => text(await client.putConfig(withoutWidget(await client.getConfig(), id)))),
  );

  server.registerTool(
    "rollback",
    { description: "Undoes the last config change (restores the previous version)." },
    () => run(async () => text(await client.rollback())),
  );

  server.registerTool(
    "get_status",
    { description: "What every widget shows right now (weather, times, device, events...), as JSON." },
    () => run(async () => text(await client.status())),
  );

  server.registerTool(
    "send_event",
    {
      description:
        "Pushes an event to feed/agents widgets. Same source+key replaces the previous one. level: info|ok|warn|error.",
      inputSchema: {
        source: z.string(),
        title: z.string(),
        key: z.string().optional(),
        kind: z.string().optional(),
        body: z.string().optional(),
        level: z.enum(["info", "ok", "warn", "error"]).optional(),
        ttlSec: z.number().int().optional(),
        url: z.string().optional(),
        at: z.string().optional().describe("ISO-8601 time the event refers to"),
      },
    },
    (event) =>
      run(async () => {
        const clean = Object.fromEntries(Object.entries(event).filter(([, v]) => v !== undefined));
        return text(await client.sendEvents(clean as never));
      }),
  );

  server.registerTool(
    "screenshot",
    { description: "PNG of what the panel shows now. Use it after a change to verify the result." },
    () =>
      run(async () => {
        const png = await client.screenshot();
        return { content: [{ type: "image", data: Buffer.from(png).toString("base64"), mimeType: "image/png" }] };
      }),
  );

  return server;
}
