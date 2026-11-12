package com.jaymospowers.rig;

import com.jaymospowers.combat.Guard;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The storing field: a barrier of brass rings that drinks the parry. Raise it (the Rig menu's Field), and for as long as
 * it holds every blow the guard parries is not only turned aside: it is kept in the rings, up to {@link #SLOTS} of them,
 * a perfect parry kept at double strength. Use the Field again and the rings let it all go at once, a blast round the
 * wearer as strong as what was stored; unspent, they let go by themselves when {@link #HOLD_TICKS} run out.
 * The guard tells the field the size of the blow it parried ({@link #noteBlow}, one added line in {@code Guard}) and
 * fires {@link Guard#PARRIED}, which is when the field takes it.
 */
public final class RigField {
	/** The Core's charge to raise the field, and how long it holds (ticks). */
	public static final int COST = 80, HOLD_TICKS = 600;
	/** Blows it keeps, and the strength a perfect parry counts as. */
	public static final int SLOTS = 3;
	public static final float PERFECT_FACTOR = 2.0F;
	/** The strength of a parry whose blow's size the guard did not give (a Z Fighter's arms, a power's own guard). */
	public static final float UNKNOWN_BLOW = 4.0F;
	/** The blast: radius (blocks) with nothing stored and for each blow stored, the share of what was stored it hits for, the least and most damage, the knockback. */
	public static final double BLAST_BASE = 2.5, BLAST_PER_BLOW = 1.0;
	public static final float BLAST_SHARE = 0.9F, BLAST_MIN = 2.0F, BLAST_MAX = 40.0F;
	public static final double KNOCK_BASE = 0.5, KNOCK_PER_BLOW = 0.35;
	/** Ticks between a raised field's rings. */
	public static final int RING_EVERY = 30;
	public static final int GOLD = 0xE8B84A;

	private static final class Field {
		int blows;
		float stored;
		long until;
	}

	private static final Map<UUID, Field> FIELDS = new HashMap<>();
	/** The size of the blow the guard just parried, kept for the moment its parry event fires. */
	private static final Map<UUID, Float> NOTED = new HashMap<>();

	private RigField() {
	}

	static void init() {
		Guard.PARRIED.register((player, attacker, perfect) -> store(player, perfect));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FIELDS.remove(handler.player.getUUID());
			NOTED.remove(handler.player.getUUID());
		});
	}

	/** Called by the guard as it parries a blow of {@code amount}: the field wants to know how big it was. */
	public static void noteBlow(ServerPlayer player, float amount) {
		if (FIELDS.containsKey(player.getUUID())) {
			NOTED.put(player.getUUID(), amount);
		}
	}

	static boolean up(ServerPlayer player) {
		return FIELDS.containsKey(player.getUUID());
	}

	static int blows(ServerPlayer player) {
		Field field = FIELDS.get(player.getUUID());
		return field == null ? 0 : field.blows;
	}

	/** The Field entry: raise it; with it up and something kept, let it go; with it up and empty, lower it. */
	static void use(ServerPlayer player) {
		Field field = FIELDS.get(player.getUUID());
		if (field == null) {
			if (Rig.core(player).isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.needs_core").withStyle(ChatFormatting.GRAY));
				return;
			}
			if (!Rig.spend(player, COST)) {
				RigMind.empty(player);
				return;
			}
			field = new Field();
			field.until = player.level().getGameTime() + HOLD_TICKS;
			FIELDS.put(player.getUUID(), field);
			ServerLevel level = player.level();
			MagicFx.ring(level, player.position().add(0.0, 1.0, 0.0), null, 1.4F, GOLD, 14);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 0.9F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.field.up", PickKit.gauge(0.0F, SLOTS)).withStyle(ChatFormatting.GOLD));
			return;
		}
		if (field.blows > 0) {
			release(player, field, false);
		} else {
			FIELDS.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.field.down").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void store(ServerPlayer player, boolean perfect) {
		Field field = FIELDS.get(player.getUUID());
		Float noted = NOTED.remove(player.getUUID());
		if (field == null || field.blows >= SLOTS) {
			return;
		}
		field.blows++;
		field.stored += (noted == null ? UNKNOWN_BLOW : noted) * (perfect ? PERFECT_FACTOR : 1.0F);
		ServerLevel level = player.level();
		Vec3 chest = player.position().add(0.0, 1.1, 0.0);
		MagicFx.ring(level, chest, null, 0.9F + field.blows * 0.2F, GOLD, 8);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.PLAYERS, 0.8F, 0.8F + field.blows * 0.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.field.kept", PickKit.gauge(field.blows / (float)SLOTS, SLOTS), field.blows, SLOTS)
			.withStyle(ChatFormatting.GOLD));
	}

	private static void release(ServerPlayer player, Field field, boolean vented) {
		FIELDS.remove(player.getUUID());
		ServerLevel level = player.level();
		Vec3 centre = player.position().add(0.0, 1.0, 0.0);
		double radius = BLAST_BASE + BLAST_PER_BLOW * field.blows;
		float damage = Mth.clamp(field.stored * BLAST_SHARE, BLAST_MIN, BLAST_MAX);
		MagicFx.blast(level, centre, (float)radius * 0.6F, GOLD);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), null, (float)radius, GOLD, 14);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		Shakes.send(player, 0.5F + field.blows * 0.25F, centre);
		double knock = KNOCK_BASE + KNOCK_PER_BLOW * field.blows;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(centre, radius), c -> PickKit.foe(player, c))) {
			if (e.position().add(0.0, e.getBbHeight() * 0.5, 0.0).distanceTo(centre) > radius) {
				continue;
			}
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().playerAttack(player), damage);
			Vec3 away = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : away.normalize();
			e.push(away.x * knock, 0.25, away.z * knock);
			e.syncVelocity = true;
		}
		player.sendOverlayMessage(Component.translatable(vented ? "message.jaymospowers.rig_picks.field.vented" : "message.jaymospowers.rig_picks.field.released", field.blows)
			.withStyle(ChatFormatting.GOLD));
	}

	static void tick(ServerPlayer player) {
		Field field = FIELDS.get(player.getUUID());
		if (field == null) {
			return;
		}
		if (!player.isAlive() || !Rig.wearsAny(player)) {
			FIELDS.remove(player.getUUID());
			return;
		}
		long now = player.level().getGameTime();
		if (now >= field.until) {
			if (field.blows > 0) {
				release(player, field, true);
			} else {
				FIELDS.remove(player.getUUID());
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.field.down").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (now % RING_EVERY == 0) {
			MagicFx.ring(player.level(), player.position().add(0.0, 0.1 + field.blows * 0.5, 0.0), null, 0.9F, GOLD, 10);
		}
	}
}
