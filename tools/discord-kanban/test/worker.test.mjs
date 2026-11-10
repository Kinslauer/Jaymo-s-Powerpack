// The Worker's API with Discord and GitHub faked: a fake vault-live branch that records the commits it gets.
import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import worker from "../src/worker.js";
import { frontmatter, parseKanban } from "../src/board.js";

const ANDY = "111", FRIEND = "222", STRANGER = "333";
const env = { DISCORD_CLIENT_ID: "app", DISCORD_CLIENT_SECRET: "s", GITHUB_TOKEN: "g", MOVER_IDS: ANDY,
              VIEWER_IDS: FRIEND, REPO: "o/r", BRANCH: "vault-live", TIME_ZONE: "America/New_York",
              ASSETS: { fetch: async () => new Response("page") } };
const task = "---\nstatus: blocked\nclaimed_by: hermes-a\npr: 1245\n---\n\n# Startup crash\n";
let files, commits, raceOnce;

beforeEach(() => {
  files = { "Kanban.md": readFileSync(new URL("./Kanban.sample.md", import.meta.url), "utf8"),
            "Inbox/startup-crash.md": task, "Inbox/stormborn-tomb-real-fix.md": "---\nstatus: open\n---\n" };
  commits = [];
  raceOnce = false;
  globalThis.fetch = async (url, init = {}) => {
    const u = new URL(url), method = init.method || "GET";
    const ok = (data) => new Response(typeof data === "string" ? data : JSON.stringify(data));
    if (u.pathname === "/api/users/@me") {
      const id = { "Bearer andy": ANDY, "Bearer friend": FRIEND, "Bearer stranger": STRANGER }[init.headers.authorization];
      return id ? ok({ id, username: id === ANDY ? "andy" : "x", global_name: id === ANDY ? "Andy" : null })
                : new Response("{}", { status: 401 });
    }
    const p = u.pathname.replace("/repos/o/r", "");
    if (p.startsWith("/contents/")) {
      const f = decodeURI(p.slice("/contents/".length));
      return f in files ? ok(files[f]) : new Response("", { status: 404 });
    }
    if (p === "/git/ref/heads/vault-live") return ok({ object: { sha: `c${commits.length}` } });
    if (p.startsWith("/git/commits/")) return ok({ tree: { sha: "t" } });
    if (p === "/git/trees") return ok({ sha: "nt", body: JSON.parse(init.body) });
    if (p === "/git/commits" && method === "POST") {
      commits.push({ pending: JSON.parse(init.body) });
      return ok({ sha: "nc" });
    }
    if (p === "/git/refs/heads/vault-live" && method === "PATCH") {
      if (raceOnce) { raceOnce = false; commits.pop(); return new Response("{}", { status: 422 }); }
      return ok({});
    }
    throw new Error(`unexpected ${method} ${url}`);
  };
  // keep the tree each commit was made with
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init = {}) => {
    const r = await real(url, init);
    if (String(url).endsWith("/git/trees")) lastTree = JSON.parse(init.body).tree;
    return r;
  };
});
let lastTree;

const call = (path, token, body) => worker.fetch(new Request(`https://x.discordsays.com/.proxy${path}`, {
  method: body ? "POST" : "GET", headers: token ? { authorization: `Bearer ${token}` } : {},
  body: body ? JSON.stringify(body) : undefined,
}), env);

test("the page itself is served without sign-in, with the proxy prefix dropped", async () => {
  assert.equal(await (await call("/index.html")).text(), "page");
});

test("only Andy and the listed viewers see the board; only Andy may move", async () => {
  assert.equal((await call("/api/board")).status, 401);
  assert.equal((await call("/api/board", "stranger")).status, 403);
  const friend = await (await call("/api/board", "friend")).json();
  assert.equal(friend.canMove, false);
  assert.equal(friend.lanes.length, 5);
  assert.equal((await call("/api/move", "friend", { path: "Inbox/startup-crash.md", lane: "Done" })).status, 403);
  assert.equal((await (await call("/api/board", "andy")).json()).canMove, true);
});

test("a drop to Waiting releases the task: one commit with the task and the board", async () => {
  const r = await (await call("/api/move", "andy", { path: "Inbox/startup-crash.md", lane: "Waiting for a bot" })).json();
  assert.equal(r.ok, true);
  assert.equal(commits.length, 1);
  assert.match(commits[0].pending.message, /startup-crash handed back to the bots/);
  const saved = lastTree.find((e) => e.path === "Inbox/startup-crash.md").content;
  assert.equal(frontmatter(saved).status, "open");
  assert.equal(frontmatter(saved).claimed_by, "");
  assert.match(saved, /## Notes\n- \d{4}-\d\d-\d\d \d{1,2}:\d\d [AP]M Andy handed back to the bots on the Discord board\.\n$/);
  const board = parseKanban(lastTree.find((e) => e.path === "Kanban.md").content);
  assert.equal(board[0].cards[0].name, "startup-crash");
  assert.deepEqual(r.lanes, board);
});

test("a drop to Done moves the file into Inbox/Done", async () => {
  await call("/api/move", "andy", { path: "Inbox/startup-crash.md", lane: "Done" });
  assert.equal(frontmatter(lastTree.find((e) => e.path === "Inbox/Done/startup-crash.md").content).status, "done");
  assert.deepEqual(lastTree.find((e) => e.path === "Inbox/startup-crash.md"), { path: "Inbox/startup-crash.md", mode: "100644", type: "blob", sha: null });
});

test("refused drops: bots-only lanes, review without a PR, agent notes, odd paths", async () => {
  const move = async (path, lane) => (await call("/api/move", "andy", { path, lane })).status;
  assert.equal(await move("Inbox/startup-crash.md", "Text pass"), 400);
  assert.equal(await move("Inbox/stormborn-tomb-real-fix.md", "In review"), 400);
  assert.equal(await move("Agents/realistic-water-trial.md", "Done"), 400);
  assert.equal(await move("Inbox/../Decisions.md", "Done"), 400);
  assert.equal((await call("/api/task?path=../secret", "andy")).status, 400);
  assert.equal(commits.length, 0);
});

test("a sync landing mid-move is retried from the new tip", async () => {
  raceOnce = true;
  const r = await (await call("/api/move", "andy", { path: "Inbox/startup-crash.md", lane: "Needs Andy" })).json();
  assert.equal(r.ok, true);
  assert.equal(commits.length, 1);
});
