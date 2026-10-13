package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;

/**
 * (0.5.52) The Survival pillar of the skill tree ({@link Skills}). It grows as you live off the land: harvesting ripe
 * crops, eating, living through the night, and taking a beating and living. Practice makes hunger drain slower.
 *
 * <p>Perks: Forager (an extra harvest), Second Wind (healing when you're low), Weathered (fire and frost bite less),
 * Iron Stomach (food's Hunger never takes hold), Hardy Landing (falls hurt less, never not at all; it only scales the
 * fall damage vanilla deals, so a power that cancels fall damage still cancels it) and Last Stand (once a day, a
 * killing blow leaves you on half a heart).
 */
public final class SurvivalSkill {
	/** Survival experience: a ripe crop harvested, every two hunger points eaten back, a night lived through. */
	static final int XP_CROP = 1, XP_NIGHT = 15;
	/** Taking damage and living: a point per this much damage (at least one), at most this often (ticks). */
	static final float XP_DAMAGE_PER = 4.0F;
	static final long XP_DAMAGE_TICKS = 100L;
	/** Practice: each Survival level takes this share off how fast hunger drains. */
	static final float PRACTICE_PER_LEVEL = 0.02F;
	/** Forager: per rank, the chance a ripe crop gives an extra harvest. */
	static final float FORAGER_PER_RANK = 0.08F;
	/** Second Wind: under this share of your health, Regeneration this long per rank (ticks), at most this often (ticks). */
	static final float SECOND_WIND_HEALTH = 0.3F;
	static final int SECOND_WIND_TICKS = 50;
	static final long SECOND_WIND_EVERY = 400L;
	/** Weathered: per rank, this share off how long you burn, and this chance a tick of freezing doesn't build. */
	static final double WEATHERED_BURN = 0.2;
	static final float WEATHERED_FROST = 0.25F;
	/** Hardy Landing: this share off fall damage. */
	static final double HARDY_LANDING = 0.3;
	/** Last Stand: how often it can save you (ticks); the Regeneration it leaves you with (ticks). */
	static final long LAST_STAND_EVERY = 24000L;
	static final int LAST_STAND_REGEN = 80;

	static final String BURN_ID = "skill_weathered", FALL_ID = "skill_hardy_landing";

	/** When Last Stand last saved a player (game time). Saved, kept through death. */
	static final AttachmentType<Long> LAST_STAND = AttachmentRegistry.create(JaymosKingdoms.id("last_stand"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Player → their food level last tick; game time their next damage experience; night (its day number) they're out in. */
	private static final Map<UUID, Integer> FOOD = new HashMap<>();
	private static final Map<UUID, Long> NEXT_DAMAGE_XP = new HashMap<>(), NIGHT = new HashMap<>(), NEXT_SECOND_WIND = new HashMap<>();

	private SurvivalSkill() {
	}

	static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level) || sp.isCreative() || !ripe(state)) {
				return;
			}
			Skills.grant(sp, Skills.Pillar.SURVIVAL, XP_CROP);
			int forager = Skills.rank(sp, Skills.Perk.FORAGER);
			if (forager > 0 && level.getRandom().nextFloat() < FORAGER_PER_RANK * forager) {
				List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, sp, sp.getMainHandItem());
				if (!drops.isEmpty()) {
					Block.popResource(level, pos, drops.get(level.getRandom().nextInt(drops.size())).copy());
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer sp) || !sp.isAlive() || sp.isCreative() || damageTaken <= 0.0F) {
				return;
			}
			long now = sp.level().getGameTime();
			if (NEXT_DAMAGE_XP.getOrDefault(sp.getUUID(), 0L) > now) {
				return;
			}
			NEXT_DAMAGE_XP.put(sp.getUUID(), now + XP_DAMAGE_TICKS);
			Skills.grant(sp, Skills.Pillar.SURVIVAL, Math.max(1, (int) (damageTaken / XP_DAMAGE_PER)));
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer sp) || Skills.rank(sp, Skills.Perk.LAST_STAND) == 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			long now = sp.level().getGameTime();
			Long last = sp.getAttached(LAST_STAND);
			if (last != null && now - last < LAST_STAND_EVERY && now >= last) {
				return true;
			}
			sp.setAttached(LAST_STAND, now);
			sp.setHealth(1.0F);
			sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, LAST_STAND_REGEN, 1));
			sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.skills.last_stand").withStyle(ChatFormatting.GOLD));
			return false;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer sp) {
				// (dying isn't living through the night; nor is a respawn's full belly eating)
				NIGHT.remove(sp.getUUID());
				FOOD.remove(sp.getUUID());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			NIGHT.remove(handler.player.getUUID());
			FOOD.remove(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(SurvivalSkill::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FOOD.clear();
			NEXT_DAMAGE_XP.clear();
			NIGHT.clear();
			NEXT_SECOND_WIND.clear();
		});
	}

	/** A crop ready to harvest. */
	private static boolean ripe(net.minecraft.world.level.block.state.BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		return state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		long clock = overworld.getOverworldClockTime();
		long t = Math.floorMod(clock, 24000L);
		boolean night = t >= 12500L && t < 23500L;
		long day = Math.floorDiv(clock + 500L, 24000L);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			UUID id = player.getUUID();
			// eating back what you've spent
			int food = player.getFoodData().getFoodLevel();
			Integer was = FOOD.put(id, food);
			if (was != null && food > was && !player.isCreative()) {
				Skills.grant(player, Skills.Pillar.SURVIVAL, Math.max(1, (food - was) / 2));
			}
			// living through the night (in the Overworld, without dying)
			if (player.level().dimension() == Level.OVERWORLD && !player.isCreative()) {
				if (night) {
					NIGHT.putIfAbsent(id, day);
				} else {
					Long since = NIGHT.remove(id);
					if (since != null) {
						Skills.grant(player, Skills.Pillar.SURVIVAL, XP_NIGHT);
						player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.skills.night", XP_NIGHT).withStyle(ChatFormatting.DARK_GREEN));
					}
				}
			}
			Skills.Progress p = Skills.of(player);
			int weathered = p.rank(Skills.Perk.WEATHERED);
			if (weathered > 0 && player.getTicksFrozen() > 0 && player.isInPowderSnow && player.getRandom().nextFloat() < WEATHERED_FROST * weathered) {
				player.setTicksFrozen(player.getTicksFrozen() - 1);
			}
			if (now % 10L != 0L) {
				continue;
			}
			if (p.rank(Skills.Perk.IRON_STOMACH) > 0 && player.hasEffect(MobEffects.HUNGER)) {
				player.removeEffect(MobEffects.HUNGER);
			}
			int secondWind = p.rank(Skills.Perk.SECOND_WIND);
			if (secondWind > 0 && player.getHealth() < player.getMaxHealth() * SECOND_WIND_HEALTH && NEXT_SECOND_WIND.getOrDefault(id, 0L) <= now) {
				NEXT_SECOND_WIND.put(id, now + SECOND_WIND_EVERY);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SECOND_WIND_TICKS * secondWind, 0));
			}
		}
	}

	/** How much slower {@code player}'s hunger drains, as a factor on its exhaustion (practice). */
	static float exhaustion(ServerPlayer player) {
		return 1.0F - PRACTICE_PER_LEVEL * Skills.of(player).level(Skills.Pillar.SURVIVAL);
	}

	/** Weathered's burning time and Hardy Landing's fall damage, as attribute modifiers. */
	static void attributes(ServerPlayer player) {
		Skills.Progress p = Skills.of(player);
		Skills.modifier(player, Attributes.BURNING_TIME, BURN_ID, -WEATHERED_BURN * p.rank(Skills.Perk.WEATHERED), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// (a share off what's left: a power that takes fall damage to nothing keeps it at nothing)
		Skills.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, p.rank(Skills.Perk.HARDY_LANDING) > 0 ? -HARDY_LANDING : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}
}
