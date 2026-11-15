package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;

/**
 * What the Hollow's blight ({@link HollowBlight}) shows of itself, Andy's picks (2026-11-15):
 * <ul>
 * <li><b>Signs others can see.</b> Once it has taken hold the skin greys toward the Hollow being made (the client's
 * {@code BlightLook}, from {@link #SIGN}; a full mask hides it, as it hides your blood), a wet cough comes now and then and
 * more often near the end, and flies gather at the last. Villagers who hear the cough know it for what it is (Andy:
 * "That's no regular cough").</li>
 * <li><b>Telling a tainted wound.</b> One who knows wounds (any Medicine perk) sees the sickness in a blighted body from the
 * start, as faint grey-green motes about it that only they see, and feels it go into their own wound. A town healer
 * who sees a blighted player says so (Andy: "Villagers can do this too").</li>
 * </ul>
 * Sounds and speech bubbles only, never a line of text in chat.
 */
public final class BlightSigns {
	/** On a blighted player, synced to everyone: the stage shown (1 or 2) in the top byte, the Hollow's colour (RGB) below. */
	public static final AttachmentType<Integer> SIGN = AttachmentRegistry.create(JaymosKingdoms.id("blight_sign"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** One cough in this many seconds, by stage (none before the blight takes hold). */
	static final int[] COUGH_EVERY = {0, 70, 25};
	/** Villagers this close hear a cough; one of them remarks on it, at most this often for one cougher (ticks). */
	static final double HEAR_RANGE = 12.0;
	static final int REMARK_GAP = 20 * 60;
	/** Lines for a cough heard ({@code blight_signs.jaymoskingdoms.cough.<n>}) and for a healer who sees it in you. */
	static final int COUGH_LINES = 5, HEALER_LINES = 4;
	/** One who knows wounds sees it this far, every this many seconds; a healer this far, at most this often (ticks). */
	static final double TELL_RANGE = 12.0, HEALER_RANGE = 6.0;
	static final int TELL_EVERY = 2, HEALER_GAP = 20 * 120;
	/** The motes only the knowing see: grey-green, small. */
	static final int MOTE = 0x8FA878;
	static final float MOTE_SIZE = 0.5F;

	static final net.minecraft.resources.Identifier COUGH = JaymosKingdoms.id("blight.cough");

	/** When each cougher was last remarked on, and each blighted player last told by a healer (game time). Not kept. */
	private static final Map<UUID, Long> REMARKED = new HashMap<>(), HEALER_TOLD = new HashMap<>();

	private BlightSigns() {
	}

	/** Loads the class at start-up, so {@link #SIGN} is registered before any world. */
	static void init() {
	}

	/** Whether {@code player} knows wounds: any perk on the Medicine page. */
	static boolean knowsWounds(Player player) {
		return Skills.rank(player, Skills.Perk.STEADY_HANDS) + Skills.rank(player, Skills.Perk.FIELD_DRESSER)
			+ Skills.rank(player, Skills.Perk.BONESETTER) + Skills.rank(player, Skills.Perk.PLAGUE_DOCTOR)
			+ Skills.rank(player, Skills.Perk.LIFESAVER) > 0;
	}

	/** Once a second while {@code player}'s blight spreads, at {@code stage}. */
	static void show(ServerPlayer player, HollowBlight.Blight blight, int stage) {
		int sign = stage > 0 ? stage << 24 | HollowBlight.tint(blight.variant()) & 0xFFFFFF : 0;
		Integer had = player.getAttached(SIGN);
		if (sign == 0) {
			if (had != null) {
				player.removeAttached(SIGN);
			}
		} else if (had == null || had != sign) {
			player.setAttached(SIGN, sign);
		}
		ServerLevel level = player.level();
		if (stage > 0 && player.getRandom().nextInt(COUGH_EVERY[stage]) == 0) {
			cough(level, player);
		}
		long now = level.getGameTime();
		if (now % (20L * TELL_EVERY) < 20) {
			tell(level, player, now);
		}
	}

	/** {@code body} coughs: the sound, a breath of grey, and a villager who hears it may say what it is. */
	static void cough(ServerLevel level, LivingEntity body) {
		level.playSound(null, body.getX(), body.getEyeY(), body.getZ(), Holder.direct(SoundEvent.createVariableRangeEvent(COUGH)),
			body instanceof Player ? SoundSource.PLAYERS : SoundSource.NEUTRAL, 0.9F, 0.9F + body.getRandom().nextFloat() * 0.2F);
		net.minecraft.world.phys.Vec3 look = body.getLookAngle();
		level.sendParticles(Fx.miasma(0x9A9E92, 0.6F), body.getX() + look.x * 0.4, body.getEyeY() - 0.1, body.getZ() + look.z * 0.4, 3,
			0.08, 0.05, 0.08, 0.01);
		BlightSpread.coughed(level, body);
		if (body instanceof ServerPlayer player) {
			BlightQuarantine.coughed(level, player);
		}
		long now = level.getGameTime();
		Long last = REMARKED.get(body.getUUID());
		if (last != null && now - last < REMARK_GAP && now >= last) {
			return;
		}
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(HEAR_RANGE),
			x -> x != body && x.isAlive() && !x.isSleeping() && !x.isBaby() && Recollections.rememberer(x) && !HollowBlight.blighted(x));
		if (near.isEmpty()) {
			return;
		}
		LivingEntity by = near.get(body.getRandom().nextInt(near.size()));
		REMARKED.put(body.getUUID(), now);
		if (by instanceof Mob m) {
			m.getLookControl().setLookAt(body, 30.0F, 30.0F);
		}
		Speech.say(level, by, Component.translatable("blight_signs.jaymoskingdoms.cough." + body.getRandom().nextInt(COUGH_LINES)),
			Speech.WHITE, HEAR_RANGE + 4.0);
		HollowBlight.heardCough(by, body);
	}

	/** The motes only the knowing see, and a healer who sees it in {@code player}. */
	static void tell(ServerLevel level, LivingEntity body, long now) {
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, body.getBoundingBox().inflate(TELL_RANGE),
			p -> p.isAlive() && knowsWounds(p))) {
			level.sendParticles(p, Fx.mote(MOTE, MOTE_SIZE), false, false, body.getX(), body.getY() + body.getBbHeight() * 0.55, body.getZ(), 3,
				body.getBbWidth() * 0.4, body.getBbHeight() * 0.25, body.getBbWidth() * 0.4, 0.0);
		}
		if (!(body instanceof ServerPlayer player)) {
			return;
		}
		Long last = HEALER_TOLD.get(player.getUUID());
		if (last != null && now - last < HEALER_GAP && now >= last) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(HEALER_RANGE),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && v.getVillagerData().profession().is(VillagerProfession.CLERIC) && v.hasLineOfSight(player))) {
			HEALER_TOLD.put(player.getUUID(), now);
			v.getLookControl().setLookAt(player, 30.0F, 30.0F);
			Speech.tell(level, v, player, Component.translatable("blight_signs.jaymoskingdoms.healer." + player.getRandom().nextInt(HEALER_LINES),
				Recollections.nameTo(player, v)), Speech.WHITE);
			break;
		}
	}

	/** The blight has gone out of {@code player} (cured, turned or faded). */
	static void clear(ServerPlayer player) {
		if (player.hasAttached(SIGN)) {
			player.removeAttached(SIGN);
		}
		REMARKED.remove(player.getUUID());
		HEALER_TOLD.remove(player.getUUID());
	}
}
