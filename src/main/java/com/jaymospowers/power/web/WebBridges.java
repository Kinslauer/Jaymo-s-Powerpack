package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Weblines: a taut web strung between two points you aim at (press B on the first, let go on the second), thick enough
 * to walk across. It's a row of thin web planks along the line, filling only empty air, and it frays away after
 * {@link #BRIDGE_TICKS}. The same two-point idea as hitching a creature to a block with B. It's Web's starter, and it
 * stays on B for good.
 */
public final class WebBridges {
	/** Longest line, how long it lasts, and the cooldown after stringing one. */
	public static final double MAX_LENGTH = 24.0;
	public static final int BRIDGE_TICKS = 1200;
	public static final int COOLDOWN = 160;
	/** How far you can reach to either end. */
	public static final double REACH = 24.0;

	public static Block BRIDGE;

	/**
	 * A webline's two ends and its {@code style} ({@link WebBridgeBlock#WEB}, {@link WebBridgeBlock#ROPE} from the suit,
	 * {@link WebBridgeBlock#TENDRIL} from Living Armor), for drawing: on each level, synced to everyone. A plain web's
	 * planks are unseen (only the line shows); a symbiote's platform shows, made of what its line is.
	 */
	public record Span(Vec3 from, Vec3 to, int style) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Span> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(Vec3.STREAM_CODEC, Span::from, Vec3.STREAM_CODEC, Span::to,
				net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Span::style, Span::new);
	}

	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<List<Span>> SPANS =
		net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(JaymosPowers.id("web_weblines"),
			builder -> builder.syncWith(Span.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
				net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));

	private record Plank(ResourceKey<Level> dimension, BlockPos pos, long until) {
	}

	/** A webline still standing: its ends, and when it frays away. */
	private record Line(ResourceKey<Level> dimension, Span span, long until) {
	}

	private static final List<Plank> PLANKS = new ArrayList<>();
	private static final List<Line> LINES = new ArrayList<>();
	private static long clock;

	private WebBridges() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("web_bridge"));
		BRIDGE = Registry.register(BuiltInRegistries.BLOCK, key, new WebBridgeBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOL)
			.strength(0.2F)
			.noLootTable()
			.noOcclusion()
			.sound(SoundType.WOOL)
			.pushReaction(PushReaction.POPPED)
			.setId(key)));
		ServerTickEvents.END_SERVER_TICK.register(WebBridges::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// a stopping server takes its weblines with it (nothing left hanging in the save)
			for (Plank plank : PLANKS) {
				ServerLevel level = server.getLevel(plank.dimension());
				if (level != null && level.isLoaded(plank.pos()) && level.getBlockState(plank.pos()).is(BRIDGE)) {
					level.setBlock(plank.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			PLANKS.clear();
			LINES.clear();
			for (ServerLevel level : server.getAllLevels()) {
				level.removeAttached(SPANS);
			}
		});
	}

	/** Strings a webline from {@code from} to {@code to}, in {@code style}. Returns false if it's too long. */
	static boolean string(ServerLevel level, Vec3 from, Vec3 to, int style) {
		double length = from.distanceTo(to);
		if (length > MAX_LENGTH || length < 1.0) {
			return false;
		}
		// planks along the line, each topped out at the line's own height where it crosses that block: an incline
		// rises in small steps, like stairs, so you walk up it without jumping
		java.util.Map<BlockPos, Integer> spots = new java.util.LinkedHashMap<>();
		int steps = (int)Math.ceil(length * 6.0);
		for (int i = 0; i <= steps; i++) {
			Vec3 at = from.lerp(to, i / (double)steps);
			BlockPos pos = BlockPos.containing(at.x, at.y - 0.01, at.z);
			int height = net.minecraft.util.Mth.clamp((int)Math.ceil((at.y - pos.getY()) * 16.0), 1, 16);
			spots.merge(pos.immutable(), height, Math::max);
		}
		for (java.util.Map.Entry<BlockPos, Integer> spot : spots.entrySet()) {
			BlockPos pos = spot.getKey();
			if (level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty()) {
				level.setBlock(pos, BRIDGE.defaultBlockState().setValue(WebBridgeBlock.HEIGHT, spot.getValue()).setValue(WebBridgeBlock.STYLE, style),
					Block.UPDATE_ALL);
				PLANKS.add(new Plank(level.dimension(), pos, clock + BRIDGE_TICKS));
			}
		}
		LINES.add(new Line(level.dimension(), new Span(from, to, style), clock + BRIDGE_TICKS));
		syncSpans(level);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, to.x, to.y, to.z, 6, 0.1, 0.1, 0.1, 0.02);
		return true;
	}

	/** Someone punched a webline (the one from {@code from} to {@code to}): it comes down, planks and all. */
	static boolean cut(ServerLevel level, Vec3 from, Vec3 to) {
		Line found = null;
		for (Line line : LINES) {
			if (line.dimension() == level.dimension() && (line.span().from().distanceTo(from) < 1.0 && line.span().to().distanceTo(to) < 1.0
				|| line.span().from().distanceTo(to) < 1.0 && line.span().to().distanceTo(from) < 1.0)) {
				found = line;
				break;
			}
		}
		if (found == null) {
			return false;
		}
		LINES.remove(found);
		Vec3 a = found.span().from(), b = found.span().to();
		for (Iterator<Plank> it = PLANKS.iterator(); it.hasNext(); ) {
			Plank plank = it.next();
			if (plank.dimension() != level.dimension() || WebCut.distanceToSegment(Vec3.atCenterOf(plank.pos()), a, b) > 1.0) {
				continue;
			}
			it.remove();
			if (level.isLoaded(plank.pos()) && level.getBlockState(plank.pos()).is(BRIDGE)) {
				level.setBlock(plank.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		syncSpans(level);
		return true;
	}

	private static void syncSpans(ServerLevel level) {
		List<Span> spans = new ArrayList<>();
		for (Line line : LINES) {
			if (line.dimension() == level.dimension()) {
				spans.add(line.span());
			}
		}
		if (spans.isEmpty()) {
			level.removeAttached(SPANS);
		} else {
			level.setAttached(SPANS, spans);
		}
	}

	private static void tick(MinecraftServer server) {
		clock++;
		for (Iterator<Line> it = LINES.iterator(); it.hasNext(); ) {
			Line line = it.next();
			if (clock >= line.until()) {
				it.remove();
				ServerLevel level = server.getLevel(line.dimension());
				if (level != null) {
					syncSpans(level);
				}
			}
		}
		for (Iterator<Plank> it = PLANKS.iterator(); it.hasNext(); ) {
			Plank plank = it.next();
			if (clock < plank.until()) {
				continue;
			}
			ServerLevel level = server.getLevel(plank.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			if (!level.isLoaded(plank.pos())) {
				continue; // wait for its chunk to come back
			}
			it.remove();
			if (level.getBlockState(plank.pos()).is(BRIDGE)) {
				level.setBlock(plank.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, plank.pos().getX() + 0.5, plank.pos().getY() + 0.1, plank.pos().getZ() + 0.5,
					2, 0.3, 0.05, 0.3, 0.0);
			}
		}
	}
}
