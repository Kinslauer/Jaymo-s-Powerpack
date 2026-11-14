package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.SpiritVoice;
import com.jaymoskingdoms.spirits.Spirits;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Reaper. Andy (2026-11-14): "anyone attuned to death magic or warlocks now attune to the same magic as the grue, meaning
 * they gain an evil mana spirit like entity called the reaper. it grows in stages, eventually resembling the grue's wing arms
 * or a wispy cloak of shadowflame and smoke ... death magicians get stronger the more corpses are nearby. the same for them
 * all, unlike the 5 spirits, and is immune to the grue's corruption as they are one in the same." Then: "the red eyed mantle
 * should appear starting at level 3, and it should be visible for ANYONE who is near death, not the attuned, because this
 * isnt ki. its death itself", it "appears like the mana spirits, whenever a ghost is nearby, and is animated harvesting it
 * with a scythe", looking like Minecraft Comes Alive's Grim Reaper, and "Warlocks make pacts with the patrons, and the reaper
 * is now one you can pact with instead".
 * <ul>
 * <li><b>Who:</b> anyone but a Death Sorcerer who has learned a forbidden death spell, and a Warlock whose pact is with the
 * Reaper ({@link #called}); never a keeper of one of the five, and its keeper is never answered by one.</li>
 * <li><b>The Toll</b> (0 to {@link #MAX_TOLL}) is paid by the dead: kills, bodies raised, the downed reaped, corpses and
 * ghosts harvested. It grows through five stages ({@link #stage}): the Pall, the Mantle, the Reach (the hooded figure, and
 * its moves), the Wing-arms, the Shroud.</li>
 * <li><b>The dead near by</b> ({@link #DEAD_RANGE}, counted to {@link #DEAD_MOST}) make its keeper's magic hit harder and
 * cost less ({@link #dealt}, {@link #costTimes}).</li>
 * <li><b>The hunger:</b> {@link #HUNGER_DAYS} days with no death near it and it feeds on its keeper, a heart a day to
 * {@link #HUNGER_HEARTS}, back when it is fed.</li>
 * <li><b>The Grue</b> never reaches for its keeper, and their forbidden casts leave no corruption ({@link ForbiddenArts}).</li>
 * </ul>
 * It never speaks. Nobody sees it but a viewer who is near death themselves, or anyone while it harvests a ghost (the client's
 * {@code ReaperClient}, from the synced {@link #REAPER} state alone).
 */
public final class Reaper {
	/** The most Toll there is, and the Toll each stage (1 to 5) begins at. */
	public static final int MAX_TOLL = 100;
	static final int[] STAGE_AT = {0, 20, 40, 60, 80};
	/** The stages things begin at: its own acts, the hooded figure and its moves, the wing-arms, the shroud. */
	public static final int ACT_STAGE = 2, FIGURE_STAGE = 3, WINGS_STAGE = 4, SHROUD_STAGE = 5;
	/** Toll for a kill, a body raised, one of the downed reaped, a corpse harvested, a ghost harvested. */
	static final int TOLL_KILL = 1, TOLL_RAISE = 2, TOLL_REAP = 2, TOLL_CORPSE = 1, TOLL_GHOST = 3;
	/** How far the dead are counted (blocks) and the most that count; what each adds to damage and takes off a cost. */
	public static final double DEAD_RANGE = 12.0;
	public static final int DEAD_MOST = 8;
	static final float DEAD_DAMAGE = 0.05F, DEAD_COST = 0.03F;
	/** Without Jaymo's Kitchen's corpses a death counts at its spot for this long (ticks). */
	static final int DEATH_COUNTS = 20 * 60;
	/** Health share (or less) at which someone is near death, here and on the client. */
	public static final float NEAR_DEATH = 0.3F;
	/** Days unfed before it feeds on its keeper, the most hearts it takes (one a day). */
	static final int HUNGER_DAYS = 3, HUNGER_HEARTS = 3;
	static final Identifier HUNGER_ID = JaymosKingdoms.id("reaper_hunger");
	/** The chill at death's door: how often (ticks), how far, how long. */
	static final int CHILL_EVERY = 100, CHILL_TICKS = 100;
	static final double CHILL_RANGE = 6.0;
	/** A ghost within this far is harvested; the harvest's length (ticks); what a ghostly monster takes from each. */
	static final double GHOST_RANGE = 10.0;
	public static final int HARVEST_TICKS = 50;
	static final float GHOST_DAMAGE = 10.0F;
	/** Its moves: how far the hand reaches, the pull's speed and hurt, the sweep's reach and hurt, mana from a harvested corpse. */
	static final double REACH = 12.0, PULL = 1.1, SWEEP_REACH = 3.5, REAPING_REACH = 6.0;
	static final float PULL_DAMAGE = 2.0F, SWEEP_DAMAGE = 6.0F, HARVEST_MANA = 12.0F;
	/** Cooldowns (ticks): Grave Hand, Reap, Harvest, the Reaping, Death's Company, the wing guard. */
	static final int HAND_COOLDOWN = 20 * 8, SWEEP_COOLDOWN = 20 * 4, HARVEST_COOLDOWN = 20 * 5, REAPING_COOLDOWN = 20 * 10, COMPANY_COOLDOWN = 20 * 180,
		GUARD_COOLDOWN = 20 * 30;
	/** The wing guard stops a blow at least this hard; the shroud burns whoever strikes from within this far, for this long. */
	static final float GUARD_FROM = 4.0F;
	static final double SHROUD_RANGE = 4.0;
	static final int SHROUD_TICKS = 80;
	/** What a body the sweep kills rises for (ticks), and the most Death's Company raises at once. */
	static final int RISEN_LIFE = 20 * 60, COMPANY_MOST = 8;
	/** The grave-captain: twice a thrall's health, and it does not crumble with time. */
	static final String CAPTAIN_TAG = "jaymoskingdoms.reaper_captain";
	static final int CAPTAIN_LIFE = Integer.MAX_VALUE / 2;
	/** The five's remark on a Reaper near by: how far, and how seldom (ticks). */
	static final double REMARK_RANGE = 16.0;
	static final int REMARK_EVERY = 20 * 60 * 10;
	/** A spirit bonded this far halves a blight laid on its keeper. */
	static final int WARD_BOND = 60;
	/** The Grue's red (its hints, its bursts). */
	public static final int RED = 0x7A0010, SMOKE = 0x16101A;

	/**
	 * What its keeper carries: the {@code toll}; the day it was last {@code fed} and the day it last {@code refused} a death; the
	 * {@code dead} near by now; when its harvest began ({@code harvestAt}, game time, 0 for none) and of what ({@code harvestId},
	 * an entity id); when it last struck ({@code actAt}: the wings open).
	 */
	public record State(int toll, long fed, long refused, int dead, long harvestAt, int harvestId, long actAt) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("toll", 0).forGetter(State::toll),
				Codec.LONG.optionalFieldOf("fed", 0L).forGetter(State::fed),
				Codec.LONG.optionalFieldOf("refused", -1L).forGetter(State::refused))
			.apply(i, (toll, fed, refused) -> new State(toll, fed, refused, 0, 0L, 0, 0L)));
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public State decode(ByteBuf buf) {
				return new State(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf) - 1L,
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
			}

			@Override
			public void encode(ByteBuf buf, State s) {
				ByteBufCodecs.VAR_INT.encode(buf, s.toll);
				ByteBufCodecs.VAR_LONG.encode(buf, s.fed);
				ByteBufCodecs.VAR_LONG.encode(buf, s.refused + 1L);
				ByteBufCodecs.VAR_INT.encode(buf, s.dead);
				ByteBufCodecs.VAR_LONG.encode(buf, s.harvestAt);
				ByteBufCodecs.VAR_INT.encode(buf, s.harvestId);
				ByteBufCodecs.VAR_LONG.encode(buf, s.actAt);
			}
		};

		State withToll(int toll, long day) {
			return new State(toll, day, this.refused, this.dead, this.harvestAt, this.harvestId, this.actAt);
		}

		State withDead(int dead, long fed) {
			return new State(this.toll, fed, this.refused, dead, this.harvestAt, this.harvestId, this.actAt);
		}

		State harvesting(long at, int id) {
			return new State(this.toll, this.fed, this.refused, this.dead, at, id, this.actAt);
		}

		State acted(long at) {
			return new State(this.toll, this.fed, this.refused, this.dead, this.harvestAt, this.harvestId, at);
		}

		State refusedOn(long day) {
			return new State(this.toll, this.fed, day, this.dead, this.harvestAt, this.harvestId, this.actAt);
		}
	}

	/** The Reaper a being carries: saved, kept through death, synced to everyone (anyone near death may see it). */
	public static final AttachmentType<State> REAPER = AttachmentRegistry.create(JaymosKingdoms.id("reaper"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** A recent death, where there are no corpses to count. */
	private record Death(ServerLevel level, Vec3 at, long time) {
	}

	/** A body the sweep killed, to rise for its killer once its corpse has been laid down. */
	private record Rising(ServerLevel level, Vec3 at, EntityType<?> type, UUID master, long due) {
	}

	private static final List<Death> DEATHS = new ArrayList<>();
	private static final List<Rising> RISING = new ArrayList<>();
	/** When each keeper's moves are ready again (game time), by move. */
	private static final Map<UUID, Map<String, Long>> READY = new HashMap<>();
	private static final Map<UUID, Long> REMARKED = new HashMap<>();

	private Reaper() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Reaper::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DEATHS.clear();
			RISING.clear();
			READY.clear();
			REMARKED.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (!KitchenCompat.loaded() && !(entity instanceof Player)) {
				DEATHS.add(new Death(level, entity.position(), level.getGameTime()));
			}
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity && has(killer)) {
				feed(killer, TOLL_KILL);
				Warlocks.killed(killer);
			}
		});
		// from the Mantle on, once a day, it refuses a killing blow: its keeper is left on half a heart
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}
			State state = player.getAttached(REAPER);
			long today = Production.day(player.level().getServer());
			if (state == null || stage(state) < ACT_STAGE || state.refused() == today) {
				return true;
			}
			player.setAttached(REAPER, state.refusedOn(today));
			player.setHealth(1.0F);
			act(player, "refuse");
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.5F);
			return false;
		});
	}

	// ---- who -----------------------------------------------------------------------------------------------------------

	/** Whether {@code entity} carries the Reaper. */
	public static boolean has(@Nullable Entity entity) {
		return entity != null && entity.getAttached(REAPER) != null;
	}

	/** The stage (1 to 5) of a Reaper with this much {@code toll}. */
	public static int stage(int toll) {
		int stage = 1;
		for (int i = 1; i < STAGE_AT.length; i++) {
			if (toll >= STAGE_AT[i]) {
				stage = i + 1;
			}
		}
		return stage;
	}

	public static int stage(State state) {
		return stage(state.toll());
	}

	/** The stage of {@code entity}'s Reaper, or 0 for none. */
	public static int stageOf(@Nullable Entity entity) {
		State state = entity == null ? null : entity.getAttached(REAPER);
		return state == null ? 0 : stage(state);
	}

	/** Whether the Reaper stands behind {@code player}: they learned death magic outside the Death Sorcerer's element, or their pact is with it, and none of the five keeps them. */
	static boolean called(Player player) {
		if (Spirits.state(player) != null) {
			return false;
		}
		if (Necromancy.necromancer(player)) {
			// (a Death Sorcerer's magic is their own element, not the Reaper's)
			return false;
		}
		if (Classes.is(player, PlayerClass.WARLOCK)) {
			// (a Warlock's magic is their patron's: only the pact with the Reaper itself brings it)
			return pact(player);
		}
		for (String spell : Classes.of(player).spells()) {
			if (ForbiddenArts.death(spell) && !spell.equals("raising") && !spell.equals("draining")) {
				return true;
			}
		}
		return false;
	}

	/** A Warlock whose pact is with the Reaper. */
	static boolean pact(Player player) {
		return "reaper".equals(Classes.optionOf(player, PlayerClass.WARLOCK));
	}

	// ---- the dead near by ----------------------------------------------------------------------------------------------

	/** The dead within {@link #DEAD_RANGE} of {@code player}: corpses (never a player's), or recent deaths without Jaymo's Kitchen. */
	private static int dead(ServerPlayer player, ServerLevel level) {
		int n = 0;
		if (KitchenCompat.loaded()) {
			n = level.getEntities((Entity)null, player.getBoundingBox().inflate(DEAD_RANGE),
				e -> KitchenCompat.isFreeCorpse(e) && !KitchenCompat.isPlayerCorpse(e) && e.distanceToSqr(player) <= DEAD_RANGE * DEAD_RANGE).size();
		} else {
			for (Death d : DEATHS) {
				if (d.level() == level && d.at().distanceToSqr(player.position()) <= DEAD_RANGE * DEAD_RANGE) {
					n++;
				}
			}
		}
		return Math.min(n, DEAD_MOST);
	}

	/** What a cast costs a Reaper's keeper, as a share: less for each of the dead near by ({@link Mana#price}). */
	static float costTimes(Player player) {
		State state = player.getAttached(REAPER);
		return state == null ? 1.0F : 1.0F - DEAD_COST * state.dead();
	}

	/**
	 * The blow {@code source} lands on {@code victim} ({@code LivingEntityHurtMixin}): a keeper's magic hits harder for each of
	 * the dead near by; from the Wing-arms on, the wings stop a hard blow on their keeper now and then; and the Shroud burns
	 * whoever struck them from close by.
	 */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		if (source.getEntity() instanceof Player caster && caster != victim
			&& (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER))) {
			State state = caster.getAttached(REAPER);
			if (state != null) {
				amount *= 1.0F + DEAD_DAMAGE * state.dead();
			}
		}
		if (victim instanceof ServerPlayer keeper && source.getEntity() instanceof LivingEntity attacker && attacker != keeper) {
			State state = keeper.getAttached(REAPER);
			if (state != null && stage(state) >= SHROUD_STAGE && attacker.distanceToSqr(keeper) <= SHROUD_RANGE * SHROUD_RANGE) {
				attacker.setRemainingFireTicks(SHROUD_TICKS);
				attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, SHROUD_TICKS, 1), keeper);
			}
			if (state != null && stage(state) >= WINGS_STAGE && amount >= GUARD_FROM && ready(keeper, "guard", GUARD_COOLDOWN)) {
				strike(keeper, state);
				act(keeper, "guard");
				keeper.level().playSound(null, keeper.getX(), keeper.getY(), keeper.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2F, 0.5F);
				return 0.0F;
			}
		}
		return amount;
	}

	/** From the Shroud on, the dead take its keeper for one of their own, unless that keeper struck them ({@code ThrallTargetMixin}). */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		return attacker.is(EntityTypeTags.UNDEAD) && target instanceof Player && stageOf(target) >= SHROUD_STAGE
			&& attacker.getLastHurtByMob() != target;
	}

	/** A blight on {@code entity} lasts half as long if a spirit bonded {@link #WARD_BOND} or more keeps them ({@link Necromancy#blight}). */
	static int warded(LivingEntity entity, int ticks) {
		SpiritState spirit = Spirits.state(entity);
		return spirit != null && spirit.bond() >= WARD_BOND ? ticks / 2 : ticks;
	}

	// ---- the toll ------------------------------------------------------------------------------------------------------

	/** The dead have paid {@code toll} to {@code player}'s Reaper: it grows, and it is fed. */
	static void feed(ServerPlayer player, int toll) {
		State state = player.getAttached(REAPER);
		if (state == null) {
			return;
		}
		int to = Math.min(MAX_TOLL, state.toll() + toll);
		int before = stage(state), after = stage(to);
		player.setAttached(REAPER, state.withToll(to, Production.day(player.level().getServer())));
		if (after > before) {
			// (a hint, never a telling: what it is stays unsaid)
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.reaper.stage." + after).withStyle(s -> s.withColor(RED).withItalic(true)));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.6F);
			act(player, null);
		}
	}

	/** Every act of its own touches its keeper's screen (the edges flash red), with a word above the hotbar if there is one. */
	private static void act(ServerPlayer player, @Nullable String what) {
		player.setAttached(Spirits.FLASH, player.level().getGameTime() * 8L + Spirits.FLASH_WARN);
		if (what != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.reaper." + what).withStyle(s -> s.withColor(RED)));
		}
	}

	/** Its wings open: it struck. */
	private static void strike(ServerPlayer player, State state) {
		player.setAttached(REAPER, state.acted(player.level().getGameTime()));
	}

	/** Whether {@code move} is ready for {@code player}; if it is, its cooldown starts. */
	private static boolean ready(ServerPlayer player, String move, int cooldown) {
		long now = player.level().getGameTime();
		Map<String, Long> ready = READY.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		if (now < ready.getOrDefault(move, 0L)) {
			return false;
		}
		ready.put(move, now + cooldown);
		return true;
	}

	private static long wait(ServerPlayer player, String move) {
		Map<String, Long> ready = READY.get(player.getUUID());
		return ready == null ? 0L : Math.max(0L, ready.getOrDefault(move, 0L) - player.level().getGameTime());
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (!DEATHS.isEmpty() && tick % 20L == 0L) {
			DEATHS.removeIf(d -> d.level().getGameTime() - d.time() > DEATH_COUNTS);
		}
		for (Iterator<Rising> it = RISING.iterator(); it.hasNext();) {
			Rising r = it.next();
			if (r.level().getGameTime() >= r.due()) {
				it.remove();
				rise(r);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long beat = tick + player.getId();
			if (beat % 100L == 0L) {
				call(player);
			}
			State state = player.getAttached(REAPER);
			if (state == null) {
				continue;
			}
			long now = level.getGameTime();
			if (state.harvestAt() > 0L && now >= state.harvestAt() + HARVEST_TICKS) {
				harvested(player, level, state);
				state = player.getAttached(REAPER);
			}
			if (beat % 20L == 0L) {
				second(player, level, state, now, beat);
			}
			// from the Wing-arms on, a keeper who crouches as they fall is let down gently
			if (stage(state) >= WINGS_STAGE && player.isShiftKeyDown() && !player.onGround() && player.getDeltaMovement().y < -0.5) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false, false));
			}
		}
	}

	/** It comes to those it is called by, and leaves those it no longer is. */
	private static void call(ServerPlayer player) {
		boolean called = called(player);
		if (called && !has(player)) {
			player.setAttached(REAPER, new State(0, Production.day(player.level().getServer()), -1L, 0, 0L, 0, 0L));
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.reaper.stage.1").withStyle(s -> s.withColor(RED).withItalic(true)));
		} else if (!called && has(player)) {
			player.removeAttached(REAPER);
			unhunger(player);
			READY.remove(player.getUUID());
		}
	}

	/** Once a second: the dead counted, the hunger, the chill, a ghost to harvest, the five's unease. */
	private static void second(ServerPlayer player, ServerLevel level, State state, long now, long beat) {
		long today = Production.day(level.getServer());
		int dead = dead(player, level);
		if (dead != state.dead() || dead > 0 && state.fed() != today) {
			state = state.withDead(dead, dead > 0 ? today : state.fed());
			player.setAttached(REAPER, state);
		}
		hunger(player, state, today);
		int stage = stage(state);
		if (stage >= ACT_STAGE && beat % CHILL_EVERY == 0L && player.getHealth() <= player.getMaxHealth() * NEAR_DEATH) {
			chill(player, level);
		}
		if (state.harvestAt() == 0L && beat % 40L == 0L) {
			Entity ghost = ghost(player, level);
			if (ghost != null) {
				player.setAttached(REAPER, state.harvesting(now, ghost.getId()));
				level.playSound(null, ghost.getX(), ghost.getY(), ghost.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
				act(player, null);
			}
		}
		if (beat % 200L == 0L) {
			remark(player, level, now);
		}
	}

	private static void hunger(ServerPlayer player, State state, long today) {
		var health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null || player.isCreative()) {
			return;
		}
		int hearts = (int)Math.clamp(today - state.fed() - HUNGER_DAYS + 1L, 0L, (long)HUNGER_HEARTS);
		AttributeModifier has = health.getModifier(HUNGER_ID);
		double want = -2.0 * hearts;
		if (hearts == 0) {
			if (has != null) {
				unhunger(player);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.reaper.fed").withStyle(s -> s.withColor(RED)));
			}
		} else if (has == null || has.amount() != want) {
			health.removeModifier(HUNGER_ID);
			health.addPermanentModifier(new AttributeModifier(HUNGER_ID, want, AttributeModifier.Operation.ADD_VALUE));
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			act(player, "hungers");
		}
	}

	private static void unhunger(ServerPlayer player) {
		var health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(HUNGER_ID);
		}
	}

	/** At death's door the cold comes off it: whatever is hunting its keeper slows and weakens. */
	private static void chill(ServerPlayer player, ServerLevel level) {
		boolean any = false;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CHILL_RANGE), m -> m.isAlive() && m.getTarget() == player)) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, 1), player);
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CHILL_TICKS, 0), player);
			any = true;
		}
		if (any) {
			level.sendParticles(Fx.miasma(SMOKE, 1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 30, CHILL_RANGE / 3.0, 0.6, CHILL_RANGE / 3.0, 0.01);
			act(player, null);
		}
	}

	/** Each of the five, out and within {@link #REMARK_RANGE} of a Reaper, says what it thinks of it (seldom). */
	private static void remark(ServerPlayer keeper, ServerLevel level, long now) {
		for (ServerPlayer other : level.getPlayers(p -> p != keeper && p.distanceToSqr(keeper) <= REMARK_RANGE * REMARK_RANGE)) {
			SpiritState spirit = Spirits.state(other);
			if (spirit != null && spirit.out() && now >= REMARKED.getOrDefault(other.getUUID(), 0L) && SpiritVoice.say(other, "grue.reaper")) {
				REMARKED.put(other.getUUID(), now + REMARK_EVERY);
			}
		}
	}

	// ---- the harvest ---------------------------------------------------------------------------------------------------

	/** A ghost for {@code player}'s Reaper to harvest: one at a grave, or a ghostly monster. A player out of their body is left alone. */
	private static @Nullable Entity ghost(ServerPlayer player, ServerLevel level) {
		Entity best = null;
		double bestD = GHOST_RANGE * GHOST_RANGE;
		for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(GHOST_RANGE),
			g -> g.isAlive() && (g instanceof GraveGhost || g.is(DamageKinds.GHOSTLY)))) {
			double d = e.distanceToSqr(player);
			if (d <= bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** The scythe has come down: a grave's ghost is gone for good, a ghostly monster is cut; the Reaper is paid. */
	private static void harvested(ServerPlayer player, ServerLevel level, State state) {
		Entity ghost = level.getEntity(state.harvestId());
		player.setAttached(REAPER, state.harvesting(0L, 0));
		if (ghost == null || !ghost.isAlive() || ghost.distanceToSqr(player) > GHOST_RANGE * GHOST_RANGE * 4.0) {
			return;
		}
		level.sendParticles(ParticleTypes.SOUL, ghost.getX(), ghost.getY() + 1.0, ghost.getZ(), 24, 0.3, 0.7, 0.3, 0.05);
		level.sendParticles(Fx.miasma(SMOKE, 1.6F), ghost.getX(), ghost.getY() + 1.0, ghost.getZ(), 20, 0.3, 0.7, 0.3, 0.01);
		level.playSound(null, ghost.getX(), ghost.getY(), ghost.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F);
		if (ghost instanceof GraveGhost grave) {
			grave.reaped(level);
			Mana.give(player, HARVEST_MANA);
			feed(player, TOLL_GHOST);
		} else if (ghost instanceof LivingEntity living) {
			living.hurtServer(level, level.damageSources().indirectMagic(player, player), GHOST_DAMAGE);
			if (!living.isAlive()) {
				feed(player, TOLL_GHOST - TOLL_KILL);
			}
		}
	}

	// ---- its moves (the spirit key) ------------------------------------------------------------------------------------

	/**
	 * The spirit key, for a keeper of the Reaper ({@code Spirits.key}): at the downed, the Reaping; at the living, the Grave Hand
	 * drags them in; at a corpse, it drags that; at nothing, Reap sweeps what stands before you. With sneak: Harvest drinks the
	 * corpse you look at, and at the Shroud, with no corpse in sight, Death's Company raises every body in reach.
	 */
	public static void key(ServerPlayer player, boolean sneak) {
		State state = player.getAttached(REAPER);
		if (state == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		int stage = stage(state);
		if (stage < FIGURE_STAGE) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.reaper.no_hands").withStyle(s -> s.withColor(RED)));
			return;
		}
		Entity corpse = KitchenCompat.loaded() ? inSight(player, level, REACH, KitchenCompat::isRaisableCorpse) : null;
		if (sneak) {
			if (corpse != null) {
				if (cooling(player, "harvest", HARVEST_COOLDOWN)) {
					return;
				}
				level.sendParticles(ParticleTypes.SOUL, corpse.getX(), corpse.getY() + 0.3, corpse.getZ(), 12, 0.3, 0.2, 0.3, 0.04);
				level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
				corpse.discard();
				Mana.give(player, HARVEST_MANA);
				strike(player, state);
				feed(player, TOLL_CORPSE);
			} else if (stage >= SHROUD_STAGE) {
				company(player, level, state);
			}
			return;
		}
		Entity seen = inSight(player, level, REACH, e -> e instanceof LivingEntity l && l.isAlive() && !Thralls.isThrall(l) && !(e instanceof GraveGhost));
		if (seen instanceof LivingEntity target) {
			if (Downed.isDowned(target) && player.distanceTo(target) <= REAPING_REACH) {
				if (cooling(player, "reaping", REAPING_COOLDOWN)) {
					return;
				}
				// the Reaping: its execution
				strike(player, state);
				level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.5), target.getZ(), 16, 0.3, 0.4, 0.3, 0.04);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F);
				if (!Necromancy.execute(level, target, player)) {
					target.hurtServer(level, level.damageSources().indirectMagic(player, player), Float.MAX_VALUE);
				}
				feed(player, TOLL_REAP);
				return;
			}
			if (cooling(player, "hand", HAND_COOLDOWN)) {
				return;
			}
			strike(player, state);
			Vec3 pull = player.position().subtract(target.position());
			double far = pull.length();
			if (far > 2.0) {
				Vec3 v = pull.normalize().scale(Math.min(PULL, far * 0.25));
				target.setDeltaMovement(v.x, Math.max(0.3, v.y + 0.25), v.z);
				target.needsSync = true;
			}
			target.hurtServer(level, level.damageSources().indirectMagic(player, player), PULL_DAMAGE);
			level.sendParticles(Fx.miasma(SMOKE, 1.2F), target.getX(), target.getY(0.5), target.getZ(), 14, 0.3, 0.5, 0.3, 0.01);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.0F, 0.5F);
			return;
		}
		if (corpse != null) {
			if (cooling(player, "hand", HAND_COOLDOWN)) {
				return;
			}
			strike(player, state);
			KitchenCompat.throwAt(corpse, player.position().add(0.0, 0.5, 0.0));
			level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 0.8F, 0.5F);
			return;
		}
		sweep(player, level, state);
	}

	/** True (and a word on the screen) while {@code move} cools down; else its cooldown starts. */
	private static boolean cooling(ServerPlayer player, String move, int cooldown) {
		long wait = wait(player, move);
		if (wait > 0L) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.restoration.cooldown",
				Component.translatable("message.jaymoskingdoms.reaper.move." + move), (wait + 19L) / 20L));
			return true;
		}
		ready(player, move, cooldown);
		return false;
	}

	/** Reap: the scythe sweeps what stands before its keeper; what it kills gets up for them. */
	private static void sweep(ServerPlayer player, ServerLevel level, State state) {
		if (cooling(player, "sweep", SWEEP_COOLDOWN)) {
			return;
		}
		strike(player, state);
		Vec3 look = player.getLookAngle();
		float damage = SWEEP_DAMAGE + stage(state);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SWEEP_REACH),
			e -> e != player && e.isAlive() && !Thralls.isThrall(e) && !(e instanceof GraveGhost) && e.distanceToSqr(player) <= SWEEP_REACH * SWEEP_REACH)) {
			Vec3 to = target.position().subtract(player.position());
			if (to.lengthSqr() > 1.0 && to.normalize().dot(look) < 0.3) {
				continue;
			}
			EntityType<?> type = target.getType();
			Vec3 at = target.position();
			target.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
			if (!target.isAlive() && !(target instanceof Player)) {
				RISING.add(new Rising(level, at, type, player.getUUID(), level.getGameTime() + 3L));
			}
		}
		Vec3 front = player.getEyePosition().add(look.scale(1.8));
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y - 0.3, front.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(Fx.miasma(SMOKE, 1.4F), front.x, front.y - 0.3, front.z, 16, 0.8, 0.3, 0.8, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** A body the sweep killed rises for its killer, its corpse (if Jaymo's Kitchen laid one) taken up into it. */
	private static void rise(Rising r) {
		if (Thralls.count(r.master()) >= Necromancy.MAX_THRALLS) {
			return;
		}
		EntityType<? extends Mob> as = Necromancy.risesAs(r.type(), Climate.at(r.level(), net.minecraft.core.BlockPos.containing(r.at())));
		if (as == null) {
			return;
		}
		Entity corpse = KitchenCompat.freeCorpseNear(r.level(), r.at(), 2.5);
		if (KitchenCompat.loaded() && corpse == null) {
			return;
		}
		if (Thralls.raise(r.level(), as, r.at(), -1, r.master(), true, RISEN_LIFE, null) != null && corpse != null) {
			corpse.discard();
		}
	}

	/** Death's Company: every body within reach gets up at once; the first, if its keeper has none, as their grave-captain. */
	private static void company(ServerPlayer player, ServerLevel level, State state) {
		if (!KitchenCompat.loaded()) {
			return;
		}
		List<Entity> bodies = level.getEntities((Entity)null, player.getBoundingBox().inflate(DEAD_RANGE),
			e -> KitchenCompat.isRaisableCorpse(e) && e.distanceToSqr(player) <= DEAD_RANGE * DEAD_RANGE);
		if (bodies.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.reaper.no_dead").withStyle(s -> s.withColor(RED)));
			return;
		}
		if (cooling(player, "company", COMPANY_COOLDOWN)) {
			return;
		}
		strike(player, state);
		boolean captain = Thralls.of(player.getUUID()).stream().anyMatch(m -> m.entityTags().contains(CAPTAIN_TAG));
		int raised = 0;
		for (Entity body : bodies) {
			if (raised >= COMPANY_MOST) {
				break;
			}
			EntityType<? extends Mob> as = Necromancy.risesAs(KitchenCompat.sourceType(body), Climate.at(level, body.blockPosition()));
			if (as == null) {
				continue;
			}
			Mob mob = Thralls.raise(level, as, body.position(), -1, player.getUUID(), true, captain ? Necromancy.RAISE_LIFE : CAPTAIN_LIFE, null);
			if (mob == null) {
				continue;
			}
			if (!captain) {
				captain = true;
				mob.addTag(CAPTAIN_TAG);
				var health = mob.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) {
					health.setBaseValue(health.getBaseValue() * 2.0);
					mob.setHealth(mob.getMaxHealth());
				}
				mob.setCustomName(Component.translatable("entity.jaymoskingdoms.reaper_captain"));
			}
			body.discard();
			raised++;
		}
		if (raised > 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.2F, 0.5F);
			feed(player, TOLL_RAISE);
			ForbiddenArts.cast(player, level, false, 0);
		}
	}

	/** What {@code player} looks at within {@code reach} that passes {@code test}: the nearest such near the line of their eyes. */
	private static @Nullable Entity inSight(ServerPlayer player, ServerLevel level, double reach, Predicate<Entity> test) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(reach), test)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d <= reach && d < bestD && to.normalize().dot(look) > 0.92) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}
}
