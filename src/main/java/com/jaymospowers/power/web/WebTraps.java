package com.jaymospowers.power.web;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * What a Web Shot does to whoever it hits: it's webbed up, a layer more ({@link Webbed}: no walking, only a hop, and
 * drawn wrapped in web), where it was once Slowness, no jumping and a cobweb at its feet. The temporary cobwebs a splash
 * strews about ({@link #scatter}) and the webbing round something hung ({@link #cling}) are only ever placed in air,
 * removed when they expire (or when the server stops), and breaking one drops nothing, so they can't be farmed for string.
 */
public final class WebTraps {
	/** How long a web shot's layer holds its target. */
	public static final int ENSNARE_TICKS = 80;
	/** The strength a shot passes on (it no longer slows: the shots web you up, {@link Webbed}). */
	public static final int ENSNARE_SLOWNESS = 3;
	/** How long a strewn cobweb lasts. */
	public static final int COBWEB_TICKS = 100;

	private record TempWeb(ResourceKey<Level> dimension, BlockPos pos, long until) {
	}

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
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return true;
			}
			for (Iterator<TempWeb> it = WEBS.iterator(); it.hasNext(); ) {
				TempWeb web = it.next();
				if (web.dimension() == serverLevel.dimension() && web.pos().equals(pos)) {
					it.remove();
					if (state.is(Blocks.COBWEB) || state.is(WebPower.SYMBIOTE_WEB)) {
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
		ensnare(level, target, ENSNARE_SLOWNESS, ENSNARE_TICKS);
	}

	/** {@code target} was caught in a web blast: a layer more web on it, holding {@code ticks} ({@code amplifier} is no longer used). */
	static void ensnare(ServerLevel level, LivingEntity target, int amplifier, int ticks) {
		ensnare(level, target, amplifier, ticks, false);
	}

	/** The same; {@code dark}: the symbiote's, with a black web. */
	static void ensnare(ServerLevel level, LivingEntity target, int amplifier, int ticks, boolean dark) {
		ensnare(level, target, amplifier, ticks, dark, false);
	}

	/** The same; {@code quiet}: no sound at all (a silent takedown). */
	static void ensnare(ServerLevel level, LivingEntity target, int amplifier, int ticks, boolean dark, boolean quiet) {
		// (an Emberborn, or anything alight, burns it off: no layer)
		Webbed.wrap(target, ticks);
		level.sendParticles(dark ? WebSymbiote.dust(1.2F) : ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
			14, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.02);
		if (!quiet) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
	}

	/** A temporary web (cobweb, or the symbiote's black one) in this spot, if it's open air. */
	private static void place(ServerLevel level, BlockPos pos, boolean dark) {
		cling(level, pos, dark, COBWEB_TICKS);
	}

	/** The same, kept for {@code ticks} (the webbing round something hung or stuck up). */
	static void cling(ServerLevel level, BlockPos pos, boolean dark, int ticks) {
		if (com.jaymospowers.core.Loaded.made(level, pos) && level.getBlockState(pos).isAir()) {
			level.setBlockAndUpdate(pos, dark ? WebPower.SYMBIOTE_WEB.defaultBlockState() : Blocks.COBWEB.defaultBlockState());
			WEBS.add(new TempWeb(level.dimension(), pos.immutable(), clock + ticks));
		}
	}

	/** A splash strews {@code count} webs about {@code center}, on the ground within {@code radius}. */
	static void scatter(ServerLevel level, net.minecraft.world.phys.Vec3 center, double radius, int count, boolean dark) {
		var random = level.getRandom();
		int placed = 0;
		for (int tries = 0; tries < count * 4 && placed < count; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(random.nextDouble()) * radius;
			BlockPos column = BlockPos.containing(center.x + Math.cos(angle) * r, center.y, center.z + Math.sin(angle) * r);
			// settle on the ground: the first open spot with something solid under it, a couple of blocks up or down
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos pos = column.above(dy);
				if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
					place(level, pos, dark);
					placed++;
					break;
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		clock++;
		for (Iterator<TempWeb> it = WEBS.iterator(); it.hasNext(); ) {
			TempWeb web = it.next();
			if (clock < web.until()) {
				continue;
			}
			ServerLevel level = server.getLevel(web.dimension());
			if (level != null && !com.jaymospowers.core.Loaded.made(level, web.pos())) {
				continue; // wait for the chunk to come back
			}
			it.remove();
			removeWeb(server, web);
		}
	}

	private static void removeWeb(MinecraftServer server, TempWeb web) {
		ServerLevel level = server.getLevel(web.dimension());
		if (level != null && (level.getBlockState(web.pos()).is(Blocks.COBWEB) || level.getBlockState(web.pos()).is(WebPower.SYMBIOTE_WEB))) {
			boolean dark = level.getBlockState(web.pos()).is(WebPower.SYMBIOTE_WEB);
			level.removeBlock(web.pos(), false);
			level.sendParticles(dark ? WebSymbiote.dust(1.0F) : ParticleTypes.ITEM_COBWEB, web.pos().getX() + 0.5, web.pos().getY() + 0.5, web.pos().getZ() + 0.5,
				6, 0.3, 0.3, 0.3, 0.01);
		}
	}
}
