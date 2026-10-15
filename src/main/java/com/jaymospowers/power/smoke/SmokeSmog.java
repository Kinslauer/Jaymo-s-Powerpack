package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke's corruption, Smog. Nothing of it is explained in game.
 * <ul>
 * <li><b>How it comes:</b> pouring black smoke (Smokescreen, the jet or Vapour Form's trail with {@link #BLACK_SOURCES}+
 * heat sources near you, or in your own smoke gone black through fire) fills the meter, a point every
 * {@link #BLACK_TICKS_PER_POINT} (about 4 minutes in all), twice as fast with a fire raging round you (a forest or a
 * village burning). Pouring clean grey smoke eases it. A cough at the halfway mark is the only hint.</li>
 * <li><b>Smog:</b> the corrupted user's smoke is smog ({@link SmokeField#markSmog}): yellow-brown, it clings (fades
 * slower, rain only thins it) and poisons anyone but its maker with their head in it; the undead shrug the poison off
 * (vanilla) and golems are only blinded. It kills what grows under it: grass to dirt, leaves rot away, crops, flowers,
 * saplings and grass wither. Costs: clean open air high up ({@link #CLEAN_AIR_Y}+) is hard on them (hunger, coughing).</li>
 * <li><b>The cure:</b> a minute of clean air at Y {@link #CURE_Y}+ in the Overworld without pouring (Vapour Form's trail
 * doesn't count), three minutes standing in the rain without pouring, or three Minecraft days.</li>
 * </ul>
 */
public final class SmokeSmog {
	// ---- the meter
	/** Heat sources round you that make your smoke black (see {@link SmokeHeat}), or the soot of the smoke you're in. */
	static final int BLACK_SOURCES = 3;
	static final float BLACK_SOOT = 0.7F;
	/** Pouring black smoke this long fills a point (100 points: about 4 minutes). */
	static final int BLACK_TICKS_PER_POINT = 48;
	/** This many burning blocks within {@link #RAGING_REACH} doubles it. */
	static final int RAGING_FIRES = 8;
	static final int RAGING_REACH = 6;
	/** Pouring grey smoke this long eases a point. */
	static final int GREY_TICKS_PER_POINT = 200;

	// ---- smog
	/** How far round a corrupted user their smog is worked, and how thick it must be to poison (and badly). */
	static final double REACH = 48.0;
	static final float TOXIC = 1.0F, VERY_TOXIC = 2.0F;
	/** How often (ticks) it poisons and how long each dose lasts. */
	static final int DOSE_EVERY = 20, DOSE_TICKS = 60;
	/** How often (ticks) it withers what grows, how many blocks each time, and the most looked at. */
	static final int WITHER_EVERY = 40, WITHER_BLOCKS = 6, WITHER_LOOK = 64;
	static final float WITHER_THICK = 1.0F;

	// ---- costs
	/** Above this, under the open sky and out of smoke, the air is too clean: extra hunger a tick and a cough. */
	static final double CLEAN_AIR_Y = 100.0;
	static final float CLEAN_AIR_EXHAUSTION = 0.012F;
	static final int COUGH_EVERY = 100;

	// ---- the cure
	static final double CURE_Y = 200.0;
	static final int CLEAN_TICKS = 1200;
	static final int RAIN_TICKS = 3600;
	public static final long CURE_TICKS = 3L * 24000L;

	/** When (overworld game time) this Smoke turned to smog. Saved and kept through death. */
	public static final AttachmentType<Long> SINCE = AttachmentRegistry.create(JaymosPowers.id("smoke_smog_since"),
		builder -> builder.persistent(com.mojang.serialization.Codec.LONG).copyOnDeath());

	private static final class Data {
		int black, grey, clean, rain, cough;
		boolean coughed, hinted;
	}

	private static final Map<UUID, Data> DATA = new HashMap<>();

	private SmokeSmog() {
	}

	private static Data data(ServerPlayer player) {
		return DATA.computeIfAbsent(player.getUUID(), k -> new Data());
	}

	/** Smokescreen or the jet running (Vapour Form's trail is left out). */
	static boolean pouring(ServerPlayer player) {
		return SmokeScreen.pouring(player) || SmokeJet.isActive(player);
	}

	/** From the power's tick. */
	static void tick(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		if (Fate.isCorrupted(player)) {
			corrupted(player);
		} else {
			meter(player);
		}
	}

	// ---------------------------------------------------------------- the meter

	private static void meter(ServerPlayer player) {
		if (player.getAttached(SINCE) != null) {
			player.removeAttached(SINCE);
		}
		if (!Fate.canCorrupt(player) || !(pouring(player) || SmokeVapour.active(player))) {
			return;
		}
		Data d = data(player);
		ServerLevel level = player.level();
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		boolean black = SmokeHeat.sources(player) >= BLACK_SOURCES
			|| SmokeField.owner(level, eye) == player.getId() && SmokeField.soot(level, eye) >= BLACK_SOOT;
		if (black) {
			d.grey = 0;
			d.black += player.tickCount % 20 == 0 && raging(level, player.blockPosition()) ? 20 : 1;
			if (d.black >= BLACK_TICKS_PER_POINT) {
				d.black = 0;
				Fate.addCorruption(player, 1);
				if (!d.coughed && Fate.corruption(player) >= Fate.MAX_CORRUPTION / 2) {
					d.coughed = true;
					cough(player, true);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_smog_hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
			}
		} else if (Fate.corruption(player) > 0 && ++d.grey >= GREY_TICKS_PER_POINT) {
			d.grey = 0;
			Fate.addCorruption(player, -1);
		}
	}

	/** A fire raging round {@code center}: a forest or a village burning. */
	private static boolean raging(ServerLevel level, BlockPos center) {
		int fires = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RAGING_REACH, -2, -RAGING_REACH), center.offset(RAGING_REACH, 4, RAGING_REACH))) {
			if (level.getBlockState(pos).is(BlockTags.FIRE) && ++fires >= RAGING_FIRES) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- smog

	private static void corrupted(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		SmokeField.markSmog(player, now + 40);
		if (cure(player, level)) {
			return;
		}
		if (player.tickCount % DOSE_EVERY == 0) {
			poison(player, level);
		}
		if (player.tickCount % WITHER_EVERY == 0) {
			wither(player, level);
		}
		// clean, open air high up is hard on smog
		Vec3 eye = player.getEyePosition();
		if (eye.y >= CLEAN_AIR_Y && level.canSeeSky(BlockPos.containing(eye)) && SmokeField.density(level, BlockPos.containing(eye)) < 0.3F) {
			if (!player.getAbilities().instabuild) {
				player.causeFoodExhaustion(CLEAN_AIR_EXHAUSTION);
			}
			Data d = data(player);
			if (++d.cough >= COUGH_EVERY) {
				d.cough = 0;
				cough(player, false);
			}
		}
	}

	/** Everyone but the maker with their head in thick smog: poisoned (and worse in the thickest), golems blinded. */
	private static void poison(ServerPlayer player, ServerLevel level) {
		int self = player.getId();
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
			e -> e.isAlive() && e != player && !(e instanceof ArmorStand) && !e.isSpectator())) {
			BlockPos eye = BlockPos.containing(living.getEyePosition());
			if (SmokeField.owner(level, eye) != self) {
				continue;
			}
			float d = SmokeField.density(level, eye);
			if (d < TOXIC) {
				continue;
			}
			if (living instanceof AbstractGolem) {
				living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DOSE_TICKS, 0), player);
				continue;
			}
			boolean worst = d >= VERY_TOXIC;
			living.addEffect(new MobEffectInstance(MobEffects.POISON, DOSE_TICKS, worst ? 1 : 0), player);
			living.addEffect(new MobEffectInstance(MobEffects.HUNGER, DOSE_TICKS, 0), player);
			if (worst) {
				living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DOSE_TICKS * 2, 0), player);
			}
			if (level.getRandom().nextInt(3) == 0) {
				level.playSound(null, living.getX(), living.getEyeY(), living.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.HOSTILE, 0.4F, 1.4F);
			}
		}
	}

	/** What grows under thick smog dies: grass to dirt, leaves rot away, crops, flowers, saplings and grass wither. */
	private static void wither(ServerPlayer player, ServerLevel level) {
		List<BlockPos> under = new ArrayList<>();
		SmokeField.forEachOwned(level, player.getId(), player.blockPosition(), (int)REACH, WITHER_THICK, (pos, d) -> {
			under.add(pos);
			return under.size() < WITHER_LOOK;
		});
		for (int i = 0; i < WITHER_BLOCKS && !under.isEmpty(); i++) {
			BlockPos pos = under.remove(level.getRandom().nextInt(under.size()));
			if (!wilt(level, pos)) {
				wilt(level, pos.below());
			}
		}
	}

	private static boolean wilt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
			level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
		} else if (state.is(BlockTags.LEAVES) || state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
			|| state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
			level.destroyBlock(pos, !state.is(BlockTags.LEAVES));
		} else {
			return false;
		}
		level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
		return true;
	}

	private static void cough(ServerPlayer player, boolean hard) {
		ServerLevel level = player.level();
		Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.4));
		level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y - 0.1, mouth.z, hard ? 12 : 5, 0.1, 0.05, 0.1, 0.02);
		level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, hard ? 0.8F : 0.4F, 0.7F);
	}

	// ---------------------------------------------------------------- the cure

	/** Clean air high up, the rain, or three days. Returns true if the player was purified. */
	private static boolean cure(ServerPlayer player, ServerLevel level) {
		Data d = data(player);
		long now = level.getServer().overworld().getGameTime();
		Long since = player.getAttached(SINCE);
		if (since == null || since > now) {
			player.setAttached(SINCE, now);
			since = now;
		}
		boolean pouring = pouring(player);
		boolean high = level.dimension() == Level.OVERWORLD && player.getY() >= CURE_Y;
		if (high && !d.hinted) {
			d.hinted = true;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_smog_whisper").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		d.clean = high && !pouring ? d.clean + 1 : 0;
		d.rain = !pouring && level.isRainingAt(BlockPos.containing(player.getEyePosition()).above()) ? d.rain + 1 : 0;
		String how = now - since >= CURE_TICKS ? "days" : d.clean >= CLEAN_TICKS ? "air" : d.rain >= RAIN_TICKS ? "rain" : null;
		if (how == null) {
			return false;
		}
		DATA.remove(player.getUUID());
		player.removeAttached(SINCE);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.smoke_smog_cured_" + how).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		Fate.purify(player);
		return true;
	}

	static void forget(ServerPlayer player) {
		DATA.remove(player.getUUID());
	}

	static void clear() {
		DATA.clear();
	}
}
