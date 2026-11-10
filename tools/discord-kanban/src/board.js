// The vault board's logic, shared by the Worker and its tests. No Discord or GitHub calls in here.
//
// The board itself is Kanban.md on the vault-live branch, which tools/vault_sync.py (on Andy's PC: the Hermes copy)
// rewrites from the Inbox on every sync. This reads its lanes as they are, so a lane added on the PC (Text pass)
// shows up here without a change. A drag changes the task file the same way `vault_sync.py --set` does.

// Where a card may be dropped, and the task properties a drop there sets. `needsPr`: only a task with a PR can go
// there. Lanes missing from this table (Text pass, anything new) only take cards from the bots, never from a drag.
export const DROPS = {
  "waiting for a bot": { props: { status: "open", claimed_by: "" }, say: "handed back to the bots" },
  "being built": { props: { status: "changes" }, needsPr: true, say: "sent back for fixes" },
  "in review": { props: { status: "in-review" }, needsPr: true, say: "sent to review" },
  "needs andy": { props: { status: "blocked" }, say: "held for Andy" },
  "done": { props: { status: "done" }, toDone: true, say: "marked done" },
};

export function dropFor(lane) {
  return DROPS[(lane || "").trim().toLowerCase()] || null;
}

// Kanban.md -> [{ name, cards: [{ path, name, meta, done, movable }] }]
export function parseKanban(text) {
  const body = text.split("%% kanban:settings")[0];
  const lanes = [];
  let lane = null;
  for (const line of body.split("\n")) {
    const head = line.match(/^## (.+?)\s*$/);
    if (head) {
      lane = { name: head[1], cards: [] };
      lanes.push(lane);
      continue;
    }
    const card = lane && line.match(/^- \[( |x)\] \[\[([^|\]]+)\|([^\]]+)\]\](.*)$/);
    if (card) {
      const path = card[2] + ".md";
      lane.cards.push({
        path,
        name: card[3],
        meta: card[4].replace(/^\s*·\s*/, "").trim(),
        done: card[1] === "x",
        movable: /^Inbox\/[^/]+\.md$/.test(path),  // Inbox tasks; agent notes and finished tasks stay put
      });
    }
  }
  return lanes;
}

export function frontmatter(text) {
  const lines = text.split("\n");
  const props = {};
  if ((lines[0] || "").trim() !== "---") return props;
  let key = null;
  for (const line of lines.slice(1)) {
    if (line.trim() === "---") break;
    if (line.startsWith("  - ") && key) {
      props[key] = (props[key] ? props[key] + ", " : "") + line.slice(4).trim();
    } else if (line.includes(":")) {
      const i = line.indexOf(":");
      key = line.slice(0, i).trim();
      props[key] = line.slice(i + 1).trim();
    }
  }
  return props;
}

// Same as vault_sync.set_props: change single-line properties in place, adding missing ones before the closing ---.
export function setProps(text, changes) {
  let lines = text.split("\n");
  if ((lines[0] || "").trim() !== "---") lines = ["---", "---", ...lines];
  let end = lines.indexOf("---", 1);
  for (const [key, value] of Object.entries(changes)) {
    let found = false;
    for (let i = 1; i < end; i++) {
      if (!lines[i].startsWith(" ") && lines[i].split(":")[0].trim() === key) {
        lines[i] = `${key}: ${value}`;
        found = true;
        break;
      }
    }
    if (!found) {
      lines.splice(end, 0, `${key}: ${value}`);
      end += 1;
    }
  }
  return lines.join("\n");
}

// Same as vault_sync.add_note: a dated line under ## Notes.
export function addNote(text, stamp, note) {
  text = text.replace(/\n+$/, "");
  if (!text.includes("\n## Notes")) text += "\n\n## Notes";
  if (text.endsWith("\n- ") || text.endsWith("\n-")) text = text.slice(0, text.lastIndexOf("\n"));
  return text + `\n- ${stamp} ${note}\n`;
}

// Andy reads times on a 12-hour clock (Decisions, 2026-11-09).
export function stamp(date, timeZone) {
  const p = Object.fromEntries(new Intl.DateTimeFormat("en-US", {
    timeZone, year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit", hour12: true,
  }).formatToParts(date).map((x) => [x.type, x.value]));
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute} ${p.dayPeriod}`;
}

// Moves one card in Kanban.md so the board shows the drop at once; the next PC sync rewrites the file anyway.
export function moveCardLine(text, path, toLane, newStatus) {
  const name = path.replace(/^Inbox\//, "").replace(/\.md$/, "");
  const lines = text.split("\n");
  const at = lines.findIndex((l) => l.startsWith("- [") && l.includes(`[[${path.replace(/\.md$/, "")}|`));
  if (at < 0) return text;
  const [old] = lines.splice(at, 1);
  let line;
  if (newStatus === "done") {
    line = `- [x] [[Inbox/Done/${name}|${name}]]`;
  } else {
    const [link, ...bits] = old.split(" · ");
    const statuses = ["open", "claimed", "changes", "writing", "in-review", "ready", "sent", "blocked"];
    const kept = bits.filter((b) => !statuses.includes(b.trim()) && b.trim() !== "fixing review notes");
    line = [link.replace("- [x]", "- [ ]"), ...kept, newStatus].join(" · ");
  }
  const head = lines.findIndex((l) => l.trim().toLowerCase() === `## ${toLane.trim().toLowerCase()}`);
  if (head < 0) return text;
  // top of the lane: under the heading and its blank line, and under Done's "**Complete**"
  const insert = (lines[head + 2] || "").startsWith("**") ? head + 3 : head + 2;
  lines.splice(insert, 0, line);
  return lines.join("\n");
}
