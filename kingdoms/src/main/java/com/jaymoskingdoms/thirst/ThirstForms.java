package com.jaymoskingdoms.thirst;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.Skills;
import com.jaymoskingdoms.TalkNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The shapes of the thirsty, picked from a menu opened by crouching and using empty-handed on the open air (the client sends
 * {@link Menu}); the same input in a shape returns them to themselves. Each costs the gauge ({@link Thirst}) and then makes them wait.
 * <ul>
 * <li><b>Bat</b> ({@link #BAT_TICKS}): a small body that flies. A vanilla bat is drawn in their place (a puppet that follows them,
 * unhurtable; a blow on it is a blow on them), their own body is not drawn, and the living nearby show to them as red outlines
 * through walls (client, {@code ThirstClient}). It drinks the gauge slowly.</li>
 * <li><b>Colony</b>: a swarm of bats lifts one foe in sight up ({@link #LIFT_TICKS}) and drops it. Players only where
 * player-versus-player is allowed, never a party member.</li>
 * <li><b>Mist</b> ({@link #MIST_TICKS}): nothing can hurt them and they can hurt nothing; they drift slowly.</li>
 * <li><b>Wolf</b> ({@link #WOLF_TICKS}): a red-eyed vanilla wolf drawn in their place that swells ({@link #SWELL}) into a beast: strong,
 * quick and hard to hurt, then spent.</li>
 * <li><b>The stare</b> fixes the eyes ({@link ThirstGaze}).</li>
 * <li><b>The riposte</b>: crouch just before a blow lands ({@link #RIPOSTE_WINDOW} ticks) and it finds only bats: they are a few
 * blocks off. (A player with the Slip perk uses Slip instead.)</li>
 * </ul>
 */
public final class ThirstForms {
	public static final String BAT = "bat", MIST = "mist", WOLF = "wolf";
	/** The tag on every puppet and swarm bat this makes (a stray one left in the world is removed as it loads). */
	public static final String PUPPET_TAG = "jaymoskingdoms_thirst_puppet";

	// ---- tuning ----
	/** The gauge points each costs (at night three quarters). */
	static final int BAT_COST = 10, MIST_COST = 12, WOLF_COST = 16, COLONY_COST = 18, STARE_COST = 6, RIPOSTE_COST = 10;
	/** How long each lasts (ticks). */
	static final int BAT_TICKS = 1200, MIST_TICKS = 100, WOLF_TICKS = 400;
	/** How long they must wait after each (ticks). */
	static final long BAT_COOLDOWN = 300L, MIST_COOLDOWN = 600L, WOLF_COOLDOWN = 900L, COLONY_COOLDOWN = 700L, STARE_COOLDOWN = 200L, RIPOSTE_COOLDOWN = 400L;
	/** The bat's size (added to the scale: the body is this much smaller), how often its flight drinks a point (ticks), and its flight and the mist's (fly speed). */
	static final double BAT_SCALE = -0.7;
	static final int BAT_DRAIN_EVERY = 60;
	static final float BAT_FLY = 0.04F, MIST_FLY = 0.02F;
	/** The wolf's swell (added to the scale), how long the swelling takes (ticks), and its gifts (amplifiers) and the weariness after it (ticks). */
	static final double SWELL = 0.6;
	static final int SWELL_TICKS = 12, WOLF_STRENGTH = 1, WOLF_SPEED = 1, WOLF_RESIST = 0, GIFT_TICKS = 40, SPENT_TICKS = 100;
	/** The colony: bats, ticks the foe is lifted, ticks the swarm stays, the lift's speed, how far it finds a foe (blocks) and how true the aim must be (cosine). */
	static final int COLONY_BATS = 9, LIFT_TICKS = 26, COLONY_END = 44;
	static final double LIFT_SPEED = 0.38, COLONY_RANGE = 14.0, COLONY_AIM = 0.8;
	/** The riposte: how recent a crouch must be (ticks), how far it carries them (blocks, nearest tried last), how long the bats fly (ticks), how many, and how far a blow can be slipped. */
	static final int RIPOSTE_WINDOW = 8, RIPOSTE_BATS = 7, BURST_TICKS = 30;
	static final double[] RIPOSTE_STEPS = {5.0, 4.0, 6.0, 3.0};
	static final double RIPOSTE_REACH = 6.0;
	/** The slow fall they get when a shape ends in the air (ticks). */
	static final int FALL_TICKS = 60;
	/** How long a menu waits for an answer (ticks). */
	static final long MENU_TICKS = 1200L;

	private static final Identifier SIZE_ID = JaymosKingdoms.id("thirst_form_size");

	/** Client to server: the open-air crouch-use (the shape menu, or back to themselves). */
	public record Menu() implements CustomPacketPayload {
		public static final Type<Menu> TYPE = new Type<>(JaymosKingdoms.id("thirst_menu"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Menu> CODEC = StreamCodec.unit(new Menu());

		@Override
		public Type<Menu> type() {
			return TYPE;
		}
	}

	/** A shape worn now: which, when, its puppet, and whether it gave them flight. */
	private static final class Active {
		final String form;
		final long start, end;
		@Nullable Mob puppet;
		boolean flight;

		Active(String form, long start, long end) {
			this.form = form;
			this.start = start;
			this.end = end;
		}
	}

	/** A swarm: whose, the bats, the foe lifted (or none), when it began, and each bat's own way out (a burst). */
	private static final class Swarm {
		final UUID owner;
		final List<Bat> bats = new ArrayList<>();
		final int foe;
		final long start;
		final Vec3 from;
		final List<Vec3> ways = new ArrayList<>();

		Swarm(UUID owner, int foe, long start, Vec3 from) {
			this.owner = owner;
			this.foe = foe;
			this.start = start;
			this.from = from;
		}
	}

	/** An open menu: the options, and when it lapses. */
	private record Open(List<String> kinds, long until) {
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	/** Puppet and swarm bats (their own id) and whose they are. */
	private static final Map<UUID, UUID> PUPPETS = new HashMap<>();
	private static final List<Swarm> SWARMS = new ArrayList<>();
	private static final Map<String, Long> READY = new HashMap<>();
	private static final Map<UUID, Open> MENUS = new HashMap<>();
	/** Who is crouching, and when they last began to. */
	private static final Set<UUID> SNEAKING = new HashSet<>();
	private static final Map<UUID, Long> PRESSED = new HashMap<>();

	private ThirstForms() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ThirstForms::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ThirstForms::allowDamage);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(PUPPET_TAG) && !PUPPETS.containsKey(entity.getUUID()) && level.getServer() != null) {
				level.getServer().execute(entity::discard);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			PUPPETS.clear();
			SWARMS.clear();
			READY.clear();
			MENUS.clear();
			SNEAKING.clear();
			PRESSED.clear();
		});
		ThirstGaze.register();
	}

	// ---- queries ----

	private static boolean night(ServerLevel level) {
		return level.isDarkOutside();
	}

	private static int cost(ServerLevel level, int base) {
		return night(level) ? Math.max(1, Math.round(base * 0.75F)) : base;
	}

	private static String key(ServerPlayer p, String what) {
		return p.getUUID() + what;
	}

	private static boolean ready(ServerPlayer p, String what) {
		return p.level().getGameTime() >= READY.getOrDefault(key(p, what), 0L);
	}

	private static boolean mist(Player p) {
		return p instanceof ServerPlayer sp && ACTIVE.containsKey(sp.getUUID()) && MIST.equals(ACTIVE.get(sp.getUUID()).form);
	}

	private static boolean canAfford(ServerPlayer p, ServerLevel level, int base) {
		return Thirst.gauge(p) >= cost(level, base);
	}

	/** The shape's own bar: how much of it is left. */
	static @Nullable MutableComponent bar(ServerPlayer p) {
		Active a = ACTIVE.get(p.getUUID());
		if (a == null) {
			return null;
		}
		long left = Math.max(0L, a.end - p.level().getGameTime());
		return Thirst.bar((int)left, (int)(a.end - a.start), ChatFormatting.DARK_RED);
	}

	// ---- the menu ----

	/** The open-air crouch-use: back to themselves when in a shape, else the menu. */
	static void menu(ServerPlayer p) {
		if (!Thirst.thirsty(p) || !p.isAlive() || p.isSpectator() || !(p.level() instanceof ServerLevel level) || ThirstBite.biting(p) || !p.getMainHandItem().isEmpty()) {
			return;
		}
		if (ACTIVE.containsKey(p.getUUID())) {
			end(p, false);
			return;
		}
		if (!ServerPlayNetworking.canSend(p, TalkNet.Ask.TYPE)) {
			return;
		}
		List<String> kinds = List.of(BAT, "colony", MIST, WOLF, "stare", "close");
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (String kind : kinds) {
			options.add(Component.translatable("talk.jaymoskingdoms.drinker.menu." + kind));
			enabled.add(open(p, level, kind));
		}
		MENUS.put(p.getUUID(), new Open(kinds, level.getGameTime() + MENU_TICKS));
		ServerPlayNetworking.send(p, new TalkNet.Ask(p.getId(), Component.translatable("talk.jaymoskingdoms.drinker.menu.speaker"),
			Component.translatable("talk.jaymoskingdoms.drinker.menu.ask"), options, enabled));
	}

	private static boolean open(ServerPlayer p, ServerLevel level, String kind) {
		return switch (kind) {
			case BAT -> canAfford(p, level, BAT_COST) && ready(p, BAT);
			case MIST -> canAfford(p, level, MIST_COST) && ready(p, MIST);
			case WOLF -> canAfford(p, level, WOLF_COST) && ready(p, WOLF);
			case "colony" -> canAfford(p, level, COLONY_COST) && ready(p, "colony") && foe(p, level) != null;
			case "stare" -> canAfford(p, level, STARE_COST) && ready(p, "stare") && !ThirstGaze.armed(p);
			default -> true;
		};
	}

	/** An answer on the shape menu; false when it was not one. */
	static boolean answer(ServerPlayer p, TalkNet.Answer answer) {
		Open open = MENUS.get(p.getUUID());
		if (open == null || answer.entityId() != p.getId()) {
			return false;
		}
		MENUS.remove(p.getUUID());
		if (answer.option() < 0 || answer.option() >= open.kinds().size() || p.level().getGameTime() > open.until() || !(p.level() instanceof ServerLevel level)
			|| !Thirst.thirsty(p) || !p.isAlive() || ACTIVE.containsKey(p.getUUID())) {
			return true;
		}
		String kind = open.kinds().get(answer.option());
		if (!open(p, level, kind)) {
			return true;
		}
		long now = level.getGameTime();
		switch (kind) {
			case BAT -> enter(p, level, BAT, BAT_COST, BAT_COOLDOWN, BAT_TICKS);
			case MIST -> enter(p, level, MIST, MIST_COST, MIST_COOLDOWN, MIST_TICKS);
			case WOLF -> enter(p, level, WOLF, WOLF_COST, WOLF_COOLDOWN, WOLF_TICKS);
			case "colony" -> {
				LivingEntity foe = foe(p, level);
				if (foe != null) {
					Thirst.spend(p, cost(level, COLONY_COST));
					READY.put(key(p, "colony"), now + COLONY_COOLDOWN);
					colony(p, level, foe);
				}
			}
			case "stare" -> {
				Thirst.spend(p, cost(level, STARE_COST));
				READY.put(key(p, "stare"), now + STARE_COOLDOWN);
				ThirstGaze.arm(p);
			}
			default -> {
			}
		}
		return true;
	}

	// ---- the shapes ----

	private static void enter(ServerPlayer p, ServerLevel level, String form, int baseCost, long cooldown, int ticks) {
		long now = level.getGameTime();
		Thirst.spend(p, cost(level, baseCost));
		READY.put(key(p, form), now + cooldown);
		ThirstGaze.disarm(p.getUUID(), level.getServer());
		Active a = new Active(form, now, now + ticks);
		ACTIVE.put(p.getUUID(), a);
		p.setAttached(Thirst.FORM, form);
		Vec3 at = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0);
		switch (form) {
			case BAT -> {
				setSize(p, BAT_SCALE);
				a.puppet = spawnPuppet(level, p, EntityTypes.BAT);
				if (a.puppet instanceof Bat bat) {
					bat.setResting(false);
				}
				a.flight = grantFlight(p, BAT_FLY);
				level.playSound(null, p.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.0F, 0.8F);
				level.sendParticles(Fx.miasma(0x2A0A12, 1.1F), at.x, at.y, at.z, 18, 0.4, 0.5, 0.4, 0.03);
			}
			case MIST -> {
				a.flight = grantFlight(p, MIST_FLY);
				level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.9F, 0.4F);
				level.sendParticles(Fx.miasma(0x7A4A5A, 1.4F), at.x, at.y, at.z, 24, 0.4, 0.6, 0.4, 0.02);
			}
			case WOLF -> {
				a.puppet = spawnPuppet(level, p, EntityTypes.WOLF);
				if (a.puppet instanceof Wolf wolf) {
					wolf.setTimeToRemainAngry(ticks + 200L);
					setPuppetScale(wolf, 1.0);
				}
				level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.3F, 0.5F);
				Fx.ring(level, p.position(), 4.0F, Thirst.BLOOD);
				Fx.blast(level, at, 2.5F, Thirst.BLOOD);
			}
			default -> {
			}
		}
	}

	/** The shape ends: they are themselves again (spent after the wolf if it ran its course). */
	static void end(ServerPlayer p, boolean spent) {
		Active a = ACTIVE.remove(p.getUUID());
		ThirstGaze.disarm(p.getUUID(), p.level().getServer());
		PRESSED.remove(p.getUUID());
		SNEAKING.remove(p.getUUID());
		if (a == null) {
			p.removeAttached(Thirst.FORM);
			return;
		}
		p.removeAttached(Thirst.FORM);
		if (a.puppet != null) {
			PUPPETS.remove(a.puppet.getUUID());
			a.puppet.discard();
		}
		setSize(p, 0.0);
		if (a.flight) {
			revokeFlight(p);
			if (!p.onGround() && !p.isCreative()) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FALL_TICKS, 0, true, false, false));
			}
		}
		if (p.level() instanceof ServerLevel level) {
			Vec3 at = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0);
			level.sendParticles(Fx.miasma(a.form.equals(MIST) ? 0x7A4A5A : 0x2A0A12, 1.1F), at.x, at.y, at.z, 14, 0.4, 0.5, 0.4, 0.02);
		}
		if (spent && a.form.equals(WOLF)) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPENT_TICKS, 1, true, false, false));
			p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SPENT_TICKS, 0, true, false, false));
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.spent").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** A player coming in: nothing they were given for a shape in the last session stays. */
	static void settleOnJoin(ServerPlayer p) {
		p.removeAttached(Thirst.FORM);
		setSize(p, 0.0);
		if (p.hasAttached(Thirst.FLIGHT)) {
			revokeFlight(p);
		}
	}

	private static boolean grantFlight(ServerPlayer p, float speed) {
		Abilities abilities = p.getAbilities();
		if (abilities.mayfly) {
			return false;
		}
		abilities.mayfly = true;
		abilities.flying = true;
		abilities.setFlyingSpeed(speed);
		p.setAttached(Thirst.FLIGHT, true);
		p.onUpdateAbilities();
		return true;
	}

	private static void revokeFlight(ServerPlayer p) {
		p.removeAttached(Thirst.FLIGHT);
		Abilities abilities = p.getAbilities();
		if (!p.isCreative() && !p.isSpectator()) {
			abilities.mayfly = false;
			abilities.flying = false;
		}
		abilities.setFlyingSpeed(0.05F);
		p.onUpdateAbilities();
	}

	/** Sets their size (added to the scale), or takes it away at 0. */
	private static void setSize(ServerPlayer p, double amount) {
		AttributeInstance scale = p.getAttribute(Attributes.SCALE);
		if (scale == null) {
			return;
		}
		AttributeModifier old = scale.getModifier(SIZE_ID);
		if (old != null && old.amount() == amount) {
			return;
		}
		scale.removeModifier(SIZE_ID);
		if (amount != 0.0) {
			scale.addTransientModifier(new AttributeModifier(SIZE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private static void setPuppetScale(Mob puppet, double scale) {
		AttributeInstance a = puppet.getAttribute(Attributes.SCALE);
		if (a != null && a.getBaseValue() != scale) {
			a.setBaseValue(scale);
		}
	}

	private static <T extends Mob> @Nullable T spawnPuppet(ServerLevel level, ServerPlayer owner, EntityType<T> type) {
		T mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		PUPPETS.put(mob.getUUID(), owner.getUUID());
		mob.addTag(PUPPET_TAG);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.setSilent(true);
		mob.setPermanentlyInvulnerable(true);
		mob.setPersistenceRequired();
		mob.snapTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
		level.addFreshEntity(mob);
		return mob;
	}

	private static void follow(ServerPlayer p, Mob puppet) {
		puppet.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
		puppet.setYBodyRot(p.yBodyRot);
		puppet.setYHeadRot(p.getYHeadRot());
	}

	// ---- the tick ----

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean sneaking = p.isShiftKeyDown() && Thirst.thirsty(p);
			if (sneaking ? SNEAKING.add(p.getUUID()) : SNEAKING.remove(p.getUUID())) {
				if (sneaking) {
					PRESSED.put(p.getUUID(), now);
				}
			}
		}
		for (Map.Entry<UUID, Active> e : new ArrayList<>(ACTIVE.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Active a = e.getValue();
			if (p == null) {
				ACTIVE.remove(e.getKey());
				if (a.puppet != null) {
					PUPPETS.remove(a.puppet.getUUID());
					a.puppet.discard();
				}
				continue;
			}
			if (!p.isAlive() || p.isSpectator() || !Thirst.thirsty(p) || a.puppet != null && a.puppet.level() != p.level()) {
				end(p, false);
				continue;
			}
			long age = p.level().getGameTime() - a.start;
			if (p.level().getGameTime() >= a.end) {
				end(p, true);
				continue;
			}
			if (a.puppet != null) {
				follow(p, a.puppet);
			}
			tickForm(p, a, age);
		}
		if (!SWARMS.isEmpty() && server.overworld() != null) {
			tickSwarms(server);
		}
	}

	private static void tickForm(ServerPlayer p, Active a, long age) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		switch (a.form) {
			case BAT -> {
				if (age > 0 && age % BAT_DRAIN_EVERY == 0) {
					Thirst.spend(p, 1);
					if (Thirst.gauge(p) <= 0) {
						end(p, false);
					}
				}
			}
			case MIST -> {
				if (age % 2 == 0) {
					level.sendParticles(Fx.miasma(0x7A4A5A, 1.3F), p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), 5, 0.35, 0.5, 0.35, 0.01);
				}
			}
			case WOLF -> {
				if (age <= SWELL_TICKS) {
					double part = Math.min(1.0, age / (double)SWELL_TICKS);
					setSize(p, SWELL * part);
					if (a.puppet != null) {
						setPuppetScale(a.puppet, 1.0 + SWELL * part);
					}
				}
				if (age % 20 == 0) {
					p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, GIFT_TICKS, WOLF_STRENGTH, true, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.SPEED, GIFT_TICKS, WOLF_SPEED, true, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, GIFT_TICKS, WOLF_RESIST, true, false, false));
				}
			}
			default -> {
			}
		}
	}

	// ---- hurt and unhurt ----

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity.entityTags().contains(PUPPET_TAG)) {
			UUID owner = PUPPETS.get(entity.getUUID());
			ServerPlayer p = owner == null || entity.level().getServer() == null ? null : entity.level().getServer().getPlayerList().getPlayer(owner);
			Active a = owner == null ? null : ACTIVE.get(owner);
			if (p != null && a != null && a.puppet == entity && source.getEntity() != p && p.level() instanceof ServerLevel level) {
				p.hurtServer(level, source, amount);
			}
			return false;
		}
		if (source.getEntity() instanceof ServerPlayer striker && mist(striker)) {
			return false;
		}
		if (!(entity instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) {
			return true;
		}
		if (mist(p) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return !riposte(p, level, source, amount);
	}

	// ---- the riposte ----

	/** A blow on someone who crouched a moment ago finds only bats. True if it was dodged. */
	private static boolean riposte(ServerPlayer p, ServerLevel level, DamageSource source, float amount) {
		if (amount <= 0.0F || !Thirst.thirsty(p) || ACTIVE.containsKey(p.getUUID()) || !(source.getDirectEntity() instanceof LivingEntity striker) || source.getEntity() != striker
			|| striker == p || p.isBlocking() || p.isSpectator() || p.isCreative() || Skills.rank(p, "slip_behind") > 0) {
			return false;
		}
		long now = level.getGameTime();
		Long pressed = PRESSED.get(p.getUUID());
		if (pressed == null || now - pressed > RIPOSTE_WINDOW || !ready(p, "riposte") || !canAfford(p, level, RIPOSTE_COST) || p.distanceTo(striker) > RIPOSTE_REACH) {
			return false;
		}
		Vec3 start = p.position();
		Vec3 away = start.subtract(striker.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? p.getLookAngle().multiply(-1.0, 0.0, -1.0) : away;
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		Vec3 dest = null;
		for (double step : RIPOSTE_STEPS) {
			Vec3 candidate = start.add(away.scale(step));
			if (level.noCollision(p, p.getBoundingBox().move(candidate.subtract(start)))) {
				dest = candidate;
				break;
			}
		}
		if (dest == null) {
			return false;
		}
		Thirst.spend(p, cost(level, RIPOSTE_COST));
		READY.put(key(p, "riposte"), now + RIPOSTE_COOLDOWN);
		PRESSED.remove(p.getUUID());
		burst(level, p, start.add(0.0, 1.0, 0.0));
		float yaw = (float)(Mth.atan2(striker.getZ() - dest.z, striker.getX() - dest.x) * 57.2957763671875) - 90.0F;
		p.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), Mth.wrapDegrees(yaw), 0.0F, false);
		level.sendParticles(Fx.miasma(0x2A0A12, 1.1F), dest.x, dest.y + 1.0, dest.z, 10, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.0F, 1.1F);
		p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.riposte").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		return true;
	}

	// ---- bats ----

	private static @Nullable Bat swarmBat(ServerLevel level, ServerPlayer owner, Vec3 at) {
		Bat bat = EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
		if (bat == null) {
			return null;
		}
		PUPPETS.put(bat.getUUID(), owner.getUUID());
		bat.addTag(PUPPET_TAG);
		bat.setNoAi(true);
		bat.setNoGravity(true);
		bat.setSilent(true);
		bat.setPermanentlyInvulnerable(true);
		bat.setPersistenceRequired();
		bat.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		level.addFreshEntity(bat);
		bat.setResting(false);
		return bat;
	}

	/** A burst of bats from {@code at}: they fly out and are gone. */
	private static void burst(ServerLevel level, ServerPlayer owner, Vec3 at) {
		Swarm swarm = new Swarm(owner.getUUID(), -1, level.getGameTime(), at);
		for (int i = 0; i < RIPOSTE_BATS; i++) {
			Bat bat = swarmBat(level, owner, at);
			if (bat != null) {
				swarm.bats.add(bat);
				double angle = i * (Math.PI * 2.0 / RIPOSTE_BATS) + owner.getRandom().nextDouble() * 0.5;
				swarm.ways.add(new Vec3(Math.cos(angle), 0.25 + owner.getRandom().nextDouble() * 0.4, Math.sin(angle)));
			}
		}
		SWARMS.add(swarm);
		Thirst.bloodMist(level, at, 0.7F, 10);
		level.sendParticles(Fx.miasma(0x2A0A12, 1.2F), at.x, at.y, at.z, 20, 0.5, 0.6, 0.5, 0.03);
	}

	/** The foe in sight a colony could lift: the nearest living thing near the line of sight. */
	private static @Nullable LivingEntity foe(ServerPlayer p, ServerLevel level) {
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(COLONY_RANGE),
			e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !BossBars.boss(e) && !PUPPETS.containsKey(e.getUUID())
				&& e.distanceToSqr(p) <= COLONY_RANGE * COLONY_RANGE)) {
			if (e instanceof Player other && (!level.isPvpAllowed() || other.isCreative() || !(other instanceof ServerPlayer sp) || Parties.allied(p, sp))) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(p.getEyePosition());
			if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) < COLONY_AIM || !p.hasLineOfSight(e)) {
				continue;
			}
			double d = to.lengthSqr();
			if (d < bestDistance) {
				best = e;
				bestDistance = d;
			}
		}
		return best;
	}

	private static void colony(ServerPlayer p, ServerLevel level, LivingEntity foe) {
		Vec3 from = p.position().add(0.0, p.getBbHeight() * 0.6, 0.0);
		Swarm swarm = new Swarm(p.getUUID(), foe.getId(), level.getGameTime(), from);
		for (int i = 0; i < COLONY_BATS; i++) {
			Bat bat = swarmBat(level, p, from);
			if (bat != null) {
				swarm.bats.add(bat);
			}
		}
		SWARMS.add(swarm);
		level.playSound(null, p.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.sendParticles(Fx.miasma(0x2A0A12, 1.2F), from.x, from.y, from.z, 20, 0.5, 0.5, 0.5, 0.03);
	}

	private static void tickSwarms(MinecraftServer server) {
		for (Swarm s : new ArrayList<>(SWARMS)) {
			ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
			if (owner == null || !(owner.level() instanceof ServerLevel level)) {
				clear(s);
				continue;
			}
			long age = level.getGameTime() - s.start;
			if (s.foe < 0) {
				if (age >= BURST_TICKS) {
					clear(s);
					continue;
				}
				for (int i = 0; i < s.bats.size(); i++) {
					Vec3 way = s.ways.get(i);
					Vec3 at = s.from.add(way.x * age * 0.45, way.y * age * 0.3 + Math.sin(age * 0.6 + i) * 0.2, way.z * age * 0.45);
					place(s.bats.get(i), at, (float)(Mth.atan2(way.z, way.x) * 57.2957763671875) - 90.0F);
				}
				continue;
			}
			if (!(level.getEntity(s.foe) instanceof LivingEntity foe) || !foe.isAlive() || age >= COLONY_END) {
				clear(s);
				continue;
			}
			if (age < LIFT_TICKS) {
				Vec3 v = foe.getDeltaMovement();
				foe.setDeltaMovement(v.x * 0.5, LIFT_SPEED, v.z * 0.5);
				foe.syncVelocity = true;
				foe.fallDistance = 0.0;
				if (foe instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			} else if (age == LIFT_TICKS) {
				foe.setDeltaMovement(0.0, -0.5, 0.0);
				foe.syncVelocity = true;
				level.playSound(null, foe.blockPosition(), SoundEvents.BAT_LOOP, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			Vec3 centre = foe.position().add(0.0, foe.getBbHeight() * 0.5, 0.0);
			double blend = Math.min(1.0, age / 8.0);
			double spread = age > LIFT_TICKS ? 1.0 + (age - LIFT_TICKS) * 0.12 : 1.0;
			for (int i = 0; i < s.bats.size(); i++) {
				double angle = age * 0.45 + i * (Math.PI * 2.0 / s.bats.size());
				double r = (1.1 + 0.2 * Math.sin(age * 0.3 + i)) * spread;
				Vec3 orbit = centre.add(Math.cos(angle) * r, 0.6 * Math.sin(age * 0.2 + i), Math.sin(angle) * r);
				Vec3 at = s.from.add(orbit.subtract(s.from).scale(blend));
				place(s.bats.get(i), at, (float)(angle * 57.2957763671875) + 90.0F);
			}
		}
	}

	private static void place(Bat bat, Vec3 at, float yaw) {
		if (bat.isAlive()) {
			bat.snapTo(at.x, at.y, at.z, yaw, 0.0F);
			bat.setYHeadRot(yaw);
		}
	}

	private static void clear(Swarm s) {
		SWARMS.remove(s);
		for (Bat bat : s.bats) {
			PUPPETS.remove(bat.getUUID());
			if (bat.level() instanceof ServerLevel level && bat.isAlive()) {
				level.sendParticles(Fx.miasma(0x2A0A12, 0.8F), bat.getX(), bat.getY(), bat.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
			}
			bat.discard();
		}
	}
}
