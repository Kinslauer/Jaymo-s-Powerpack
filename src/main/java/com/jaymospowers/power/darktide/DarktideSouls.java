package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.web.KitchenCompat;
import com.jaymospowers.stamina.Feathers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.Vec3;

/**
 * The summoner's side of Necromancy (Andy's Awakening pick "Soul summoner", folded into Darktide).
 * <ul>
 * <li><b>Kills give shadows.</b> Corrupted or not, whatever you (or a risen shadow of yours) kill gives up its shadow at once,
 * with no body to kneel over; its corpse is marked ({@link #TAKEN_TAG}) so the same shadow is not taken from it again. A
 * villager, and anything with {@link #GREAT_HEALTH} health or more, still has to be taken from the body (Z held).</li>
 * <li><b>They cost stamina to keep.</b> Every risen shadow wears {@link #UPKEEP} of a feather a tick off your row. Run dry
 * and the newest sinks back into the dark: it is not lost, and neither is one that melts when the corruption lifts or you
 * leave. One that <b>dies</b> is gone for good.</li>
 * <li><b>The soul menu</b> (sprint + Z) lists the shadows you hold; pick up to {@link DarktideTide#MAX_RISEN} as your
 * <b>wave</b>, and a tap of C raises all of the wave that is not up yet (with no wave set, the last shadow taken, as
 * before).</li>
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
	/** A corpse whose shadow a kill already gave up. Kept on the corpse for good: it stays true. */
	static final String TAKEN_TAG = "jaymospowers_shadow_taken";

	/** The wave picked in the soul menu (shadows as they are stored). Saved, kept through death. */
	static final AttachmentType<List<String>> WAVE = AttachmentRegistry.create(JaymosPowers.id("darktide_wave"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	private record Kill(ServerLevel level, Vec3 at, EntityType<?> type, long until) {
	}

	private static final List<Kill> KILLS = new ArrayList<>();

	private DarktideSouls() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(DarktideSouls::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(DarktideSouls::tick);
		ServerPlayNetworking.registerGlobalReceiver(DarktideNet.SetWave.TYPE, (payload, ctx) -> setWave(ctx.player(), payload.picked()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> KILLS.clear());
	}

	// ---- kills give shadows -------------------------------------------------------------------------------------------

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || mob instanceof AbstractVillager
			|| mob.getMaxHealth() >= GREAT_HEALTH || mob.entityTags().contains(DarktideTide.NO_CORPSE_TAG)) {
			return;
		}
		Entity by = source.getEntity();
		ServerPlayer killer = by instanceof ServerPlayer p ? p : by == null ? null : DarktideTide.masterOf(by, level.getServer());
		if (killer == null || !DarktidePower.has(killer)) {
			return;
		}
		List<String> shadows = DarktideState.mutable(DarktideState.save(killer).shadows());
		// the risen count too: each must have room to sink back
		if (shadows.size() + DarktideTide.risenShadows(killer).size() >= DarktideTide.MAX_SHADOWS) {
			return;
		}
		shadows.add(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
		com.jaymospowers.core.trials.PowerTrials.count(killer, "darktide.shadow", 1);
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
		// only for the corrupted with shadows held, and never over a corpse in the sights (Z there is the harvest)
		if (!DarktidePower.corrupted(player) || shadows.isEmpty() || DarktideTide.aimedCorpse(player) != null) {
			return false;
		}
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

	private static void tick(MinecraftServer server) {
		if (!KILLS.isEmpty()) {
			markCorpses();
		}
	}
}
