#!/usr/bin/env node
import { readFile, writeFile } from "node:fs/promises";
import { parseArgs } from "node:util";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { ApiError, VigiaClient, type PanelConfig, type PanelEvent } from "./client.js";
import { loadConnection, parseLogin, saveConnection } from "./config.js";
import { createMcpServer } from "./mcp.js";
import { parseParams, parsePos, withWidget, withoutWidget } from "./widgets.js";

const VERSION = "0.1.0";

const USAGE = `vigia ${VERSION} - control a Vigía panel

  vigia login <url> <token> | vigia login 'vigia://pair?url=...&token=...'
  vigia status                         what every widget shows now
  vigia types                          widget types and their params
  vigia config get | set <file> | patch '<json>' | validate <file> | rollback | history
  vigia widget add <type> --id <id> --pos x,y,w,h [--title t] [--param key=value ...]
  vigia widget rm <id>
  vigia event send --source s --title t [--key k] [--kind k] [--level info|ok|warn|error] [--body b] [--ttl sec] [--url u]
  vigia secret set <name>              reads the value from stdin (keeps it out of shell history)
  vigia secret rm <name> | list
  vigia screenshot [-o panel.png]
  vigia mcp                            MCP server on stdio, for Claude Code and other agents

Connection: ~/.config/vigia/config.json, or VIGIA_URL and VIGIA_TOKEN.`;

const print = (value: unknown) => console.log(typeof value === "string" ? value : JSON.stringify(value, null, 2));

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of process.stdin) chunks.push(chunk as Buffer);
  return Buffer.concat(chunks).toString("utf8").trim();
}

async function readJsonFile(path: string): Promise<PanelConfig> {
  return JSON.parse(await readFile(path, "utf8")) as PanelConfig;
}

async function client(): Promise<VigiaClient> {
  return new VigiaClient(await loadConnection());
}

export async function main(argv: string[]): Promise<number> {
  const { values, positionals } = parseArgs({
    args: argv,
    allowPositionals: true,
    options: {
      id: { type: "string" },
      pos: { type: "string" },
      title: { type: "string" },
      param: { type: "string", multiple: true },
      source: { type: "string" },
      key: { type: "string" },
      kind: { type: "string" },
      level: { type: "string" },
      body: { type: "string" },
      ttl: { type: "string" },
      url: { type: "string" },
      output: { type: "string", short: "o" },
      help: { type: "boolean", short: "h" },
      version: { type: "boolean", short: "v" },
    },
  });
  const [command, sub, ...rest] = positionals;
  if (values.version) return print(VERSION), 0;
  if (values.help || !command) return print(USAGE), command ? 0 : 1;

  switch (command) {
    case "login": {
      if (!sub) throw new Error("Usage: vigia login <url> <token>");
      const connection = parseLogin(sub, rest[0]);
      const health = await new VigiaClient(connection).health();
      await new VigiaClient(connection).listSecrets();
      print(`Logged in to ${connection.url} (Vigía ${health.version}); saved to ${await saveConnection(connection)}`);
      return 0;
    }
    case "status":
      return print(await (await client()).status()), 0;
    case "types":
      return print(await (await client()).widgetTypes()), 0;
    case "config": {
      const api = await client();
      switch (sub) {
        case "get":
          return print(await api.getConfig()), 0;
        case "set":
          return print(await api.putConfig(await readJsonFile(required(rest[0], "file")))), 0;
        case "patch":
          return print(await api.patchConfig(JSON.parse(required(rest[0], "json")) as Record<string, unknown>)), 0;
        case "validate":
          return print(await api.validateConfig(await readJsonFile(required(rest[0], "file")))), 0;
        case "rollback":
          return print(await api.rollback()), 0;
        case "history":
          return print(await api.history()), 0;
        default:
          throw new Error("Usage: vigia config get | set <file> | patch '<json>' | validate <file> | rollback | history");
      }
    }
    case "widget": {
      const api = await client();
      if (sub === "add") {
        const type = required(rest[0], "type");
        const widget = {
          id: required(values.id, "--id"),
          type,
          pos: parsePos(required(values.pos, "--pos")),
          ...(values.title === undefined ? {} : { title: values.title }),
          ...(values.param === undefined ? {} : { params: parseParams(values.param) }),
        };
        return print(await api.putConfig(withWidget(await api.getConfig(), widget))), 0;
      }
      if (sub === "rm") return print(await api.putConfig(withoutWidget(await api.getConfig(), required(rest[0], "id")))), 0;
      throw new Error("Usage: vigia widget add <type> --id <id> --pos x,y,w,h | vigia widget rm <id>");
    }
    case "event": {
      if (sub !== "send") throw new Error("Usage: vigia event send --source s --title t");
      const event: PanelEvent = {
        source: required(values.source, "--source"),
        title: required(values.title, "--title"),
        ...(values.key === undefined ? {} : { key: values.key }),
        ...(values.kind === undefined ? {} : { kind: values.kind }),
        ...(values.level === undefined ? {} : { level: values.level as NonNullable<PanelEvent["level"]> }),
        ...(values.body === undefined ? {} : { body: values.body }),
        ...(values.ttl === undefined ? {} : { ttlSec: Number(values.ttl) }),
        ...(values.url === undefined ? {} : { url: values.url }),
      };
      return print(await (await client()).sendEvents(event)), 0;
    }
    case "secret": {
      const api = await client();
      if (sub === "set") {
        const value = await readStdin();
        if (!value) throw new Error("Pipe the secret value on stdin, e.g. pbpaste | vigia secret set github");
        await api.setSecret(required(rest[0], "name"), value);
        return print(`Secret '${rest[0]}' stored`), 0;
      }
      if (sub === "rm") return await api.deleteSecret(required(rest[0], "name")), print(`Secret '${rest[0]}' removed`), 0;
      if (sub === "list") return print(await api.listSecrets()), 0;
      throw new Error("Usage: vigia secret set <name> | rm <name> | list");
    }
    case "screenshot": {
      const path = values.output ?? "panel.png";
      await writeFile(path, await (await client()).screenshot());
      return print(`Saved ${path}`), 0;
    }
    case "mcp": {
      const server = createMcpServer(new VigiaClient(await loadConnection(), fetch, "vigia-mcp"), VERSION);
      await server.connect(new StdioServerTransport());
      return new Promise<number>(() => {});
    }
    default:
      throw new Error(`Unknown command '${command}'.\n\n${USAGE}`);
  }
}

function required(value: string | undefined, name: string): string {
  if (value === undefined || value === "") throw new Error(`Missing ${name}`);
  return value;
}

// Run only as the CLI entry point, not when imported by tests.
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith("/vigia")) {
  main(process.argv.slice(2)).then(
    (code) => process.exit(code),
    (error: unknown) => {
      console.error(error instanceof ApiError ? `Panel rejected the request (HTTP ${error.status}):\n${error.message}` : (error as Error).message);
      process.exit(1);
    },
  );
}
