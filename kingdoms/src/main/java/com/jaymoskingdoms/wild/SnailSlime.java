package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.monsters.MocWild;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "you should be able to right click the splatters to harvest the slime, then craft as intended." A snail or slug
 * stamped flat ({@link SpiderSquash}) leaves a splat of its slime on the ground (Jaymo's Powers' stain). Right-click that
 * splat with an empty hand (or one holding snail slime already) and you scrape it up: the splat is gone and its slime
 * goes into your pack, Kingdoms' snail slime (four make a slimeball). A splat lasts as long as its stain does; washed,
 * scrubbed or soaked away first, there's nothing left to take. Without Powers there's no splat to click, so the slime is
 * simply left lying where the snail burst.
 */
public final class SnailSlime {
	// ================================================================================================================
	// Tuning
	/** How near the click must land to a splat (blocks, across; up and down). */
	static final double REACH = 0.8, REACH_UP = 0.6;
	/** How long a splat can be harvested at most (ticks: as long as Powers keeps a stain). */
	static final long LIFE = 12000L;
	/** How far round a splat its stain is scraped up (blocks). */
	static final double SCRAPE = 0.5;
	// ================================================================================================================

	private static final boolean POWERS = FabricLoader.getInstance().isModLoaded("jaymospowers");

	/** One splat of snail slime: where it lies and until when. */
	private record Splat(Vec3 at, long until) {
	}

	/** The splats lying in each level. */
	private static final Map<ResourceKey<Level>, List<Splat>> SPLATS = new HashMap<>();

	private SnailSlime() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SPLATS.clear());
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || sp.isSpectator()
				|| !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			if (!held.isEmpty() && !held.is(MocWild.SNAIL_SLIME)) {
				return InteractionResult.PASS;
			}
			return harvest(server, sp, hit.getLocation()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	/** A snail or slug of {@code body}'s size just burst where it stood: its slime lies there for the taking. */
	static void burst(ServerLevel level, LivingEntity body) {
		if (MocWild.SNAIL_SLIME == null) {
			return;
		}
		if (!POWERS) {
			// (no splat to click: the slime is left lying where it burst)
			ItemEntity slime = new ItemEntity(level, body.getX(), body.getY() + 0.05, body.getZ(), new ItemStack(MocWild.SNAIL_SLIME), 0.0, 0.05, 0.0);
			slime.setDefaultPickUpDelay();
			level.addFreshEntity(slime);
			return;
		}
		List<Splat> splats = SPLATS.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
		long now = level.getGameTime();
		splats.removeIf(s -> s.until <= now);
		splats.add(new Splat(body.position(), now + LIFE));
	}

	/** {@code player} right-clicked the ground at {@code at}: scrapes up the nearest splat there, if any. */
	private static boolean harvest(ServerLevel level, ServerPlayer player, Vec3 at) {
		List<Splat> splats = SPLATS.get(level.dimension());
		if (splats == null || splats.isEmpty()) {
			return false;
		}
		long now = level.getGameTime();
		splats.removeIf(s -> s.until <= now);
		Splat best = null;
		double bestDist = REACH * REACH;
		for (Splat s : splats) {
			double dx = s.at.x - at.x, dz = s.at.z - at.z;
			double d = dx * dx + dz * dz;
			if (d <= bestDist && Math.abs(s.at.y - at.y) <= REACH_UP) {
				best = s;
				bestDist = d;
			}
		}
		if (best == null) {
			return false;
		}
		splats.remove(best);
		if (PowersCompat.bloodStains(level, best.at, SCRAPE, 1).isEmpty()) {
			// washed or scrubbed away already
			return false;
		}
		PowersCompat.scrubStains(level, best.at, SCRAPE, 10.0F);
		player.getInventory().placeItemBackInInventory(new ItemStack(MocWild.SNAIL_SLIME), net.minecraft.util.Prediction.SERVER_ONLY);
		level.playSound(null, best.at.x, best.at.y, best.at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.2F);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return true;
	}
}
