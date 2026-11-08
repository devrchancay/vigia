import type { PanelConfig, Widget } from "./client.js";

/** Adds or replaces (same id) a widget; the panel validates bounds and overlaps when the config is saved. */
export function withWidget(config: PanelConfig, widget: Widget): PanelConfig {
  const widgets = (config.widgets ?? []).filter((w) => w.id !== widget.id);
  return { ...config, widgets: [...widgets, widget] };
}

export function withoutWidget(config: PanelConfig, id: string): PanelConfig {
  const widgets = config.widgets ?? [];
  if (!widgets.some((w) => w.id === id)) throw new Error(`No widget with id '${id}'`);
  return { ...config, widgets: widgets.filter((w) => w.id !== id) };
}

/** Parses "x,y,w,h" (grid cells). */
export function parsePos(text: string): Widget["pos"] {
  const parts = text.split(",").map((n) => Number(n.trim()));
  if (parts.length !== 4 || parts.some((n) => !Number.isInteger(n))) throw new Error(`Position must be x,y,w,h, got '${text}'`);
  const [x, y, w, h] = parts as [number, number, number, number];
  return { x, y, w, h };
}

/** Parses repeated key=value params; values that are valid JSON (numbers, arrays, objects) are decoded. */
export function parseParams(pairs: string[]): Record<string, unknown> {
  const params: Record<string, unknown> = {};
  for (const pair of pairs) {
    const index = pair.indexOf("=");
    if (index <= 0) throw new Error(`Param must be key=value, got '${pair}'`);
    const raw = pair.slice(index + 1);
    let value: unknown = raw;
    try {
      value = JSON.parse(raw);
    } catch {
      value = raw;
    }
    params[pair.slice(0, index)] = value;
  }
  return params;
}
