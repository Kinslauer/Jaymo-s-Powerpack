package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Z Fighter: a martial artist who fights with his fists and his ki. Everything is decided here, on the server.
 * <ul>
 * <li>Ki: a meter ({@link #BASE_MAX_KI}, more with every foe that ever beat him) that trickles back on a full stomach,
 * comes in fast while charging, from landing punches, and from eating. The techniques spend it.</li>
 * <li>R (hold), Charge Ki: stand your ground, the aura flares, ki pours in (hungry work).</li>
 * <li>G, Ki Blast; V (hold), Ki Wave; B, Flash Step ({@link ZFighterKi}).</li>
 * <li>Z, Flight: ki-borne, creative-style flight that drains ki.</li>
 * <li>C (ascended), Super: the form reached through hardship ({@link ZFighterBattle}); fists and ki hit far harder,
 * the aura turns gold, ki burns away while it lasts.</li>
 * <li>Passive: fists only (weapons won't strike, {@code ZFighterPlayerMixin}), strong fists that grow with mastery,
 * five-hit combos, an enormous appetite, and no foe fells him twice ({@link ZFighterBattle}).</li>
 * </ul>
 */
public final class ZFighterPower implements Power {
	public static final ZFighterPower INSTANCE = new ZFighterPower();
	public static final Identifier ID = JaymosPowers.id("zfighter");
	public static final int COLOR = 0xFF8C1A;
	public static final int SUPER_COLOR = 0xFFD54A;

	// ---- ki ----
	public static final int BASE_MAX_KI = 100;
	/** Each foe that ever beat him raises his ceiling this much (up to {@link #ZENKAI_CAP} foes). */
	static final int KI_PER_DEFEAT = 5;
	/** How many different victors count towards the growth. */
	static final int ZENKAI_CAP = 20;
	/** Ki back per tick on a full stomach, not charging, not Super (0.5 a second). */
	static final float KI_REGEN = 0.025F;
	/** Ki per tick while charging (R); the fresh awakening's weak charge. */
	static final float CHARGE_RATE = 1.0F;
	static final float STARTER_CHARGE_RATE = 0.4F;
	/** Ki per hunger point eaten. */
	static final float KI_PER_FOOD = 3.0F;
	/** Ki per punch that lands. */
	static final float KI_PER_PUNCH = 2.0F;

	// ---- the appetite (exhaustion per tick; 4 exhaustion is one hunger point) ----
	static final float BASE_EXHAUSTION = 0.01F;
	static final float CHARGE_EXHAUSTION = 0.05F;
	static final float FLIGHT_EXHAUSTION = 0.01F;
	/** Super burns this many times the base appetite. */
	static final float SUPER_HUNGER = 2.5F;
	/** At or below this food level he's hungry: no ki comes back by itself and his fists weaken. */
	static final int HUNGRY_FOOD = 6;
	static final double HUNGRY_WEAKNESS = 0.3;

	// ---- fists (added to the player's attack damage) ----
	static final double FIST_DAMAGE = 3.0;
	static final double FIST_PER_ABILITY = 0.75;
	static final double FIST_PER_DEFEAT = 0.25;
	static final double ASCENDED_FIST = 1.0;

	// ---- movement ----
	/** Charging (R) or gathering a Ki Wave (V) slows you this much. */
	static final double CHARGE_SLOW = 0.6;
	/** Ki per tick while flying (2 a second), and the flying speed (creative's is 0.05). */
	static final float FLY_KI = 0.1F;
	static final float FLY_SPEED = 0.08F;
	static final float SUPER_FLY_SPEED = 0.12F;
	private static final float NORMAL_FLY_SPEED = 0.05F;
	/** Mastery needed for Z (Flight): abilities unlocked. */
	static final int FLIGHT_UNLOCK = 2;

	// ---- Super ----
	static final int SUPER_MIN_KI = 30;
	/** Ki per tick while Super (1.5 a second). */
	static final float SUPER_KI = 0.075F;
	static final double SUPER_FIST = 0.6;
	static final double SUPER_SPEED = 0.25;
	/** Ki techniques hit this many times harder while Super. */
	static final float SUPER_KI_POWER = 1.5F;
	static final int SUPER_COOLDOWN = 200;
	static final double SUPER_BURST_RADIUS = 5.0;

	private static final Identifier FIST_ID = JaymosPowers.id("zfighter_fists");
	private static final Identifier SUPER_FIST_ID = JaymosPowers.id("zfighter_super_fists");
	private static final Identifier HUNGRY_ID = JaymosPowers.id("zfighter_hungry");
	private static final Identifier SLOW_ID = JaymosPowers.id("zfighter_charging");
	private static final Identifier SUPER_SPEED_ID = JaymosPowers.id("zfighter_super_speed");

	/** The owner's HUD state (ki, cooldowns, what he's doing). Not saved: a relog clears it. */
	public static final AttachmentType<ZFighterState> STATE = AttachmentRegistry.create(JaymosPowers.id("zfighter_state"),
		builder -> builder.syncWith(ZFighterState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** Every foe that has ever beaten him (entity type ids, or {@code player:<uuid>}). Saved, kept through death. */
	public static final AttachmentType<List<String>> DEFEATS = AttachmentRegistry.create(JaymosPowers.id("zfighter_defeats"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** Hardship lived through (the road to Super). Saved, kept through death. */
	public static final AttachmentType<Integer> HARDSHIP = AttachmentRegistry.create(JaymosPowers.id("zfighter_hardship"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** What the server keeps on each Z Fighter between ticks. */
	static final class Live {
		float ki;
		boolean charging;
		long waveFrom = -1L;
		boolean flying;
		boolean superOn;
		int combo;
		long lastHit = -1000L;
		/** When his health fell into the danger zone mid-fight (-1: it hasn't). */
		long clutchFrom = -1L;
		long lastClutchCredit = -100000L;
		int lastFood = -1;
		/** Until when the movement check is off (a Flash Step). */
		long stepUntil;
		long lastGrowl = -100000L;
		long lastRefusal = -100L;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private ZFighterPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}. */
	public static void init() {
		Powers.register(INSTANCE);
		ZFighterKi.init();
		ZFighterBattle.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ZFighterDebugCommand.register(dispatcher));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LIVE.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			ZFighterKi.clear();
		});
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
		return new ItemStack(Items.BLAZE_POWDER);
	}

	/** Weak Charge Ki on R from the start; then Charge Ki in full, Ki Blast, Flash Step, and the Ki Wave last. */
	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_4, PowerKey.ABILITY_3};
	}

	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return switch (slot) {
			case "mode" -> Mastery.unlocked(player) >= FLIGHT_UNLOCK;
			case "special" -> false;
			default -> true;
		};
	}

	@Override
	public String statusSuffix(Player player) {
		return state(player).has(ZFighterState.SUPER) ? ".super" : null;
	}

	@Override
	public void onGained(ServerPlayer player) {
		player.setAttached(STATE, ZFighterState.NONE);
	}

	@Override
	public void onLost(ServerPlayer player) {
		Live live = LIVE.remove(player.getUUID());
		if (live != null && live.flying) {
			live.flying = false;
		}
		landFlight(player);
		for (Identifier id : new Identifier[] {FIST_ID, SUPER_FIST_ID, HUNGRY_ID}) {
			remove(player, Attributes.ATTACK_DAMAGE, id);
		}
		remove(player, Attributes.MOVEMENT_SPEED, SLOW_ID);
		remove(player, Attributes.MOVEMENT_SPEED, SUPER_SPEED_ID);
		player.removeAttached(STATE);
		player.removeAttached(DEFEATS);
		player.removeAttached(HARDSHIP);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		return live(player).flying;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		Live live = live(player);
		return live.flying && player.getAbilities().flying || live.stepUntil > player.level().getGameTime();
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		Live live = live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int max = maxKi(player);
		boolean creative = player.isCreative();
		int food = player.getFoodData().getFoodLevel();
		boolean hungry = food <= HUNGRY_FOOD && !creative;
		boolean starving = food <= 0 && !creative;

		// a Z Fighter eats like three men, and every bite comes back as ki
		if (live.lastFood >= 0 && food > live.lastFood) {
			live.ki += (food - live.lastFood) * KI_PER_FOOD;
		}
		live.lastFood = food;
		if (!creative) {
			player.causeFoodExhaustion(live.superOn ? BASE_EXHAUSTION * SUPER_HUNGER : BASE_EXHAUSTION);
		}
		if (hungry && now - live.lastGrowl > 600) {
			live.lastGrowl = now;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_hungry").withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6F, 0.5F);
		}

		if (live.charging) {
			if (starving) {
				live.charging = false;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_starving").withStyle(ChatFormatting.RED));
			} else {
				live.ki += Mastery.starterOnly(player) ? STARTER_CHARGE_RATE : CHARGE_RATE;
				if (!creative) {
					player.causeFoodExhaustion(CHARGE_EXHAUSTION);
				}
				aura(player, live, live.ki >= max ? 6 : 3);
				if (now % 16 == 0) {
					float fill = Mth.clamp(live.ki / max, 0.0F, 1.0F);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F,
						0.7F + fill * 0.9F);
				}
			}
		} else if (!live.superOn && !hungry && live.waveFrom < 0) {
			live.ki += KI_REGEN;
		}

		ZFighterKi.tickWave(player, live);

		// flight: ki-borne, and it burns ki only while actually in the air
		if (live.flying) {
			if (player.getAbilities().flying) {
				live.ki -= FLY_KI;
				if (!creative) {
					player.causeFoodExhaustion(FLIGHT_EXHAUSTION);
				}
				if (now % 4 == 0) {
					level.sendParticles(dust(live), true, false, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
				}
				if (live.ki <= 0.0F) {
					endFlight(player, live);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
				}
			}
		} else if (player.getAbilities().mayfly && !creative && !player.isSpectator()) {
			// left over from a flight cut short (a death, a relog): nobody else gives a Z Fighter wings
			landFlight(player);
		}

		if (live.superOn) {
			live.ki -= SUPER_KI;
			if (now % 2 == 0) {
				aura(player, live, 2);
			}
			if (now % 7 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.4, 0.6, 0.4,
					0.1);
			}
			if (live.ki <= 0.0F) {
				endSuper(player, live);
			}
		}

		live.ki = Mth.clamp(live.ki, 0.0F, max);
		ZFighterBattle.tick(player, live);
		if (now % 10 == 0) {
			refresh(player, live);
		}
		sync(player, live, max);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		Live live = live(player);
		switch (key) {
			case ABILITY_1 -> {
				if (down && player.getFoodData().getFoodLevel() <= 0 && !player.isCreative()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_starving").withStyle(ChatFormatting.RED));
					return;
				}
				live.charging = down;
				if (down) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F,
						1.4F);
				}
				refresh(player, live);
			}
			case ABILITY_2 -> {
				if (down && ready(player, ZFighterState.BLAST) && ZFighterKi.blast(player, live)) {
					cooldown(player, ZFighterState.BLAST, ZFighterKi.BLAST_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (down) {
					if (ready(player, ZFighterState.WAVE)) {
						ZFighterKi.startWave(player, live);
					}
				} else if (ZFighterKi.releaseWave(player, live)) {
					cooldown(player, ZFighterState.WAVE, ZFighterKi.WAVE_COOLDOWN);
				}
				refresh(player, live);
			}
			case ABILITY_4 -> {
				if (down && ready(player, ZFighterState.STEP) && ZFighterKi.step(player, live)) {
					cooldown(player, ZFighterState.STEP, ZFighterKi.STEP_COOLDOWN);
				}
			}
		}
		sync(player, live, maxKi(player));
	}

	/** Z: take off on your ki, or come down. */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (!down || !slotUnlocked(player, "mode")) {
			return;
		}
		Live live = live(player);
		if (live.flying) {
			endFlight(player, live);
		} else if (live.ki < 5.0F) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
		} else {
			live.flying = true;
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.getAbilities().setFlyingSpeed(live.superOn ? SUPER_FLY_SPEED : FLY_SPEED);
			player.onUpdateAbilities();
			player.resetFallDistance();
			player.push(0.0, 0.5, 0.0);
			player.syncVelocity = true;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 0.8F);
			player.level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 12, 0.4, 0.05, 0.4, 0.05);
		}
		sync(player, live, maxKi(player));
	}

	/** C: go Super, or let it go. */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		Live live = live(player);
		if (live.superOn) {
			endSuper(player, live);
		} else if (ready(player, ZFighterState.SUPER_SLOT)) {
			if (live.ki < SUPER_MIN_KI) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			} else {
				enterSuper(player, live);
			}
		}
		sync(player, live, maxKi(player));
	}

	// ---------------------------------------------------------------- Super and flight

	/** The transformation: a blast of gold that throws back everything near. */
	static void enterSuper(ServerPlayer player, Live live) {
		live.superOn = true;
		ServerLevel level = player.level();
		Vec3 center = player.position();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SUPER_BURST_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 away = near.position().subtract(center);
			double distance = Math.max(0.5, away.length());
			if (distance > SUPER_BURST_RADIUS) {
				continue;
			}
			double strength = 1.6 * (1.0 - distance / (SUPER_BURST_RADIUS + 1.0));
			ZFighterKi.push(near, away, strength, 0.4);
		}
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE08A), true, true, center.x, center.y + 1.0, center.z, 1, 0.0,
			0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, center.x, center.y + 0.5, center.z, 6, 1.2, 0.4, 1.2, 0.0);
		level.sendParticles(new DustParticleOptions(SUPER_COLOR, 2.0F), true, false, center.x, center.y + 1.0, center.z, 60, 1.2, 1.2, 1.2, 0.2);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.4F);
		if (live.flying) {
			player.getAbilities().setFlyingSpeed(SUPER_FLY_SPEED);
			player.onUpdateAbilities();
		}
		refresh(player, live);
	}

	static void endSuper(ServerPlayer player, Live live) {
		if (!live.superOn) {
			return;
		}
		live.superOn = false;
		cooldown(player, ZFighterState.SUPER_SLOT, SUPER_COOLDOWN);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		if (live.flying) {
			player.getAbilities().setFlyingSpeed(FLY_SPEED);
			player.onUpdateAbilities();
		}
		refresh(player, live);
	}

	static void endFlight(ServerPlayer player, Live live) {
		live.flying = false;
		landFlight(player);
	}

	/** Takes the wings away (creative and spectator keep theirs). The fall from here is a real fall. */
	private static void landFlight(ServerPlayer player) {
		player.getAbilities().setFlyingSpeed(NORMAL_FLY_SPEED);
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
		}
		player.onUpdateAbilities();
	}

	/** A few motes of the aura round the player, rising (white-orange; gold when Super). Everyone sees it. */
	static void aura(ServerPlayer player, Live live, int count) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		DustParticleOptions dust = dust(live);
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = 0.45 + random.nextDouble() * 0.25;
			double h = random.nextDouble() * player.getBbHeight();
			level.sendParticles(dust, true, false, player.getX() + Math.cos(a) * r, player.getY() + h, player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0,
				0.0, 0.15);
		}
	}

	static DustParticleOptions dust(Live live) {
		return new DustParticleOptions(live.superOn ? SUPER_COLOR : 0xFFE6B0, live.superOn ? 1.6F : 1.2F);
	}

	// ---------------------------------------------------------------- attributes

	/** Fists, Super, hunger and the charging stance, as attribute modifiers (only rewritten when they change). */
	static void refresh(ServerPlayer player, Live live) {
		boolean hungry = player.getFoodData().getFoodLevel() <= HUNGRY_FOOD && !player.isCreative();
		set(player, Attributes.ATTACK_DAMAGE, FIST_ID, fistBonus(player), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ATTACK_DAMAGE, SUPER_FIST_ID, live.superOn ? SUPER_FIST : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.ATTACK_DAMAGE, HUNGRY_ID, hungry ? -HUNGRY_WEAKNESS : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, SLOW_ID, live.charging || live.waveFrom >= 0 ? -CHARGE_SLOW : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, SUPER_SPEED_ID, live.superOn ? SUPER_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** What his bare fists add to a punch: mastery, every foe that ever beat him, and ascension. */
	static double fistBonus(Player player) {
		return FIST_DAMAGE + FIST_PER_ABILITY * Mastery.unlocked(player) + FIST_PER_DEFEAT * zenkai(player)
			+ (Fate.isAscended(player) ? ASCENDED_FIST : 0.0);
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			if (instance.getModifier(id) != null) {
				instance.removeModifier(id);
			}
			return;
		}
		AttributeModifier now = instance.getModifier(id);
		if (now == null || now.amount() != amount || now.operation() != operation) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	// ---------------------------------------------------------------- state

	static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player.getUUID(), id -> new Live());
	}

	/** Forgets the live state (a death: the next life starts with no ki, on the ground, not Super). */
	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
	}

	public static ZFighterState state(Player player) {
		ZFighterState state = player.getAttached(STATE);
		return state == null ? ZFighterState.NONE : state;
	}

	static void set(ServerPlayer player, ZFighterState state) {
		player.setAttached(STATE, state);
	}

	static boolean ready(ServerPlayer player, int slot) {
		return state(player).left(slot, player.level().getGameTime()) <= 0;
	}

	static void cooldown(ServerPlayer player, int slot, int ticks) {
		set(player, state(player).withCooldown(slot, player.level().getGameTime(), ticks));
	}

	private static void sync(ServerPlayer player, Live live, int max) {
		int flags = (live.charging ? ZFighterState.CHARGING : 0) | (live.flying ? ZFighterState.FLYING : 0)
			| (live.superOn ? ZFighterState.SUPER : 0);
		int combo = player.level().getGameTime() - live.lastHit <= ZFighterBattle.COMBO_WINDOW ? live.combo : 0;
		ZFighterState state = state(player);
		int ki = Mth.floor(live.ki);
		if (!state.sameAs(ki, max, live.waveFrom, flags, combo)) {
			set(player, state.with(ki, max, live.waveFrom, flags, combo));
		}
	}

	/** Spends {@code cost} ki if he has it; otherwise says so and spends nothing. */
	static boolean spend(ServerPlayer player, Live live, float cost) {
		if (player.isCreative()) {
			return true;
		}
		if (live.ki < cost) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			return false;
		}
		live.ki -= cost;
		return true;
	}

	/** How many different foes have ever beaten him (up to {@link #ZENKAI_CAP}): the growth from defeat. */
	public static int zenkai(Player player) {
		return Math.min(ZENKAI_CAP, player.getAttachedOrElse(DEFEATS, List.of()).size());
	}

	public static int maxKi(Player player) {
		return BASE_MAX_KI + KI_PER_DEFEAT * zenkai(player);
	}

	public static int hardship(Player player) {
		return player.getAttachedOrElse(HARDSHIP, 0);
	}

	static void addHardship(ServerPlayer player, int amount) {
		if (amount > 0) {
			player.setAttached(HARDSHIP, hardship(player) + amount);
		}
	}

	/** Ki techniques: stronger with mastery, and half again while Super. */
	static float kiPower(ServerPlayer player, Live live) {
		return (0.75F + 0.0625F * Mastery.unlocked(player)) * (live.superOn ? SUPER_KI_POWER : 1.0F) + (Fate.isAscended(player) ? 0.1F : 0.0F);
	}
}
