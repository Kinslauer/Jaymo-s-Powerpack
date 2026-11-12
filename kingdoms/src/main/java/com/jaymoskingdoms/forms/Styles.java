package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.forms.Schools.School;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The schools' fighting styles (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: its seven lightsaber forms
 * are stat trades; ours are one a school, five in all, named for the schools). A style is how a school's student
 * fights with that school's weapon: it trades the feathers a landed swing costs, the feathers a blocked blow costs,
 * the speed of the swings and how fast feathers come back ({@link Style}; Powers applies them,
 * {@link PowersCompat#bladeStyle}).
 * <ul>
 * <li>You fight in a school's style while its weapon is in your hand and you have joined it (the first you joined, if
 * the weapon fits two).</li>
 * <li>Sneak and use a Training Post with a weapon in hand to put the styles aside and fight plain, or take them up
 * again ({@link #PLAIN}).</li>
 * <li>The style you are in shows over the belt in battle stance ({@link #ACTIVE}, synced to you).</li>
 * <li>A school's guards and duellists fight in it too: its rhythm ({@link #tempo}), how often they turn a blow in a
 * duel ({@link #turns}), and whether they tire.</li>
 * </ul>
 */
public final class Styles {
	/** How often each player's style is looked at again (ticks). */
	static final int CHECK_EVERY = 10;

	/**
	 * A school's style. For a player: feathers a landed swing costs, what a blocked blow costs (times the usual),
	 * attack speed (times the weapon's), feather regrowth (times the usual). For its guards and duellists: the wait
	 * between blows (times the usual), the chance they turn a duel blow aside, and after how many blows they tire (0:
	 * never) and how much slower they are then.
	 */
	public enum Style {
		/** The Quiet Tide: the warding style. It turns blows cheaply all day and pays for its own. */
		STILL_WATER(School.BLADE, 0.75F, 0.6F, 0.95F, 1.0F, 1.15F, 0.45F, 0, 1.0F),
		/** The Skyward Reach: the fencer's style. Cheap thrusts at full measure, a poor guard. */
		LONG_MEASURE(School.SPEAR, 0.4F, 1.4F, 1.0F, 1.1F, 1.0F, 0.25F, 0, 1.0F),
		/** The Stone Root: slow, sure, and never out of breath. */
		DEEP_ROOT(School.FIST, 0.5F, 0.8F, 0.9F, 1.3F, 1.2F, 0.3F, 0, 1.0F),
		/** The Low Lantern: quick hands and nothing behind them to block with. */
		LOW_FLAME(School.KNIFE, 0.5F, 1.5F, 1.15F, 1.0F, 0.85F, 0.15F, 14, 1.3F),
		/** The Ashen Fang: the wild style. The fastest, the dearest to block with, and it burns out. */
		RED_EMBER(School.CLAW, 1.0F, 1.8F, 1.25F, 0.85F, 0.7F, 0.05F, 8, 1.8F);

		public final School school;
		final float swing, block, speed, regen, tempo, turn, tired;
		final int tireAfter;

		Style(School school, float swing, float block, float speed, float regen, float tempo, float turn, int tireAfter, float tired) {
			this.school = school;
			this.swing = swing;
			this.block = block;
			this.speed = speed;
			this.regen = regen;
			this.tempo = tempo;
			this.turn = turn;
			this.tireAfter = tireAfter;
			this.tired = tired;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public Component displayName() {
			return Component.translatable("style.jaymoskingdoms." + key());
		}

		public static Style of(School school) {
			for (Style style : values()) {
				if (style.school == school) {
					return style;
				}
			}
			return STILL_WATER;
		}

		public static @Nullable Style byKey(@Nullable String key) {
			for (Style style : values()) {
				if (style.key().equals(key)) {
					return style;
				}
			}
			return null;
		}
	}

	/** On a player who has put the styles aside. Saved, kept through death. */
	public static final AttachmentType<Boolean> PLAIN = AttachmentRegistry.create(JaymosKingdoms.id("style_plain"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** On a player: the key of the style they are fighting in now, for their own HUD. Not saved. */
	public static final AttachmentType<String> ACTIVE = AttachmentRegistry.create(JaymosKingdoms.id("style_active"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

	/** The style each player was last found in (absent: none). */
	private static final Map<UUID, Style> LAST = new HashMap<>();

	private Styles() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Styles::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
		// sneak and use a Training Post with a weapon in hand: the styles are put aside, or taken up again
		// (an empty hand there opens the belt, Forms)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && sp.isShiftKeyDown() && !sp.getMainHandItem().isEmpty()
				&& !sp.isSpectator() && !Schools.joined(sp).isEmpty()
				&& BuiltInRegistries.BLOCK.getKey(level.getBlockState(hit.getBlockPos()).getBlock()).toString().equals(Forms.TRAINING_POST)) {
				boolean plain = !Boolean.TRUE.equals(sp.getAttached(PLAIN));
				if (plain) {
					sp.setAttached(PLAIN, true);
				} else {
					sp.removeAttached(PLAIN);
				}
				sp.sendOverlayMessage(Component.translatable(plain ? "message.jaymoskingdoms.style.plain" : "message.jaymoskingdoms.style.taken")
					.withStyle(plain ? ChatFormatting.GRAY : ChatFormatting.GOLD));
				sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5F, plain ? 1.0F : 1.5F);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
	}

	/** The style {@code player} fights in now, or null: none of their schools' weapons in hand, or the styles put aside. */
	public static @Nullable Style of(Player player) {
		if (Boolean.TRUE.equals(player.getAttached(PLAIN))) {
			return null;
		}
		for (School school : Schools.joined(player)) {
			if (school.family().holds(player.getMainHandItem())) {
				return Style.of(school);
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Style style = player.isAlive() && !player.isSpectator() ? of(player) : null;
			if (LAST.get(player.getUUID()) == style && (style == null) == (player.getAttached(ACTIVE) == null)) {
				continue;
			}
			if (style == null) {
				LAST.remove(player.getUUID());
				player.removeAttached(ACTIVE);
				PowersCompat.clearBladeStyle(player);
			} else {
				LAST.put(player.getUUID(), style);
				player.setAttached(ACTIVE, style.key());
				PowersCompat.bladeStyle(player, style.swing, style.block, style.speed, style.regen);
			}
		}
	}

	// ---- the schools' own fighters ----------------------------------------------------------------------------------------

	/** The wait between a {@code school} fighter's blows, times the usual, after {@code blows} of them (a wild style tires). */
	public static float tempo(@Nullable School school, int blows) {
		if (school == null) {
			return 1.0F;
		}
		Style style = Style.of(school);
		return style.tempo * (style.tireAfter > 0 && blows >= style.tireAfter ? style.tired : 1.0F);
	}

	/**
	 * A duel blow of {@code attacker}'s on a {@code school} duellist: true when they turned it aside in their style (the
	 * sparks and the ring are done here, and none of it lands). A tired fighter turns nothing.
	 */
	static boolean turns(ServerLevel level, School school, LivingEntity duellist, LivingEntity attacker, int blows) {
		Style style = Style.of(school);
		if (style.tireAfter > 0 && blows >= style.tireAfter || level.getRandom().nextFloat() >= style.turn) {
			return false;
		}
		duellist.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		PowersCompat.guardMet(level, duellist, duellist.getMainHandItem(), attacker, 8, 0.9F, 0);
		level.playSound(null, duellist.getX(), duellist.getY(), duellist.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.7F, 1.4F);
		return true;
	}

	/** The school whose weapon family is {@code family}. */
	static @Nullable School schoolOf(Forms.Family family) {
		for (School school : School.values()) {
			if (school.family() == family) {
				return school;
			}
		}
		return null;
	}
}
