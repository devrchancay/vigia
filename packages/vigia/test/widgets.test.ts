import { describe, expect, it } from "vitest";
import { parseParams, parsePos, withWidget, withoutWidget } from "../src/widgets.js";

const config = { schemaVersion: 1, widgets: [{ id: "clock", type: "clock", pos: { x: 0, y: 0, w: 3, h: 1 } }] };

describe("widget helpers", () => {
  it("adds, replaces by id and removes widgets", () => {
    const note = { id: "todo", type: "note", pos: { x: 0, y: 1, w: 2, h: 1 }, params: { text: "hi" } };
    const added = withWidget(config, note);
    expect(added.widgets?.map((w) => w.id)).toEqual(["clock", "todo"]);
    expect(withWidget(added, { ...note, params: { text: "bye" } }).widgets?.filter((w) => w.id === "todo")).toHaveLength(1);
    expect(withoutWidget(added, "todo").widgets?.map((w) => w.id)).toEqual(["clock"]);
    expect(() => withoutWidget(config, "nope")).toThrow(/No widget/);
  });

  it("parses positions and typed params", () => {
    expect(parsePos("3, 0, 5, 1")).toEqual({ x: 3, y: 0, w: 5, h: 1 });
    expect(() => parsePos("1,2,3")).toThrow(/x,y,w,h/);
    expect(parseParams(["name=Daule", "latitude=-2.07", 'zones=[{"label":"Perth","zone":"Australia/Perth"}]'])).toEqual({
      name: "Daule",
      latitude: -2.07,
      zones: [{ label: "Perth", zone: "Australia/Perth" }],
    });
    expect(() => parseParams(["novalue"])).toThrow(/key=value/);
  });
});
