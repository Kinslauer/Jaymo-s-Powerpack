package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Crowned Sun's arts (Andy's Awakening pick "Sun power", 2026-11-17: "The crowned patrons's moves initially all dawnborn,
 * clerics and paladins can learn it"), on the Sun key ({@link ArtKeys}). Every Dawnborn has them by blood; a Cleric
 * ({@link #CLERIC_NODE}) or a Paladin ({@link #PALADIN_NODE}) learns them in the tree. The Ash Whisperer's sworn are refused:
 * the Sun lends no fire to its rival's own.
 * <ul>
 * <li><b>The higher the sun, the stronger</b>: the arts and your own blows go by the sun's height where you stand under the
 * open sky ({@link #ART_NOON} and {@link #BLOW_NOON} at noon); by night, under a roof, or where there is no sun, the arts are
 * at {@link #ART_DARK} and your blows are only your own.</li>
 * <li><b>Ascension</b>: fighting (and standing in the sun) fills you with its heat; each {@link #STEP_HEAT} is a step, four of
 * them, and at each you grow, hit harder and your arts burn hotter. It ebbs when the fighting stops.</li>
 * <li><b>Sun orb</b> (tap): an orb of sunfire flies where you look and bursts.</li>
 * <li><b>Sunfire pillar</b> (sneak + tap at a spot): the ground is marked, then a pillar of fire stands on it and burns what
 * is in it, beat after beat.</li>
 * <li><b>Pin and pummel</b> (sprint + tap at a foe near you): it is held fast, and you rain blows on it; the last throws it.</li>
 * <li><b>A sun of your own</b> (hold): a small sun hangs over you for a while: light to fight by, and it counts as a sun
 * ({@link #ART_OWN_SUN}) where the real one cannot be seen. Hold again to put it out.</li>
 * <li><b>The Zenith Axe</b> (sneak + hold, an axe in the hand and a nether star in the other): the star is forged into the
 * axe. With it in hand the ascension has a fifth step.</li>
 * </ul>
 */
public final class SunArts {
	// ---- tuning ----
	public static final String CLERIC_NODE = "cle_sun", PALADIN_NODE = "pal_sun";
	/** The arts' strength with no sun, at sunrise and sunset, at noon, and under a sun of your own (where the real one is less). */
	static final float ART_DARK = 0.7F, ART_HORIZON = 1.0F, ART_NOON = 1.5F, ART_OWN_SUN = 1.1F;
	/** Your own blows at noon (a factor; nothing is taken from them without the sun). */
	static final float BLOW_NOON = 1.2F;
	/** Ascension: steps, and with the Zenith Axe in hand; the heat of one step. */
	static final int STEPS = 4, AXE_STEPS = 5;
	static final float STEP_HEAT = 100.0F;
	/** Heat for a blow landed, for a blow taken, and each second under the day's open sky (at noon; less as the sun sinks). */
	static final float HEAT_BLOW = 8.0F, HEAT_TAKEN = 4.0F, HEAT_SUN = 2.0F;
	/** After this long (ticks) with no blow either way the heat ebbs, this much each second. */
	static final int CALM_TICKS = 200;
	static final float HEAT_EBB = 6.0F;
	/** Each step: how much the body grows, the share more your blows do, the share more the arts do. */
	static final double STEP_SCALE = 0.1;
	static final float STEP_BLOW = 0.06F, STEP_ART = 0.12F;
	/** Sun orb: speed (blocks a tick), how long it flies (ticks), harm, its burst, how long it burns (ticks), cooldown. */
	static final double ORB_SPEED = 1.1;
	static final int ORB_LIFE = 40, ORB_FIRE = 80, ORB_COOLDOWN = 40;
	static final float ORB_DAMAGE = 5.0F, ORB_BURST = 2.5F;
	/** Sunfire pillar: reach, the warning (ticks), its radius and height, beats and the ticks between them, harm a beat, fire, cooldown. */
	static final double PILLAR_REACH = 24.0;
	static final int PILLAR_WARN = 15, PILLAR_BEATS = 4, PILLAR_BEAT = 10, PILLAR_FIRE = 100, PILLAR_COOLDOWN = 240;
	static final float PILLAR_RADIUS = 2.5F, PILLAR_HEIGHT = 9.0F, PILLAR_DAMAGE = 4.0F;
	/** Pin and pummel: reach, the hold (ticks), blows and the ticks between them, harm a blow, the throw, cooldown. */
	static final double PIN_REACH = 5.0, PIN_THROW = 1.1;
	static final int PIN_TICKS = 30, PIN_BLOWS = 5, PIN_BEAT = 6, PIN_COOLDOWN = 300;
	static final float PIN_DAMAGE = 3.0F;
	/** The last blow of the pummelling is this many times as hard, and throws the foe up this fast (blocks a tick). */
	static final float PIN_LAST = 2.0F;
	static final double PIN_LIFT = 0.45;
	/** A sun of your own: how long it burns (ticks), how high it hangs, its light, cooldown once it is out. */
	static final int OWN_SUN_TICKS = 2400, OWN_SUN_LIGHT = 15, OWN_SUN_COOLDOWN = 1200;
	static final double OWN_SUN_HEIGHT = 3.0;
	static final String AXE_TAG = "jaymoskingdoms_zenith_axe";
	static final int GOLD = 0xFFC21A, FLAME = 0xFF7A1A, WHITE = 0xFFF4C8;

	private static final Identifier SCALE_ID = JaymosKingdoms.id("sun_ascension");

	private static final class State {
		float heat;
		int step;
		long lastBlow;
		long sunUntil;
		@Nullable ServerLevel lightLevel;
		@Nullable BlockPos light;
	}

	private static final class Orb {
		final ServerLevel level;
		final ServerPlayer owner;
		final Vec3 step;
		final float power;
		Vec3 at;
		int life = ORB_LIFE;

		Orb(ServerLevel level, ServerPlayer owner, Vec3 at, Vec3 step, float power) {
			this.level = level;
			this.owner = owner;
			this.at = at;
			this.step = step;
			this.power = power;
		}
	}

	private static final class Pillar {
		final ServerLevel level;
		final ServerPlayer owner;
		final Vec3 at;
		final float power;
		final long from;
		int beats;

		Pillar(ServerLevel level, ServerPlayer owner, Vec3 at, float power, long from) {
			this.level = level;
			this.owner = owner;
			this.at = at;
			this.power = power;
			this.from = from;
		}
	}

	private static final class Pin {
		final ServerPlayer owner;
		final LivingEntity target;
		final long from;
		int blows;

		Pin(ServerPlayer owner, LivingEntity target, long from) {
			this.owner = owner;
			this.target = target;
			this.from = from;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final List<Orb> ORBS = new ArrayList<>();
	private static final List<Pillar> PILLARS = new ArrayList<>();
	private static final List<Pin> PINS = new ArrayList<>();

	private SunArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SunArts::tick);
		// heat: a blow landed, a blow taken
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (damage <= 0.0F) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer by && by != entity && holds(by)) {
				heat(by, HEAT_BLOW);
			}
			if (entity instanceof ServerPlayer hit && source.getEntity() != null && holds(hit)) {
				heat(hit, HEAT_TAKEN);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			State s = STATES.remove(handler.player.getUUID());
			if (s != null) {
				dark(s);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.values().forEach(SunArts::dark);
			STATES.clear();
			ORBS.clear();
			PILLARS.clear();
			PINS.clear();
		});
	}

	/** Whether the Sun's arts are {@code player}'s: a Dawnborn, or a Cleric or Paladin who learned them; never the Ash's sworn. */
	public static boolean holds(Player player) {
		return (Heritage.is(player, Heritage.Kind.DAWNBORN) || ClassTrees.has(player, CLERIC_NODE) > 0 || ClassTrees.has(player, PALADIN_NODE) > 0)
			&& Patrons.of(player) != Patron.ASH_WHISPERER;
	}

	/** How high the sun stands over {@code player}, 0 (no sun: night, a roof, no sky) to 1 (noon under the open sky). */
	static float height(Player player) {
		if (!player.level().isBrightOutside() || !player.level().canSeeSky(player.blockPosition())) {
			return 0.0F;
		}
		long clock = player.level().getOverworldClockTime() % 24000L;
		return Math.max(0.0F, Mth.sin((float) (clock / 12000.0 * Math.PI)));
	}

	/** The arts' strength for {@code player} now: the sun's height, a sun of their own, and the steps climbed. */
	static float power(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		boolean seen = player.level().isBrightOutside() && player.level().canSeeSky(player.blockPosition());
		float sun = seen ? Mth.lerp(height(player), ART_HORIZON, ART_NOON) : ART_DARK;
		if (s != null && s.light != null) {
			sun = Math.max(sun, ART_OWN_SUN);
		}
		return sun * (1.0F + STEP_ART * (s == null ? 0 : s.step));
	}

	/** The step of ascension {@code player} stands on (0 to {@link #AXE_STEPS}). */
	public static int step(Player player) {
		State s = STATES.get(player.getUUID());
		return s == null ? 0 : s.step;
	}

	/** A sun holder's own blows: more the higher the sun, and with each step ({@code LivingEntityHurtMixin}). */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		// (their own blows only: the arts carry the sun's height already)
		if (amount <= 0.0F || !source.is(DamageTypes.PLAYER_ATTACK) || !(source.getDirectEntity() instanceof ServerPlayer by) || by == victim || !holds(by)) {
			return amount;
		}
		return amount * Mth.lerp(height(by), 1.0F, BLOW_NOON) * (1.0F + STEP_BLOW * step(by));
	}

	public static boolean axe(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(AXE_TAG);
	}

	private static int ceiling(ServerPlayer player) {
		return axe(player.getMainHandItem()) ? AXE_STEPS : STEPS;
	}

	private static void heat(ServerPlayer player, float by) {
		State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		s.lastBlow = player.level().getGameTime();
		s.heat = Math.min(s.heat + by, (ceiling(player) + 0.5F) * STEP_HEAT);
	}

	// ---- the key --------------------------------------------------------------------------------------------------------------

	static void key(ServerPlayer player, ArtKeys.Gesture gesture) {
		if (!holds(player) || !(player.level() instanceof ServerLevel level)) {
			if (Patrons.of(player) == Patron.ASH_WHISPERER && (Heritage.is(player, Heritage.Kind.DAWNBORN) || ClassTrees.has(player, CLERIC_NODE) > 0
				|| ClassTrees.has(player, PALADIN_NODE) > 0)) {
				// theirs by blood or by learning, and refused: they are told why
				ArtKeys.refuse(player, "sun_ash");
			}
			return;
		}
		switch (gesture) {
			case TAP -> {
				if (!ArtKeys.cooling(player, "sun_orb", ORB_COOLDOWN)) {
					Vec3 look = player.getLookAngle();
					ORBS.add(new Orb(level, player, player.getEyePosition().add(look.scale(0.8)), look.scale(ORB_SPEED), power(player)));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.3F);
				}
			}
			case SNEAK -> {
				LivingEntity foe = ArtKeys.sight(player, PILLAR_REACH, e -> !ArtKeys.friend(player, e));
				BlockHitResult hit = foe == null ? ArtKeys.block(player, PILLAR_REACH) : null;
				if (foe == null && hit == null) {
					ArtKeys.refuse(player, "sun_no_spot");
				} else if (!ArtKeys.cooling(player, "sun_pillar", PILLAR_COOLDOWN)) {
					Vec3 at = foe != null ? foe.position() : hit.getLocation();
					PILLARS.add(new Pillar(level, player, at, power(player), level.getGameTime()));
					Fx.sigil(level, at.add(0.0, 0.05, 0.0), PILLAR_RADIUS, GOLD, PILLAR_WARN + PILLAR_BEATS * PILLAR_BEAT);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
				}
			}
			case SPRINT -> {
				LivingEntity foe = ArtKeys.sight(player, PIN_REACH, e -> !ArtKeys.friend(player, e));
				if (foe == null) {
					ArtKeys.refuse(player, "no_foe_near");
				} else if (!ArtKeys.cooling(player, "sun_pin", PIN_COOLDOWN)) {
					// what nothing can hold is still pummelled; it just is not held for it
					HeldFast.hold(foe, PIN_TICKS);
					PINS.removeIf(p -> p.owner == player);
					PINS.add(new Pin(player, foe, level.getGameTime()));
					Fx.ring(level, foe.position().add(0.0, 0.1, 0.0), foe.getBbWidth() + 0.8F, GOLD);
					level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.6F);
				}
			}
			case HOLD -> ownSun(player, level);
			case SNEAK_HOLD -> forge(player, level);
		}
	}

	private static void ownSun(ServerPlayer player, ServerLevel level) {
		State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		long now = level.getGameTime();
		if (s.sunUntil > now) {
			s.sunUntil = 0L;
			dark(s);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
			return;
		}
		if (ArtKeys.cooling(player, "sun_own", OWN_SUN_TICKS + OWN_SUN_COOLDOWN)) {
			return;
		}
		s.sunUntil = now + OWN_SUN_TICKS;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	/** Takes {@code s}'s light block away. */
	private static void dark(State s) {
		if (s.light != null && s.lightLevel != null && s.lightLevel.getBlockState(s.light).is(Blocks.LIGHT)) {
			s.lightLevel.setBlock(s.light, Blocks.AIR.defaultBlockState(), 3);
		}
		s.light = null;
		s.lightLevel = null;
	}

	private static void forge(ServerPlayer player, ServerLevel level) {
		ItemStack held = player.getMainHandItem();
		if (!held.is(ItemTags.AXES) || axe(held)) {
			ArtKeys.refuse(player, axe(held) ? "sun_axe_made" : "sun_axe_needs");
			return;
		}
		ItemStack star = player.getOffhandItem();
		if (!star.is(Items.NETHER_STAR)) {
			ArtKeys.refuse(player, "sun_axe_needs");
			return;
		}
		star.shrink(1);
		CustomData.update(DataComponents.CUSTOM_DATA, held, tag -> tag.putBoolean(AXE_TAG, true));
		held.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.zenith_axe").withStyle(s -> s.withColor(GOLD).withItalic(false)));
		held.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.zenith_axe.desc").withStyle(ChatFormatting.GRAY))));
		held.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		Fx.blast(level, player.position().add(0.0, 1.0, 0.0), 2.0F, WHITE);
		Fx.beam(level, player.position(), player.position().add(0.0, 12.0, 0.0), 0.3F, GOLD, 20);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State s = STATES.get(player.getUUID());
			if (s == null) {
				// the sun's heat finds its own under the open sky
				if (player.tickCount % 20 == 0 && player.isAlive() && height(player) > 0.0F && holds(player)) {
					STATES.put(player.getUUID(), new State());
				}
				continue;
			}
			if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || !holds(player)) {
				dark(s);
				s.sunUntil = 0L;
				s.heat = 0.0F;
				climb(player, s, 0);
				STATES.remove(player.getUUID());
				continue;
			}
			long now = level.getGameTime();
			ownSunTick(player, level, s, now);
			if ((now + player.getId()) % 20L != 0L) {
				continue;
			}
			if (now - s.lastBlow >= CALM_TICKS) {
				s.heat = Math.max(0.0F, s.heat - HEAT_EBB);
			} else {
				s.heat = Math.min(s.heat + HEAT_SUN * height(player), (ceiling(player) + 0.5F) * STEP_HEAT);
			}
			climb(player, s, Math.min(ceiling(player), (int) (s.heat / STEP_HEAT)));
			if (s.step > 0) {
				level.sendParticles(Fx.mote(GOLD, 0.7F + 0.15F * s.step), player.getX(), player.getY(0.6), player.getZ(), 1 + s.step, 0.3, 0.5, 0.3, 0.0);
			}
		}
		orbs();
		pillars();
		pins();
	}

	/** Sets the step {@code player} stands on: the body grows with it where there is room (the strength climbs either way). */
	private static void climb(ServerPlayer player, State s, int step) {
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale == null) {
			return;
		}
		AttributeModifier worn = scale.getModifier(SCALE_ID);
		double want = step * STEP_SCALE;
		if (step != s.step || (worn == null ? 0.0 : worn.amount()) != want) {
			double now = 1.0 + (worn == null ? 0.0 : worn.amount());
			if (want + 1.0 <= now || room(player, (1.0 + want) / now)) {
				scale.removeModifier(SCALE_ID);
				if (want > 0.0) {
					scale.addTransientModifier(new AttributeModifier(SCALE_ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		}
		if (step == s.step) {
			return;
		}
		boolean up = step > s.step;
		s.step = step;
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.sun.step", step, ceiling(player)).withStyle(ChatFormatting.GOLD));
		if (up && player.level() instanceof ServerLevel level) {
			Fx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.2F + 0.4F * step, GOLD);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 0.8F + 0.15F * step);
		}
	}

	private static boolean room(ServerPlayer player, double times) {
		AABB box = player.getBoundingBox();
		double w = box.getXsize() * times / 2.0, h = box.getYsize() * times;
		return player.level().noCollision(player, new AABB(player.getX() - w, box.minY + 0.01, player.getZ() - w, player.getX() + w, box.minY + h, player.getZ() + w));
	}

	/** The sun of your own: a light that keeps over the head, and the orb drawn there. */
	private static void ownSunTick(ServerPlayer player, ServerLevel level, State s, long now) {
		if (s.sunUntil <= now) {
			if (s.light != null) {
				dark(s);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
			return;
		}
		Vec3 over = player.position().add(0.0, player.getBbHeight() + OWN_SUN_HEIGHT, 0.0);
		if (now % 10L == 0L) {
			Fx.orb(level, over, 0.6F, GOLD, 11);
		}
		BlockPos want = BlockPos.containing(over);
		// the light sits in the nearest air under the sun, down to the head
		while (want.getY() > player.blockPosition().getY() && !level.getBlockState(want).isAir() && !want.equals(s.light)) {
			want = want.below();
		}
		if (want.equals(s.light) && s.lightLevel == level) {
			return;
		}
		if (!level.getBlockState(want).isAir()) {
			return;
		}
		dark(s);
		level.setBlock(want, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, OWN_SUN_LIGHT), 3);
		s.light = want;
		s.lightLevel = level;
	}

	private static void orbs() {
		for (Iterator<Orb> it = ORBS.iterator(); it.hasNext();) {
			Orb o = it.next();
			Vec3 next = o.at.add(o.step);
			boolean wall = o.level.clip(new net.minecraft.world.level.ClipContext(o.at, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, o.owner)).getType() != net.minecraft.world.phys.HitResult.Type.MISS;
			o.at = next;
			boolean struck = wall || !o.level.getEntitiesOfClass(LivingEntity.class, new AABB(o.at, o.at).inflate(0.7), e -> ArtKeys.foe(o.owner, e)).isEmpty();
			if (!struck && --o.life > 0) {
				o.level.sendParticles(Fx.mote(GOLD, 1.6F), o.at.x, o.at.y, o.at.z, 2, 0.05, 0.05, 0.05, 0.0);
				o.level.sendParticles(Fx.spark(FLAME, 0.8F), o.at.x, o.at.y, o.at.z, 1, 0.1, 0.1, 0.1, 0.0);
				continue;
			}
			it.remove();
			Fx.blast(o.level, o.at, ORB_BURST, GOLD);
			o.level.playSound(null, o.at.x, o.at.y, o.at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
			for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, new AABB(o.at, o.at).inflate(ORB_BURST), e -> ArtKeys.foe(o.owner, e))) {
				if (e.hurtServer(o.level, o.level.damageSources().indirectMagic(o.owner, o.owner), ORB_DAMAGE * o.power)) {
					e.igniteForTicks(ORB_FIRE);
				}
			}
		}
	}

	private static void pillars() {
		for (Iterator<Pillar> it = PILLARS.iterator(); it.hasNext();) {
			Pillar p = it.next();
			long since = p.level.getGameTime() - p.from - PILLAR_WARN;
			if (since < 0L || since % PILLAR_BEAT != 0L) {
				continue;
			}
			Fx.beam(p.level, p.at, p.at.add(0.0, PILLAR_HEIGHT, 0.0), PILLAR_RADIUS * 0.6F, p.beats % 2 == 0 ? GOLD : FLAME, PILLAR_BEAT + 2);
			p.level.playSound(null, p.at.x, p.at.y, p.at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
			for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class,
				new AABB(p.at.x - PILLAR_RADIUS, p.at.y - 1.0, p.at.z - PILLAR_RADIUS, p.at.x + PILLAR_RADIUS, p.at.y + PILLAR_HEIGHT, p.at.z + PILLAR_RADIUS),
				e -> ArtKeys.foe(p.owner, e) && e.position().subtract(p.at).horizontalDistanceSqr() <= PILLAR_RADIUS * PILLAR_RADIUS)) {
				e.setInvulnerableTime(0);
				if (e.hurtServer(p.level, p.level.damageSources().indirectMagic(p.owner, p.owner), PILLAR_DAMAGE * p.power)) {
					e.igniteForTicks(PILLAR_FIRE);
				}
			}
			if (++p.beats >= PILLAR_BEATS) {
				it.remove();
			}
		}
	}

	private static void pins() {
		for (Iterator<Pin> it = PINS.iterator(); it.hasNext();) {
			Pin p = it.next();
			if (!(p.owner.level() instanceof ServerLevel level) || !p.owner.isAlive() || !p.target.isAlive() || p.target.level() != level
				|| p.owner.distanceToSqr(p.target) > (PIN_REACH + 2.0) * (PIN_REACH + 2.0)) {
				it.remove();
				continue;
			}
			long since = level.getGameTime() - p.from;
			if (since == 0L || since % PIN_BEAT != 0L) {
				continue;
			}
			boolean last = ++p.blows >= PIN_BLOWS;
			Vec3 mid = p.target.getBoundingBox().getCenter();
			Vec3 from = p.owner.getEyePosition();
			Fx.stroke(level, from.add(mid.subtract(from).scale(0.4)), mid, 0.5F, last ? WHITE : GOLD);
			p.owner.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			p.target.setInvulnerableTime(0);
			p.target.hurtServer(level, level.damageSources().indirectMagic(p.owner, p.owner), PIN_DAMAGE * power(p.owner) * (last ? PIN_LAST : 1.0F));
			level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, last ? 0.7F : 1.1F);
			if (last) {
				it.remove();
				Vec3 away = mid.subtract(from).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() > 1.0E-4) {
					p.target.setDeltaMovement(away.normalize().scale(PIN_THROW).add(0.0, PIN_LIFT, 0.0));
					p.target.needsSync = true;
				}
				p.target.igniteForTicks(ORB_FIRE);
				Fx.blast(level, mid, 1.5F, GOLD);
			}
		}
	}
}
