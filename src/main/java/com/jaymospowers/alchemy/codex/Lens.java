package com.jaymospowers.alchemy.codex;

import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Phials;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.alchemy.essence.Essences;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the Scrying Lens looks at, and what a scan does. The same {@link #aim} runs on the server (the scan) and the
 * client (the panel by the crosshair), so they always agree.
 * <p>
 * Aim: a creature or dropped item in view, else the block in view, else the item in the other hand. A scan reads
 * the thing's essences: each one the player hasn't read from that thing before earns insight, and any the player has
 * never seen is discovered (primals first, so a compound whose parents turn up in the same thing is read at once).
 * An essence whose parents the player hasn't found yet stays "?" and is read on a later scan.
 */
public final class Lens {
	/** How far past the player's normal reach the lens sees (blocks). */
	static final double REACH_BONUS = 1.5;
	/** Insight earned for each essence read, per point of it in the thing (at least 1). */
	static final double INSIGHT_PER_AMOUNT = 0.4;
	/** Extra insight, of the essence itself, the first time the player ever discovers it. */
	static final int NEW_ESSENCE_BONUS = 2;
	/** A thing with this much essence in all is rare, and teaches {@link #RARE_BONUS} more of each. */
	static final int RARE_TOTAL = 25, RARE_BONUS = 1;
	/** The research the first scan teaches. */
	public static final String BASICS = "alchemy_basics";

	private Lens() {
	}

	/** One thing the lens can read: a stable key, what it's made of, its name, and where it is (for particles). */
	public record Thing(String key, EssenceList essences, Component name, Vec3 at) {
	}

	public static @Nullable Thing aim(Player player, InteractionHand lensHand) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double blockRange = player.blockInteractionRange() + REACH_BONUS;
		double entityRange = player.entityInteractionRange() + REACH_BONUS;
		Vec3 end = eye.add(look.scale(Math.max(blockRange, entityRange)));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double blockDistance = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceTo(block.getLocation());
		Vec3 entityEnd = eye.add(look.scale(Math.min(entityRange, blockDistance)));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, entityEnd,
			player.getBoundingBox().expandTowards(entityEnd.subtract(eye)).inflate(1.0),
			e -> e != player && !e.isSpectator() && (e.isPickable() || e instanceof ItemEntity), eye.distanceToSqr(entityEnd));
		if (hit != null) {
			return ofEntity(hit.getEntity());
		}
		if (blockDistance <= blockRange) {
			BlockPos pos = block.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Item item = state.getBlock().asItem();
			if (item != Items.AIR) {
				Thing thing = ofStack(new ItemStack(item), Vec3.atCenterOf(pos));
				return new Thing(thing.key(), thing.essences(), state.getBlock().getName(), thing.at());
			}
		}
		ItemStack other = player.getItemInHand(lensHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		return other.isEmpty() ? null : ofStack(other, player.getEyePosition().add(look.scale(0.8)));
	}

	static Thing ofStack(ItemStack stack, Vec3 at) {
		String key = "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
		Essence held = Phials.contents(stack).main();
		if (held != null) {
			// a phial is read by what's in it
			key += "#" + held.id();
		}
		return new Thing(key, Essences.of(stack), stack.getHoverName(), at);
	}

	static Thing ofEntity(Entity entity) {
		if (entity instanceof ItemEntity dropped) {
			return ofStack(dropped.getItem(), dropped.position());
		}
		return new Thing("entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), Essences.of(entity), entity.getDisplayName(),
			entity.getBoundingBox().getCenter());
	}

	/** The essences in order of amount, biggest first (the order they're listed in). */
	public static List<Map.Entry<Essence, Integer>> sorted(EssenceList list) {
		List<Map.Entry<Essence, Integer>> out = new ArrayList<>(list.amounts().entrySet());
		out.sort(Map.Entry.<Essence, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
		return out;
	}

	/** Reads one thing for a player: discoveries, insight, the first research, and the words about it. */
	public static void scan(ServerPlayer player, Thing thing) {
		EssenceList list = thing.essences();
		if (list.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.lens.empty", thing.name()));
			return;
		}
		// primals first: a compound is only discovered once both its parents are
		List<Essence> found = new ArrayList<>();
		list.amounts().keySet().stream().sorted(Comparator.comparingInt(Essence::tier)).forEach(e -> {
			if (Knowledge.discover(player, e)) {
				found.add(e);
			}
		});
		int total = list.total();
		EssenceList gained = EssenceList.EMPTY;
		boolean unread = false, anyNew = false;
		for (Map.Entry<Essence, Integer> en : sorted(list)) {
			Essence e = en.getKey();
			if (!Knowledge.hasSeen(player, e)) {
				unread = true;
				continue;
			}
			if (Scans.has(player, thing.key(), e)) {
				continue;
			}
			anyNew = true;
			Scans.add(player, thing.key(), e);
			int insight = Math.max(1, (int) Math.round(en.getValue() * INSIGHT_PER_AMOUNT)) + (total >= RARE_TOTAL ? RARE_BONUS : 0)
				+ (found.contains(e) ? NEW_ESSENCE_BONUS : 0);
			gained = gained.plus(e, insight);
		}
		if (!anyNew && found.isEmpty()) {
			player.sendOverlayMessage(Component.translatable(unread ? "message.jaymospowers.lens.partial" : "message.jaymospowers.lens.nothing_new"));
			return;
		}
		if (!gained.isEmpty()) {
			Knowledge.addInsight(player, gained);
		}

		MutableComponent shown = Component.empty();
		boolean first = true;
		for (Map.Entry<Essence, Integer> en : sorted(list)) {
			if (!first) {
				shown.append(Component.literal(", ").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			first = false;
			if (Knowledge.hasSeen(player, en.getKey())) {
				shown.append(Component.translatable(en.getKey().key()).append(" " + en.getValue()).withColor(en.getKey().colour));
			} else {
				shown.append(Component.literal("?").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.lens.scanned", thing.name(), shown)
			.withStyle(net.minecraft.ChatFormatting.GRAY));
		for (Essence e : found) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.lens.discovered", Component.translatable(e.key()).withColor(e.colour))
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		}
		Research.Entry basics = Research.get(BASICS);
		if (basics != null && Knowledge.unlock(player, basics)) {
			Codex.learned(player, basics);
		}

		if (player.level() instanceof ServerLevel level) {
			Essence main = list.main();
			level.sendParticles(ParticleTypes.ENCHANT, thing.at().x, thing.at().y, thing.at().z, 14, 0.3, 0.3, 0.3, 0.6);
			level.sendParticles(ParticleTypes.END_ROD, thing.at().x, thing.at().y, thing.at().z, 3, 0.2, 0.2, 0.2, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.7F, found.isEmpty() ? 1.3F : 1.0F + (main == null ? 0.0F : main.tier() * 0.05F));
		}
	}
}
