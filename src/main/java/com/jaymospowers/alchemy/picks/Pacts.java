package com.jaymospowers.alchemy.picks;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.monster.AetherEffects;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.power.chrono.ChronoFlow;
import com.jaymospowers.power.darktide.DarktideState;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.zfighter.ZFighterNpcs;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The two pacts: one-way, permanent choices for a holder whose power is already corrupted, one pact for a person. Nothing
 * undoes them: cures, cleansing, wishes and the Draught of Release ({@link GivingUp}) leave them alone, and a pact-bound
 * drinker is refused the draught. Each shows as a small permanent status icon ({@link #CINDER}, {@link #HOLLOW}).
 * <ul>
 * <li><b>Cinder Pact</b>: offered by a demon master (his talk screen, to a corrupted holder, in two steps). Slower, much
 * tougher; everything they touch catches light (their hits ignite, mobs that hit them ignite), fire cannot hurt them, and
 * water hurts a little.</li>
 * <li><b>Hollow Pact</b>: no one offers it. A corrupted holder who carries a Maw void shard for {@link #WHISPER_TICKS} starts
 * to hear (a few lines on the action bar, rarely) and may then accept by using the shard while sneaking under an open night
 * sky or in the Maw, twice within {@link #CONFIRM_TICKS}. Nothing can stun, root or hold them, their hits shove hard, and
 * every hurt lands {@link #HOLLOW_HURT} times as hard.</li>
 * </ul>
 */
public final class Pacts {
	// ---- tuning: the Cinder Pact ----
	/** Speed lost (a share), max health and armour toughness gained. */
	public static final double CINDER_SPEED = -0.15, CINDER_HEALTH = 12.0, CINDER_TOUGHNESS = 4.0;
	/** Seconds a struck mob burns, and a mob that struck them. */
	public static final float CINDER_BURN = 6.0F, CINDER_RETURN = 4.0F;
	/** Water's damage every second they stand in it or rain. */
	public static final float CINDER_WATER = 1.0F;
	// ---- tuning: the Hollow Pact ----
	/** How much harder every hurt lands. */
	public static final float HOLLOW_HURT = 1.2F;
	/** The shove a hit gives (sideways, up). */
	public static final double HOLLOW_SHOVE = 1.5, HOLLOW_LIFT = 0.35;
	/** Slowness from this amplifier is a root, and is cleared. */
	public static final int ROOT_AMPLIFIER = 4;
	/** Days (24000 ticks) a shard is carried before it whispers, how often a whisper may come (ticks), its chance each time. */
	public static final long WHISPER_TICKS = 3 * 24000L;
	public static final int WHISPER_EVERY = 1200;
	public static final float WHISPER_CHANCE = 0.12F;
	/** How many whisper lines there are. */
	public static final int WHISPER_LINES = 3;
	/** The two-step: the second use must come within this many ticks. */
	public static final int CONFIRM_TICKS = 20 * 6;

	public static final String CINDER_ID = "cinder", HOLLOW_ID = "hollow";

	/** The pact someone is bound by ({@code "cinder"} or {@code "hollow"}). Saved, kept through death, synced to all. */
	public static final AttachmentType<String> PACT = AttachmentRegistry.create(JaymosPowers.id("pact"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));
	/** Ticks a corrupted holder has carried a void shard. */
	public static final AttachmentType<Long> CARRIED = AttachmentRegistry.create(JaymosPowers.id("pact_shard_carried"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());
	/** The whispers heard so far. */
	public static final AttachmentType<Integer> HEARD = AttachmentRegistry.create(JaymosPowers.id("pact_whispers_heard"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** Game time the first use of the shard stands until. */
	private static final AttachmentType<Long> ASKED = AttachmentRegistry.create(JaymosPowers.id("pact_asked"));

	public static Holder<MobEffect> CINDER, HOLLOW;

	private static final Identifier SPEED_ID = JaymosPowers.id("pact_cinder_speed"), HEALTH_ID = JaymosPowers.id("pact_cinder_health"),
		TOUGHNESS_ID = JaymosPowers.id("pact_cinder_toughness");
	private static final Identifier SHARD = JaymosPowers.id("void_shard_ingot");
	private static final Identifier MAW = JaymosPowers.id("maw");

	private Pacts() {
	}

	static void init() {
		CINDER = register("cinder_pact", 0xD2562B);
		HOLLOW = register("hollow_pact", 0x5B4A8C);
		UseItemCallback.EVENT.register(Pacts::useShard);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Pacts::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Pacts::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(Pacts::tick);
	}

	private static Holder<MobEffect> register(String id, int colour) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id(id)),
			new MobEffect(MobEffectCategory.NEUTRAL, colour) {
			});
	}

	/** The pact {@code player} is bound by, or null. */
	public static @Nullable String pactOf(net.minecraft.world.entity.player.Player player) {
		return player.getAttached(PACT);
	}

	public static boolean bound(net.minecraft.world.entity.player.Player player) {
		return player.hasAttached(PACT);
	}

	public static boolean hollow(LivingEntity entity) {
		return HOLLOW_ID.equals(entity.getAttached(PACT));
	}

	public static boolean cinder(LivingEntity entity) {
		return CINDER_ID.equals(entity.getAttached(PACT));
	}

	/** Takes a pact. False if they already have one or their power is not corrupted. */
	public static boolean take(ServerPlayer player, String pact) {
		if (bound(player) || !Fate.isCorrupted(player)) {
			return false;
		}
		player.setAttached(PACT, pact);
		player.removeAttached(ASKED);
		refresh(player, pact);
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		if (CINDER_ID.equals(pact)) {
			player.heal((float)CINDER_HEALTH);
			MagicFx.blast(level, at, 2.0F, 0xFF8A2A);
			level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pact.cinder").withStyle(ChatFormatting.GOLD));
		} else {
			MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 2.4F, 0x6B58A8);
			level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 1.0F, 0.5F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pact.hollow").withStyle(ChatFormatting.DARK_PURPLE));
		}
		return true;
	}

	/** Puts the pact's gains and costs on the player, and its icon (safe to repeat). */
	private static void refresh(ServerPlayer player, String pact) {
		Holder<MobEffect> icon = CINDER_ID.equals(pact) ? CINDER : HOLLOW;
		if (!player.hasEffect(icon)) {
			player.addEffect(new MobEffectInstance(icon, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
		}
		if (CINDER_ID.equals(pact)) {
			modify(player, Attributes.MOVEMENT_SPEED, SPEED_ID, CINDER_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			modify(player, Attributes.MAX_HEALTH, HEALTH_ID, CINDER_HEALTH, AttributeModifier.Operation.ADD_VALUE);
			modify(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, CINDER_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
		}
	}

	private static void modify(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	// ---------------------------------------------------------------- the Cinder Pact's offer

	/**
	 * The demon master's talk option (empty unless {@code player} is a corrupted holder with no pact yet): the offer, then a
	 * second screen that asks again before it is taken. {@code back} returns to the master's menu.
	 */
	public static List<ZFighterNpcs.Option> offer(ServerPlayer player, LivingEntity master, Runnable back) {
		if (bound(player) || !Fate.isCorrupted(player)) {
			return List.of();
		}
		Runnable decline = back;
		Runnable sure = () -> ZFighterNpcs.ask(player, master, Component.translatable("talk.jaymospowers.pact.sure"), List.of(
			ZFighterNpcs.Option.of("talk.jaymospowers.pact.take", true, () -> {
				if (take(player, CINDER_ID)) {
					ZFighterNpcs.ask(player, master, Component.translatable("talk.jaymospowers.pact.done"), List.of());
				} else {
					decline.run();
				}
			}),
			ZFighterNpcs.Option.of("talk.jaymospowers.pact.no", true, decline)));
		return List.of(ZFighterNpcs.Option.of("talk.jaymospowers.pact.ask", true, () -> ZFighterNpcs.ask(player, master,
			Component.translatable("talk.jaymospowers.pact.offer"), List.of(
				ZFighterNpcs.Option.of("talk.jaymospowers.pact.go_on", true, sure),
				ZFighterNpcs.Option.of("talk.jaymospowers.pact.no", true, decline)))));
	}

	// ---------------------------------------------------------------- the Hollow Pact's shard

	private static boolean isShard(ItemStack stack) {
		return !stack.isEmpty() && SHARD.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	private static boolean carriesShard(ServerPlayer player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (isShard(stack)) {
				return true;
			}
		}
		return isShard(player.getOffhandItem());
	}

	private static boolean underOpenNight(ServerPlayer player) {
		ServerLevel level = player.level();
		return level.dimension().identifier().equals(MAW) || level.canSeeSky(player.blockPosition().above()) && level.isDarkOutside();
	}

	private static InteractionResult useShard(net.minecraft.world.entity.player.Player who, Level level, net.minecraft.world.InteractionHand hand) {
		ItemStack stack = who.getItemInHand(hand);
		if (level.isClientSide() || !(who instanceof ServerPlayer player) || !isShard(stack) || !player.isShiftKeyDown() || bound(player)
			|| !Fate.isCorrupted(player) || player.getAttachedOrElse(CARRIED, 0L) < WHISPER_TICKS || player.getAttachedOrElse(HEARD, 0) < 1
			|| !underOpenNight(player)) {
			return InteractionResult.PASS;
		}
		Long asked = player.getAttached(ASKED);
		if (asked == null || level.getGameTime() > asked) {
			player.setAttached(ASKED, level.getGameTime() + CONFIRM_TICKS);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pact.hollow_ask").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
			return InteractionResult.SUCCESS;
		}
		if (take(player, HOLLOW_ID)) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- damage

	/** What a hit does to a pact-bound victim's size: the Hollow's hurts land harder. */
	static float incoming(LivingEntity victim, DamageSource source, float amount) {
		if (hollow(victim) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount * HOLLOW_HURT;
		}
		return amount;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (cinder(entity) && source.is(DamageTypeTags.IS_FIRE)) {
			entity.clearFire();
			return false;
		}
		return true;
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (taken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living && source.getDirectEntity() == living ? living : null;
		if (attacker == null) {
			return;
		}
		if (cinder(attacker) && attacker != entity) {
			entity.igniteForSeconds(CINDER_BURN);
		}
		if (cinder(entity) && attacker != entity) {
			attacker.igniteForSeconds(CINDER_RETURN);
		}
		if (hollow(attacker) && attacker != entity) {
			Vec3 away = entity.position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
			Vec3 dir = away.lengthSqr() < 1.0E-4 ? attacker.getLookAngle().multiply(1.0, 0.0, 1.0) : away;
			dir = dir.normalize();
			entity.push(dir.x * HOLLOW_SHOVE, HOLLOW_LIFT, dir.z * HOLLOW_SHOVE);
			entity.syncVelocity = true;
			MagicFx.ring(level, entity.position().add(0.0, 0.1, 0.0), 1.2F, 0x6B58A8);
		}
	}

	// ---------------------------------------------------------------- the passing time

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}
			String pact = pactOf(player);
			if (pact == null) {
				if (player.tickCount % 20 == 0) {
					carry(player);
				}
				continue;
			}
			if (HOLLOW_ID.equals(pact)) {
				free(player);
			} else if (player.isOnFire()) {
				player.clearFire();
			}
			if (player.tickCount % 20 == 0) {
				refresh(player, pact);
				if (CINDER_ID.equals(pact) && player.isInWaterOrRain()) {
					player.hurtServer(player.level(), player.level().damageSources().generic(), CINDER_WATER);
				}
			}
		}
	}

	/** The Hollow's freedom: no stun, trip, hold or root stays on them. */
	private static void free(ServerPlayer player) {
		player.removeAttached(DarktideState.STUN);
		player.removeAttached(DarktideState.CRIPPLE);
		player.removeAttached(ChronoFlow.STUN);
		player.removeAttached(Tripped.TRIP);
		player.removeAttached(GravityPower.HELD);
		player.removeAttached(WebPower.HELD);
		if (AetherEffects.WEBBED != null && player.hasEffect(AetherEffects.WEBBED)) {
			player.removeEffect(AetherEffects.WEBBED);
		}
		MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
		if (slow != null && slow.getAmplifier() >= ROOT_AMPLIFIER) {
			player.removeEffect(MobEffects.SLOWNESS);
		}
	}

	/** A corrupted holder carrying a void shard: the days add up, and the shard begins to whisper. */
	private static void carry(ServerPlayer player) {
		if (!Fate.isCorrupted(player) || !carriesShard(player)) {
			return;
		}
		long carried = player.getAttachedOrElse(CARRIED, 0L) + 20;
		player.setAttached(CARRIED, carried);
		if (carried < WHISPER_TICKS || carried % WHISPER_EVERY >= 20 || player.getRandom().nextFloat() >= WHISPER_CHANCE) {
			return;
		}
		int heard = player.getAttachedOrElse(HEARD, 0);
		player.setAttached(HEARD, heard + 1);
		int line = player.getRandom().nextInt(WHISPER_LINES) + 1;
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.pact.whisper." + line).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.7F, 0.5F);
	}
}
