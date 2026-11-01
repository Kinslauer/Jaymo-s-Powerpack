package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.468) The Grue's trial. Jung, through Andy (2026-11-01): when the Grue takes you, "the player gets tped to a black
 * realm with a boss fight to subjugate the curse when it can then be used at will"; Andy chose a sealed arena in the sky
 * (no new dimension) and "the blindness fog rollin effect happens first, then you appear in the skybox".
 * <ul>
 * <li><b>The fog:</b> as the hold reaches {@link Grue#TAKEN}, black fog rolls in over the keeper ({@link Fog}, blindness)
 * for {@link #FOG_TICKS}.</li>
 * <li><b>The arena:</b> a sealed, lightless drum of black concrete {@link #ARENA_UP} below the top of the world over where
 * they stood, a few crying obsidian set in the floor for light, built only into air ({@link #build}) and cleared after
 * ({@link #clear}: only arena blocks in the arena's shape). They wake in it; the Grue rises across from them.</li>
 * <li><b>The Grue:</b> a dark red wizard (an evoker, {@link #BOSS_HEALTH} health, its own fangs and vexes) with the spirit's
 * stolen arms: a crushing strike up close, a grasp that drags you in. Below two thirds it calls shades and throws darkness;
 * below a third it blinks about the arena in the dark, and the keeper's spirit, still fighting, breaks through to hold it.</li>
 * <li><b>Won</b> (it dies): the curse is subjugated. The spirit is theirs again, the Grue's hold gone for good, and two moves
 * join the spirit's ({@link #MOVES}): Grue Form (a toggle: arms deep red, every spirit move {@link #FORM_DAMAGE}x, mana a
 * second) and Grue's Grasp.</li>
 * <li><b>Lost</b> (they fall, or {@link #LIMIT} runs out): back where they stood, and the Grue has them ({@link Grue}).</li>
 * <li><b>Cut short</b> (logging out, the server stopping): the arena is cleared and they're put back; the hold drops to
 * {@link #RETRY_HOLD}, so it comes for them again soon. A crash is mended at their next login ({@link #TRIAL} is saved).</li>
 * </ul>
 */
public final class GrueTrial {
	/** The fog's ticks before the arena; the ticks after waking before the Grue rises. */
	static final int FOG_TICKS = 60, RISE_TICKS = 40;
	/** The arena: how far below the top of the world its floor is, its radius, its inside height (blocks). */
	static final int ARENA_UP = 26, RADIUS = 13, HEIGHT = 10;
	/** Where the keeper wakes and where the Grue rises, from the centre (blocks). */
	static final double WAKE = 7.0;
	/** Longest the trial lasts (ticks) before the Grue wins. */
	static final int LIMIT = 6000;
	/** The Grue's health and armour. */
	static final double BOSS_HEALTH = 260.0, BOSS_ARMOUR = 10.0;
	/** Its strike (close, hurt, ticks between) and grasp (from, to, hurt, ticks between, pull). */
	static final double STRIKE_REACH = 3.5, GRASP_FROM = 4.0, GRASP_TO = 20.0, GRASP_PULL = 1.2;
	static final float STRIKE_HURT = 7.0F, GRASP_HURT = 5.0F;
	static final int STRIKE_EVERY = 30, GRASP_EVERY = 100;
	/** Phase two (below two thirds): shades called (how many, ticks between, their life), darkness thrown (ticks between, how long). */
	static final int SHADES = 3, SHADE_EVERY = 400, SHADE_LIFE = 600, DARK_EVERY = 300, DARK_TICKS = 80;
	/** Phase three (below a third): a blink this often; the spirit holds it this often, for this long (ticks). */
	static final int BLINK_EVERY = 120, SPIRIT_EVERY = 200, SPIRIT_HOLD = 60;
	/** Hold left after a trial cut short (it comes back for them at {@link Grue#TAKEN}). */
	static final float RETRY_HOLD = 95.0F;
	/** Health they're left with when they fall in it (they don't die there: the Grue wants the body). */
	static final float FALLEN_HEALTH = 4.0F;
	/** Grue Form: damage times, mana a second to hold; Grue's Grasp: reach, hurt, pull. */
	public static final float FORM_DAMAGE = 1.75F;
	static final float FORM_UPKEEP = 2.0F, GRASP_MOVE_HURT = 10.0F;
	static final double GRASP_MOVE_REACH = 20.0;

	/** Server → player: the fog to {@code to} (0 clear, 1 black) over {@code ticks}. */
	public record Fog(float to, int ticks) implements CustomPacketPayload {
		public static final Type<Fog> TYPE = new Type<>(JaymosKingdoms.id("grue_fog"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fog> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Fog::to,
			ByteBufCodecs.VAR_INT, Fog::ticks, Fog::new);

		@Override
		public Type<Fog> type() {
			return TYPE;
		}
	}

	/** Saved while a trial is on (to mend it after a crash): the arena's world and centre, and where they stood. */
	public record Saved(String dimension, long centre, double x, double y, double z) {
		public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Saved::dimension),
				Codec.LONG.fieldOf("centre").forGetter(Saved::centre),
				Codec.DOUBLE.fieldOf("x").forGetter(Saved::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Saved::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Saved::z))
			.apply(i, Saved::new));
	}

	public static final AttachmentType<Saved> TRIAL = AttachmentRegistry.create(JaymosKingdoms.id("grue_trial"),
		builder -> builder.persistent(Saved.CODEC));

	/** A trial under way. */
	private static final class Trial {
		final UUID player;
		final ServerLevel level;
		final BlockPos centre;
		final Vec3 back;
		final long since;
		boolean inside;
		@Nullable UUID boss;
		@Nullable ServerBossEvent bar;
		long nextStrike, nextGrasp, nextShades, nextDark, nextBlink, nextSpirit;

		Trial(UUID player, ServerLevel level, BlockPos centre, Vec3 back, long since) {
			this.player = player;
			this.level = level;
			this.centre = centre;
			this.back = back;
			this.since = since;
		}
	}

	private static final Map<UUID, Trial> TRIALS = new HashMap<>();
	/** The moves a keeper who subjugated the Grue has ({@link Spirits#moves}). */
	static final List<Move> MOVES = new ArrayList<>();

	static {
		MOVES.add(new Ambrose.Def("grue_form", 15.0F, 40, 1, Poses.REACH, Mana.HARM, GrueTrial::form));
		MOVES.add(new Ambrose.Def("grue_grasp", 25.0F, 120, 1, Poses.PULL, Mana.HARM, GrueTrial::grasp));
	}

	private GrueTrial() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("grue trial attachment: {}", TRIAL);
		for (Move move : MOVES) {
			Moves.register(move);
		}
		PayloadTypeRegistry.clientboundPlay().register(Fog.TYPE, Fog.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(GrueTrial::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && TRIALS.containsKey(player.getUUID())) {
				// (they fall in there, and wake back where they stood, the Grue in them)
				player.setHealth(FALLEN_HEALTH);
				end(player, false);
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			for (Trial trial : List.copyOf(TRIALS.values())) {
				if (entity.getUUID().equals(trial.boss)) {
					ServerPlayer player = trial.level.getServer().getPlayerList().getPlayer(trial.player);
					if (player != null) {
						end(player, true);
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Trial trial = TRIALS.remove(handler.getPlayer().getUUID());
			if (trial != null) {
				// (cleared now; put back where they stood when they come back, TRIAL being saved)
				tearDown(trial);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> mend(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Trial trial : List.copyOf(TRIALS.values())) {
				ServerPlayer player = server.getPlayerList().getPlayer(trial.player);
				if (player != null) {
					cutShort(player, trial);
				} else {
					tearDown(trial);
				}
			}
			TRIALS.clear();
		});
	}

	/** Whether {@code player} is in the Grue's trial (fog or arena). */
	public static boolean active(Entity player) {
		return TRIALS.containsKey(player.getUUID());
	}

	/** Whether {@code state}'s keeper has subjugated the Grue. */
	public static boolean mastered(@Nullable SpiritState state) {
		return state != null && state.toggled(SpiritState.MASTERED);
	}

	// ---- the start ------------------------------------------------------------------------------------------------------

	/** The hold reached {@link Grue#TAKEN}: the fog rolls in and the trial begins. False if it can't (then the Grue just takes them). */
	static boolean start(ServerPlayer player) {
		if (TRIALS.containsKey(player.getUUID())) {
			return true;
		}
		ServerLevel level = (ServerLevel)player.level();
		int floor = level.getMaxY() - ARENA_UP;
		if (floor - level.getMinY() < 40) {
			return false;
		}
		player.stopRiding();
		BlockPos centre = new BlockPos(player.getBlockX(), floor, player.getBlockZ());
		Trial trial = new Trial(player.getUUID(), level, centre, player.position(), level.getGameTime());
		TRIALS.put(player.getUUID(), trial);
		player.setAttached(TRIAL, new Saved(level.dimension().identifier().toString(), centre.asLong(), player.getX(), player.getY(), player.getZ()));
		Spirits.putAway(player, null);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FOG_TICKS + RISE_TICKS, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, FOG_TICKS + RISE_TICKS * 2, 0, false, false));
		ServerPlayNetworking.send(player, new Fog(1.0F, FOG_TICKS - 10));
		ServerPlayNetworking.send(player, new Grue.Lash(0.8F));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.2F, 0.5F);
		SpiritState spirit = Spirits.state(player);
		if (spirit != null) {
			SpiritVoice.sayNow(player, spirit.kind(), "grue.fight");
		}
		Grue.speakTo(player, "trial");
		return true;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (TRIALS.isEmpty()) {
			return;
		}
		for (Trial trial : List.copyOf(TRIALS.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(trial.player);
			if (player == null) {
				TRIALS.remove(trial.player);
				tearDown(trial);
				continue;
			}
			long t = trial.level.getGameTime() - trial.since;
			if (!trial.inside) {
				if (t >= FOG_TICKS) {
					enter(player, trial);
				}
				continue;
			}
			if (player.level() != trial.level || player.isCreative() || player.isSpectator()) {
				cutShort(player, trial);
				continue;
			}
			if (Downed.isDowned(player) || t > LIMIT) {
				end(player, false);
				continue;
			}
			// (no wandering out: the drum holds them, but an ender pearl or a power might not)
			if (player.position().distanceTo(Vec3.atBottomCenterOf(trial.centre.above())) > RADIUS + 4) {
				teleport(player, trial.level, Vec3.atBottomCenterOf(trial.centre.above()).add(-WAKE, 0.0, 0.0), -90.0F);
			}
			if (trial.boss == null) {
				if (t >= FOG_TICKS + RISE_TICKS) {
					rise(player, trial);
				}
				continue;
			}
			Entity found = trial.level.getEntity(trial.boss);
			if (found == null) {
				// (gone without dying: no free win, it comes again)
				cutShort(player, trial);
				continue;
			}
			if (!(found instanceof Evoker boss) || !boss.isAlive()) {
				end(player, true);
				continue;
			}
			fight(player, trial, boss, trial.level.getGameTime());
		}
	}

	/** The fog's done: the arena is raised and they wake in it. */
	private static void enter(ServerPlayer player, Trial trial) {
		build(trial.level, trial.centre);
		trial.inside = true;
		teleport(player, trial.level, Vec3.atBottomCenterOf(trial.centre.above()).add(-WAKE, 0.0, 0.0), -90.0F);
		player.removeEffect(MobEffects.BLINDNESS);
		ServerPlayNetworking.send(player, new Fog(0.0F, RISE_TICKS));
		trial.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 1.5F, 0.5F);
	}

	/** Across from them, the Grue rises. */
	private static void rise(ServerPlayer player, Trial trial) {
		ServerLevel level = trial.level;
		Evoker boss = EntityTypes.EVOKER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (boss == null) {
			end(player, false);
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(trial.centre.above()).add(WAKE, 0.0, 0.0);
		boss.snapTo(at.x, at.y, at.z, 90.0F, 0.0F);
		boss.setCustomName(Component.translatable("spirit.jaymoskingdoms.grue.name").withStyle(s -> s.withColor(Grue.COLOR)));
		boss.setCustomNameVisible(true);
		boss.setPersistenceRequired();
		boss.addTag(BOSS_TAG);
		AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(BOSS_HEALTH);
		}
		AttributeInstance armour = boss.getAttribute(Attributes.ARMOR);
		if (armour != null) {
			armour.setBaseValue(BOSS_ARMOUR);
		}
		boss.setHealth(boss.getMaxHealth());
		boss.setTarget(player);
		level.addFreshEntity(boss);
		trial.boss = boss.getUUID();
		long now = level.getGameTime();
		trial.nextStrike = now + STRIKE_EVERY;
		trial.nextGrasp = now + GRASP_EVERY / 2;
		trial.nextShades = now + SHADE_EVERY / 2;
		trial.nextDark = now + DARK_EVERY / 2;
		trial.nextBlink = now + BLINK_EVERY;
		trial.nextSpirit = now + SPIRIT_EVERY;
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), boss.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		bar.setDarkenScreen(true);
		bar.addPlayer(player);
		trial.bar = bar;
		burst(level, at.add(0.0, 1.0, 0.0), 80, 1.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.5F, 0.6F);
	}

	/** The Grue's turn: its stolen arms, then what each phase adds. */
	private static void fight(ServerPlayer player, Trial trial, Evoker boss, long now) {
		ServerLevel level = trial.level;
		float share = boss.getHealth() / boss.getMaxHealth();
		if (trial.bar != null) {
			trial.bar.setProgress(share);
		}
		boss.setTarget(player);
		if ((now & 3L) == 0L) {
			level.sendParticles(new DustParticleOptions(Grue.COLOR, 1.6F), boss.getX(), boss.getY() + 1.2, boss.getZ(), 4, 0.4, 0.7, 0.4, 0.01);
		}
		boolean held = boss.hasEffect(MobEffects.SLOWNESS) && boss.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 4;
		double d = boss.distanceTo(player);
		if (!held && d <= STRIKE_REACH && now >= trial.nextStrike) {
			trial.nextStrike = now + STRIKE_EVERY;
			player.hurtServer(level, level.damageSources().indirectMagic(boss, boss), STRIKE_HURT);
			player.knockback(1.0, boss.getX() - player.getX(), boss.getZ() - player.getZ(), level.damageSources().indirectMagic(boss, boss), STRIKE_HURT);
			burst(level, player.getBoundingBox().getCenter(), 20, 0.4);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0F, 0.7F);
		} else if (!held && d >= GRASP_FROM && d <= GRASP_TO && now >= trial.nextGrasp && boss.hasLineOfSight(player)) {
			trial.nextGrasp = now + GRASP_EVERY;
			player.hurtServer(level, level.damageSources().indirectMagic(boss, boss), GRASP_HURT);
			Vec3 in = boss.position().subtract(player.position()).normalize().scale(GRASP_PULL);
			player.setDeltaMovement(in.x, 0.35, in.z);
			player.needsSync = true;
			line(level, boss.getEyePosition(), player.getBoundingBox().getCenter());
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE, 1.2F, 0.5F);
		}
		if (share < 2.0F / 3.0F) {
			if (now >= trial.nextShades) {
				trial.nextShades = now + SHADE_EVERY;
				shades(level, boss, player);
			}
			if (now >= trial.nextDark) {
				trial.nextDark = now + DARK_EVERY;
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_TICKS, 0, false, false));
				ServerPlayNetworking.send(player, new Grue.Lash(0.5F));
				if (player.getRandom().nextBoolean()) {
					Grue.speakTo(player, "whisper");
				}
			}
		}
		if (share < 1.0F / 3.0F) {
			if (!player.hasEffect(MobEffects.DARKNESS)) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_TICKS * 2, 0, false, false));
			}
			if (!held && now >= trial.nextBlink) {
				trial.nextBlink = now + BLINK_EVERY;
				double a = player.getRandom().nextDouble() * Math.PI * 2.0, r = 3.0 + player.getRandom().nextDouble() * (RADIUS - 5);
				Vec3 to = Vec3.atBottomCenterOf(trial.centre.above()).add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
				burst(level, boss.position().add(0.0, 1.0, 0.0), 30, 0.5);
				boss.teleportTo(to.x, to.y, to.z);
				burst(level, to.add(0.0, 1.0, 0.0), 30, 0.5);
				level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.5F);
			}
			if (now >= trial.nextSpirit) {
				// the spirit, still fighting it, breaks through and holds it
				trial.nextSpirit = now + SPIRIT_EVERY;
				SpiritState spirit = Spirits.state(player);
				boss.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPIRIT_HOLD, 5, false, true));
				boss.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SPIRIT_HOLD, 2, false, true));
				int color = spirit == null ? 0x9AD8FF : Spirits.of(spirit.kind()).color() & 0xFFFFFF;
				level.sendParticles(new DustParticleOptions(color, 1.8F), boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
				if (spirit != null) {
					SpiritVoice.sayNow(player, spirit.kind(), "grue.fight");
				}
			}
		}
	}

	private static void shades(ServerLevel level, Evoker boss, ServerPlayer player) {
		for (int i = 0; i < SHADES; i++) {
			Vex shade = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (shade == null) {
				return;
			}
			double a = i * Math.PI * 2.0 / SHADES;
			shade.snapTo(boss.getX() + Math.cos(a) * 1.5, boss.getY() + 1.5, boss.getZ() + Math.sin(a) * 1.5, 0.0F, 0.0F);
			shade.setOwner(boss);
			shade.setLimitedLife(SHADE_LIFE);
			shade.setCustomName(Component.translatable("entity.jaymoskingdoms.grue_shade").withStyle(s -> s.withColor(Grue.COLOR)));
			shade.addTag(BOSS_TAG);
			shade.setTarget(player);
			level.addFreshEntity(shade);
		}
		burst(level, boss.position().add(0.0, 1.5, 0.0), 40, 1.0);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.4F, 0.6F);
	}

	// ---- the end ----------------------------------------------------------------------------------------------------

	/** The trial is over: {@code won}, the curse is theirs; else the Grue has them. Either way they're put back where they stood. */
	static void end(ServerPlayer player, boolean won) {
		Trial trial = TRIALS.remove(player.getUUID());
		if (trial == null) {
			return;
		}
		tearDown(trial);
		player.removeAttached(TRIAL);
		player.removeEffect(MobEffects.DARKNESS);
		player.removeEffect(MobEffects.BLINDNESS);
		teleport(player, trial.level, trial.back, player.getYRot());
		ServerPlayNetworking.send(player, new Fog(0.0F, 20));
		SpiritState spirit = Spirits.state(player);
		if (!won || spirit == null) {
			Grue.overtakeNow(player);
			return;
		}
		player.removeAttached(Grue.GRUE);
		player.setAttached(Spirits.SPIRIT, spirit.withToggle(SpiritState.MASTERED, true));
		Grue.speakTo(player, "beaten");
		SpiritVoice.sayNow(player, spirit.kind(), "grue.won");
		ServerLevel level = (ServerLevel)player.level();
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.6, 0.9, 0.6, 0.08);
		burst(level, player.position().add(0.0, 1.0, 0.0), 60, 1.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.grue.subjugated").withStyle(s -> s.withColor(Grue.COLOR).withBold(true)));
		PowersCompat.shake(player, 0.6F);
	}

	/** Cut short (gone elsewhere, the server stopping): put back, the hold just short of taking them. */
	private static void cutShort(ServerPlayer player, Trial trial) {
		TRIALS.remove(player.getUUID());
		tearDown(trial);
		player.removeAttached(TRIAL);
		player.removeEffect(MobEffects.DARKNESS);
		player.removeEffect(MobEffects.BLINDNESS);
		if (trial.inside) {
			teleport(player, trial.level, trial.back, player.getYRot());
		}
		ServerPlayNetworking.send(player, new Fog(0.0F, 20));
		player.setAttached(Grue.GRUE, new Grue.State(RETRY_HOLD, false));
	}

	/** At login: a trial that was on when they left (or the server fell over) is mended: the arena cleared, they're put back. */
	private static void mend(ServerPlayer player) {
		Saved saved = player.getAttached(TRIAL);
		if (saved == null || TRIALS.containsKey(player.getUUID())) {
			return;
		}
		player.removeAttached(TRIAL);
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(saved.dimension()));
		ServerLevel level = player.level().getServer().getLevel(key);
		if (level != null) {
			clear(level, BlockPos.of(saved.centre()));
			for (Entity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(BlockPos.of(saved.centre())).inflate(RADIUS + 2, HEIGHT + 2, RADIUS + 2),
				e -> e.entityTags().contains(BOSS_TAG))) {
				e.discard();
			}
			teleport(player, level, new Vec3(saved.x(), saved.y(), saved.z()), player.getYRot());
		}
		player.setAttached(Grue.GRUE, new Grue.State(RETRY_HOLD, false));
	}

	/** The boss and its shades gone, the bar down, the arena cleared. */
	private static void tearDown(Trial trial) {
		if (trial.bar != null) {
			trial.bar.removeAllPlayers();
		}
		for (Entity e : trial.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(trial.centre).inflate(RADIUS + 4, HEIGHT + 4, RADIUS + 4),
			e -> e.entityTags().contains(BOSS_TAG))) {
			e.discard();
		}
		if (trial.inside) {
			clear(trial.level, trial.centre);
		}
	}

	static final String BOSS_TAG = "jaymoskingdoms.grue_boss";

	// ---- the arena --------------------------------------------------------------------------------------------------

	/** Which arena block goes at ({@code dx}, {@code dy}, {@code dz}) from the floor's centre, or null for none (inside, or outside it). */
	private static @Nullable BlockState shape(int dx, int dy, int dz) {
		int r2 = dx * dx + dz * dz;
		if (r2 > RADIUS * RADIUS) {
			return null;
		}
		boolean shell = dy == 0 || dy == HEIGHT + 1 || r2 > (RADIUS - 1) * (RADIUS - 1);
		if (!shell) {
			return null;
		}
		// a few cold lights set in the floor, in a ring
		if (dy == 0 && r2 >= 64 && r2 <= 81 && Math.floorMod(dx * 3 + dz * 7, 11) == 0) {
			return Blocks.CRYING_OBSIDIAN.defaultBlockState();
		}
		return Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
	}

	/** The drum raised round {@code centre} (its floor's centre), only into air. */
	private static void build(ServerLevel level, BlockPos centre) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy <= HEIGHT + 1; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockState block = shape(dx, dy, dz);
					if (block == null) {
						continue;
					}
					at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (level.getBlockState(at).isAir()) {
						level.setBlock(at, block, 2);
					}
				}
			}
		}
	}

	/** The drum taken down: only blocks of the arena's kind in the arena's shape (anything else there was there before). */
	private static void clear(ServerLevel level, BlockPos centre) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy <= HEIGHT + 1; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockState block = shape(dx, dy, dz);
					if (block == null) {
						continue;
					}
					at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (level.getBlockState(at).is(block.getBlock())) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	private static void teleport(ServerPlayer player, ServerLevel level, Vec3 to, float yaw) {
		player.teleportTo(level, to.x, to.y, to.z, Set.<Relative>of(), yaw, 0.0F, false);
		player.resetFallDistance();
	}

	private static void burst(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(new DustParticleOptions(Grue.COLOR, 1.8F), at.x, at.y, at.z, count, spread, spread, spread, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, count / 3, spread, spread, spread, 0.02);
	}

	private static void line(ServerLevel level, Vec3 from, Vec3 to) {
		for (int i = 0; i <= 12; i++) {
			Vec3 p = from.lerp(to, i / 12.0);
			level.sendParticles(new DustParticleOptions(Grue.COLOR, 1.4F), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
	}

	// ---- the moves of one who mastered it -----------------------------------------------------------------------------

	/** Grue Form: on or off. While on, the arms burn deep red and every spirit move hits {@link #FORM_DAMAGE} times as hard. */
	private static boolean form(ServerPlayer player, SpiritState state, int charge) {
		boolean on = !state.toggled(SpiritState.GRUE_FORM);
		Spirits.toggle(player, SpiritState.GRUE_FORM, on);
		ServerLevel level = (ServerLevel)player.level();
		if (on) {
			burst(level, player.position().add(0.0, 1.2, 0.0), 40, 0.6);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 0.6F);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
		return true;
	}

	/** Grue's Grasp: drag what you look at in from afar, and hurt it. */
	private static boolean grasp(ServerPlayer player, SpiritState state, int charge) {
		LivingEntity target = Arcana.sighted(player);
		if (target == null || target.distanceTo(player) > GRASP_MOVE_REACH || !SpiritFoes.foe(player, target, true)) {
			return false;
		}
		ServerLevel level = (ServerLevel)player.level();
		SpiritFoes.strike(level, player, target, GRASP_MOVE_HURT * Spirits.damageScale(state));
		Vec3 in = player.position().subtract(target.position()).normalize().scale(GRASP_PULL);
		target.setDeltaMovement(in.x, 0.35, in.z);
		target.needsSync = true;
		line(level, player.getEyePosition(), target.getBoundingBox().getCenter());
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.2F, 0.5F);
		CastHints.setReach(player, target.distanceTo(player));
		return true;
	}

	/** Once a second while Grue Form is on ({@link Grue#tick}): its mana, or it fades. */
	static void formSecond(ServerPlayer player, SpiritState state) {
		if (state.toggled(SpiritState.GRUE_FORM) && (!state.out() || !Spirits.upkeep(player, FORM_UPKEEP))) {
			Spirits.toggle(player, SpiritState.GRUE_FORM, false);
		}
	}
}
