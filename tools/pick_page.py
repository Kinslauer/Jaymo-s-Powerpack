"""Turn a vault pick list (markdown) into a page Andy can tick, and write his picks back into the list.

    python tools/pick_page.py "E:/Jaymo Vault Live/Picklists/Small-armies pick-list.md"
        -> writes "Small-armies pick-list.html" beside it: open it in any browser (double-click), no internet needed.
    python tools/pick_page.py --apply "<pick list>.md" "<Downloads>/Small-armies pick-list.picks.txt"
    python tools/pick_page.py --apply "<pick list>.md" -        # picks pasted on stdin (what "Copy picks" gave)
        -> ticks the rows Andy picked, writes his notes and answers into the .md, sets `status: picked`.

Why: Andy reads pick lists in apps that show markdown raw (frontmatter, `- [ ]`, `^ids`), where nothing can be ticked.
The page is one self-contained HTML file: ticks and notes stay in that browser until he presses "Copy picks" (to paste
into a chat) or "Save picks" (a .picks.txt in Downloads). Both give the same text, which --apply reads.

The markdown format (the vault's existing pick lists already follow it; see .agents/skills/pick-page/SKILL.md):
    ---                                  frontmatter: type: picklist, source, url, licence, status, created, tags
    # Title                              the page title
    intro paragraphs
    ## Part                              a part of the page (a heading over groups)
    ### Group                            optional: a group of rows inside a part
    - [ ] **Row title** `S` ^row-id      a tickable row: size S/M/L, a unique id (letters, digits, -)
      what it is (indented lines)
      For us: how we'd build it
      Notes:                             Andy's note goes here
    ## Questions                         optional: decisions with one answer each
    ### The question? ^q-id
    context paragraph
    - (*) **Suggested option** ^opt-id   (*) = the suggested answer, picked by default
      what happens if he picks it
    - ( ) **Other option** ^opt-id2
A heading with no rows under it (Notes, Already ours, a shared rule...) shows as plain text.
"""
from __future__ import annotations

import html
import json
import re
import sys
from datetime import date
from pathlib import Path

ROW = re.compile(r"^- \[( |x|X)\] \*\*(.+?)\*\*(?:\s*`(\w+)`)?\s*\^([\w-]+)\s*$")
OPT = re.compile(r"^- \(( |\*|x)\) \*\*(.+?)\*\*\s*\^([\w-]+)\s*$")
HEAD_ID = re.compile(r"^(.*?)\s*\^([\w-]+)\s*$")


def split_front(text: str) -> tuple[dict, str, str]:
    """Frontmatter as a dict, its raw text, and the body."""
    if not text.startswith("---"):
        return {}, "", text
    end = text.find("\n---", 3)
    if end < 0:
        return {}, "", text
    raw = text[3:end].strip("\n")
    meta = {}
    for line in raw.splitlines():
        if ":" in line and not line.startswith(" "):
            k, v = line.split(":", 1)
            meta[k.strip()] = v.strip()
    return meta, raw, text[end + 4:].lstrip("\n")


def parse(text: str) -> dict:
    meta, _, body = split_front(text)
    page = {"meta": meta, "title": "", "intro": [], "blocks": []}
    part = None      # current ## heading
    group = None     # current block: {"kind": "rows"|"info"|"question", ...}
    item = None      # current row or option
    in_questions = False

    def new_block(kind, head, level):
        nonlocal group, item
        group = {"kind": kind, "head": head, "level": level, "part": part, "text": [], "rows": [], "opts": []}
        page["blocks"].append(group)
        item = None

    for line in body.splitlines():
        s = line.strip()
        if line.startswith("# ") and not page["title"]:
            page["title"] = line[2:].strip()
            continue
        if line.startswith("## "):
            part = line[3:].strip()
            in_questions = part.lower().startswith("questions")
            new_block("info", part, 2)
            continue
        if line.startswith("### "):
            head = line[4:].strip()
            if in_questions:
                m = HEAD_ID.match(head)
                new_block("question", m.group(1) if m else head, 3)
                group["id"] = m.group(2) if m else re.sub(r"\W+", "-", head.lower()).strip("-")[:40]
            else:
                new_block("info", head, 3)
            continue
        m = ROW.match(line)
        if m and group is not None:
            group["kind"] = "rows" if group["kind"] == "info" else group["kind"]
            item = {"id": m.group(4), "t": m.group(2), "s": m.group(3) or "", "on": m.group(1) != " ",
                    "d": [], "f": "", "note": ""}
            group["rows"].append(item)
            continue
        m = OPT.match(line)
        if m and group is not None and group["kind"] == "question":
            item = {"id": m.group(3), "t": m.group(2), "suggested": m.group(1) == "*", "chosen": m.group(1) == "x",
                    "d": []}
            group["opts"].append(item)
            continue
        if not s:
            continue
        if item is not None and line.startswith("  "):
            if "f" in item and s.startswith("For us:"):
                item["f"] = s[7:].strip()
            elif "note" in item and s.startswith("Notes:"):
                item["note"] = s[6:].strip()
            elif "note" in item and item["note"]:
                item["note"] += " " + s
            else:
                item["d"].append(s)
            continue
        if s.startswith("- [ ] (") or s.startswith("<!--"):
            continue
        item = None
        if group is not None and group["kind"] == "question" and s.startswith("**Andy's answer:**"):
            m = re.search(r"\^([\w-]+)", s)
            group["answer"] = m.group(1) if m else ""
            continue
        if group is None:
            page["intro"].append(s)
        else:
            group["text"].append(s)
    page["blocks"] = [b for b in page["blocks"] if b["rows"] or b["opts"] or b["text"] or b["level"] == 2]
    for b in page["blocks"]:
        for o in b["opts"]:
            o["chosen"] = o["chosen"] or o["id"] == b.get("answer")
    return page


def build(md_path: Path) -> Path:
    page = parse(md_path.read_text(encoding="utf-8"))
    rows = sum(len(b["rows"]) for b in page["blocks"])
    qs = sum(1 for b in page["blocks"] if b["kind"] == "question")
    if not rows and not qs:
        sys.exit(f"{md_path.name}: no rows (`- [ ] **Title** `S` ^id`) or questions found; nothing to tick.")
    ids = [r["id"] for b in page["blocks"] for r in b["rows"]] + [b["id"] for b in page["blocks"] if b.get("id")]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        sys.exit(f"{md_path.name}: these ids are used twice, make them unique: {', '.join(sorted(dup))}")
    page["file"] = md_path.name
    page["key"] = "pick-page:" + md_path.stem + ":" + page["meta"].get("created", "")
    data = json.dumps(page, ensure_ascii=False).replace("<", "\\u003c")
    title = page["title"] or md_path.stem
    out = md_path.with_suffix(".html")
    out.write_text(TEMPLATE.replace("__TITLE__", html.escape(title)).replace("__DATA__", data), encoding="utf-8")
    print(f"{out}  ({rows} rows, {qs} questions)")
    return out


def read_picks(text: str) -> dict:
    picks = {"rows": {}, "answers": {}, "note": ""}
    last = None
    for line in text.splitlines():
        s = line.strip()
        m = re.match(r"^\[( |x)\] ([\w-]+)", s)
        if m:
            last = picks["rows"][m.group(2)] = {"on": m.group(1) == "x", "note": ""}
            continue
        m = re.match(r"^Q ([\w-]+) = ([\w-]+)", s)
        if m:
            last = picks["answers"][m.group(1)] = {"opt": m.group(2), "note": ""}
            continue
        if s.startswith("note:") and last is not None:
            last["note"] = s[5:].strip()
            continue
        if s.startswith("NOTE:"):
            picks["note"] = s[5:].strip()
            last = None
    if not picks["rows"] and not picks["answers"]:
        sys.exit("No picks found: paste the text from the page's \"Copy picks\" (it starts with PICKS).")
    return picks


def apply(md_path: Path, picks_src: str) -> None:
    if picks_src == "-":
        sys.stdin.reconfigure(encoding="utf-8")  # Windows consoles default to cp1252; the picks text has "·"
    text = sys.stdin.read() if picks_src == "-" else Path(picks_src).expanduser().read_text(encoding="utf-8")
    picks = read_picks(text)
    lines = md_path.read_text(encoding="utf-8").splitlines()
    out, i, ticked, answered = [], 0, 0, 0
    seen_rows, seen_qs = set(), set()
    in_questions = False
    while i < len(lines):
        line = lines[i]
        if line.startswith("## "):
            in_questions = line[3:].strip().lower().startswith("questions")
        m = ROW.match(line)
        if m:
            rid = m.group(4)
            p = picks["rows"].get(rid, {"on": False, "note": ""})
            seen_rows.add(rid)
            ticked += p["on"]
            out.append(line.replace("- [" + m.group(1) + "]", "- [x]" if p["on"] else "- [ ]", 1))
            i += 1
            block = []
            while i < len(lines) and lines[i].startswith("  ") and not ROW.match(lines[i]):
                block.append(lines[i])
                i += 1
            had_notes = any(b.strip().startswith("Notes:") for b in block)
            block = [b for b in block if not b.strip().startswith("Notes:")]
            if p["note"] or had_notes:
                block.append(("  Notes: " + p["note"]) if p["note"] else "  Notes:")
            out.extend(block)
            continue
        if in_questions and line.startswith("### "):
            hm = HEAD_ID.match(line[4:].strip())
            out.append(line)
            i += 1
            if hm and hm.group(2) in picks["answers"]:
                a = picks["answers"][hm.group(2)]
                seen_qs.add(hm.group(2))
                answered += 1
                while i < len(lines) and lines[i].startswith("**Andy's answer:**"):
                    i += 1
                label = a["opt"]
                for later in lines[i:]:
                    if later.startswith("#"):
                        break
                    om = OPT.match(later)
                    if om and om.group(3) == a["opt"]:
                        label = om.group(2)
                out.append(f"**Andy's answer:** {label} ^{a['opt']}" + (f" (note: {a['note']})" if a["note"] else ""))
            continue
        out.append(line)
        i += 1
    body = "\n".join(out) + "\n"
    if picks["note"]:
        body = body.rstrip("\n") + f"\n\n**Andy's note ({date.today().isoformat()}):** {picks['note']}\n"
    if body.startswith("---"):
        end = body.find("\n---", 3)
        front = body[3:end]
        front = re.sub(r"(?m)^status:.*$", "status: picked", front) if re.search(r"(?m)^status:", front) \
            else front.rstrip("\n") + "\nstatus: picked\n"
        front = re.sub(r"(?m)^picked:.*\n?", "", front).rstrip("\n") + f"\npicked: {date.today().isoformat()}\n"
        body = "---\n" + front.lstrip("\n") + body[end + 1:]
    md_path.write_text(body, encoding="utf-8")
    unknown = sorted(set(picks["rows"]) - seen_rows) + sorted(set(picks["answers"]) - seen_qs)
    print(f"{md_path.name}: {ticked} rows ticked, {answered} questions answered, status: picked")
    if unknown:
        print("Not in the list (ids changed since the page was made?): " + ", ".join(unknown))


TEMPLATE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>__TITLE__</title>
<style>
:root{--bg:#f3f5f4;--panel:#fff;--fg:#1b2422;--muted:#5d6b67;--line:#d9e0dd;--accent:#1f6f5c;--accent-soft:#e1efea;
--on-accent:#fff;--font:"Segoe UI",system-ui,-apple-system,Roboto,sans-serif;color-scheme:light}
@media (prefers-color-scheme:dark){:root{--bg:#121816;--panel:#1a2220;--fg:#e4ebe8;--muted:#97a6a1;--line:#2d3936;
--accent:#5cc3a6;--accent-soft:#1f3630;--on-accent:#0d1f1a;color-scheme:dark}}
*{box-sizing:border-box}[hidden]{display:none!important}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 var(--font)}
.wrap{max-width:860px;margin:0 auto;padding:0 16px}
header{position:sticky;top:0;z-index:5;background:var(--panel);border-bottom:1px solid var(--line)}
.top{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;padding:14px 0 10px}
h1{font-size:1.35rem;margin:0}.sub{color:var(--muted);font-size:.85rem;margin:2px 0 0}
.count{font-size:.85rem;color:var(--muted);font-variant-numeric:tabular-nums}
.bar{display:flex;gap:8px;flex-wrap:wrap;padding-bottom:10px}
.search{flex:1 1 200px;min-width:0;font:inherit;font-size:.9rem;padding:7px 10px;border:1px solid var(--line);border-radius:8px;background:var(--bg);color:var(--fg)}
.btn{font:inherit;font-size:.88rem;font-weight:600;padding:7px 14px;border-radius:8px;border:1px solid var(--accent);background:var(--accent);color:var(--on-accent);cursor:pointer}
.btn.ghost{background:transparent;color:var(--accent)}
main{padding:18px 0 60px;display:flex;flex-direction:column;gap:14px}
.intro{color:var(--muted);margin:0;max-width:68ch}
h2{font-size:.8rem;text-transform:uppercase;letter-spacing:.08em;color:var(--muted);margin:12px 0 0}
.text{color:var(--muted);font-size:.9rem;margin:0;max-width:68ch}
.group{background:var(--panel);border:1px solid var(--line);border-radius:10px;overflow:hidden}
.ghead{padding:12px 16px;border-bottom:1px solid var(--line)}.ghead b{font-size:1rem}
.ghead p{margin:4px 0 0;color:var(--muted);font-size:.85rem}
.row{display:grid;grid-template-columns:auto 1fr;gap:4px 12px;padding:12px 16px;border-top:1px solid var(--line)}
.row:first-of-type{border-top:0}.row.on{background:var(--accent-soft)}
.row input[type=checkbox]{width:18px;height:18px;margin-top:3px;accent-color:var(--accent)}
.rb{display:flex;flex-direction:column;gap:4px;min-width:0}
.rt{font-weight:600}.size{font-size:.7rem;font-weight:600;border:1px solid var(--line);border-radius:4px;padding:0 5px;color:var(--muted);margin-left:6px}
.rd{margin:0;font-size:.9rem}.rf{margin:0;font-size:.82rem;color:var(--muted)}
.linkbtn{font:inherit;font-size:.8rem;background:none;border:0;color:var(--accent);cursor:pointer;padding:2px 0;align-self:flex-start}
textarea{width:100%;font:inherit;font-size:.9rem;color:var(--fg);background:var(--bg);border:1px solid var(--line);border-radius:8px;padding:8px 10px;min-height:2.6em;resize:vertical}
.card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px;display:flex;flex-direction:column;gap:8px}
.card h3{margin:0;font-size:1.05rem}
.opt{display:grid;grid-template-columns:auto 1fr;gap:2px 10px;padding:9px 11px;border:1px solid var(--line);border-radius:8px;cursor:pointer}
.opt:has(input:checked){border-color:var(--accent);background:var(--accent-soft)}
.opt input{margin-top:4px;accent-color:var(--accent)}.opt .oc{grid-column:2;color:var(--muted);font-size:.85rem}
.rec{font-size:.7rem;font-weight:700;color:var(--accent);margin-left:6px}
a{color:var(--accent)}code{font-size:.85em}
#out{display:none;min-height:10em}
:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
</style></head><body>
<header><div class="wrap">
 <div class="top"><div><h1 id="title"></h1><p class="sub" id="sub"></p></div><span class="count" id="count"></span></div>
 <div class="bar">
  <input class="search" id="q" type="search" placeholder="Find a row" aria-label="Find a row">
  <button class="btn ghost" id="only" type="button" aria-pressed="false">Show ticked only</button>
  <button class="btn" id="copy" type="button">Copy picks</button>
  <button class="btn ghost" id="save" type="button">Save picks</button>
 </div>
</div></header>
<main class="wrap" id="main"></main>
<div class="wrap"><p class="intro" id="msg">Your ticks stay in this browser. When you're done, press <b>Copy picks</b> and paste it to the assistant, or <b>Save picks</b> and tell it the file is in Downloads.</p>
<textarea id="out" readonly aria-label="Your picks as text"></textarea></div>
<script type="application/json" id="data">__DATA__</script>
<script>
(function(){
const P=JSON.parse(document.getElementById('data').textContent);
const $=id=>document.getElementById(id);
let S={rows:{},answers:{},note:''};
try{const v=JSON.parse(localStorage.getItem(P.key)||'null');if(v)S=Object.assign(S,v);}catch(e){}
P.blocks.forEach(b=>b.rows.forEach(r=>{if(r.on&&!(r.id in S.rows))S.rows[r.id]={on:true,note:r.note||''};else if(r.note&&!(r.id in S.rows))S.rows[r.id]={on:false,note:r.note};}));
function keep(){try{localStorage.setItem(P.key,JSON.stringify(S));}catch(e){}count();}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function md(s){return esc(s).replace(/\*\*(.+?)\*\*/g,'<b>$1</b>').replace(/`([^`]+)`/g,'<code>$1</code>')
  .replace(/\[([^\]]+)\]\((https:[^)\s]+)\)/g,'<a href="$2" target="_blank" rel="noopener">$1</a>')
  .replace(/(^|\s)(https:\/\/[^\s<]+)/g,'$1<a href="$2" target="_blank" rel="noopener">$2</a>')
  .replace(/\[\[([^\]|]+\|)?([^\]]+)\]\]/g,'$2');}
function el(t,cls,h){const e=document.createElement(t);if(cls)e.className=cls;if(h!=null)e.innerHTML=h;return e;}
$('title').textContent=P.title||P.file;document.title=P.title||P.file;
$('sub').textContent=[P.meta.source,P.meta.licence].filter(Boolean).join(' · ');
function count(){let n=0,t=0;P.blocks.forEach(b=>b.rows.forEach(r=>{n++;if(S.rows[r.id]&&S.rows[r.id].on)t++;}));
  const q=P.blocks.filter(b=>b.kind==='question').length;$('count').textContent=t+' of '+n+' ticked'+(q?' · '+q+' question'+(q>1?'s':''):'');}
function row(r){const st=S.rows[r.id]||{};const d=el('div','row'+(st.on?' on':''));
  const cb=el('input');cb.type='checkbox';cb.id='cb-'+r.id;cb.checked=!!st.on;
  const b=el('div','rb');b.appendChild(el('label','rt',md(r.t)+(r.s?'<span class="size" title="Size of the job">'+esc(r.s)+'</span>':''))).htmlFor=cb.id;
  r.d.forEach(x=>b.appendChild(el('p','rd',md(x))));if(r.f)b.appendChild(el('p','rf','For us: '+md(r.f)));
  const nb=el('button','linkbtn',st.note?'Edit note':'Add note');nb.type='button';b.appendChild(nb);
  const ta=el('textarea');ta.placeholder='Your note on this row';ta.value=st.note||'';ta.hidden=!st.note;b.appendChild(ta);
  nb.onclick=()=>{ta.hidden=false;ta.focus();};
  ta.oninput=()=>{S.rows[r.id]=Object.assign({},S.rows[r.id],{note:ta.value});keep();};
  cb.onchange=()=>{S.rows[r.id]=Object.assign({},S.rows[r.id],{on:cb.checked});d.classList.toggle('on',cb.checked);keep();};
  d.appendChild(cb);d.appendChild(b);return d;}
function question(b){const c=el('div','card');c.appendChild(el('h3',null,md(b.head)));
  b.text.forEach(x=>c.appendChild(el('p','text',md(x))));
  const cur=(S.answers[b.id]||{}).opt||((b.opts.find(o=>o.chosen)||b.opts.find(o=>o.suggested)||{}).id);
  b.opts.forEach(o=>{const l=el('label','opt');const i=el('input');i.type='radio';i.name='q-'+b.id;i.checked=cur===o.id;
    i.onchange=()=>{S.answers[b.id]=Object.assign({},S.answers[b.id],{opt:o.id});keep();};
    l.appendChild(i);l.appendChild(el('span',null,md(o.t)+(o.suggested?'<span class="rec">SUGGESTED</span>':'')));
    if(o.d.length)l.appendChild(el('span','oc',md(o.d.join(' '))));c.appendChild(l);});
  const ta=el('textarea');ta.placeholder='Anything else, or your own answer';ta.value=(S.answers[b.id]||{}).note||'';
  ta.oninput=()=>{S.answers[b.id]=Object.assign({opt:cur},S.answers[b.id],{note:ta.value});keep();};c.appendChild(ta);return c;}
function render(){const m=$('main');m.textContent='';const term=$('q').value.toLowerCase();const only=$('only').getAttribute('aria-pressed')==='true';
  if(!term&&!only)P.intro.forEach(x=>m.appendChild(el('p','intro',md(x))));
  P.blocks.forEach(b=>{
    if(b.kind==='question'){if(!only&&(!term||b.head.toLowerCase().includes(term)))m.appendChild(question(b));return;}
    if(!b.rows.length){if(term||only)return;m.appendChild(el(b.level===2?'h2':'div',b.level===2?null:'text',b.level===2?esc(b.head):'<b>'+md(b.head)+'</b>'));
      b.text.forEach(x=>m.appendChild(el('p','text',md(x))));return;}
    const rows=b.rows.filter(r=>(!only||(S.rows[r.id]||{}).on)&&(!term||(b.head+' '+r.t+' '+r.d.join(' ')).toLowerCase().includes(term)));
    if(!rows.length)return;const g=el('div','group');const h=el('div','ghead','<b>'+md(b.head)+'</b>');
    b.text.forEach(x=>h.appendChild(el('p',null,md(x))));g.appendChild(h);rows.forEach(r=>g.appendChild(row(r)));m.appendChild(g);});
  const c=el('div','card');c.appendChild(el('h3',null,'Anything else?'));const ta=el('textarea');ta.placeholder='A note on the whole list';ta.value=S.note||'';
  ta.oninput=()=>{S.note=ta.value;keep();};c.appendChild(ta);if(!term&&!only)m.appendChild(c);count();}
function text(){const L=['PICKS v1 · '+P.file,''];
  P.blocks.forEach(b=>{b.rows.forEach(r=>{const s=S.rows[r.id];if(s&&(s.on||s.note)){L.push('['+(s.on?'x':' ')+'] '+r.id+' · '+r.t);if(s.note)L.push('    note: '+s.note.replace(/\s+/g,' '));}});
    if(b.kind==='question'){const a=S.answers[b.id]||{};const opt=a.opt||((b.opts.find(o=>o.chosen)||b.opts.find(o=>o.suggested)||{}).id);
      if(opt){const o=b.opts.find(x=>x.id===opt);L.push('Q '+b.id+' = '+opt+' · '+(o?o.t:''));if(a.note)L.push('    note: '+a.note.replace(/\s+/g,' '));}}});
  if(S.note)L.push('NOTE: '+S.note.replace(/\s+/g,' '));return L.join('\n')+'\n';}
$('copy').onclick=()=>{const t=text();const o=$('out');o.value=t;const fb=()=>{o.style.display='block';o.focus();o.select();$('msg').textContent='Copy the text below (Ctrl+C) and paste it to the assistant.';};
  try{navigator.clipboard.writeText(t).then(()=>{$('msg').textContent='Copied. Paste it to the assistant.';},fb);}catch(e){fb();}};
$('save').onclick=()=>{const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([text()],{type:'text/plain'}));
  a.download=P.file.replace(/\.md$/,'')+'.picks.txt';document.body.appendChild(a);a.click();a.remove();
  $('msg').textContent='Saved as '+a.download+' in your Downloads. Tell the assistant it is there.';};
$('q').oninput=render;
$('only').onclick=e=>{const b=e.currentTarget;b.setAttribute('aria-pressed',String(b.getAttribute('aria-pressed')!=='true'));b.textContent=b.getAttribute('aria-pressed')==='true'?'Show all':'Show ticked only';render();};
render();
})();
</script></body></html>
"""

if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 3 and args[0] == "--apply":
        apply(Path(args[1]).expanduser(), args[2])
    elif len(args) == 1 and not args[0].startswith("-"):
        build(Path(args[0]).expanduser())
    else:
        sys.exit(__doc__)
