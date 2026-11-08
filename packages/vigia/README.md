# vigia

CLI and MCP server for a [Vigía](../../README.md) panel: change widgets, layout and theme of the phone on your desk
from a terminal, a script, or by asking your AI agent.

Node 22+. Dependencies: the MCP SDK and zod.

## Connect

The phone's setup screen (first launch, or long-press the panel) shows the address, the token and a QR.

```sh
vigia login 'vigia://pair?url=http%3A%2F%2F100.101.102.103%3A8787&token=...'   # the QR content
vigia login http://100.101.102.103:8787 <token>                                 # or address + token
```

The connection is saved to `~/.config/vigia/config.json` (mode 600). `VIGIA_URL` and `VIGIA_TOKEN` override it.
The panel accepts Tailscale and loopback only: over USB use `adb forward tcp:8787 tcp:8787` and
`http://127.0.0.1:8787`.

## CLI

```sh
vigia status                                     # what every widget shows now
vigia types                                      # widget types and their params
vigia config get > panel.json
vigia config validate panel.json                 # dry run, every error with its path
vigia config set panel.json
vigia config patch '{"theme":{"palette":"teal"}}'
vigia config rollback                            # undo the last change
vigia widget add weather --id home --pos 0,1,4,3 --param name=Home --param latitude=40.4 --param longitude=-3.7
vigia widget rm home
vigia event send --source radar --title "New lead" --level info
pbpaste | vigia secret set github                # value from stdin, never in shell history
vigia screenshot -o panel.png
```

Rejected changes print the panel's errors with their JSON path and exit with code 1.

## MCP (Claude Code and other agents)

```sh
claude mcp add vigia -e VIGIA_URL=http://100.101.102.103:8787 -e VIGIA_TOKEN=<token> -- npx -y vigia mcp
```

Until the package is published to npm (milestone M7), point at a local build instead:
`npm run build` here, then use `node /path/to/packages/vigia/dist/cli.js mcp` as the command.

Tools: `get_config`, `list_widget_types`, `validate_config`, `patch_config`, `add_widget` (validates first),
`remove_widget`, `rollback`, `get_status`, `send_event`, `screenshot` (returns the image). Their descriptions tell
the agent to validate before applying and to take a screenshot afterwards. Panel rejections come back as readable
tool errors, so the agent can fix the field and retry.

## Development

```sh
npm ci
npm run typecheck && npm test && npm run build
```
