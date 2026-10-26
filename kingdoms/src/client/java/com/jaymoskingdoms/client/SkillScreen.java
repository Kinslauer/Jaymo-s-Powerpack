package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Renown;
import com.jaymoskingdoms.Skills;
import com.jaymoskingdoms.Stats;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.52) The skill tree ({@link Skills}), on the {@code K} key. (0.7.40) Restyled like the H screen: a header with the
 * pillar's level, its experience bar and the points to spend; a tab per pillar; its perks as a tree of flat tiles (press
 * one to spend points on it); and a box at the bottom that shows the hovered perk in full, or else what the pillar
 * levels on, your class and your alignment ({@link Renown}). The server checks every spend and syncs the result back.
 * (0.7.57) A fourth tab, Attributes ({@link Stats}): your character level (every pillar's level added up), the
 * attribute points it earns, and the six attributes as rows of ten pips (yours, your heritage's, your class's).
 * (0.7.218) A fifth tab, Jobs ({@link Careers}): every job with your rank in it, where you hold it and its orders, and
 * (hovered) what each rank's perk does.
 */
final class SkillScreen extends Screen {
	static final int PANEL_W = 340, PANEL_H = 230, HEADER = 34, TAB_W = 78, TAB_H = 16, STAT_ROW = 16, PIP_W = 9, PERK_W = 98, PERK_H = 18, TIER_H = 26,
		COLUMN_GAP = 106, TREE_Y = 56, DETAIL_Y = 156, DETAIL_LINES = 5, FOOTER = 22, XP_BAR_W = 120;
	static final int COLOR_LINE_OFF = 0xFF3A3A44;

	private static KeyMapping key;
	/** The tab last looked at (Stealth first: it's the one built). */
	private static Skills.Pillar pillar = Skills.Pillar.STEALTH;
	/** (0.6.5) The page of the pillar's tree shown: 0, or 1 for the tiers past the first page (Strength's Combat Arts). */
	private static int page;
	/** (0.7.57) Whether the Attributes tab is the one shown. */
	private static boolean stats;
	/** (0.7.218) Whether the Jobs tab is the one shown. */
	private static boolean jobs;
	/** (0.7.218) The Jobs tab's rows. */
	static final int JOB_ROW = 10, JOB_ACCENT = 0xFFC8A060;

	SkillScreen() {
		super(Component.translatable("gui.jaymoskingdoms.skills.title"));
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("skills"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.skills", InputConstants.KEY_K, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new SkillScreen());
				}
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Skills.Progress progress() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null ? Skills.Progress.EMPTY : Skills.of(minecraft.player);
	}

	static int accent(Skills.Pillar pillar) {
		return switch (pillar) {
			case SURVIVAL -> Ui.SURVIVAL;
			case STEALTH -> Ui.STEALTH;
			case STRENGTH -> Ui.STRENGTH;
		};
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	/** Where perk {@code perk}'s tile sits. */
	private int perkX(Skills.Perk perk) {
		return this.width / 2 + (perk.column - 1) * COLUMN_GAP - PERK_W / 2;
	}

	private int perkY(Skills.Perk perk) {
		return top() + TREE_Y + (perk.tier % Skills.Perk.PAGE_TIERS) * TIER_H;
	}

	private static boolean onPage(Skills.Perk perk) {
		return perk.pillar == pillar && perk.tier / Skills.Perk.PAGE_TIERS == page;
	}

	/** How many pages {@code pillar}'s tree runs to (1 if it fits on the first). */
	private static int pages() {
		int deepest = 0;
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (perk.pillar == pillar) {
				deepest = Math.max(deepest, perk.tier / Skills.Perk.PAGE_TIERS);
			}
		}
		return deepest + 1;
	}

	@Override
	protected void init() {
		int tabs = Skills.Pillar.values().length + 2;
		int tabW = Math.min(TAB_W, (panelW() - 12 - 4 * (tabs - 1)) / tabs);
		int x = left() + (panelW() - tabW * tabs - 4 * (tabs - 1)) / 2, y = top() + HEADER + 2;
		for (Skills.Pillar p : Skills.Pillar.values()) {
			Button tab = Button.builder(Component.translatable("skill.jaymoskingdoms." + p.key()), b -> {
				pillar = p;
				page = 0;
				stats = false;
				jobs = false;
				rebuildWidgets();
			}).bounds(x, y, tabW, TAB_H).build();
			tab.active = stats || jobs || p != pillar;
			addRenderableWidget(tab);
			x += tabW + 4;
		}
		Button statsTab = Button.builder(Component.translatable("gui.jaymoskingdoms.stats.tab"), b -> {
			stats = true;
			jobs = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		statsTab.active = !stats;
		addRenderableWidget(statsTab);
		x += tabW + 4;
		Button jobsTab = Button.builder(Component.translatable("gui.jaymoskingdoms.jobs.tab"), b -> {
			jobs = true;
			stats = false;
			rebuildWidgets();
		}).bounds(x, y, tabW, TAB_H).build();
		jobsTab.active = !jobs;
		addRenderableWidget(jobsTab);
		int fy = top() + PANEL_H - FOOTER + 3;
		int right = left() + panelW() - 6;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 60, fy, 60, 16).build());
		// (0.7.82) the world tiers, beside Done
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.tier.button"), b -> this.minecraft.gui.setScreen(new TierScreen(this)))
			.bounds(right - 60 - 4 - 80, fy, 80, 16).build());
		int pages = pages();
		if (stats || jobs || pages < 2) {
			page = 0;
		} else {
			// (the button names the page it goes to; the last page goes round to the first)
			int next = (page + 1) % pages;
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.skills.page." + pillar.key() + "." + next), b -> {
				page = next;
				rebuildWidgets();
			}).bounds(left() + 6, fy, 92, 16).build());
		}
	}

	private Skills.@Nullable Perk perkAt(double mouseX, double mouseY) {
		if (stats || jobs || !pillar.open) {
			return null;
		}
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (onPage(perk) && Ui.over((int)mouseX, (int)mouseY, perkX(perk), perkY(perk), PERK_W, PERK_H)) {
				return perk;
			}
		}
		return null;
	}

	// ---- (0.7.57) the Attributes tab --------------------------------------------------------------------------------

	/** (0.7.212) The rows shown: all but Arcana, which shows once you're attuned; a little closer together with it. */
	private List<Stats.Stat> shownStats() {
		List<Stats.Stat> out = new ArrayList<>();
		for (Stats.Stat stat : Stats.Stat.values()) {
			if (this.minecraft.player == null || Stats.shown(this.minecraft.player, stat)) {
				out.add(stat);
			}
		}
		return out;
	}

	private int statRow() {
		return shownStats().size() > 6 ? STAT_ROW - 2 : STAT_ROW;
	}

	private int statY(Stats.Stat stat) {
		return top() + TREE_Y + Math.max(0, shownStats().indexOf(stat)) * statRow();
	}

	private Stats.@Nullable Stat statAt(double mouseX, double mouseY) {
		if (!stats) {
			return null;
		}
		for (Stats.Stat stat : shownStats()) {
			if (Ui.over((int)mouseX, (int)mouseY, left() + 8, statY(stat), panelW() - 16, statRow() - 2)) {
				return stat;
			}
		}
		return null;
	}

	/** The header, the six rows of pips and the detail box. */
	private void attributes(GuiGraphicsExtractor graphics, Skills.Progress shown, int mouseX, int mouseY) {
		int x = left(), y = top(), w = panelW();
		int accent = Ui.GOLD;
		Ui.panel(graphics, x, y, w, PANEL_H, accent);
		var player = this.minecraft.player;
		int level = Stats.characterLevel(shown), points = player == null ? 0 : Stats.points(player, shown);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.stats.level", level), x + 9, y + 8, w / 2 - 12, accent);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.stats.points", points), x + 9, y + 19, w / 2 - 12, points > 0 ? Ui.GOOD : Ui.DIM);
		Heritage.Kind kind = player == null ? null : Heritage.of(player);
		Classes.PlayerClass cls = player == null ? null : Classes.classOf(player);
		Ui.lineRight(graphics, this.font, kind == null ? Component.translatable("gui.jaymoskingdoms.stats.heritage.none")
			: Component.translatable("gui.jaymoskingdoms.stats.heritage", Component.translatable("heritage.jaymoskingdoms." + kind.key())), x + w - 9, y + 8, w / 2 - 12,
			HeritageScreen.ACCENT);
		Ui.lineRight(graphics, this.font, cls == null ? Component.translatable("gui.jaymoskingdoms.skills.class.none")
			: Component.translatable("gui.jaymoskingdoms.skills.class", Component.translatable("class.jaymoskingdoms." + cls.key())), x + w - 9, y + 19, w / 2 - 12, Ui.GOLD);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);

		Stats.Stat hovered = statAt(mouseX, mouseY);
		int pipsX = x + 84;
		for (Stats.Stat stat : shownStats()) {
			int ry = statY(stat);
			Ui.row(graphics, x + 8, ry, x + w - 8, ry + statRow() - 2, stat.ordinal(), stat == hovered);
			int spent = Stats.spent(shown, stat), fromHeritage = player == null ? 0 : Stats.fromHeritage(player, stat);
			int fromClass = player == null ? 0 : Stats.fromClass(player, stat);
			int fromStory = player == null ? 0 : Stats.fromStory(player, stat);
			int value = Math.min(Stats.MAX, spent + fromHeritage + fromClass + fromStory);
			String blocked = player == null ? "" : Stats.blocked(player, shown, stat);
			Ui.line(graphics, this.font, Component.translatable("stat.jaymoskingdoms." + stat.key()), x + 13, ry + 3, pipsX - x - 18, value > 0 ? Ui.TEXT : Ui.DIM);
			// ten pips: heritage's (teal), then class's (gold), then (0.7.216) your story's (parchment), then yours (white); the rest empty
			for (int i = 0; i < Stats.MAX; i++) {
				int color = i < fromHeritage ? HeritageScreen.ACCENT : i < fromHeritage + fromClass ? Ui.GOLD
					: i < fromHeritage + fromClass + fromStory ? LifeStoryScreen.ACCENT : i < value ? Ui.TEXT : Ui.BAR_BACK;
				int px = pipsX + i * (PIP_W + 2);
				graphics.fill(px, ry + 4, px + PIP_W, ry + 10, color);
			}
			int nx = pipsX + Stats.MAX * (PIP_W + 2) + 6;
			graphics.text(this.font, value + "/" + Stats.MAX, nx, ry + 3, value >= Stats.MAX ? accent : Ui.TEXT);
			if (blocked == null) {
				graphics.text(this.font, "+", x + w - 18, ry + 3, Ui.GOOD);
			}
		}

		Component name;
		List<Component> text = new ArrayList<>();
		if (hovered != null && player != null) {
			int value = Stats.value(player, shown, hovered);
			name = Component.translatable("stat.jaymoskingdoms." + hovered.key()).append("  " + value + "/" + Stats.MAX);
			text.add(Component.translatable("stat.jaymoskingdoms." + hovered.key() + ".desc"));
			text.add(Stats.now(hovered, value).copy().withColor(accent));
			if (hovered == Stats.Stat.ARCANA) {
				// (0.7.212) the mana itself: how much now, the most, how fast it comes back
				text.add(Component.translatable("gui.jaymoskingdoms.stats.mana", Math.round(com.jaymoskingdoms.Mana.current(player)),
					Math.round(com.jaymoskingdoms.Mana.max(player)), String.format(java.util.Locale.ROOT, "%.1f", com.jaymoskingdoms.Mana.regen(player)))
					.withColor(0x6FA8FF));
			}
			text.add(Component.translatable("gui.jaymoskingdoms.stats.from", Stats.spent(shown, hovered), Stats.fromHeritage(player, hovered),
				Stats.fromClass(player, hovered), Stats.fromStory(player, hovered)).withColor(Ui.DIM));
			String blocked = Stats.blocked(player, shown, hovered);
			text.add(blocked == null ? Component.translatable("gui.jaymoskingdoms.stats.raise").withColor(Ui.GOOD)
				: Component.translatable(blocked).withColor(blocked.endsWith(".max") ? accent : Ui.BAD));
		} else {
			name = Component.translatable("gui.jaymoskingdoms.stats.tab");
			text.add(Component.translatable("gui.jaymoskingdoms.stats.hint", Stats.LEVELS_PER_POINT).withColor(Ui.DIM));
			text.add(Component.translatable("gui.jaymoskingdoms.stats.legend").withColor(Ui.HINT));
		}
		Ui.detail(graphics, this.font, x, y + DETAIL_Y, w, DETAIL_LINES, name, accent, text);
	}

	// ---- (0.7.218) the Jobs tab --------------------------------------------------------------------------------------

	private int jobY(int index) {
		return top() + TREE_Y - 2 + index * JOB_ROW;
	}

	private Careers.@Nullable Job jobAt(double mouseX, double mouseY) {
		if (!jobs) {
			return null;
		}
		Careers.Job[] all = Careers.Job.values();
		for (int i = 0; i < all.length; i++) {
			if (Ui.over((int)mouseX, (int)mouseY, left() + 8, jobY(i), panelW() - 16, JOB_ROW)) {
				return all[i];
			}
		}
		return null;
	}

	/** The header (jobs held, your stall), a row per job (rank, where, its experience, orders open) and the detail box. */
	private void jobs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left(), y = top(), w = panelW();
		int accent = JOB_ACCENT;
		Ui.panel(graphics, x, y, w, PANEL_H, accent);
		Careers.Career c = this.minecraft.player == null ? Careers.Career.EMPTY : Careers.of(this.minecraft.player);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.title"), x + 9, y + 8, w / 2 - 12, accent);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.held", c.posts().size()), x + 9, y + 19, w / 2 - 12,
			c.posts().isEmpty() ? Ui.DIM : Ui.TEXT);
		Ui.lineRight(graphics, this.font, c.stall().isEmpty() ? Component.translatable("gui.jaymoskingdoms.jobs.no_stall")
			: Component.translatable("gui.jaymoskingdoms.jobs.stall", c.stall().get().getX(), c.stall().get().getZ()), x + w - 9, y + 8, w / 2 - 12,
			c.stall().isEmpty() ? Ui.DIM : Ui.GOOD);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.hint"), x + w - 9, y + 19, w / 2 - 12, Ui.HINT);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);

		Careers.Job hovered = jobAt(mouseX, mouseY);
		Careers.Job[] all = Careers.Job.values();
		int barW = 50, townX = x + 130, barX = x + w - 8 - 40 - barW;
		for (int i = 0; i < all.length; i++) {
			Careers.Job job = all[i];
			int ry = jobY(i), xp = c.xpIn(job.key), rank = c.rank(job.key);
			Careers.Post post = c.post(job.key);
			Ui.row(graphics, x + 8, ry, x + w - 8, ry + JOB_ROW - 1, i, job == hovered);
			if (post != null) {
				graphics.fill(x + 8, ry, x + 10, ry + JOB_ROW - 1, accent);
			}
			Component name = xp > 0 || post != null ? Careers.title(job, rank) : job.title();
			Ui.line(graphics, this.font, name, x + 13, ry + 1, townX - x - 18, post != null ? Ui.TEXT : xp > 0 ? Ui.DIM : Ui.HINT);
			Ui.line(graphics, this.font, post == null ? Component.translatable("gui.jaymoskingdoms.jobs.not_held") : Component.literal(post.town()), townX, ry + 1,
				barX - townX - 6, post == null ? Ui.HINT : Ui.TEXT);
			int next = Careers.nextRankXp(rank), from = rank == Careers.MASTER ? Careers.MASTER_XP : rank == Careers.JOURNEYMAN ? Careers.JOURNEYMAN_XP : 0;
			float share = next < 0 ? 1.0F : (xp - from) / (float)Math.max(1, next - from);
			Ui.bar(graphics, barX, ry + 4, barW, 2, share, rank == Careers.MASTER ? Ui.GOLD : accent);
			if (post != null) {
				Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.jobs.orders", post.orders().size()), x + w - 12, ry + 1, 38,
					post.orders().isEmpty() ? Ui.DIM : Ui.GOOD);
			}
		}

		Component title;
		List<Component> text = new ArrayList<>();
		if (hovered != null) {
			int rank = c.rank(hovered.key), xp = c.xpIn(hovered.key), next = Careers.nextRankXp(rank);
			title = Careers.title(hovered, rank).copy().append("  " + (next < 0 ? xp : xp + "/" + next));
			text.add(Component.translatable("job.jaymoskingdoms." + hovered.key + ".desc"));
			for (int r = Careers.JOURNEYMAN; r <= Careers.MASTER; r++) {
				text.add(Component.translatable("gui.jaymoskingdoms.jobs.perk", Component.translatable("job.jaymoskingdoms.rank." + r),
					Component.translatable("job.jaymoskingdoms." + hovered.key + ".perk." + r)).withColor(rank >= r ? Ui.GOOD : Ui.DIM));
			}
			Careers.Post post = c.post(hovered.key);
			if (post != null) {
				for (Careers.Order o : post.orders()) {
					text.add(Component.empty().append(Careers.describe(null, o)).append("  " + o.progress() + "/" + o.count()).withColor(Ui.TEXT));
				}
			}
		} else {
			title = Component.translatable("gui.jaymoskingdoms.jobs.tab");
			text.add(Component.translatable("gui.jaymoskingdoms.jobs.about").withColor(Ui.DIM));
			text.add(Component.translatable("gui.jaymoskingdoms.jobs.ranks", Careers.JOURNEYMAN_XP, Careers.MASTER_XP).withColor(Ui.HINT));
		}
		Ui.detail(graphics, this.font, x, y + DETAIL_Y, w, DETAIL_LINES, title, accent, text);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Stats.Stat stat = statAt(event.x(), event.y());
		if (stat != null) {
			if (this.minecraft.player != null && Stats.blocked(this.minecraft.player, progress(), stat) == null) {
				ClientPlayNetworking.send(new Stats.Raise(stat.ordinal()));
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		Skills.Perk perk = perkAt(event.x(), event.y());
		if (perk != null) {
			if (progress().blocked(perk) == null) {
				ClientPlayNetworking.send(new Skills.Spend(perk.ordinal()));
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Skills.Progress shown = progress();
		if (jobs) {
			jobs(graphics, mouseX, mouseY);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		if (stats) {
			attributes(graphics, shown, mouseX, mouseY);
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		int x = left(), y = top(), w = panelW();
		int accent = accent(pillar);
		Ui.panel(graphics, x, y, w, PANEL_H, accent);
		header(graphics, shown, accent);

		Skills.Perk hovered = perkAt(mouseX, mouseY);
		if (pillar.open) {
			tree(graphics, shown, accent, hovered);
		}

		// the detail box: the hovered perk in full, or what the pillar is about, your class and your alignment
		Component name = Component.empty();
		int nameColor = accent;
		List<Component> text = new ArrayList<>();
		if (hovered != null) {
			int rank = shown.rank(hovered);
			name = Component.translatable("skill.jaymoskingdoms.perk." + hovered.key()).append("  " + rank + "/" + hovered.maxRank);
			nameColor = rank > 0 ? accent : Ui.TEXT;
			text.add(Component.translatable("skill.jaymoskingdoms.perk." + hovered.key() + ".desc"));
			Component cost = Component.translatable("gui.jaymoskingdoms.skills.cost", hovered.cost);
			if (hovered.minLevel > 0) {
				cost = Component.empty().append(cost).append("  ·  ").append(Component.translatable("gui.jaymoskingdoms.skills.needs_level", hovered.minLevel));
			}
			text.add(cost.copy().withColor(Ui.DIM));
			String blocked = shown.blocked(hovered);
			if (blocked == null) {
				text.add(Component.translatable("gui.jaymoskingdoms.skills.learn").withColor(Ui.GOOD));
			} else {
				text.add(Component.translatable(blocked).withColor(blocked.endsWith(".max") ? accent : Ui.BAD));
			}
		} else if (!pillar.open) {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.plan." + pillar.key()));
		} else {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.hint." + pillar.key() + (page > 0 ? "." + page : "")).withColor(Ui.DIM));
			Classes.PlayerClass cls = this.minecraft.player == null ? null : Classes.classOf(this.minecraft.player);
			text.add(cls == null ? Component.translatable("gui.jaymoskingdoms.skills.class.none").withColor(Ui.DIM)
				: Component.translatable("gui.jaymoskingdoms.skills.class", Component.translatable("class.jaymoskingdoms." + cls.key())).withColor(Ui.GOLD));
			// (your alignment: the pillar you've made your own, as others see it over your head)
			Skills.Pillar aligned = Renown.alignment(shown);
			text.add(aligned == null ? Component.translatable("gui.jaymoskingdoms.skills.alignment.none", Renown.ALIGN_LEVEL).withColor(Ui.DIM)
				: Component.translatable("gui.jaymoskingdoms.skills.alignment", Component.translatable("title.jaymoskingdoms." + aligned.key()
					+ (shown.level(aligned) >= aligned.maxLevel ? ".master" : ""))).withColor(accent(aligned)));
			text.add(Component.translatable("gui.jaymoskingdoms.skills.hover").withColor(Ui.HINT));
		}
		Ui.detail(graphics, this.font, x, y + DETAIL_Y, w, DETAIL_LINES, name, nameColor, text);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The pillar (and page) and its level on the left; the points to spend, and the experience bar, on the right. */
	private void header(GuiGraphicsExtractor graphics, Skills.Progress shown, int accent) {
		int x = left(), y = top(), w = panelW();
		Component name = Component.translatable("skill.jaymoskingdoms." + pillar.key());
		if (page > 0) {
			name = Component.translatable("gui.jaymoskingdoms.skills.page." + pillar.key() + "." + page + ".title");
		}
		int textW = w - 20 - XP_BAR_W - 12;
		if (!pillar.open) {
			Ui.line(graphics, this.font, name, x + 9, y + 8, textW, accent);
			Ui.line(graphics, this.font, this.title, x + 9, y + 19, textW, Ui.DIM);
			Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
			return;
		}
		int level = shown.level(pillar), xp = shown.xp(pillar);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.level", name, level), x + 9, y + 8, textW, accent);
		int points = shown.points(pillar);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.points", points), x + 9, y + 19, textW,
			points > 0 ? Ui.GOOD : Ui.DIM);
		int bx = x + w - XP_BAR_W - 9;
		if (level >= pillar.maxLevel) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.mastered"), bx, y + 8, XP_BAR_W, accent);
			Ui.bar(graphics, bx, y + 20, XP_BAR_W, 3, 1.0F, accent);
		} else {
			int into = xp - Skills.total(level), need = Skills.toNext(level);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.xp", into, need), bx, y + 8, XP_BAR_W, Ui.TEXT);
			Ui.bar(graphics, bx, y + 20, XP_BAR_W, 3, into / (float)Math.max(1, need), accent);
		}
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
	}

	/** The lines from each perk to the ones it opens, then the perks' tiles, on this page. */
	private void tree(GuiGraphicsExtractor graphics, Skills.Progress shown, int accent, Skills.@Nullable Perk hovered) {
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (!onPage(perk)) {
				continue;
			}
			for (Skills.Perk need : perk.requires) {
				if (!onPage(need)) {
					continue;
				}
				int color = shown.rank(need) > 0 ? accent : COLOR_LINE_OFF;
				int fromX = perkX(need) + PERK_W / 2, fromY = perkY(need) + PERK_H, toX = perkX(perk) + PERK_W / 2, toY = perkY(perk);
				int midY = (fromY + toY) / 2;
				graphics.fill(fromX - 1, fromY, fromX + 1, midY + 1, color);
				graphics.fill(Math.min(fromX, toX) - 1, midY - 1, Math.max(fromX, toX) + 1, midY + 1, color);
				graphics.fill(toX - 1, midY, toX + 1, toY, color);
			}
		}
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (!onPage(perk)) {
				continue;
			}
			int px = perkX(perk), py = perkY(perk);
			int rank = shown.rank(perk);
			String blocked = shown.blocked(perk);
			boolean open = blocked == null, full = rank >= perk.maxRank;
			graphics.fill(px, py, px + PERK_W, py + PERK_H, perk == hovered ? Ui.ROW_HOVER : 0xE0181820);
			graphics.fill(px, py, px + PERK_W, py + PERK_H, rank > 0 ? Ui.ROW : 0x00000000);
			if (rank > 0) {
				// learnt: the pillar's colour down its left
				graphics.fill(px, py, px + 2, py + PERK_H, accent);
			}
			if (open) {
				// can be learnt now: a green line under it
				graphics.fill(px, py + PERK_H - 1, px + PERK_W, py + PERK_H, Ui.GOOD);
			}
			String ranks = rank + "/" + perk.maxRank;
			int ranksW = this.font.width(ranks);
			int color = rank > 0 || open ? Ui.TEXT : Ui.DIM;
			Ui.line(graphics, this.font, Component.translatable("skill.jaymoskingdoms.perk." + perk.key()), px + 6, py + 5, PERK_W - ranksW - 14, color);
			graphics.text(this.font, ranks, px + PERK_W - ranksW - 5, py + 5, full ? accent : rank > 0 ? Ui.TEXT : Ui.HINT);
		}
	}
}
