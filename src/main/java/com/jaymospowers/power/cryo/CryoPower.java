package com.jaymospowers.power.cryo;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Seasons;
import com.jaymospowers.power.wind.WindCold;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.14.0) Cryokinesis, the ice power (Andy, 2026-10-17: an ice power whose synergy is "with smoke instead of fire").
 * Everything is decided here, on the server.
 * <ul>
 * <li>R, Ice Slide ({@link CryoSlide}): a toggle; you skate on a lane of ice frozen under and ahead of you (Sprint for
 * speed, inertia, hunger; the client skates you), water freezes into a bridge that melts behind you. The starter.</li>
 * <li>G (hold), Frost Breath ({@link CryoBreath}): a cone of cold that slows, freezes and turns water to ice.</li>
 * <li>V, Ice Shards ({@link CryoShards}): a fan of five shards that hit and slow.</li>
 * <li>B, Flash Freeze ({@link CryoFlashFreeze}): what you look at is shut in ice for 3 s.</li>
 * <li>X, Ice Wall ({@link CryoWall}): a wall of packed ice where you look; it melts.</li>
 * <li>Z, Snow mode ({@link #SNOW}): Frost Breath and Ice Shards turn to snow; they slow and blind but never hurt.</li>
 * <li>C, Blizzard ({@link CryoBlizzard}): a whiteout round you; the capstone of mastery (no fate needed, see
 * {@link #ultimateAnswers}).</li>
 * <li>Frost-born (the Z Fighter lineage, which Kingdoms' heritage gives anyone): freezing never hurts them, the calling
 * comes sooner ({@code core/Callings}), and in the cold the power is a little stronger ({@link #strength}).</li>
 * <li>Freezing fog: the breath, the shards, Flash Freeze and the Blizzard freeze any smoke they touch
 * ({@code SmokeField.freeze}).</li>
 * </ul>
 * Every block of ice it makes melts again ({@link CryoIce}). The way in is a calling: stand in powder snow or a
 * snowstorm, freezing, for a minute.
 */
public final class CryoPower implements Power {
	public static final CryoPower INSTANCE = new CryoPower();
	public static final Identifier ID = JaymosPowers.id("cryo");
	public static final int COLOR = 0x8FD3F5;

	// ---- tuning ----
	/** A Frost-born in the cold: the power's numbers (reach, freezing, damage, how long the ice holds) are this much stronger. */
	public static final float FROST_BORN_COLD = 1.25F;
	/** (0.15.28) Strength in winter and in summer, where there are seasons (Kitchen's). */
	static final float WINTER_STRENGTH = 1.2F, SUMMER_STRENGTH = 0.85F;
	/** How many abilities unlock Snow mode (Z, with Frost Breath) and the Ice Wall (X). */
	static final int MODE_AT = 2;
	static final int WALL_AT = 3;

	/** The owner's HUD state (cooldowns, what's running). Not saved: a relog clears cooldowns; death keeps them. */
	public static final AttachmentType<CryoState> STATE = AttachmentRegistry.create(JaymosPowers.id("cryo_state"),
		builder -> builder.copyOnDeath().syncWith(CryoState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** Snow mode (Z) is on: saved, synced to the user (the HUD). */
	public static final AttachmentType<Boolean> SNOW = AttachmentRegistry.create(JaymosPowers.id("cryo_snow"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	private CryoPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}. */
	public static void init() {
		Powers.register(INSTANCE);
		CryoNet.register();
		CryoIce.init();
		CryoShards.init();
		CryoFlashFreeze.init();
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			// a Frost-born Cryokinesis user: freezing never hurts them
			!(entity instanceof ServerPlayer player && source.is(DamageTypes.FREEZE) && Powers.has(player, INSTANCE) && frostBorn(player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CryoSlide.clear();
			CryoBreath.clear();
			CryoShards.clear();
			CryoBlizzard.clear();
		});
	}

	private static void forget(ServerPlayer player) {
		CryoSlide.stop(player, false);
		CryoBreath.stop(player, false);
		CryoBlizzard.end(player, false);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.BLUE_ICE);
	}

	/** R (Ice Slide) first, then G (Frost Breath), V (Ice Shards), B (Flash Freeze). */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_3, PowerKey.ABILITY_4};
	}

	/** A fresh awakening skates (slower) on R. */
	@Override
	public @Nullable PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/** Z (Snow mode) comes with Frost Breath; X (Ice Wall) with the third ability. */
	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return "mode".equals(slot) ? Mastery.unlocked(player) >= MODE_AT : !"special".equals(slot) || Mastery.unlocked(player) >= WALL_AT;
	}

	/** C (the Blizzard) answers once every ability is mastered; no fate is needed (Andy's fates question is still open). */
	@Override
	public boolean ultimateAnswers(Player player) {
		return Mastery.unlocked(player) >= Mastery.ALL;
	}

	@Override
	public void onGained(ServerPlayer player) {
		player.setAttached(STATE, CryoState.NONE);
	}

	@Override
	public void onLost(ServerPlayer player) {
		forget(player);
		player.removeAttached(STATE);
		player.removeAttached(SNOW);
	}

	@Override
	public void tick(ServerPlayer player) {
		CryoSlide.tick(player);
		CryoBreath.tick(player);
		CryoBlizzard.tick(player);
	}

	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		if (!slotUnlocked(player, "mode") && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.locked").withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean on = !snow(player);
		if (on) {
			player.setAttached(SNOW, true);
		} else {
			player.removeAttached(SNOW);
		}
		player.sendOverlayMessage(Component.translatable(on ? "message.jaymospowers.cryo_snow_on" : "message.jaymospowers.cryo_snow_off")
			.withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.8F,
			on ? 1.4F : 0.8F);
	}

	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (down) {
			CryoBlizzard.start(player);
		}
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		switch (key) {
			case ABILITY_1 -> {
				if (down) {
					CryoSlide.toggle(player);
				}
			}
			case ABILITY_2 -> {
				if (down) {
					CryoBreath.start(player);
				} else {
					CryoBreath.stop(player, true);
				}
			}
			case ABILITY_3 -> {
				if (down && ready(player, CryoState.SHARDS)) {
					CryoShards.fire(player);
				}
			}
			case ABILITY_4 -> {
				if (down && ready(player, CryoState.FREEZE)) {
					CryoFlashFreeze.cast(player);
				}
			}
		}
	}

	// ---------------------------------------------------------------- shared

	public static CryoState state(Player player) {
		CryoState state = player.getAttached(STATE);
		return state == null ? CryoState.NONE : state;
	}

	static void set(ServerPlayer player, CryoState state) {
		player.setAttached(STATE, state);
	}

	static boolean ready(ServerPlayer player, int slot) {
		return state(player).left(slot, player.level().getGameTime()) <= 0;
	}

	static void cooldown(ServerPlayer player, int slot, int ticks) {
		set(player, state(player).withCooldown(slot, player.level().getGameTime(), ticks));
	}

	/** Every ability unlocked. */
	static boolean mastered(Player player) {
		return Mastery.unlocked(player) >= Mastery.ALL;
	}

	/** Snow mode is on (both sides). */
	public static boolean snow(Player player) {
		return Boolean.TRUE.equals(player.getAttached(SNOW));
	}

	/** Born to the Frost lineage (Powers knows it through the Z Fighter lineage; Kingdoms' heritage sets it). */
	public static boolean frostBorn(Player player) {
		return ZFighterLineage.of(player) == ZFighterLineage.FROST;
	}

	/**
	 * The power's strength: {@link #FROST_BORN_COLD} for a Frost-born where it would snow, else 1; (0.15.28) times
	 * {@link #WINTER_STRENGTH} in winter and {@link #SUMMER_STRENGTH} in summer in a land with seasons ({@link Seasons}).
	 */
	public static float strength(Player player) {
		float base = frostBorn(player) && WindCold.isCold(player) ? FROST_BORN_COLD : 1.0F;
		Seasons.Season season = Seasons.at(player);
		return season == Seasons.Season.WINTER ? base * WINTER_STRENGTH : season == Seasons.Season.SUMMER ? base * SUMMER_STRENGTH : base;
	}

	/** Too hungry to keep a held move up (creative never is). */
	static boolean spent(ServerPlayer player, int minFood) {
		return !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= minFood;
	}
}
