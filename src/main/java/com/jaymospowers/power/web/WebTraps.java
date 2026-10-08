package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * What a Web Shot does to whoever it hits: heavy Slowness, no jumping, and a temporary cobweb at their feet.
 * The cobweb is only ever placed in air, removed when it expires (or when the server stops), and breaking it drops
 * nothing, so it can't be farmed for string.
 */
public final class WebTraps {
	/** How long a web shot holds its target. */
	public static final int ENSNARE_TICKS = 80;
	/** Slowness level (0 = I): IV slows to a crawl. */
	public static final int ENSNARE_SLOWNESS = 3;
	/** How long the cobweb at their feet lasts. */
	public static final int COBWEB_TICKS = 100;

	private static final Identifier NO_JUMP = JaymosPowers.id("web_no_jump");

	private record JumpLock(ResourceKey<Level> dimension, UUID entity, long until) {
	}

	private record TempWeb(ResourceKey<Level> dimension, BlockPos pos, long until) {
	}

	private static final List<JumpLock> JUMP_LOCKS = new ArrayList<>();
	private static final List<TempWeb> WEBS = new ArrayList<>();
	private static long clock;

	private WebTraps() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebTraps::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (TempWeb web : WEBS) {
				removeWeb(server, web);
			}
			WEBS.clear();
			JUMP_LOCKS.clear();
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return true;
			}
			for (Iterator<TempWeb> it = WEBS.iterator(); it.hasNext(); ) {
				TempWeb web = it.next();
				if (web.dimension() == serverLevel.dimension() && web.pos().equals(pos)) {
					it.remove();
					if (state.is(Blocks.COBWEB)) {
						// break it ourselves, with no drops
						serverLevel.removeBlock(pos, false);
						serverLevel.playSound(null, pos, SoundEvents.COBWEB_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
						return false;
					}
					return true;
				}
			}
			return true;
		});
	}

	/** {@code target} was hit by a web shot. */
	static void ensnare(ServerLevel level, LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ENSNARE_TICKS, ENSNARE_SLOWNESS));
		AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.addOrUpdateTransientModifier(new AttributeModifier(NO_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			JUMP_LOCKS.removeIf(lock -> lock.entity().equals(target.getUUID()));
			JUMP_LOCKS.add(new JumpLock(level.dimension(), target.getUUID(), clock + ENSNARE_TICKS));
		}
		BlockPos feet = target.blockPosition();
		if (level.isLoaded(feet) && level.getBlockState(feet).isAir()) {
			level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
			WEBS.add(new TempWeb(level.dimension(), feet.immutable(), clock + COBWEB_TICKS));
		}
		level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
			14, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.02);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	private static void tick(MinecraftServer server) {
		clock++;
		for (Iterator<JumpLock> it = JUMP_LOCKS.iterator(); it.hasNext(); ) {
			JumpLock lock = it.next();
			if (clock < lock.until()) {
				continue;
			}
			it.remove();
			ServerLevel level = server.getLevel(lock.dimension());
			Entity entity = level == null ? null : level.getEntity(lock.entity());
			if (entity instanceof LivingEntity living) {
				AttributeInstance jump = living.getAttribute(Attributes.JUMP_STRENGTH);
				if (jump != null) {
					jump.removeModifier(NO_JUMP);
				}
			}
		}
		for (Iterator<TempWeb> it = WEBS.iterator(); it.hasNext(); ) {
			TempWeb web = it.next();
			if (clock < web.until()) {
				continue;
			}
			ServerLevel level = server.getLevel(web.dimension());
			if (level != null && !level.isLoaded(web.pos())) {
				continue; // wait for the chunk to come back
			}
			it.remove();
			removeWeb(server, web);
		}
	}

	private static void removeWeb(MinecraftServer server, TempWeb web) {
		ServerLevel level = server.getLevel(web.dimension());
		if (level != null && level.getBlockState(web.pos()).is(Blocks.COBWEB)) {
			level.removeBlock(web.pos(), false);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, web.pos().getX() + 0.5, web.pos().getY() + 0.5, web.pos().getZ() + 0.5,
				6, 0.3, 0.3, 0.3, 0.01);
		}
	}
}
