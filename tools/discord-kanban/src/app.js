// The board page inside the Discord Activity. Bundled by esbuild into public/app.js (npm run build).
import { DiscordSDK } from "@discord/embedded-app-sdk";
import Sortable from "sortablejs";

// Inside Discord the page lives on <app id>.discordsays.com and reaches its Worker through Discord's /.proxy/.
const inDiscord = location.hostname.endsWith("discordsays.com");
const api = (p) => (inDiscord ? "/.proxy" : "") + p;
const REFRESH_MS = 30000;
let accessToken = null, canMove = false, drops = [], dragging = false;

const $ = (s) => document.querySelector(s);
function toast(text, bad = false) {
  const t = $("#toast");
  t.textContent = text;
  t.className = bad ? "show bad" : "show";
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.className = ""), 3500);
}

async function call(path, init = {}) {
  const r = await fetch(api(path), {
    ...init, headers: { "content-type": "application/json", authorization: `Bearer ${accessToken}`, ...(init.headers || {}) },
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.error || `Error ${r.status}`);
  return data;
}

async function signIn() {
  if (!inDiscord) throw new Error("Open this board from the Activities button in Discord.");
  const { clientId } = await (await fetch(api("/api/config"))).json();
  const sdk = new DiscordSDK(clientId);
  await sdk.ready();
  const { code } = await sdk.commands.authorize({
    client_id: clientId, response_type: "code", state: "", prompt: "none", scope: ["identify"],
  });
  const r = await fetch(api("/api/token"), {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ code }),
  });
  const data = await r.json();
  if (!data.access_token) throw new Error(data.error || "Discord sign-in failed.");
  accessToken = data.access_token;
  await sdk.commands.authenticate({ access_token: accessToken });
}

const dropRule = (lane) => drops.find((d) => d.lane === lane.trim().toLowerCase());

function render(lanes) {
  const board = $("#board");
  board.replaceChildren();
  for (const lane of lanes) {
    const col = document.createElement("section");
    col.className = "lane";
    const rule = dropRule(lane.name);
    if (!rule) col.classList.add("bots-only");
    col.innerHTML = `<header><h2></h2><span class="count"></span></header><ul></ul>`;
    col.querySelector("h2").textContent = lane.name;
    col.querySelector(".count").textContent = lane.cards.length;
    if (!rule) col.querySelector("header").title = "Only the bots move cards here";
    const list = col.querySelector("ul");
    list.dataset.lane = lane.name;
    for (const c of lane.cards) {
      const li = document.createElement("li");
      li.className = "card" + (c.movable && canMove ? "" : " fixed") + (c.done ? " done" : "");
      li.dataset.path = c.path;
      li.innerHTML = `<strong></strong><span></span>`;
      li.querySelector("strong").textContent = c.name;
      li.querySelector("span").textContent = c.meta;
      li.addEventListener("click", () => openTask(c));
      list.append(li);
    }
    board.append(col);
    if (canMove) {
      Sortable.create(list, {
        group: { name: "board", pull: true, put: () => !!rule },
        filter: ".fixed", preventOnFilter: false, sort: false,
        animation: 150, delay: 180, delayOnTouchOnly: true, ghostClass: "ghost",
        onStart: () => (dragging = true),
        onEnd: (e) => {
          dragging = false;
          if (e.from !== e.to) drop(e.item.dataset.path, e.to.dataset.lane, e);
        },
      });
    }
  }
}

async function drop(path, lane, e) {
  const name = path.replace(/^Inbox\//, "").replace(/\.md$/, "");
  e.item.classList.add("saving");
  try {
    const r = await call("/api/move", { method: "POST", body: JSON.stringify({ path, lane }) });
    render(r.lanes);
    toast(`${name} → ${lane}`);
  } catch (err) {
    e.from.insertBefore(e.item, e.from.children[e.oldIndex] || null);  // put it back
    e.item.classList.remove("saving");
    toast(err.message, true);
  }
}

async function openTask(card) {
  if (dragging) return;
  const panel = $("#task");
  panel.querySelector("h3").textContent = card.name;
  panel.querySelector("pre").textContent = "Loading…";
  panel.showModal();
  try {
    panel.querySelector("pre").textContent = (await call(`/api/task?path=${encodeURIComponent(card.path)}`)).text;
  } catch (err) {
    panel.querySelector("pre").textContent = err.message;
  }
}

async function refresh() {
  if (dragging || document.querySelector(".saving")) return;
  try {
    const r = await call("/api/board");
    canMove = r.canMove;
    drops = r.drops;
    render(r.lanes);
    $("#hint").textContent = canMove ? "Drag a card to move it. Tap a card to read it." : "Tap a card to read it.";
  } catch (err) {
    toast(err.message, true);
  }
}

$("#task button").addEventListener("click", () => $("#task").close());
signIn().then(() => {
  refresh();
  setInterval(refresh, REFRESH_MS);
}).catch((err) => {
  $("#hint").textContent = err.message;
});
