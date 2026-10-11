package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Drawing a slingshot (0.8.6: you stay where you braced; nothing pulls you back): the webs grind you into whatever
 * you're against, the floor under you and anything behind. Those blocks crack as the strain builds (the usual mining
 * cracks), up to {@code ALMOST} (all but broken) and no further. Only Living Armor breaks them: held
 * {@code progress} 1 (the client sends it after 5 s of drawing), they give way, a 3 x 3 patch, and the client fires
 * you off as if you'd let go. Only blocks up to {@code MAX_HARDNESS} (or {@code ARMOR_MAX_HARDNESS}) crack; the client
 * reports the strain, the server checks and breaks.
 */
public final class WebStrain {
	static final float MAX_HARDNESS = 3.0F, ARMOR_MAX_HARDNESS = 5.0F;
	/** The furthest the cracks go without Living Armor: all but broken. */
	public static final float ALMOST = 0.9F;

	/** Client → server: straining against the wall {@code behind} (a direction) this far, 0-1 (0: let go). */
	public record Strain(float progress, Vec3 behind) implements CustomPacketPayload {
		public static final Type<Strain> TYPE = new Type<>(JaymosPowers.id("web_strain"));
		public static final StreamCodec<ByteBuf, Strain> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Strain::progress, Vec3.STREAM_CODEC, Strain::behind, Strain::new);

		@Override
		public Type<Strain> type() {
			return TYPE;
		}
	}

	/** The blocks each player is cracking right now (to clear their cracks when they stop). */
	private static final Map<UUID, List<BlockPos>> CRACKING = new HashMap<>();

	private WebStrain() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Strain.TYPE, Strain.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Strain.TYPE, (payload, ctx) -> strain(ctx.player(), payload));
	}

	/**
	 * The blocks the strain grinds into: the wall behind at feet and head height, and the floor under you and under
	 * that wall (three wide for Living Armor, and three high).
	 */
	public static List<BlockPos> behind(net.minecraft.world.entity.player.Player player, Vec3 dir, boolean armored) {
		Vec3 back = new Vec3(dir.x, 0.0, dir.z);
		back = back.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : back.normalize();
		Vec3 side = new Vec3(-back.z, 0.0, back.x);
		Vec3 base = player.position().add(back.scale(player.getBbWidth() * 0.5 + 0.4));
		List<BlockPos> blocks = new ArrayList<>();
		int rows = armored ? 3 : 2;
		for (int across = armored ? -1 : 0; across <= (armored ? 1 : 0); across++) {
			for (int up = 0; up < rows; up++) {
				BlockPos pos = BlockPos.containing(base.add(side.scale(across)).add(0.0, 0.5 + up, 0.0));
				if (!blocks.contains(pos)) {
					blocks.add(pos);
				}
			}
			// and the ground: under you, and under the wall
			for (Vec3 at : new Vec3[] {player.position(), base}) {
				BlockPos floor = BlockPos.containing(at.add(side.scale(across)).add(0.0, -0.5, 0.0));
				if (!blocks.contains(floor)) {
					blocks.add(floor);
				}
			}
		}
		return blocks;
	}

	private static void strain(ServerPlayer player, Strain payload) {
		ServerLevel level = player.level();
		List<BlockPos> was = CRACKING.remove(player.getUUID());
		int crackId = player.getId() * 31 + 7;
		if (was != null) {
			for (int i = 0; i < was.size(); i++) {
				level.destroyBlockProgress(crackId + i, was.get(i), -1);
			}
		}
		float progress = Mth.clamp(payload.progress(), 0.0F, 1.0F);
		if (progress <= 0.0F || !player.isAlive() || player.isSpectator() || !Powers.has(player, WebPower.INSTANCE)
			|| !player.mayBuild()) {
			return;
		}
		boolean armored = WebSymbiote.armored(player);
		if (!armored) {
			// only the armour breaks through
			progress = Math.min(progress, ALMOST);
		}
		float hardest = armored ? ARMOR_MAX_HARDNESS : MAX_HARDNESS;
		List<BlockPos> blocks = new ArrayList<>();
		for (BlockPos pos : behind(player, payload.behind(), armored)) {
			BlockState state = level.getBlockState(pos);
			float hardness = state.getDestroySpeed(level, pos);
			if (!state.isAir() && state.getFluidState().isEmpty() && hardness >= 0.0F && hardness <= hardest
				&& pos.distToCenterSqr(player.position()) < 9.0) {
				blocks.add(pos);
			}
		}
		if (progress >= 1.0F) {
			// Living Armor: it gives way, and you're fired off (the client launches you)
			for (BlockPos pos : blocks) {
				level.destroyBlock(pos, true, player);
			}
			if (!blocks.isEmpty()) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.2F, 0.6F);
				BlockPos first = blocks.get(0);
				com.jaymospowers.core.Debris.puff(level, first, level.getBlockState(first.below()), 2.0F);
			}
			WebSymbiote.narrate(player, "sling");
			return;
		}
		int stage = Math.min(8, (int)(progress * 10.0F));
		for (int i = 0; i < blocks.size(); i++) {
			level.destroyBlockProgress(crackId + i, blocks.get(i), stage);
		}
		CRACKING.put(player.getUUID(), blocks);
		if (player.tickCount % 6 == 0 && !blocks.isEmpty()) {
			BlockPos pos = blocks.get(0);
			level.playSound(null, pos, level.getBlockState(pos).getSoundType().getHitSound(), SoundSource.BLOCKS, 0.7F, 0.6F);
		}
	}
}
