package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-26): "I'd also like if captives did more than just stand there, maybe sit in different animations or be seen
 * trying at their bindings, or digging in the ground near them, etc."
 * <p>
 * A captive in chains ({@link Captives}) passes the time as a prisoner would, one thing for a while and then another
 * ({@link #HOLD_LEAST} to {@link #HOLD_MOST}): sitting hugging their knees, slumped against nothing with their head hung,
 * kneeling, straining at their bonds (a jerk of the shoulders and a clink of the chain now and then), picking at the knot at
 * their wrists, or scraping at the ground beside them (dirt flies; dig long enough on grass and the turf comes away
 * bare). Someone coming near brings them up onto their knees, facing them, to plead ({@link Captives}' own lines). Drawn
 * by the client's {@code CaptivePose} from {@link #DOING}; nothing here changes what a captive is or how they're freed.
 */
public final class CaptiveIdle {
	/** What a captive is doing just now, for the client to draw (0: standing). Not kept. */
	public static final int STAND = 0, HUG = 1, SLUMP = 2, KNEEL = 3, STRAIN = 4, PICK = 5, DIG = 6;
	public static final AttachmentType<Integer> DOING = AttachmentRegistry.create(JaymosKingdoms.id("captive_doing"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** How long one thing is kept up (ticks). */
	static final int HOLD_LEAST = 20 * 15, HOLD_MOST = 20 * 45;
	/** How likely each is (standing, hugging knees, slumped, kneeling, straining, picking at the knot, digging). */
	static final int[] WEIGHTS = {2, 4, 3, 2, 3, 3, 2};
	/** The chance, each look ({@link Captives#THINK}), of a clink of the chain while straining or a scrape while digging. */
	static final float NOISE_CHANCE = 0.5F;
	/** Scrapes it takes to strip a patch of turf bare. */
	static final int TURF_SCRAPES = 30;

	/** Each captive's next change (game time), and how many scrapes they've made at the ground. */
	private static final Map<Villager, long[]> NEXT = new WeakHashMap<>();

	private CaptiveIdle() {
	}

	/** (loads {@link #DOING}) */
	static void init() {
	}

	/** From {@link Captives}' look at a captive still in chains; {@code near}: someone close enough to plead with, or null. */
	static void step(ServerLevel level, Villager v, @Nullable Player near, long now) {
		long[] next = NEXT.computeIfAbsent(v, k -> new long[] {0L, 0L});
		RandomSource random = level.getRandom();
		if (near != null) {
			// up onto their knees to beg whoever's come
			set(v, KNEEL);
			next[0] = now + 40L;
			return;
		}
		int doing = doing(v);
		if (now >= next[0]) {
			int was = doing;
			doing = pick(random);
			if (doing != was && doing != STAND && doing != KNEEL) {
				// a new way to sit or kneel faces a new way
				v.setYRot(random.nextFloat() * 360.0F);
				v.setYBodyRot(v.getYRot());
				v.setYHeadRot(v.getYRot());
			}
			set(v, doing);
			next[0] = now + Mth.nextInt(random, HOLD_LEAST, HOLD_MOST);
		}
		if (doing == STRAIN && random.nextFloat() < NOISE_CHANCE) {
			level.playSound(null, v.getX(), v.getY() + 1.0, v.getZ(), SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL, 0.5F, 0.8F + random.nextFloat() * 0.3F);
		} else if (doing == DIG) {
			dig(level, v, random, next);
		}
	}

	/** {@code v} scrapes at the ground at their side: dirt flies, and enough scraping strips grass to bare earth. */
	private static void dig(ServerLevel level, Villager v, RandomSource random, long[] next) {
		float yaw = v.getYRot() * Mth.DEG_TO_RAD;
		// their right hand's side, a little in front
		BlockPos at = BlockPos.containing(v.getX() - Mth.cos(yaw) * 0.5 - Mth.sin(yaw) * 0.5, v.getY() - 0.5, v.getZ() - Mth.sin(yaw) * 0.5 + Mth.cos(yaw) * 0.5);
		BlockState ground = level.getBlockState(at);
		if (ground.isAir() || !ground.is(BlockTags.DIRT) && !ground.is(BlockTags.SAND) && !ground.is(Blocks.GRAVEL)) {
			// nothing to dig at here: they pick at their bonds instead
			set(v, PICK);
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.getX() + 0.5, at.getY() + 1.05, at.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.05);
		if (random.nextFloat() < NOISE_CHANCE) {
			level.playSound(null, at, ground.is(Blocks.GRAVEL) ? SoundEvents.GRAVEL_HIT : SoundEvents.GRASS_HIT, SoundSource.NEUTRAL, 0.4F, 0.9F);
		}
		if (++next[1] >= TURF_SCRAPES && ground.is(Blocks.GRASS_BLOCK)) {
			level.setBlockAndUpdate(at, Blocks.DIRT.defaultBlockState());
			next[1] = 0L;
		}
	}

	private static int pick(RandomSource random) {
		int total = 0;
		for (int w : WEIGHTS) {
			total += w;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < WEIGHTS.length; i++) {
			roll -= WEIGHTS[i];
			if (roll < 0) {
				return i;
			}
		}
		return STAND;
	}

	static int doing(Villager v) {
		Integer d = v.getAttached(DOING);
		return d == null ? STAND : d;
	}

	private static void set(Villager v, int doing) {
		if (doing == STAND) {
			v.removeAttached(DOING);
		} else if (doing(v) != doing) {
			v.setAttached(DOING, doing);
		}
	}

	/** Out of their chains: on their feet again. */
	static void clear(Villager v) {
		v.removeAttached(DOING);
		NEXT.remove(v);
	}
}
