// Cloudflare Worker behind the Discord Activity: serves the board page (public/) and a small API.
//
//   GET  /api/config                    the Discord app's client id (public), for the page's SDK
//   POST /api/token   {code}            Discord OAuth code -> access token (the Activity's sign-in)
//   GET  /api/board                     the lanes of Kanban.md on vault-live
//   GET  /api/task?path=Inbox/x.md      one task's text, for the card's detail panel
//   POST /api/move    {path, lane}      a drag: changes the task like `vault_sync.py --set`, one commit on vault-live
//
// Every /api call except /api/token needs the Discord access token; only the user ids in MOVER_IDS may move cards.
// Secrets (wrangler secret put): DISCORD_CLIENT_SECRET, GITHUB_TOKEN (fine-grained, this repo, Contents read/write).
// Vars (wrangler.toml): DISCORD_CLIENT_ID, MOVER_IDS, VIEWER_IDS, REPO, BRANCH, TIME_ZONE.
// Inside Discord every request comes through its proxy as /.proxy/...; that prefix is dropped before routing.
import { addNote, dropFor, frontmatter, moveCardLine, parseKanban, setProps, stamp } from "./board.js";

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    let path = url.pathname.replace(/^\/\.proxy(?=\/)/, "");
    if (!path.startsWith("/api/")) {
      const asset = new URL(request.url);
      asset.pathname = path;
      return env.ASSETS.fetch(new Request(asset, request));
    }
    try {
      if (path === "/api/config") return json({ clientId: env.DISCORD_CLIENT_ID });
      if (path === "/api/token" && request.method === "POST") return await token(request, env);
      const user = await discordUser(request);
      if (!user) return json({ error: "Sign in through Discord first." }, 401);
      if (!listed(env.VIEWER_IDS, user.id) && !listed(env.MOVER_IDS, user.id)) {
        return json({ error: "This board is private." }, 403);
      }
      if (path === "/api/board" && request.method === "GET") {
        return json({ lanes: parseKanban(await readFile(env, "Kanban.md")), canMove: listed(env.MOVER_IDS, user.id),
                      drops: dropLanes() });
      }
      if (path === "/api/task" && request.method === "GET") {
        const file = url.searchParams.get("path") || "";
        if (!/^(Inbox|Agents)\/(Done\/)?[\w.-]+\.md$/.test(file)) return json({ error: "Not a task." }, 400);
        return json({ text: await readFile(env, file) });
      }
      if (path === "/api/move" && request.method === "POST") {
        if (!listed(env.MOVER_IDS, user.id)) return json({ error: "Only Andy can move cards." }, 403);
        const { path: file, lane } = await request.json();
        return await move(env, file, lane, user);
      }
      return json({ error: "Not found." }, 404);
    } catch (e) {
      return json({ error: String(e.message || e) }, 500);
    }
  },
};

function listed(ids, id) {
  return (ids || "").split(",").map((s) => s.trim()).includes(id);
}

function dropLanes() {
  return ["waiting for a bot", "being built", "in review", "needs andy", "done"]
    .map((l) => ({ lane: l, needsPr: !!dropFor(l).needsPr }));
}

async function token(request, env) {
  const { code } = await request.json();
  const r = await fetch("https://discord.com/api/oauth2/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: env.DISCORD_CLIENT_ID, client_secret: env.DISCORD_CLIENT_SECRET,
      grant_type: "authorization_code", code,
    }),
  });
  const data = await r.json();
  if (!r.ok || !data.access_token) return json({ error: "Discord sign-in failed." }, 401);
  return json({ access_token: data.access_token });
}

async function discordUser(request) {
  const auth = request.headers.get("authorization") || "";
  if (!auth.startsWith("Bearer ")) return null;
  const r = await fetch("https://discord.com/api/users/@me", { headers: { authorization: auth } });
  return r.ok ? r.json() : null;
}

// --- GitHub (vault-live branch) ---

function gh(env, path, init = {}) {
  return fetch(`https://api.github.com/repos/${env.REPO}${path}`, {
    ...init,
    headers: {
      authorization: `Bearer ${env.GITHUB_TOKEN}`, accept: "application/vnd.github+json",
      "user-agent": "vault-kanban-activity", "x-github-api-version": "2022-11-28", ...(init.headers || {}),
    },
  });
}

async function ghJson(env, path, init) {
  const r = await gh(env, path, init);
  if (!r.ok) throw new Error(`GitHub ${r.status} on ${path.split("?")[0]}`);
  return r.json();
}

async function readFile(env, file, ref = env.BRANCH) {
  const r = await gh(env, `/contents/${encodeURI(file)}?ref=${encodeURIComponent(ref)}`,
                     { headers: { accept: "application/vnd.github.raw+json" } });
  if (r.status === 404) throw new Error(`${file} isn't on ${env.BRANCH}.`);
  if (!r.ok) throw new Error(`GitHub ${r.status} reading ${file}`);
  return r.text();
}

// One commit with the task's new text (moved to Inbox/Done/ for Done) and the board line moved, so the board and
// the task never disagree. The PC's next sync merges it (its own copy wins a clash) and rewrites Kanban.md.
async function move(env, file, lane, user) {
  const drop = dropFor(lane);
  if (!/^Inbox\/[\w.-]+\.md$/.test(file || "")) return json({ error: "Only Inbox tasks can be moved." }, 400);
  if (!drop) return json({ error: `Cards can't be dropped in ${lane}: the bots move them there.` }, 400);
  for (let attempt = 0; attempt < 3; attempt++) {
    const head = (await ghJson(env, `/git/ref/heads/${env.BRANCH}`)).object.sha;
    const text = await readFile(env, file, head);
    const props = frontmatter(text);
    if (drop.needsPr && !(props.pr || "").trim()) {
      return json({ error: `${file.slice(6, -3)} has no PR yet, so it can't go to ${lane}.` }, 400);
    }
    const who = user.global_name || user.username;
    let task = setProps(text, drop.props);
    task = addNote(task, stamp(new Date(), env.TIME_ZONE), `${who} ${drop.say} on the Discord board.`);
    const board = moveCardLine(await readFile(env, "Kanban.md", head), file, lane, drop.props.status);
    const name = file.slice("Inbox/".length);
    const tree = [{ path: "Kanban.md", mode: "100644", type: "blob", content: board }];
    if (drop.toDone) {
      tree.push({ path: `Inbox/Done/${name}`, mode: "100644", type: "blob", content: task });
      tree.push({ path: file, mode: "100644", type: "blob", sha: null });
    } else {
      tree.push({ path: file, mode: "100644", type: "blob", content: task });
    }
    const base = (await ghJson(env, `/git/commits/${head}`)).tree.sha;
    const newTree = await ghJson(env, "/git/trees", { method: "POST", body: JSON.stringify({ base_tree: base, tree }) });
    const commit = await ghJson(env, "/git/commits", {
      method: "POST",
      body: JSON.stringify({ message: `Discord board: ${name.slice(0, -3)} ${drop.say}`, tree: newTree.sha, parents: [head] }),
    });
    const r = await gh(env, `/git/refs/heads/${env.BRANCH}`,
                       { method: "PATCH", body: JSON.stringify({ sha: commit.sha, force: false }) });
    if (r.ok) return json({ ok: true, status: drop.props.status, lanes: parseKanban(board) });
    if (r.status !== 422) throw new Error(`GitHub ${r.status} saving the move`);
    // 422: someone synced in between; start again from the new tip
  }
  return json({ error: "The vault kept changing under the move. Try again in a moment." }, 409);
}
