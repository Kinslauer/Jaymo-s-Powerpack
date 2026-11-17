package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.web.KitchenCompat;
import com.jaymospowers.stamina.Feathers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The summoner's side of Necromancy (Andy's Awakening pick "Soul summoner", folded into Darktide).
 * <ul>
 * <li><b>Kills give shadows.</b> While corrupted, whatever you (or a risen shadow of yours) kill gives up its shadow at once,
 * with no body to kneel over; its corpse is marked ({@link #TAKEN_TAG}) so the same shadow is not taken from it again. A
 * villager, and anything with {@link #GREAT_HEALTH} health or more, still has to be taken from the body (Z held).</li>
 * <li><b>They cost stamina to keep.</b> Every risen shadow wears {@link #UPKEEP} of a feather a tick off your row. Run dry
 * and the newest sinks back into the dark: it is not lost, and neither is one that melts when the corruption lifts or you
 * leave. One that <b>dies</b> is gone for good.</li>
 * <li><b>The soul menu</b> (sprint + Z) lists the shadows you hold; pick up to {@link DarktideTide#MAX_RISEN} as your
 * <b>wave</b>, and a tap of C raises all of the wave that is not up yet (with no wave set, the last shadow taken, as
 * before).</li>
 * <li><b>Possession</b> (Z at a risen shadow of yours): your body is left standing where it was (a mannequin in your skin)
 * and you ride in the shadow: you see from it, the move keys walk it (left and right turn it, jump jumps, sprint runs),
 * and it strikes what is in front of it. Sneak, and you are back in your body; sneak with sprint held, and your body is
 * <b>warped</b> to the shadow instead. A blow to the body throws you back into it.</li>
 * </ul>
 */
public final class DarktideSouls {
	// ---- tuning ----
	/** A kill with this much health or more does not give its shadow up at once. */
	static final float GREAT_HEALTH = 100.0F;
	/** How far from where it died a kill's corpse is looked for (blocks), and for how long after (ticks). */
	static final double CORPSE_REACH = 3.0;
	static final int CORPSE_TICKS = 5;
	/** Stamina (half-feathers a tick) each risen shadow wears off, and how often a spent master loses one (ticks). */
	static final float UPKEEP = 0.04F;
	static final int SINK_EVERY = 20;
	/** Possession: reach, how long it lasts (ticks), the wait after. */
	static final double POSSESS_REACH = 32.0;
	static final int POSSESS_TICKS = 400, POSSESS_COOLDOWN = 600;
	/** A ridden shadow: degrees a tick it turns, its walk and run as shares of its own speed, how often and how far it strikes. */
	static final float TURN = 6.0F;
	static final double WALK = 0.6, RUN = 1.0, STRIKE_REACH = 2.2;
	static final int STRIKE_EVERY = 15;
	/** A corpse whose shadow a kill already gave up. Kept on the corpse for good: it stays true. */
	static final String TAKEN_TAG = "jaymospowers_shadow_taken";
	/** The body left behind, so one left over from a restart can be swept away. */
	static final String BODY_TAG = "jaymospowers_darktide_body";

	/** The wave picked in the soul menu (shadows as they are stored). Saved, kept through death. */
	static final AttachmentType<List<String>> WAVE = AttachmentRegistry.create(JaymosPowers.id("darktide_wave"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** On a player while possessing: the game mode to give back (its id). Saved, so a crash cannot leave a spectator behind. */
	static final AttachmentType<Integer> POSSESSING = AttachmentRegistry.create(JaymosPowers.id("darktide_possessing"),
		builder -> builder.persistent(Codec.INT));

	private record Kill(ServerLevel level, Vec3 at, EntityType<?> type, long until) {
	}

	private static final class Ride {
		ServerLevel level;
		Mob mob;
		UUID body;
		Vec3 at;
		float yaw, pitch, steer;
		GameType mode;
		long until;
	}

	private static final List<Kill> KILLS = new ArrayList<>();
	private static final Map<UUID, Ride> RIDES = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private DarktideSouls() {
	}

	/** The mannequin's skin, which only a mannequin's own code may set (as {@code RigDecoy} reads it). */
	private abstract static class Profile extends Mannequin {
		private Profile(Level level) {
			super(level);
		}

		static EntityDataAccessor<ResolvableProfile> key() {
			return DATA_PROFILE;
		}
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(DarktideSouls::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(DarktideSouls::tick);
		ServerPlayNetworking.registerGlobalReceiver(DarktideNet.SetWave.TYPE, (payload, ctx) -> setWave(ctx.player(), payload.picked()));
		// a blow to the body left behind throws its owner back into it, and lands on them
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Mannequin) || !entity.entityTags().contains(BODY_TAG)) {
				return true;
			}
			for (Map.Entry<UUID, Ride> e : RIDES.entrySet()) {
				if (e.getValue().body.equals(entity.getUUID()) && entity.level().getServer() != null) {
					ServerPlayer owner = entity.level().getServer().getPlayerList().getPlayer(e.getKey());
					if (owner != null) {
						end(owner, false);
						owner.hurtServer(owner.level(), source, amount);
					}
					break;
				}
			}
			return false;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(BODY_TAG) && RIDES.values().stream().noneMatch(r -> r.body.equals(entity.getUUID()))) {
				entity.discard();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			// a possession cut off by a crash: the game mode comes back
			ServerPlayer player = handler.getPlayer();
			Integer mode = player.getAttached(POSSESSING);
			if (mode != null && !RIDES.containsKey(player.getUUID())) {
				player.setGameMode(GameType.byId(mode));
				player.removeAttached(POSSESSING);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			end(handler.player, false);
			READY.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				end(player, false);
			}
			KILLS.clear();
			READY.clear();
		});
	}

	// ---- kills give shadows -------------------------------------------------------------------------------------------

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || mob instanceof AbstractVillager
			|| mob.getMaxHealth() >= GREAT_HEALTH || mob.entityTags().contains(DarktideTide.NO_CORPSE_TAG)) {
			return;
		}
		Entity by = source.getEntity();
		ServerPlayer killer = by instanceof ServerPlayer p ? p : by == null ? null : DarktideTide.masterOf(by, level.getServer());
		if (killer == null || !DarktidePower.has(killer) || !DarktidePower.corrupted(killer)) {
			return;
		}
		List<String> shadows = DarktideState.mutable(DarktideState.save(killer).shadows());
		if (shadows.size() >= DarktideTide.MAX_SHADOWS) {
			return;
		}
		shadows.add(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
		killer.setAttached(DarktideState.SAVE, DarktideState.save(killer).withShadows(shadows));
		KILLS.add(new Kill(level, mob.position(), mob.getType(), level.getGameTime() + CORPSE_TICKS));
		Vec3 from = mob.getBoundingBox().getCenter();
		MagicFx.stroke(level, from, killer.getEyePosition().add(0.0, -0.4, 0.0), 0.08F, DarktideLooks.INK);
		level.sendParticles(MagicFx.miasma(DarktideLooks.INK, 1.4F), from.x, from.y, from.z, 8, 0.3, 0.3, 0.3, 0.03);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7F, 0.8F);
		killer.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.shadow_taken", mob.getType().getDescription(), shadows.size(),
			DarktideTide.MAX_SHADOWS).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
	}

	/** Whether the shadow of {@code corpse} went to a kill already. */
	static boolean taken(Entity corpse) {
		return corpse.entityTags().contains(TAKEN_TAG);
	}

	/** The kills whose shadows were taken: the corpse each left is marked, once Jaymo's Kitchen has laid it. */
	private static void markCorpses() {
		for (Iterator<Kill> it = KILLS.iterator(); it.hasNext(); ) {
			Kill kill = it.next();
			Entity corpse = null;
			double best = CORPSE_REACH * CORPSE_REACH;
			for (Entity e : kill.level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(kill.at, kill.at).inflate(CORPSE_REACH),
				e -> !taken(e) && KitchenCompat.freeCorpseOf(e) == kill.type)) {
				double d = e.distanceToSqr(kill.at);
				if (d <= best) {
					best = d;
					corpse = e;
				}
			}
			if (corpse != null) {
				corpse.addTag(TAKEN_TAG);
				it.remove();
			} else if (kill.level.getGameTime() >= kill.until) {
				it.remove();
			}
		}
	}

	// ---- the wave -----------------------------------------------------------------------------------------------------

	static List<String> wave(ServerPlayer player) {
		List<String> wave = player.getAttached(WAVE);
		return wave == null ? List.of() : wave;
	}

	/**
	 * The shadows of {@code player}'s wave that are not up yet, taken out of {@code shadows} (as many as there is room for).
	 * Empty with no wave set, or when all of it is up.
	 */
	static List<String> pending(ServerPlayer player, List<String> shadows, int room) {
		List<String> want = new ArrayList<>(wave(player));
		for (String up : DarktideTide.risenShadows(player)) {
			want.remove(up);
		}
		List<String> out = new ArrayList<>();
		for (String id : want) {
			if (out.size() >= room) {
				break;
			}
			if (shadows.remove(id)) {
				out.add(id);
			}
		}
		return out;
	}

	/** Sprint + Z: the soul menu opens. True if it did (the key is taken). */
	static boolean openMenu(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, DarktideNet.Souls.TYPE)) {
			return false;
		}
		List<String> shadows = DarktideState.save(player).shadows();
		List<String> names = new ArrayList<>();
		for (String shadow : shadows) {
			names.add(typeOf(shadow));
		}
		// which of them the wave holds: each wave entry marks one shadow
		List<String> want = new ArrayList<>(wave(player));
		for (String up : DarktideTide.risenShadows(player)) {
			want.remove(up);
		}
		List<Integer> picked = new ArrayList<>();
		for (int i = 0; i < shadows.size(); i++) {
			if (want.remove(shadows.get(i))) {
				picked.add(i);
			}
		}
		ServerPlayNetworking.send(player, new DarktideNet.Souls(names, picked, DarktideTide.MAX_RISEN));
		return true;
	}

	/** The entity type id a stored shadow is of (a villager's shadow is its whole saved data). */
	private static String typeOf(String shadow) {
		if (!shadow.startsWith("{")) {
			return shadow;
		}
		try {
			return net.minecraft.nbt.TagParser.parseCompoundFully(shadow).getStringOr("id", "minecraft:villager");
		} catch (Exception e) {
			return "minecraft:villager";
		}
	}

	/** The menu closed: the wave is the shadows picked there, and those of the old wave that are up now. */
	private static void setWave(ServerPlayer player, List<Integer> picked) {
		if (!DarktidePower.has(player)) {
			return;
		}
		List<String> shadows = DarktideState.save(player).shadows();
		List<String> old = new ArrayList<>(wave(player));
		List<String> wave = new ArrayList<>();
		for (String up : DarktideTide.risenShadows(player)) {
			if (old.remove(up)) {
				wave.add(up);
			}
		}
		for (int i : new java.util.TreeSet<>(picked)) {
			if (i >= 0 && i < shadows.size() && wave.size() < DarktideTide.MAX_RISEN) {
				wave.add(shadows.get(i));
			}
		}
		if (wave.isEmpty()) {
			player.removeAttached(WAVE);
		} else {
			player.setAttached(WAVE, List.copyOf(wave));
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.wave_set", wave.size(), DarktideTide.MAX_RISEN)
			.withStyle(ChatFormatting.DARK_AQUA));
	}

	// ---- upkeep -------------------------------------------------------------------------------------------------------

	/** Each tick for a Darktide user with shadows up: they wear the stamina down, and a spent master loses the newest. */
	static void upkeep(ServerPlayer player, DarktideLive live) {
		if (live.risen.isEmpty()) {
			return;
		}
		Feathers.drain(player, UPKEEP * live.risen.size());
		if (player.tickCount % SINK_EVERY == 0 && !player.isCreative() && !player.isSpectator() && Feathers.view(player).exhausted()
			&& DarktideTide.sinkNewest(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.shadow_sinks").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		}
	}

	// ---- possession ---------------------------------------------------------------------------------------------------

	/** Whether {@code mob} is being ridden by its master (its own will is set aside). */
	static boolean ridden(Mob mob) {
		for (Ride ride : RIDES.values()) {
			if (ride.mob == mob) {
				return true;
			}
		}
		return false;
	}

	/** Z at one of your risen shadows: you ride in it. True if the key was taken (a shadow was aimed at). */
	static boolean possess(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		Mob best = null;
		double bestDot = 0.96;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(POSSESS_REACH), m -> m.isAlive() && DarktideTide.risenOf(m, player))) {
			Vec3 to = mob.getBoundingBox().getCenter().subtract(eye);
			double dot = to.normalize().dot(look);
			if (to.length() <= POSSESS_REACH && dot > bestDot && player.hasLineOfSight(mob)) {
				bestDot = dot;
				best = mob;
			}
		}
		if (best == null) {
			return false;
		}
		long now = level.getGameTime();
		long wait = READY.getOrDefault(player.getUUID(), 0L) - now;
		if (wait > 0L || RIDES.containsKey(player.getUUID()) || player.isPassenger() || player.isSpectator()) {
			if (wait > 0L) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.possess_wait", (wait + 19L) / 20L).withStyle(ChatFormatting.GRAY));
			}
			return true;
		}
		Mannequin body = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (body == null) {
			return true;
		}
		body.getEntityData().set(Profile.key(), ResolvableProfile.createResolved(player.getGameProfile()));
		body.addTag(BODY_TAG);
		body.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		body.setYHeadRot(player.getYHeadRot());
		body.setYBodyRot(player.yBodyRot);
		if (!level.addFreshEntity(body)) {
			return true;
		}
		Ride ride = new Ride();
		ride.level = level;
		ride.mob = best;
		ride.body = body.getUUID();
		ride.at = player.position();
		ride.yaw = player.getYRot();
		ride.pitch = player.getXRot();
		ride.steer = best.getYRot();
		ride.mode = player.gameMode();
		ride.until = now + POSSESS_TICKS;
		RIDES.put(player.getUUID(), ride);
		player.setAttached(POSSESSING, ride.mode.getId());
		MagicFx.stroke(level, eye.add(0.0, -0.3, 0.0), best.getBoundingBox().getCenter(), 0.1F, DarktideLooks.INK);
		level.sendParticles(MagicFx.miasma(DarktideLooks.INK, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.6, 0.3, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		best.setTarget(null);
		best.getNavigation().stop();
		player.setGameMode(GameType.SPECTATOR);
		player.setCamera(best);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.possess_hint").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		return true;
	}

	/**
	 * The possession ends. {@code warp}: the body comes to the shadow (you stand where it stands); else you are back where
	 * your body was left.
	 */
	static void end(ServerPlayer player, boolean warp) {
		Ride ride = RIDES.remove(player.getUUID());
		if (ride == null) {
			return;
		}
		if (player.getCamera() != player) {
			player.setCamera(player);
		}
		player.setGameMode(ride.mode);
		player.removeAttached(POSSESSING);
		Vec3 to = warp && ride.mob.isAlive() && ride.mob.level() == ride.level ? ride.mob.position() : ride.at;
		player.teleportTo(ride.level, to.x, to.y, to.z, Set.of(), ride.yaw, ride.pitch, false);
		player.resetFallDistance();
		Entity body = ride.level.getEntity(ride.body);
		if (body != null) {
			if (to != ride.at) {
				ride.level.sendParticles(MagicFx.miasma(DarktideLooks.INK, 1.6F), body.getX(), body.getY() + 1.0, body.getZ(), 14, 0.3, 0.6, 0.3, 0.03);
			}
			body.discard();
		}
		READY.put(player.getUUID(), ride.level.getGameTime() + POSSESS_COOLDOWN);
		ride.level.sendParticles(MagicFx.miasma(DarktideLooks.INK, 1.6F), to.x, to.y + 1.0, to.z, 14, 0.3, 0.6, 0.3, 0.03);
		ride.level.playSound(null, to.x, to.y, to.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, warp ? 1.3F : 0.8F);
	}

	private static void tick(MinecraftServer server) {
		if (!KILLS.isEmpty()) {
			markCorpses();
		}
		if (RIDES.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Ride> e : new ArrayList<>(RIDES.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Ride ride = e.getValue();
			if (player == null) {
				RIDES.remove(e.getKey());
				continue;
			}
			Mob mob = ride.mob;
			Input input = player.getLastClientInput();
			// sneak lets go (the game hands the camera back by itself); with sprint held the body is warped to the shadow
			boolean let = player.getCamera() != mob;
			if (let || !mob.isAlive() || mob.isRemoved() || !player.isAlive() || player.level() != ride.level || ride.level.getGameTime() >= ride.until
				|| !DarktidePower.has(player) || !DarktidePower.corrupted(player) || ride.level.getEntity(ride.body) == null) {
				end(player, let && input.sprint() && mob.isAlive());
				continue;
			}
			drive(player, ride, input);
		}
	}

	/** The ridden shadow goes where the keys say, and strikes what stands in front of it. */
	private static void drive(ServerPlayer player, Ride ride, Input input) {
		Mob mob = ride.mob;
		if (input.left()) {
			ride.steer -= TURN;
		}
		if (input.right()) {
			ride.steer += TURN;
		}
		ride.steer = Mth.wrapDegrees(ride.steer);
		mob.getNavigation().stop();
		mob.setYRot(ride.steer);
		mob.setYHeadRot(ride.steer);
		mob.setYBodyRot(ride.steer);
		Vec3 dir = Vec3.directionFromRotation(0.0F, ride.steer);
		if (input.forward() || input.backward()) {
			double speed = Mth.clamp(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * (input.sprint() ? RUN : WALK), 0.06, 0.45);
			Vec3 go = input.forward() ? dir.scale(speed) : dir.scale(-speed * 0.5);
			mob.setDeltaMovement(go.x, mob.getDeltaMovement().y, go.z);
			if (mob.horizontalCollision && mob.onGround()) {
				mob.getJumpControl().jump();
			}
		}
		if (input.jump() && mob.onGround()) {
			mob.getJumpControl().jump();
		}
		if (mob.tickCount % STRIKE_EVERY != 0) {
			return;
		}
		double reach = mob.getBbWidth() * 0.5 + STRIKE_REACH;
		LivingEntity foe = null;
		double best = reach * reach;
		for (LivingEntity e : ride.level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(reach),
			e -> e != mob && !(e instanceof Mannequin) && PickKit.foe(player, e) && !DarktideTide.risenOf(e, player))) {
			Vec3 to = e.position().subtract(mob.position());
			double d = to.lengthSqr();
			if (d <= best && (d < 0.5 || to.normalize().dot(dir) > 0.3)) {
				best = d;
				foe = e;
			}
		}
		if (foe == null) {
			return;
		}
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
			mob.doHurtTarget(ride.level, foe);
		} else {
			foe.hurtServer(ride.level, ride.level.damageSources().mobAttack(mob), 2.0F);
		}
		DarktideTide.enemy(player, foe);
	}

	/** Whether {@code player} is riding a shadow now. */
	static boolean possessing(@Nullable ServerPlayer player) {
		return player != null && RIDES.containsKey(player.getUUID());
	}
}
