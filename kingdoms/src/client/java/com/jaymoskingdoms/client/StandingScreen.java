package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Carousing;
import com.jaymoskingdoms.ChalkMarks;
import com.jaymoskingdoms.Lessons;
import com.jaymoskingdoms.Lifestyle;
import com.jaymoskingdoms.Sparks;
import com.jaymoskingdoms.StartPerks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

/**
 * Standing: the social side of a character in one place. Their start's lasting perk, the ideal, bond and flaw they chose
 * and the spark they hold (with a button to give it to a friend standing near), how they live and what it earns, the
 * course they are taking, and what the nights out left them (a debt, rivals, a betrothal). Everything shown is what the
 * server has synced to them; the only action is giving the spark ({@link Sparks.Give}).
 */
final class StandingScreen extends Screen {
	static final int W = 320, PAD = 10, LINE = 10, GAP = 6, GIVE_RANGE = 8;
	private final Screen parent;

	StandingScreen(Screen parent) {
		super(Component.translatable("standing.jaymoskingdoms.title"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<Player> friends() {
		List<Player> out = new ArrayList<>();
		if (this.minecraft.player != null && this.minecraft.level != null) {
			for (AbstractClientPlayer p : this.minecraft.level.players()) {
				if (p != this.minecraft.player && p.distanceTo(this.minecraft.player) <= GIVE_RANGE && !p.isSpectator()) {
					out.add(p);
				}
			}
		}
		return out;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return 18;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left() + W - 70 - PAD, this.height - 28, 70, 18).build());
		if (this.minecraft.player != null && Sparks.held(this.minecraft.player)) {
			int y = this.height - 28;
			int x = left() + PAD;
			for (Player friend : friends()) {
				if (x + 80 > left() + W - 90) {
					break;
				}
				addRenderableWidget(Button.builder(Component.translatable("standing.jaymoskingdoms.give", friend.getName()), b -> {
					ClientPlayNetworking.send(new Sparks.Give(friend.getUUID()));
					onClose();
				}).bounds(x, y, 80, 18).build());
				x += 84;
			}
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private List<Component> lines(Player me) {
		List<Component> out = new ArrayList<>();
		StartPerks.Perk perk = StartPerks.of(me);
		out.add(Component.translatable("standing.jaymoskingdoms.head.start"));
		if (perk == null) {
			out.add(Component.translatable("standing.jaymoskingdoms.nostart"));
		} else {
			out.add(Component.translatable("standing.jaymoskingdoms.circle." + perk.circle().name().toLowerCase(java.util.Locale.ROOT)));
			for (StartPerks.Boon b : perk.boons()) {
				out.add(Component.translatable("standing.jaymoskingdoms.boon." + b.name().toLowerCase(java.util.Locale.ROOT)));
			}
		}
		out.add(Component.translatable("standing.jaymoskingdoms.head.sparks"));
		String ideal = Sparks.pick(me, 0), bond = Sparks.pick(me, 1), flaw = Sparks.pick(me, 2);
		if (ideal == null) {
			out.add(Component.translatable("standing.jaymoskingdoms.nopicks"));
		} else {
			out.add(Component.translatable("standing.jaymoskingdoms.ideal", Component.translatable("spark.ideal." + ideal)));
			out.add(Component.translatable("standing.jaymoskingdoms.bond", Component.translatable("spark.bond." + bond)));
			out.add(Component.translatable("standing.jaymoskingdoms.flaw", Component.translatable("spark.flaw." + flaw)));
		}
		out.add(Component.translatable(Sparks.held(me) ? "standing.jaymoskingdoms.spark.held" : "standing.jaymoskingdoms.spark.none"));
		out.add(Component.translatable("standing.jaymoskingdoms.head.life"));
		Lifestyle.Living l = Lifestyle.of(me);
		out.add(Component.translatable("standing.jaymoskingdoms.life." + Lifestyle.tier(me).name().toLowerCase(java.util.Locale.ROOT), l.score()));
		long day = me.level().getGameTime() / 24000L;
		out.add(Component.translatable("standing.jaymoskingdoms.life.clothes." + (l.dressedTo() > day ? "good" : "poor")));
		out.add(Component.translatable("standing.jaymoskingdoms.life.bath." + (l.bathedTo() > day ? "good" : "poor")));
		Lessons.Course course = me.getAttached(Lessons.COURSE);
		if (course != null) {
			out.add(Component.translatable("standing.jaymoskingdoms.head.course"));
			out.add(Component.translatable("standing.jaymoskingdoms.course", course.name(), course.done(), course.needed()));
		}
		Carousing.Debt debt = me.getAttached(Carousing.DEBT);
		String betrothed = me.getAttachedOrElse(Carousing.BETROTHED, "");
		List<String> rivals = me.getAttachedOrElse(Carousing.RIVALS, List.of());
		if (debt != null || !betrothed.isEmpty() || !rivals.isEmpty()) {
			out.add(Component.translatable("standing.jaymoskingdoms.head.nights"));
			if (debt != null) {
				out.add(Component.translatable("standing.jaymoskingdoms.debt", debt.creditor(), Math.max(1, debt.owed() / 1000L)));
			}
			if (!betrothed.isEmpty()) {
				out.add(Component.translatable("standing.jaymoskingdoms.betrothed", betrothed));
			}
			for (String r : rivals) {
				out.add(Component.translatable("standing.jaymoskingdoms.rival", r));
			}
		}
		if (ChalkMarks.reads(me)) {
			out.add(Component.translatable("standing.jaymoskingdoms.head.chalk"));
			for (String n : ChalkMarks.NAMES.subList(0, ChalkMarks.SCRIBBLE)) {
				out.add(Component.translatable("chalk.jaymoskingdoms.meaning." + n));
			}
		}
		return out;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		List<Component> lines = lines(this.minecraft.player);
		int h = this.height - 36 - y;
		Ui.panel(g, x, y, W, h, Ui.GOLD);
		Ui.line(g, this.font, this.title, x + PAD, y + 8, W - PAD * 2, Ui.GOLD);
		Ui.rule(g, x + 6, x + W - 6, y + 19);
		int ty = y + 24;
		for (Component c : lines) {
			boolean head = c.getString().isEmpty() ? false : c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().contains(".head.");
			if (head) {
				ty += GAP / 2;
			}
			for (FormattedCharSequence part : this.font.split(c, W - PAD * 2)) {
				if (ty + LINE > y + h - 2) {
					break;
				}
				g.text(this.font, part, x + PAD, ty, head ? Ui.GOLD : Ui.TEXT);
				ty += LINE;
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}
}
