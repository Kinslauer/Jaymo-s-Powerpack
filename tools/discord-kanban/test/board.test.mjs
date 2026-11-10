import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { addNote, dropFor, frontmatter, moveCardLine, parseKanban, setProps, stamp } from "../src/board.js";

const sample = readFileSync(new URL("./Kanban.sample.md", import.meta.url), "utf8");
const lane = (lanes, name) => lanes.find((l) => l.name === name);

test("reads every lane and card of the board vault_sync.py writes", () => {
  const lanes = parseKanban(sample);
  assert.deepEqual(lanes.map((l) => l.name), ["Waiting for a bot", "Being built", "In review", "Needs Andy", "Done"]);
  const crash = lane(lanes, "Needs Andy").cards.find((c) => c.name === "startup-crash");
  assert.equal(crash.path, "Inbox/startup-crash.md");
  assert.equal(crash.meta, "☀️ Apollo · #1245 · blocked");
  assert.equal(crash.movable, true);
  const done = lane(lanes, "Done").cards[0];
  assert.equal(done.done, true);
  assert.equal(done.movable, false);
  assert.equal(lane(lanes, "Being built").cards.length, 0);
});

test("a lane the PC adds (Text pass) shows, but takes no drops", () => {
  const lanes = parseKanban(sample.replace("## In review", "## Text pass\n\n\n\n## In review"));
  assert.ok(lane(lanes, "Text pass"));
  assert.equal(dropFor("Text pass"), null);
  assert.equal(dropFor("Needs Andy").props.status, "blocked");
});

test("setProps and addNote change a task like vault_sync.py --set and --note", () => {
  const task = "---\nstatus: blocked\nclaimed_by: hermes-a\npr: 1245\n---\n\n# Crash\n\n## Notes\n- old line\n";
  const changed = setProps(task, { status: "open", claimed_by: "", text_pass: "" });
  assert.equal(frontmatter(changed).status, "open");
  assert.equal(frontmatter(changed).claimed_by, "");
  assert.match(changed, /text_pass: \n---/);
  const noted = addNote(changed, "2026-11-10 11:24 AM", "Andy handed back to the bots on the Discord board.");
  assert.ok(noted.endsWith("- old line\n- 2026-11-10 11:24 AM Andy handed back to the bots on the Discord board.\n"));
  assert.match(addNote("---\n---\n# x", "t", "n"), /# x\n\n## Notes\n- t n\n$/);
});

test("stamp is a 12-hour clock in Andy's time zone", () => {
  assert.equal(stamp(new Date("2026-11-10T16:24:00Z"), "America/New_York"), "2026-11-10 11:24 AM");
  assert.equal(stamp(new Date("2026-11-11T02:05:00Z"), "America/New_York"), "2026-11-10 9:05 PM");
});

test("moveCardLine moves the card to the top of the new lane with its new status", () => {
  const moved = moveCardLine(sample, "Inbox/startup-crash.md", "Waiting for a bot", "open");
  const lanes = parseKanban(moved);
  assert.equal(lane(lanes, "Needs Andy").cards.some((c) => c.name === "startup-crash"), false);
  assert.deepEqual(lane(lanes, "Waiting for a bot").cards[0],
    { path: "Inbox/startup-crash.md", name: "startup-crash", meta: "☀️ Apollo · #1245 · open", done: false, movable: true });
  assert.equal(lane(parseKanban(sample), "Waiting for a bot").cards.length + 1, lane(lanes, "Waiting for a bot").cards.length);
});

test("moveCardLine into an empty lane, and into Done", () => {
  const built = parseKanban(moveCardLine(sample, "Inbox/startup-crash.md", "Being built", "changes"));
  assert.equal(lane(built, "Being built").cards[0].meta, "☀️ Apollo · #1245 · changes");
  const done = parseKanban(moveCardLine(sample, "Inbox/startup-crash.md", "Done", "done"));
  assert.deepEqual(lane(done, "Done").cards[0],
    { path: "Inbox/Done/startup-crash.md", name: "startup-crash", meta: "", done: true, movable: false });
  assert.equal(lane(done, "Done").cards.length, 9);
});

test("an unknown card leaves the board alone", () => {
  assert.equal(moveCardLine(sample, "Inbox/nope.md", "Done", "done"), sample);
});
