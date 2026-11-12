package com.jaymospowers.power.xenoform.minds;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.GreatFoes;
import com.jaymospowers.power.xenoform.XenoDouble;
import com.jaymospowers.power.xenoform.XenoMinds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/**
 * Mirage wears a face: sneak + V while looking at a creature or a player takes its look (the model and, for a player, the skin
 * and name plate; {@code MirageFaceClient} draws it for everyone), and again, or at nothing, puts your own back. Mobs of the
 * kind you wear lose any hold on you and don't take up a fight with you until you strike. The face drops when you strike
 * anything, when a single blow of {@link #BREAK_DAMAGE} or more lands, or after {@link #FACE_TICKS}. It is a look only: your size,
 * health and moves are your own, and a great foe's face can't be taken.
 */
public final class MirageFace {
	// ---- tuning ----
	public static final double REACH = 24.0, KIN_RANGE = 20.0;
	public static final int FACE_TICKS = 4800, KIN_EVERY = 4, FACE_COOLDOWN = 40;
	public static final float BREAK_DAMAGE = 4.0F;
	public static final int COLOR = 0x2E4AA0;

	private record Worn(EntityType<?> type, boolean player, long until) {
	}

	private static final Map<UUID, Worn> WORN = new HashMap<>();

	private MirageFace() {
	}

	public static boolean wearing(ServerPlayer player) {
		return WORN.containsKey(player.getUUID());
	}

	/** Sneak + V. */
	public static void toggle(ServerPlayer player) {
		if (!XenoMinds.ready(player, "mirage_face")) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity target = MindsKit.aimed(player, REACH, e -> !(e instanceof XenoDouble) && !(e instanceof ArmorStand) && !GreatFoes.is(e));
		if (target == null) {
			if (wearing(player)) {
				drop(player, true);
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.mirage.no_face").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		String face;
		boolean isPlayer = target instanceof Player;
		if (target instanceof Player other) {
			face = "p:" + other.getUUID() + ":" + other.getGameProfile().name();
		} else {
			face = "e:" + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
		}
		WORN.put(player.getUUID(), new Worn(target.getType(), isPlayer, level.getGameTime() + FACE_TICKS));
		player.setAttached(XenoMinds.FACE, face);
		MagicFx.blast(level, player.position().add(0.0, 1.0, 0.0), 1.4F, COLOR);
		MindsKit.sound(player, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.mirage.face", target.getDisplayName()).withStyle(ChatFormatting.BLUE));
		XenoMinds.cool(player, "mirage_face", FACE_COOLDOWN);
	}

	/** Puts the player's own face back. */
	public static void drop(ServerPlayer player, boolean fx) {
		if (WORN.remove(player.getUUID()) == null) {
			return;
		}
		player.removeAttached(XenoMinds.FACE);
		if (fx) {
			ServerLevel level = player.level();
			MagicFx.blast(level, player.position().add(0.0, 1.0, 0.0), 1.2F, COLOR);
			MindsKit.sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9F, 1.4F);
		}
	}

	public static void forget(ServerPlayer player) {
		drop(player, false);
	}

	/** A blow on the face: a hard one drops it. */
	public static void struck(ServerPlayer player, float amount) {
		if (amount >= BREAK_DAMAGE) {
			drop(player, true);
		}
	}

	/** Every tick as Mirage: the kin leave you be, and the face wears off. */
	public static void tick(ServerPlayer player) {
		Worn worn = WORN.get(player.getUUID());
		if (worn == null) {
			return;
		}
		ServerLevel level = player.level();
		if (level.getGameTime() >= worn.until) {
			drop(player, true);
			return;
		}
		if (worn.player || player.tickCount % KIN_EVERY != 0) {
			return;
		}
		List<Mob> kin = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(KIN_RANGE), m -> m.getType() == worn.type && m.isAlive());
		for (Mob mob : kin) {
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (mob instanceof NeutralMob angry && angry.isAngry() && mob.getLastHurtByMob() != player) {
				angry.stopBeingAngry();
			}
		}
	}

	/** The look was a creature of this entity: is {@code other} one of its kind. Helper for lookups by other moves. */
	public static boolean kin(ServerPlayer player, Entity other) {
		Worn worn = WORN.get(player.getUUID());
		return worn != null && !worn.player && other.getType() == worn.type;
	}
}
