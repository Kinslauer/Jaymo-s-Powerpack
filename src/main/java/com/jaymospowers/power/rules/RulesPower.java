package com.jaymospowers.power.rules;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Lawgiver (Andy's Awakening pick: "Rule writer"). Whatever they write as a rule is, for a while, simply true. They keep a
 * rulebook of up to {@link Rules#MOST} rules, each one sentence ({@link Rules}): who it's about (themselves, their allies near,
 * their foes near, the one they look at, everyone near), what is true of them (they mend, wither, are swift or slow, strong
 * or weak, hardened, float, burn, have no powers, glow, are blind), how strong (1, and one more with each ability learned)
 * and how long (5 s to a minute). Enacting a rule costs <b>ink</b> ({@link Rules#cost}), which comes back slowly
 * ({@link #INK_REGEN} a second, twice that with no rule running). One rule runs at a time; three at mastery 9.
 * <ul>
 * <li>R: enact the rule picked (the starter: the first rulebook's "I mend").</li>
 * <li>G: open the rulebook and write.</li>
 * <li>V: pick the next rule (Sneak + V, the one before).</li>
 * <li>B: repeal: every rule of yours running ends, and a share of the ink left in them comes back.</li>
 * </ul>
 * "Have no powers" is the Eight-Sign Circle's seal ({@link #muted}): power keys refused, Jaymo's Kingdoms' casters hushed
 * ({@link #MUTE_TAG}) and forms sealed, a gifted villager's power asleep.
 */
public final class RulesPower implements Power {
	public static final RulesPower INSTANCE = new RulesPower();
	public static final Identifier ID = JaymosPowers.id("rules");
	public static final int COLOR = 0xE8C66A;
	static final int INK = 0x2A2140;

	// ---- tuning ----
	/** Ink: the most held, regained a second (twice with no rule running), the share of a repealed rule's ink given back. */
	public static final float INK_MOST = 150.0F, INK_REGEN = 1.0F, REPEAL_BACK = 0.5F;
	/** How near "near" is (blocks), and how far "the one I look at" can be. */
	static final double NEAR = 12.0, LOOK = 24.0;
	/** Wither's harm a second for each point of strength; burning's seconds a point. */
	static final float WITHER_PER = 0.75F;
	static final int BURN_PER = 2;
	/** The tag on one a rule says has no powers; Kingdoms' Will reads it as hushed. */
	public static final String MUTE_TAG = "jaymospowers_ruled_mute";

	private static final class Running {
		int rule;
		long until;
		float paid;
		@Nullable LivingEntity target;
		final Set<LivingEntity> held = new HashSet<>();
	}

	private static final class Live {
		float ink = INK_MOST;
		int picked;
		final List<Running> running = new ArrayList<>();
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	/** Everyone a running rule says has no powers. */
	private static final Map<UUID, Integer> MUTED = new HashMap<>();

	private RulesPower() {
	}

	public static void init() {
		Powers.register(INSTANCE);
		Rules.registerPayloads();
		ServerPlayNetworking.registerGlobalReceiver(Rules.Write.TYPE, (payload, ctx) -> write(ctx.player(), payload.rules()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> repealAll(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			MUTED.clear();
		});
	}

	/** Whether a running rule says {@code e} has no powers (the Eight-Sign silencer and the gifted ask). */
	public static boolean muted(Entity e) {
		return !MUTED.isEmpty() && MUTED.containsKey(e.getUUID());
	}

	private static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player.getUUID(), id -> new Live());
	}

	static List<Integer> written(ServerPlayer player) {
		List<Integer> rules = player.getAttached(Rules.WRITTEN);
		return rules == null || rules.isEmpty() ? Rules.first() : rules;
	}

	/** The rulebook as the client wrote it: kept, each rule packed fresh (so nothing out of range is kept). */
	private static void write(ServerPlayer player, List<Integer> rules) {
		if (!Powers.has(player, INSTANCE) || !com.jaymospowers.core.Mastery.isUnlocked(player, PowerKey.ABILITY_2)) {
			return;
		}
		List<Integer> kept = new ArrayList<>();
		for (int i = 0; i < rules.size() && i < Rules.MOST; i++) {
			int r = rules.get(i);
			kept.add(Rules.pack(Rules.who(r), Rules.what(r), Rules.strength(r), Rules.length(r)));
		}
		player.setAttached(Rules.WRITTEN, kept.isEmpty() ? Rules.first() : List.copyOf(kept));
		com.jaymospowers.core.trials.PowerTrials.reach(player, "rules.book", kept.size());
		live(player).picked = 0;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.WRITABLE_BOOK);
	}

	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/** R (enact) first, then G (the rulebook), V (pick), B (repeal). */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_3, PowerKey.ABILITY_4};
	}

	@Override
	public boolean slotUnlocked(net.minecraft.world.entity.player.Player player, String slot) {
		return false;
	}

	@Override
	public void onGained(ServerPlayer player) {
		if (!player.hasAttached(Rules.WRITTEN)) {
			player.setAttached(Rules.WRITTEN, Rules.first());
		}
	}

	@Override
	public void onLost(ServerPlayer player) {
		repealAll(player, false);
		LIVE.remove(player.getUUID());
		player.removeAttached(Rules.WRITTEN);
	}

	@Override
	public void onDowned(ServerPlayer player) {
		repealAll(player, false);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		Live live = live(player);
		List<Integer> rules = written(player);
		switch (key) {
			case ABILITY_1 -> enact(player, live, rules.get(Math.min(live.picked, rules.size() - 1)));
			case ABILITY_2 -> ServerPlayNetworking.send(player, new Rules.OpenBook());
			case ABILITY_3 -> {
				live.picked = Math.floorMod(live.picked + (player.isShiftKeyDown() ? -1 : 1), rules.size());
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5F, 1.4F);
				int r = rules.get(live.picked);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rules.picked", live.picked + 1, sentence(r), Math.round(Rules.cost(r)))
					.withStyle(ChatFormatting.GOLD));
			}
			case ABILITY_4 -> repealAll(player, true);
		}
	}

	/** The rule as a line: "The foes near me are slowed (strength 3, 30 s)". */
	static Component sentence(int rule) {
		return Component.translatable("rule.jaymospowers.sentence", Component.translatable("rule.jaymospowers.who." + Rules.WHO_NAMES[Rules.who(rule)]),
			Component.translatable("rule.jaymospowers.what." + Rules.WHAT_NAMES[Rules.what(rule)] + (Rules.who(rule) == Rules.SELF ? ".self" : "")),
			Rules.strength(rule), Rules.SECONDS[Rules.length(rule)]);
	}

	private static void enact(ServerPlayer player, Live live, int rule) {
		ServerLevel level = player.level();
		if (Rules.strength(rule) > Rules.strongest(player)) {
			rule = Rules.pack(Rules.who(rule), Rules.what(rule), Rules.strongest(player), Rules.length(rule));
		}
		if (live.running.size() >= Rules.atOnce(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rules.full", Rules.atOnce(player)).withStyle(ChatFormatting.GRAY));
			return;
		}
		float cost = Rules.cost(rule);
		if (live.ink + 1.0E-3F < cost && !player.getAbilities().instabuild) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rules.no_ink", Math.round(cost), Math.round(live.ink)).withStyle(ChatFormatting.GRAY));
			return;
		}
		Running run = new Running();
		run.rule = rule;
		run.until = level.getGameTime() + Rules.SECONDS[Rules.length(rule)] * 20L;
		run.paid = cost;
		if (Rules.who(rule) == Rules.TARGET) {
			run.target = looked(player);
			if (run.target == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rules.no_one").withStyle(ChatFormatting.GRAY));
				return;
			}
		}
		if (!player.getAbilities().instabuild) {
			live.ink -= cost;
		}
		live.running.add(run);
		trials(player, live, rule);
		MagicFx.sigil(level, player.getEyePosition().add(player.getLookAngle().scale(0.8)), player.getLookAngle(), 0.6F, COLOR, 20);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rules.enacted", sentence(rule)).withStyle(ChatFormatting.GOLD));
		apply(level, player, run);
	}

	/** A rule enacted, as the Lawgiver's trials count it: what it is about, how strong, how long, how many run beside it. */
	private static void trials(ServerPlayer player, Live live, int rule) {
		com.jaymospowers.core.trials.PowerTrials.count(player, "rules.enact", 1);
		com.jaymospowers.core.trials.PowerTrials.count(live.picked > 0 ? player : null, "rules.pick", 1);
		com.jaymospowers.core.trials.PowerTrials.count(Rules.who(rule) == Rules.FOES ? player : null, "rules.foes", 1);
		com.jaymospowers.core.trials.PowerTrials.count(Rules.who(rule) == Rules.ALLIES ? player : null, "rules.allies", 1);
		com.jaymospowers.core.trials.PowerTrials.count(Rules.who(rule) == Rules.TARGET ? player : null, "rules.target", 1);
		com.jaymospowers.core.trials.PowerTrials.count(Rules.what(rule) == Rules.MUTE ? player : null, "rules.mute", 1);
		com.jaymospowers.core.trials.PowerTrials.reach(player, "rules.strength", Rules.strength(rule));
		com.jaymospowers.core.trials.PowerTrials.reach(player, "rules.long", Rules.SECONDS[Rules.length(rule)]);
		com.jaymospowers.core.trials.PowerTrials.reach(player, "rules.at_once", live.running.size());
	}

	/** Anyone under the crosshair within {@link #LOOK} (friend or foe). */
	private static @Nullable LivingEntity looked(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(LOOK));
		LivingEntity best = null;
		double near = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < near) {
				near = hit.get().distanceToSqr(eye);
				best = e;
			}
		}
		return best;
	}

	/** Who the rule is about, now. */
	private static List<LivingEntity> who(ServerLevel level, ServerPlayer player, Running run) {
		int who = Rules.who(run.rule);
		if (who == Rules.SELF) {
			return List.of(player);
		}
		if (who == Rules.TARGET) {
			return run.target != null && run.target.isAlive() && run.target.level() == level ? List.of(run.target) : List.of();
		}
		return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(NEAR), e -> e != player && e.isAlive() && !e.isSpectator()
			&& e.distanceTo(player) <= NEAR && switch (who) {
				case Rules.ALLIES -> player.isAlliedTo(e) || e instanceof OwnableEntity pet && pet.getOwner() == player;
				case Rules.FOES -> PickKit.foe(player, e);
				default -> true;
			});
	}

	/** One second of a running rule on everyone it is about. */
	private static void apply(ServerLevel level, ServerPlayer player, Running run) {
		int s = Rules.strength(run.rule);
		int what = Rules.what(run.rule);
		List<LivingEntity> about = who(level, player, run);
		com.jaymospowers.core.trials.PowerTrials.reach(player, "rules.many", about.size());
		if (what == Rules.MUTE) {
			// who it no longer covers has their powers back
			for (Iterator<LivingEntity> it = run.held.iterator(); it.hasNext();) {
				LivingEntity e = it.next();
				if (!about.contains(e)) {
					unmute(e);
					it.remove();
				}
			}
		}
		for (LivingEntity e : about) {
			switch (what) {
				case Rules.MEND -> effect(e, player, MobEffects.REGENERATION, s - 1);
				case Rules.WITHER -> e.hurtServer(level, player.damageSources().indirectMagic(player, player), WITHER_PER * s);
				case Rules.SWIFT -> effect(e, player, MobEffects.SPEED, s - 1);
				case Rules.SLOW -> effect(e, player, MobEffects.SLOWNESS, s - 1);
				case Rules.STRONG -> effect(e, player, MobEffects.STRENGTH, s - 1);
				case Rules.WEAK -> effect(e, player, MobEffects.WEAKNESS, s - 1);
				case Rules.HARD -> effect(e, player, MobEffects.RESISTANCE, Math.min(3, s - 1));
				case Rules.FLOAT -> {
					effect(e, player, MobEffects.LEVITATION, s - 1);
					e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true), player);
				}
				case Rules.BURN -> e.igniteForSeconds(BURN_PER * s);
				case Rules.MUTE -> {
					if (run.held.add(e)) {
						MUTED.merge(e.getUUID(), 1, Integer::sum);
						e.addTag(MUTE_TAG);
					}
				}
				case Rules.GLOW -> effect(e, player, MobEffects.GLOWING, 0);
				case Rules.BLIND -> {
					effect(e, player, MobEffects.BLINDNESS, 0);
					if (s >= 3) {
						effect(e, player, MobEffects.DARKNESS, 0);
					}
				}
				default -> {
				}
			}
			Vec3 c = e.getBoundingBox().getCenter();
			level.sendParticles(MagicFx.rune(COLOR, 0.6F), c.x, c.y + e.getBbHeight() * 0.5, c.z, 1, 0.2, 0.1, 0.2, 0.01);
		}
	}

	private static void effect(LivingEntity e, ServerPlayer by, Holder<MobEffect> effect, int amplifier) {
		e.addEffect(new MobEffectInstance(effect, 40, Math.max(0, amplifier), false, true), by);
	}

	private static void unmute(LivingEntity e) {
		Integer n = MUTED.get(e.getUUID());
		if (n == null || n <= 1) {
			MUTED.remove(e.getUUID());
			e.removeTag(MUTE_TAG);
		} else {
			MUTED.put(e.getUUID(), n - 1);
		}
	}

	private static void end(Running run) {
		run.held.forEach(RulesPower::unmute);
		run.held.clear();
	}

	/** B: every rule of theirs ends ({@code refund}: a share of the ink left in them comes back). */
	static void repealAll(ServerPlayer player, boolean refund) {
		Live live = LIVE.get(player.getUUID());
		if (live == null || live.running.isEmpty()) {
			return;
		}
		long now = player.level().getGameTime();
		for (Running run : live.running) {
			end(run);
			if (refund) {
				float left = Math.max(0.0F, (run.until - now) / (Rules.SECONDS[Rules.length(run.rule)] * 20.0F));
				com.jaymospowers.core.trials.PowerTrials.count(player, "rules.repeal", 1);
				live.ink = Math.min(INK_MOST, live.ink + run.paid * left * REPEAL_BACK);
			}
		}
		live.running.clear();
		if (refund) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 0.8F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rules.repealed").withStyle(ChatFormatting.GOLD));
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			live = live(player);
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now % 20 != 0) {
			return;
		}
		live.ink = Math.min(INK_MOST, live.ink + INK_REGEN * (live.running.isEmpty() ? 2.0F : 1.0F));
		for (Iterator<Running> it = live.running.iterator(); it.hasNext();) {
			Running run = it.next();
			if (now >= run.until || !player.isAlive()) {
				end(run);
				it.remove();
				continue;
			}
			apply(level, player, run);
		}
		if (!live.running.isEmpty() || live.ink < INK_MOST) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rules.ink", PickKit.gauge(live.ink / INK_MOST, 10), Math.round(live.ink),
				live.running.size(), Rules.atOnce(player)).withStyle(ChatFormatting.GOLD));
		}
	}
}
