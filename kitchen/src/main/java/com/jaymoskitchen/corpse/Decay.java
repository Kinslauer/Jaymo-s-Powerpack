package com.jaymoskitchen.corpse;

import net.minecraft.util.Mth;

/**
 * How a body weathers while it lies ({@link Wear}), worked out on the server a few times a second. Fire blackens it in
 * stages and, near the end of a long burn, leaves bare bone before the ash ({@link CorpseEntity#burnEnd}). Rot, over the
 * time it has before it rots away (or, for the dead that get up, before they do): the joints lock (rigor), the body goes
 * grey, patches darken, the patches fall away to bone (outer flesh first), and it sinks slowly into the ground.
 */
final class Decay {
	/** How often (ticks) a body's wear is worked out. */
	static final int EVERY = 10;
	/** Shares of a body's time (0 to 1) at which each stage begins and ends. */
	static final float STIFF_AT = 0.1F, GREY_FROM = 0.15F, GREY_TO = 0.7F, DARK_FROM = 0.4F, DARK_TO = 0.8F, BARE_FROM = 0.7F, BARE_TO = 1.0F,
		SINK_FROM = 0.6F, SINK_TO = 1.0F;
	/** The dead that get up never rot to bone, only this share of the way. */
	static final float RISER_SHARE = 0.75F;
	/** Shares of a burn at which the char finishes and the bone begins. */
	static final float CHAR_TO = 0.6F, BONE_FROM = 0.75F;

	private Decay() {
	}

	/** One stage's level (0 to {@link Wear#LEVELS}) for how far {@code at} is from {@code from} to {@code to}. */
	private static int level(float at, float from, float to) {
		if (at <= from) {
			return 0;
		}
		return Math.min(Wear.LEVELS, 1 + (int) (Mth.clamp((at - from) / (to - from), 0.0F, 1.0F) * (Wear.LEVELS - 0.001F)));
	}

	/** Works out {@code corpse}'s wear from its age and its burn, and stiffens its joints; sets it if it changed. */
	static void tick(CorpseEntity corpse) {
		if (corpse.tickCount % EVERY != 0 || corpse.isMachine()) {
			return;
		}
		int wear = corpse.wear();
		if (Wear.frozen(wear) || Wear.fading(wear)) {
			return;
		}
		int limit = corpse.rotLimit();
		float rot = limit <= 0 ? 0.0F : Math.min(1.0F, corpse.age() / (float) limit) * (corpse.rises() ? RISER_SHARE : 1.0F);
		float burn = corpse.burnTicks() <= 0 ? 0.0F : Math.min(1.0F, corpse.burnTicks() / (float) corpse.burnEnd());
		int charred = level(burn, 0.0F, CHAR_TO);
		int bare = Math.max(level(rot, BARE_FROM, BARE_TO), level(burn, BONE_FROM, 1.0F));
		int dark = Math.max(level(rot, DARK_FROM, DARK_TO), bare > 0 ? Wear.LEVELS : 0);
		int grey = level(rot, GREY_FROM, GREY_TO);
		int sink = (int) (Mth.clamp((rot - SINK_FROM) / (SINK_TO - SINK_FROM), 0.0F, 1.0F) * Wear.SINK_PERCENT);
		// (never less than it was: a fire put out leaves the char, and the burnt stay burnt)
		int now = wear;
		now = Wear.with(now, Wear.CHAR, Math.max(charred, Wear.get(wear, Wear.CHAR)));
		now = Wear.with(now, Wear.DARK, Math.max(dark, Wear.get(wear, Wear.DARK)));
		now = Wear.with(now, Wear.BARE, Math.max(bare, Wear.get(wear, Wear.BARE)));
		now = Wear.with(now, Wear.GREY, Math.max(grey, Wear.get(wear, Wear.GREY)));
		now = Wear.with(now, Wear.SINK, Math.max(sink, Wear.get(wear, Wear.SINK)));
		corpse.setWear(now);
		corpse.stiffen(rot >= STIFF_AT ? 1.0F : 0.0F);
	}
}
