#!/usr/bin/env python3
"""Checks the story data Kingdoms loads: tales (data/jaymoskingdoms/tales/*.json) and overheard scenes
(data/jaymoskingdoms/scenes/*.json). The format is docs/StoryQuests-Plan.md, section 3 and 4.

Run from the repo root: python3 tools/tale_check.py [files...]. With no files it checks every tale and scene.
Exit code 1 if anything is wrong. tools/pr_checks.py runs it.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "kingdoms/src/main/resources/data/jaymoskingdoms"

STARTS = {"giver", "rumour", "book", "letter", "companion", "found", "notice"}
BODIES = {"villager", "fighter"}
PLACES = {"home", "town", "other_town", "tavern", "wild", "road", "ruin", "camp", "near"}
STEP_FIELDS = {
    "talk": {"who", "say", "replies", "voices", "not_now"},
    "name": {"who", "say", "suspects", "none", "not_now"},
    "vigil": {"at", "radius", "lines", "gap", "left"},
    "go": {"to", "radius"},
    "search": {"at", "clues", "need"},
    "foe": {"who", "slain", "spared", "taunt", "plea", "mercy", "thanks", "yield_at", "spared_returns", "weak_to"},
    "bring": {"who", "item", "count", "say", "not_now"},
    "slay": {"target", "count"},
    "wait": {"days", "wake", "letter"},
    "await": {"who", "noted"},
    "defend": {"at", "waves", "foes", "stars", "failed"},
    "escort": {"who", "to", "failed"},
    "pack": {"at", "foes", "count", "stars"},
    "build": {"at", "blocks", "count"},
    "blast": {"at"},
    "stash": {"at", "item", "count"},
    "lamps": {"at", "count", "failed"},
    "rescue": {"who", "at", "kin", "stars", "failed"},
    "capture": {"who", "at", "crime", "stars", "failed", "let_go"},
}
COMMON = {"do", "goal", "journal", "then", "give", "mark", "pay", "hidden", "within", "late", "until", "clock", "when", "candle"}
# (docs/StoryQuests-Plan.md, section 7) when a step can be done, how a try is made, how someone is dressed
WHENS = {"night", "day", "moon:full", "moon:new"}
TRIES = {"persuade", "threaten", "bribe", "dice"}
DRESS = {"fine", "plain", "ragged", "armoured", "bloodied"}
TONGUES = {"common", "northern", "southern", "wildwood", "blossom", "old"}
PATRONS = {"hearthkeeper", "anvil_father", "deep_mother", "green_huntsman", "lantern_warden", "crowned_sun", "ash_whisperer", "truth", "none"}
HERITAGES = {"heartlander", "stoneborn", "tideborn", "sylvan", "verdant", "emberborn", "frostborn", "nightborn", "warriorborn", "earthling",
             "stormborn", "dawnborn", "endborn", "dragonblood", "deepborn", "divine", "biobuilt", "rubberfolk", "goblin", "orc", "beastfolk", "hollow"}
COMPARE = re.compile(r"^(reputation|hearts|coins|since:[a-z0-9_]+)(>=|<)(-?\d+)$")
REQUIRED = {
    "talk": {"who", "say"}, "go": {"to"}, "search": {"at", "clues"}, "foe": {"who", "slain"},
    "bring": {"who", "item", "count"}, "slay": {"target", "count"}, "wait": {"days"}, "defend": {"at"},
    "escort": {"who", "to"}, "pack": {"at", "foes", "count"}, "build": {"at", "blocks", "count"}, "blast": {"at"},
    "stash": {"at", "item", "count"}, "lamps": {"at", "count"},
    "rescue": {"who", "at"}, "capture": {"who", "at"}, "await": {"who"},
    "name": {"who", "suspects"}, "vigil": {"at", "lines"},
}
# Whose kin a captive is, and what a criminal is wanted for (work.jaymoskingdoms.kin.* / crime.* in lang/parts/captures.json).
KIN = {"daughter", "son", "sister", "brother", "wife", "husband", "mother", "father", "apprentice", "neighbour"}
CRIMES = {"theft", "murder", "arson", "smuggling", "poaching", "desertion", "kidnapping", "forgery"}
ENDING_FIELDS = {"text", "reward", "marks", "failed", "town", "returns", "joins", "against", "later"}
REWARD_FIELDS = {"coins", "items", "relic", "book", "hearts", "reputation"}
RETURNS = {"thanks", "warning", "recruit", "revenge"}
# What a companion can truly believe in (Convictions.java); "self" is the companion whose own tale it is.
CONVICTIONS = {"mercy", "faith", "law", "courage", "honour", "gain", "vengeance", "self"}
BELIEFS = CONVICTIONS - {"self"}
PLACEHOLDER = re.compile(r"\{([^{}]+)\}")
ITEM = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
BANNED = ("—", "–")


def check_against(c, where, value, tale):
    if not isinstance(value, list) or not value or any(v not in CONVICTIONS for v in value):
        c.err(where, f"against is a list of {sorted(CONVICTIONS)}")
    elif "self" in value and tale.get("start", {}).get("by") != "companion":
        c.err(where, "'self' is only for a companion's own tale")


def check_returns(c, where, back, tale, who):
    """Who comes back, and how: { who, days, as, say, gift } (who may be left out where it's the foe spared)."""
    if not isinstance(back, dict):
        c.err(where, "returns is one object: { who, days, as, say, gift }")
        return
    who = back.get("who", who)
    check_role(c, where, who, tale)
    if back.get("as") not in RETURNS:
        c.err(where, f"as must be one of {sorted(RETURNS)}")
    check_days(c, where + ".days", back.get("days"))
    if "say" not in back:
        c.err(where, "needs 'say'")
    else:
        check_text(c, where + ".say", back["say"], tale)
    if tale.get("cast", {}).get(who, {}).get("body") != "fighter" and back.get("as") in ("revenge", "recruit"):
        c.err(where, "only a fighter comes back for revenge or to join")


class Check:
    def __init__(self, path):
        self.path = path
        self.errors = []

    def err(self, where, what):
        self.errors.append(f"{self.path.name}: {where}: {what}")


def is_switch(value):
    return isinstance(value, dict) and value and all(k.startswith("?") for k in value)


def branches(value):
    """Every value a field can take: itself, or each arm of a switch."""
    if is_switch(value):
        out = []
        for arm in value.values():
            out.extend(branches(arm))
        return out
    return [value]


def check_condition(c, where, cond, tale):
    if not isinstance(cond, str):
        c.err(where, f"condition must be a string, got {cond!r}")
        return
    if "&" in cond:
        for part in cond.split("&"):
            check_condition(c, where, part.strip(), tale)
        return
    body = cond[1:] if cond.startswith("!") else cond
    if body == "powers" or body.startswith("mark:") or body.startswith("done:"):
        return
    if body in ("night", "day", "human", "cursed", "companion", "moon:full", "moon:new", "moon:blood") or COMPARE.match(body):
        return
    kind, _, value = body.partition(":")
    known = {"heritage": HERITAGES, "patron": PATRONS, "dress": DRESS, "reads": TONGUES, "companion": BELIEFS,
             "late": clocks(tale), "met": set(tale.get("cast", {})) | {"giver"}}
    if kind in known and "=" not in body:
        if value not in known[kind]:
            c.err(where, f"condition '{cond}': '{value}' is not one of {sorted(known[kind])}")
        return
    if "=" in body:
        key, value = body.split("=", 1)
        rolls = tale.get("roll", {})
        if key not in rolls:
            c.err(where, f"condition '{cond}' uses roll '{key}', which the tale doesn't roll")
        elif value not in rolls[key]:
            c.err(where, f"condition '{cond}': '{value}' is not one of roll '{key}'")
        return
    c.err(where, f"unknown condition '{cond}'")


def clocks(tale):
    """Every clock a step of the tale starts ("clock": {name: days})."""
    out = set()
    for step in tale.get("steps", {}).values():
        if isinstance(step.get("clock"), dict):
            out |= set(step["clock"])
    return out


def check_written(c, where, spec, tale):
    """What a letter, a clue or a thing given may say of how it's read: tongue, mark, told."""
    if "tongue" in spec and spec["tongue"] not in TONGUES:
        c.err(where, f"tongue must be one of {sorted(TONGUES)}")
    if "told" in spec:
        check_text(c, where + ".told", spec["told"], tale)
        if "tongue" not in spec:
            c.err(where, "'told' is what a reader may spread: it needs a 'tongue'")


def check_switch_keys(c, where, value, tale):
    if is_switch(value):
        keys = list(value)
        if "?" not in keys:
            c.err(where, "switch has no catch-all '?' arm")
        for k in keys:
            if k != "?":
                check_condition(c, where, k[1:], tale)
        for arm in value.values():
            check_switch_keys(c, where, arm, tale)


def check_text(c, where, value, tale, extra=()):
    check_switch_keys(c, where, value, tale)
    for text in branches(value):
        texts = text if isinstance(text, list) else [text]
        for t in texts:
            if not isinstance(t, str) or not t.strip():
                c.err(where, f"text must be a non-empty string, got {t!r}")
                continue
            for bad in BANNED:
                if bad in t:
                    c.err(where, "no long dashes in text")
            for name in PLACEHOLDER.findall(t):
                if name in ("player", "giver", "town", "named") or name in extra:
                    continue
                if name in tale.get("cast", {}) or name in tale.get("roll", {}):
                    continue
                if name.startswith("coins:"):
                    if not name[6:].isdigit():
                        c.err(where, f"{{{name}}}: coins need a whole number")
                    continue
                if name.startswith("left:"):
                    if name[5:] not in clocks(tale):
                        c.err(where, f"{{{name}}}: no step starts that clock")
                    continue
                if name.startswith("place:") or name.startswith("way:"):
                    if name.split(":", 1)[1] not in tale.get("places", {}):
                        c.err(where, f"{{{name}}}: no such place")
                    continue
                c.err(where, f"unknown placeholder {{{name}}}")


def check_role(c, where, who, tale):
    for w in branches(who):
        if w != "giver" and w not in tale.get("cast", {}):
            c.err(where, f"no such role '{w}'")


def check_place(c, where, key, tale):
    for k in branches(key):
        if k not in tale.get("places", {}):
            c.err(where, f"no such place '{k}'")


def check_then(c, where, then, tale, edges):
    check_switch_keys(c, where, then, tale)
    for t in branches(then):
        if not isinstance(t, str):
            c.err(where, f"'then' must be a step id or end:<ending>, got {t!r}")
        elif t.startswith("end:"):
            if t[4:] not in tale.get("endings", {}):
                c.err(where, f"no such ending '{t[4:]}'")
        elif t not in tale.get("steps", {}):
            c.err(where, f"no such step '{t}'")
        else:
            edges.append(t)


def check_item(c, where, item):
    if not isinstance(item, str) or not (ITEM.match(item) or item.startswith("#")):
        c.err(where, f"'{item}' is not an item id")


def check_days(c, where, days):
    if not (isinstance(days, list) and len(days) == 2 and all(isinstance(d, int) for d in days) and 0 < days[0] <= days[1]):
        c.err(where, f"days must be [least, most] whole days, got {days!r}")


def check_letter(c, where, letter, tale):
    if not isinstance(letter, dict) or "from" not in letter or "lines" not in letter:
        c.err(where, "a letter needs 'from' and 'lines'")
        return
    check_text(c, where + ".from", letter["from"], tale)
    check_text(c, where + ".lines", letter["lines"], tale)
    check_written(c, where, letter, tale)


def check_try(c, where, t, tale, edges):
    """A reply that may or may not sway its listener: { how, odds, coins, helps, hurts, fail, refuse }."""
    if not isinstance(t, dict) or t.get("how") not in TRIES:
        c.err(where, f"a try needs 'how': one of {sorted(TRIES)}")
        return
    unknown = set(t) - {"how", "odds", "coins", "helps", "hurts", "fail", "refuse"}
    if unknown:
        c.err(where, f"unknown fields {sorted(unknown)}")
    if not (isinstance(t.get("odds", 0), int) and -2 <= t.get("odds", 0) <= 2):
        c.err(where, "odds is -2 (very hard) to 2 (very easy)")
    if t["how"] in ("bribe", "dice") and not (isinstance(t.get("coins"), int) and t["coins"] > 0):
        c.err(where, "a bribe needs 'coins' (its price before the listener's greed), and a throw of the dice its stake")
    if t["how"] not in ("bribe", "dice") and "coins" in t:
        c.err(where, "only a bribe or a throw of the dice has 'coins'")
    for field in ("helps", "hurts"):
        for cond in t.get(field, []):
            check_condition(c, f"{where}.{field}", cond, tale)
    if "fail" in t:
        check_then(c, where + ".fail", t["fail"], tale, edges)
    if "refuse" in t:
        check_text(c, where + ".refuse", t["refuse"], tale)


def check_lie(c, where, lie, tale):
    """A lie told, and what it costs if it comes out: { name, days, unless, town, reputation, hearts }."""
    if not isinstance(lie, dict) or not re.match(r"^[a-z0-9_]+$", str(lie.get("name", ""))):
        c.err(where, "a lie needs a 'name' in lower_snake_case (it's marked lie:<name>, and caught:<name> if it comes out)")
        return
    unknown = set(lie) - {"name", "days", "unless", "town", "reputation", "hearts", "returns"}
    if unknown:
        c.err(where, f"unknown fields {sorted(unknown)}")
    check_days(c, where + ".days", lie.get("days"))
    if "unless" in lie:
        check_condition(c, where + ".unless", lie["unless"], tale)
    if "returns" in lie:
        # (they come on the day it's out: no days of their own)
        back = lie["returns"]
        if not isinstance(back, dict) or set(back) - {"who", "as", "say", "gift"} or not {"who", "as", "say"} <= set(back) or back.get("as") not in RETURNS:
            c.err(where + ".returns", f"returns is {{ who, as, say, gift }}, as one of {sorted(RETURNS)}")
        else:
            check_role(c, where + ".returns", back["who"], tale)
            check_text(c, where + ".returns.say", back["say"], tale)
    if "town" not in lie:
        c.err(where, "needs 'town' (what's said when it comes out)")
    else:
        check_text(c, where + ".town", lie["town"], tale)


def check_later(c, where, later, tale):
    """What an ending brings weeks on: { days, if, letter, town, scene, settles, mark }."""
    if not isinstance(later, dict):
        c.err(where, "later is a list of { days, ... }")
        return
    unknown = set(later) - {"days", "if", "letter", "town", "scene", "settles", "mark"}
    if unknown:
        c.err(where, f"unknown fields {sorted(unknown)}")
    check_days(c, where + ".days", later.get("days"))
    if not set(later) & {"letter", "town", "scene", "settles", "mark"}:
        c.err(where, "brings nothing: add a letter, town, scene, settles or mark")
    if "if" in later:
        check_condition(c, where, later["if"], tale)
    if "letter" in later:
        check_letter(c, where + ".letter", later["letter"], tale)
    if "town" in later:
        check_text(c, where + ".town", later["town"], tale)
    if "settles" in later:
        check_role(c, where + ".settles", later["settles"], tale)
        if tale.get("cast", {}).get(later["settles"], {}).get("body", "villager") != "villager":
            c.err(where, "only a villager settles in a town")
    scene = later.get("scene", [])
    if "scene" in later and not (isinstance(scene, list) and 2 <= len(scene) <= 6):
        c.err(where + ".scene", "two to six lines")
    for i, line in enumerate(scene if isinstance(scene, list) else []):
        if not (isinstance(line, list) and len(line) == 2 and line[0] in ("a", "b") and isinstance(line[1], str) and line[1].strip()):
            c.err(f"{where}.scene[{i}]", "each line is [\"a\" or \"b\", \"words\"]")
        else:
            check_text(c, f"{where}.scene[{i}]", line[1], tale)
            if len(line[1]) > 140:
                c.err(f"{where}.scene[{i}]", "a line is at most 140 characters (it's a speech bubble)")


def check_tale(path):
    c = Check(path)
    try:
        tale = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as e:
        c.err("file", f"not valid JSON: {e}")
        return c.errors
    for key in ("title", "start", "first", "steps", "endings"):
        if key not in tale:
            c.err("tale", f"missing '{key}'")
    if c.errors:
        return c.errors
    unknown = set(tale) - {"title", "arc", "start", "roll", "cast", "places", "first", "steps", "endings"}
    if unknown:
        c.err("tale", f"unknown fields {sorted(unknown)}")
    if not re.match(r"^[a-z0-9_]+$", path.stem):
        c.err("file", "file name must be lower_snake_case")
    check_text(c, "title", tale["title"], tale)

    for key, values in tale.get("roll", {}).items():
        if not (isinstance(values, list) and len(values) >= 2 and all(isinstance(v, str) for v in values)):
            c.err(f"roll.{key}", "a roll needs a list of two or more values")

    start = tale["start"]
    by = start.get("by")
    if by not in STARTS:
        c.err("start", f"'by' must be one of {sorted(STARTS)}")
    for cond in start.get("needs", []) + start.get("bars", []):
        check_condition(c, "start", cond, tale)
    if by == "rumour":
        if "rumour" not in start:
            c.err("start", "a rumour start needs 'rumour' (what the teller says)")
        else:
            check_text(c, "start.rumour", start["rumour"], tale)
    if by == "notice":
        notice = start.get("notice")
        if not isinstance(notice, dict) or not {"title", "lines", "hand"} <= set(notice):
            c.err("start", "a notice start needs notice.title, notice.lines and notice.hand (whose hand it's in)")
        else:
            check_text(c, "start.notice.lines", notice["lines"], tale, extra=())
            for line in notice["lines"] if isinstance(notice["lines"], list) else []:
                if "{" in line:
                    c.err("start.notice.lines", "a notice is on the board before anyone has the tale: no placeholders")
    if by == "book":
        book = start.get("book")
        if not isinstance(book, dict) or not {"title", "pages", "found"} <= set(book):
            c.err("start", "a book start needs book.title, book.pages and book.found")
        else:
            check_text(c, "start.book.pages", book["pages"], tale)
            if len(book["title"]) > 32:
                c.err("start.book.title", "a book's title is at most 32 characters")
            for page in book["pages"]:
                if len(page) > 900:
                    c.err("start.book.pages", "a page is at most 900 characters")
            for found in book["found"]:
                if found != "ruin" and not found.startswith("mob:"):
                    c.err("start.book.found", f"'{found}' must be 'ruin' or 'mob:<entity id or #tag>'")
    if by == "letter":
        letter = start.get("letter")
        if not isinstance(letter, dict) or "days" not in letter:
            c.err("start", "a letter start needs letter.from, letter.lines and letter.days")
        else:
            check_letter(c, "start.letter", letter, tale)
            check_days(c, "start.letter.days", letter["days"])
        if not start.get("needs"):
            c.err("start", "a letter start needs 'needs' (what must have happened first)")

    for role, who in tale.get("cast", {}).items():
        where = f"cast.{role}"
        if role == "giver":
            c.err(where, "'giver' is always the asker; don't define it")
        if "name" not in who:
            c.err(where, "missing 'name'")
        body = who.get("body", "villager")
        if body not in BODIES and not ITEM.match(body):
            c.err(where, f"body '{body}' must be villager, fighter or an entity id")
        if "at" in who:
            check_place(c, where, who["at"], tale)
        if "if" in who:
            check_condition(c, where, who["if"], tale)
        if who.get("hours", "night") not in ("night", "day"):
            c.err(where, "hours must be night or day")
        unknown = set(who) - {"name", "body", "job", "female", "at", "if", "late", "hours", "lying", "health", "damage", "armor", "hand", "head", "chest", "men", "men_body"}
        if unknown:
            c.err(where, f"unknown fields {sorted(unknown)}")
        for slot in ("hand", "head", "chest"):
            if slot in who:
                check_item(c, where + "." + slot, who[slot])

    for key, place in tale.get("places", {}).items():
        where = f"places.{key}"
        if place.get("at") not in PLACES:
            c.err(where, f"'at' must be one of {sorted(PLACES)}")
        if "name" not in place:
            c.err(where, "missing 'name'")
        if "from" in place and place["from"] != "home" and place["from"] not in tale.get("places", {}):
            c.err(where, f"'from': no such place '{place['from']}'")
        if place.get("at") in ("wild", "road", "near") and "dist" not in place:
            c.err(where, "needs 'dist': [least, most]")
        if "dist" in place:
            d = place["dist"]
            if not (isinstance(d, list) and len(d) == 2 and 0 < d[0] <= d[1] <= 1500):
                c.err(where, f"dist must be [least, most] within 1500 blocks, got {d!r}")

    steps = tale["steps"]
    graph = {}
    has_wait = False
    for sid, step in steps.items():
        where = f"steps.{sid}"
        do = step.get("do")
        edges = []
        graph[sid] = edges
        if do not in STEP_FIELDS:
            c.err(where, f"'do' must be one of {sorted(STEP_FIELDS)}")
            continue
        unknown = set(step) - COMMON - STEP_FIELDS[do]
        if unknown:
            c.err(where, f"unknown fields {sorted(unknown)} for '{do}'")
        missing = REQUIRED[do] - set(step)
        if missing:
            c.err(where, f"missing {sorted(missing)}")
            continue
        for field in ("goal", "journal", "until", "not_now"):
            if field in step:
                check_text(c, f"{where}.{field}", step[field], tale)
        if "when" in step and step["when"] not in WHENS:
            c.err(where, f"when must be one of {sorted(WHENS)}")
        if "clock" in step:
            if not isinstance(step["clock"], dict) or not step["clock"]:
                c.err(where, "clock is { name: days }")
            else:
                for name, days in step["clock"].items():
                    if not (isinstance(days, int) and days > 0):
                        check_days(c, f"{where}.clock.{name}", days)
        if ("within" in step) != ("late" in step):
            c.err(where, "'within' (the days it has) and 'late' (where it goes when they run out) go together")
        if "within" in step:
            within = step["within"]
            if isinstance(within, str):
                if within not in clocks(tale):
                    c.err(where, f"within: no step starts the clock '{within}'")
            elif not (isinstance(within, int) and within > 0):
                check_days(c, where + ".within", within)
            check_then(c, where + ".late", step["late"], tale, edges)
        for give in step.get("give", []):
            check_written(c, where + ".give", give, tale)
        if do not in ("talk", "foe", "name") and "then" not in step:
            c.err(where, "missing 'then'")
        if "then" in step:
            check_then(c, where, step["then"], tale, edges)
        if "failed" in step:
            check_then(c, where + ".failed", step["failed"], tale, edges)
        if "let_go" in step:
            check_then(c, where + ".let_go", step["let_go"], tale, edges)
        if "goal" not in step and do not in ("talk", "wait", "name") and not step.get("hidden"):
            c.err(where, "missing 'goal' (the tracker line)")
        for give in step.get("give", []):
            check_item(c, where + ".give", give.get("item"))
        if do == "talk":
            check_role(c, where, step["who"], tale)
            check_text(c, where + ".say", step["say"], tale)
            replies = step.get("replies", [])
            if not replies and "then" not in step:
                c.err(where, "a talk with no replies needs 'then'")
            if len(replies) > 5:
                c.err(where, "at most five replies")
            open_reply = False
            for i, r in enumerate(replies):
                rw = f"{where}.replies[{i}]"
                if "say" not in r or "then" not in r:
                    c.err(rw, "a reply needs 'say' and 'then'")
                    continue
                unknown = set(r) - {"say", "then", "if", "needs_item", "needs_coins", "mark", "journal", "against", "returns", "try", "for", "irks", "lie"}
                if unknown:
                    c.err(rw, f"unknown fields {sorted(unknown)}")
                check_text(c, rw + ".say", r["say"], tale, extra=("price",) if r.get("try", {}).get("how") in ("bribe", "dice") else ())
                if "try" in r:
                    check_try(c, rw + ".try", r["try"], tale, edges)
                for field in ("for", "irks"):
                    if field in r and (not isinstance(r[field], list) or not r[field] or any(v not in BELIEFS for v in r[field])):
                        c.err(rw, f"{field} is a list of {sorted(BELIEFS)}")
                if "lie" in r:
                    check_lie(c, rw + ".lie", r["lie"], tale)
                if "journal" in r:
                    check_text(c, rw + ".journal", r["journal"], tale)
                check_then(c, rw, r["then"], tale, edges)
                if "against" in r:
                    check_against(c, rw + ".against", r["against"], tale)
                if "returns" in r:
                    check_returns(c, rw + ".returns", r["returns"], tale, None)
                if "if" in r:
                    check_condition(c, rw, r["if"], tale)
                if "needs_item" in r:
                    check_item(c, rw, r["needs_item"].get("item"))
                if "needs_coins" in r and not (isinstance(r["needs_coins"], int) and r["needs_coins"] > 0):
                    c.err(rw, "needs_coins must be a whole number of coins")
                if "if" not in r and "needs_item" not in r and "needs_coins" not in r and "try" not in r:
                    open_reply = True
            if replies and not open_reply:
                c.err(where, "at least one reply must always be open (no 'if', 'needs_item', 'needs_coins' or 'try')")
            for belief, line in step.get("voices", {}).items():
                if belief not in BELIEFS:
                    c.err(where + ".voices", f"'{belief}' is not one of {sorted(BELIEFS)}")
                check_text(c, f"{where}.voices.{belief}", line, tale)
        elif do == "name":
            check_role(c, where, step["who"], tale)
            if "say" in step:
                check_text(c, where + ".say", step["say"], tale)
            suspects = step["suspects"]
            if not isinstance(suspects, list) or not 2 <= len(suspects) <= 4:
                c.err(where, "two to four suspects")
                suspects = []
            for i, sus in enumerate(suspects):
                sw = f"{where}.suspects[{i}]"
                if not {"who", "say", "then"} <= set(sus):
                    c.err(sw, "a suspect needs 'who', 'say' and 'then'")
                    continue
                unknown = set(sus) - {"who", "say", "then", "if", "proof", "need", "doubted", "mark", "journal"}
                if unknown:
                    c.err(sw, f"unknown fields {sorted(unknown)}")
                check_role(c, sw, sus["who"], tale)
                if sus["who"] != "giver" and "at" not in tale.get("cast", {}).get(sus["who"], {}):
                    c.err(sw, f"cast.{sus['who']} needs 'at': nobody can name someone they can't have met")
                check_text(c, sw + ".say", sus["say"], tale)
                if "journal" in sus:
                    check_text(c, sw + ".journal", sus["journal"], tale)
                check_then(c, sw, sus["then"], tale, edges)
                if "doubted" in sus:
                    check_then(c, sw + ".doubted", sus["doubted"], tale, edges)
                    if not sus.get("proof"):
                        c.err(sw, "'doubted' needs 'proof' (what must hold for them to be believed)")
                for proof in sus.get("proof", []):
                    check_condition(c, sw + ".proof", proof, tale)
                if "if" in sus:
                    check_condition(c, sw, sus["if"], tale)
            none = step.get("none", {})
            if "say" in none:
                check_text(c, where + ".none.say", none["say"], tale)
            if "then" in none:
                check_then(c, where + ".none", none["then"], tale, edges)
        elif do == "vigil":
            check_place(c, where, step["at"], tale)
            check_switch_keys(c, where + ".lines", step["lines"], tale)
            for lines in branches(step["lines"]):
                if not isinstance(lines, list) or not lines:
                    c.err(where, "a watch needs a list of lines")
                    continue
                for i, line in enumerate(lines):
                    lw = f"{where}.lines[{i}]"
                    unknown = set(line) - {"who", "say", "sound", "if", "mark"}
                    if unknown:
                        c.err(lw, f"unknown fields {sorted(unknown)}")
                    if "say" not in line and "sound" not in line:
                        c.err(lw, "a line needs 'say' or 'sound'")
                    if "say" in line:
                        check_text(c, lw + ".say", line["say"], tale)
                    if "who" in line:
                        check_role(c, lw, line["who"], tale)
                    if "sound" in line and not ITEM.match(line["sound"]):
                        c.err(lw, f"'{line['sound']}' is not a sound id")
                    if "if" in line:
                        check_condition(c, lw, line["if"], tale)
            if "left" in step:
                check_then(c, where + ".left", step["left"], tale, edges)
        elif do == "go":
            check_place(c, where, step["to"], tale)
        elif do == "search":
            check_place(c, where, step["at"], tale)
            clues = step["clues"]
            if not isinstance(clues, list) or not clues:
                c.err(where, "needs a list of clues")
            always = 0
            for i, clue in enumerate(clues):
                cw = f"{where}.clues[{i}]"
                if not {"name", "item", "lore"} <= set(clue):
                    c.err(cw, "a clue needs 'name', 'item' and 'lore'")
                    continue
                check_item(c, cw, clue["item"])
                check_text(c, cw + ".name", clue["name"], tale)
                check_text(c, cw + ".lore", clue["lore"], tale)
                if "journal" in clue:
                    check_text(c, cw + ".journal", clue["journal"], tale)
                unknown = set(clue) - {"name", "item", "lore", "journal", "if", "count", "mark", "tongue", "told"}
                if unknown:
                    c.err(cw, f"unknown fields {sorted(unknown)}")
                check_written(c, cw, clue, tale)
                if "if" in clue:
                    check_condition(c, cw, clue["if"], tale)
                else:
                    always += 1
            if isinstance(step.get("need"), int) and step["need"] > max(always, 1) and not tale.get("roll"):
                c.err(where, "'need' is more than the clues that are always there")
        elif do == "foe":
            check_role(c, where, step["who"], tale)
            if "spared_returns" in step:
                check_returns(c, where + ".spared_returns", step["spared_returns"], tale, step["who"] if isinstance(step["who"], str) else None)
            for w in branches(step["who"]):
                body = tale.get("cast", {}).get(w, {}).get("body", "villager")
                if body == "villager":
                    c.err(where, f"'{w}' is a villager; a foe must be a fighter or a creature")
                if "spared" in step and body != "fighter":
                    c.err(where, f"only a fighter can be spared; '{w}' is {body}")
                if "at" not in tale.get("cast", {}).get(w, {}):
                    c.err(where, f"cast.{w} needs 'at' (where they wait)")
            check_then(c, where + ".slain", step["slain"], tale, edges)
            if "spared" in step:
                check_then(c, where + ".spared", step["spared"], tale, edges)
                if "plea" not in step:
                    c.err(where, "a foe who can be spared needs a 'plea'")
            for field in ("taunt", "plea", "mercy", "thanks"):
                if field in step:
                    check_text(c, f"{where}.{field}", step[field], tale)
            for i, weak in enumerate(step.get("weak_to", [])):
                ww = f"{where}.weak_to[{i}]"
                unknown = set(weak) - {"know", "item", "share", "journal", "bestiary"}
                if unknown:
                    c.err(ww, f"unknown fields {sorted(unknown)}")
                if "know" not in weak and "item" not in weak:
                    c.err(ww, "needs 'know' (what was learned) or 'item' (what's carried)")
                if "know" in weak:
                    check_condition(c, ww, weak["know"], tale)
                if "item" in weak:
                    check_item(c, ww, weak["item"])
                if not 0.3 <= weak.get("share", 0.7) < 1:
                    c.err(ww, "share is what's left of the foe: 0.3 to under 1")
                if "journal" not in weak:
                    c.err(ww, "needs 'journal' (the journal says why it was easier)")
                for field in ("journal", "bestiary"):
                    if field in weak:
                        check_text(c, f"{ww}.{field}", weak[field], tale)
        elif do == "await":
            # (code carries it on: KeepingCells sets a mark and calls Tales.advance)
            check_role(c, where, step["who"], tale)
        elif do == "bring":
            check_role(c, where, step["who"], tale)
            check_item(c, where, step["item"])
        elif do == "wait":
            has_wait = True
            check_days(c, where + ".days", step["days"])
            wake = step.get("wake", "giver")
            if wake not in ("giver", "letter", "none"):
                c.err(where, "'wake' must be giver, letter or none")
            if wake == "letter":
                if "letter" not in step:
                    c.err(where, "wake by letter needs 'letter'")
                else:
                    check_letter(c, where + ".letter", step["letter"], tale)
        elif do == "escort":
            check_role(c, where, step["who"], tale)
            check_place(c, where, step["to"], tale)
        elif do in ("rescue", "capture"):
            # (set out by the job at its camp: bound, or armed; they're the tale's own once a captive is brought home)
            check_role(c, where, step["who"], tale)
            check_place(c, where, step["at"], tale)
            for w in branches(step["who"]):
                role = tale.get("cast", {}).get(w, {})
                body = role.get("body", "villager")
                if do == "rescue" and body != "villager":
                    c.err(where, f"'{w}' is {body}; a captive is a villager")
                if do == "capture" and body != "fighter":
                    c.err(where, f"'{w}' is {body}; a criminal taken alive is a fighter")
                if "at" in role and not role.get("late"):
                    c.err(where, f"cast.{w} would be set out at its 'at' before the job: make it 'late'")
            if "kin" in step and step["kin"] not in KIN:
                c.err(where, f"kin must be one of {sorted(KIN)}")
            if "crime" in step and step["crime"] not in CRIMES:
                c.err(where, f"crime must be one of {sorted(CRIMES)}")
        elif do in ("defend", "pack", "build", "blast", "stash", "lamps"):
            check_place(c, where, step["at"], tale)
            if do == "stash":
                check_item(c, where, step["item"])
            if do == "build":
                check_item(c, where, step["blocks"])
        for role in branches(step.get("who", [])) if do in ("talk", "bring", "escort", "name") else []:
            if role != "giver" and "at" not in tale.get("cast", {}).get(role, {}):
                c.err(where, f"cast.{role} needs 'at' (where they wait)")

    if tale["first"] not in steps:
        c.err("first", f"no such step '{tale['first']}'")
    else:
        seen = set()
        stack = [tale["first"]]
        while stack:
            s = stack.pop()
            if s in seen:
                continue
            seen.add(s)
            stack.extend(graph.get(s, []))
        for sid in steps:
            if sid not in seen:
                c.err(f"steps.{sid}", "can never be reached")
        first = steps[tale["first"]]
        if by in ("giver", "companion", "notice") and first.get("do") != "talk":
            c.err("first", "a tale an asker gives must open with a talk with 'giver'")
        if by in ("giver", "companion", "notice") and first.get("do") == "talk" and first.get("who") != "giver":
            c.err("first", "the opening talk must be with 'giver'")
        if by == "rumour" and not (first.get("do") == "go" and first.get("hidden")):
            c.err("first", "a rumour's tale must open with a hidden 'go'")
    if not has_wait and not any("within" in step or "clock" in step for step in steps.values()):
        c.err("tale", "a tale should take days: add at least one 'wait', or a day it must be done by")

    used = set()

    def ends(then):
        for t in branches(then) if then is not None else []:
            if isinstance(t, str) and t.startswith("end:"):
                used.add(t[4:])

    for sid, step in steps.items():
        for field in ("then", "slain", "spared", "failed", "let_go", "late", "left"):
            ends(step.get(field))
        for r in step.get("replies", []):
            ends(r.get("then"))
            ends(r.get("try", {}).get("fail") if isinstance(r.get("try"), dict) else None)
        for sus in step.get("suspects", []) if isinstance(step.get("suspects"), list) else []:
            ends(sus.get("then"))
            ends(sus.get("doubted"))
        ends(step.get("none", {}).get("then") if isinstance(step.get("none"), dict) else None)
    for eid, ending in tale["endings"].items():
        where = f"endings.{eid}"
        if eid not in used:
            c.err(where, "no step ends here")
        unknown = set(ending) - ENDING_FIELDS
        if unknown:
            c.err(where, f"unknown fields {sorted(unknown)}")
        if "text" not in ending:
            c.err(where, "missing 'text'")
        else:
            check_text(c, where + ".text", ending["text"], tale)
        unknown = set(ending.get("reward", {})) - REWARD_FIELDS
        if unknown:
            c.err(where, f"unknown reward fields {sorted(unknown)}")
        for spec in ending.get("reward", {}).get("items", []):
            check_item(c, where + ".reward", spec.split("*")[0])
        if "town" in ending:
            check_text(c, where + ".town", ending["town"], tale)
        if ending.get("returns"):
            check_returns(c, where + ".returns", ending["returns"], tale, None)
        if "against" in ending:
            check_against(c, where + ".against", ending["against"], tale)
        if "joins" in ending:
            check_role(c, where + ".joins", ending["joins"], tale)
        for i, later in enumerate(ending.get("later", [])):
            check_later(c, f"{where}.later[{i}]", later, tale)
    if len(tale["endings"]) < 2:
        c.err("endings", "a tale needs more than one way to end")
    return c.errors


def check_scene(path):
    c = Check(path)
    try:
        scene = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as e:
        c.err("file", f"not valid JSON: {e}")
        return c.errors
    unknown = set(scene) - {"jobs", "when", "lines"}
    if unknown:
        c.err("scene", f"unknown fields {sorted(unknown)}")
    jobs = scene.get("jobs", ["*", "*"])
    if not (isinstance(jobs, list) and len(jobs) == 2):
        c.err("jobs", "must be the two speakers' trades")
    if scene.get("when", "any") not in ("day", "night", "rain", "any"):
        c.err("when", "must be day, night, rain or any")
    lines = scene.get("lines")
    if not (isinstance(lines, list) and 2 <= len(lines) <= 6):
        c.err("lines", "two to six lines")
        return c.errors
    for i, line in enumerate(lines):
        if not (isinstance(line, list) and len(line) == 2 and line[0] in ("a", "b") and isinstance(line[1], str) and line[1].strip()):
            c.err(f"lines[{i}]", "each line is [\"a\" or \"b\", \"words\"]")
        elif len(line[1]) > 140:
            c.err(f"lines[{i}]", "a line is at most 140 characters (it's a speech bubble)")
        elif any(bad in line[1] for bad in BANNED):
            c.err(f"lines[{i}]", "no long dashes in text")
    return c.errors


def main(argv):
    files = [Path(a) for a in argv] if argv else sorted((DATA / "tales").glob("*.json")) + sorted((DATA / "scenes").glob("*.json"))
    errors = []
    for f in files:
        errors.extend(check_scene(f) if f.parent.name == "scenes" else check_tale(f))
    for e in errors:
        print(e)
    print(f"tale_check: {len(files)} files, {len(errors)} problems")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
