package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterNpcs;
import com.jaymospowers.tinkering.Tinkering;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.122, Xeno-Band patch 6) The star-readers: one fallen star in {@link #ONE_IN} draws one, a day or so after it came
 * down ({@link #DELAY}, {@link #DELAY_SPREAD}). They come as one of Kingdoms' MCA characters (via {@link ZFighterNpcs#villager},
 * tagged {@link #TAG}), standing on the crater's rim and keeping to it. "Talk about work" opens their talk screen
 * ({@link #talk}): a star tablet for {@link #TABLET_PRICE} star metal, a reading of your band, and a few words about the sky
 * that hint without telling. Without Kingdoms and MCA no reader comes.
 * <p>
 * The craters still waiting for a reader are kept on the level ({@link #WAITING}), so a restart doesn't lose them.
 */
public final class StarReaders {
	// ---- tuning ----
	/** One fallen star in this many draws a reader. */
	public static final int ONE_IN = 3;
	/** How long after the star fell the reader comes (ticks), plus up to this much more. */
	public static final long DELAY = 24000L, DELAY_SPREAD = 12000L;
	/** How often (ticks) waiting craters are looked at. */
	public static final int CHECK_EVERY = 200;
	/** A reader comes only while a player is this near the crater (so its ground is loaded and someone may find them). */
	public static final double PLAYER_NEAR = 96.0;
	/** No second reader within this many blocks of one already there. */
	public static final double READER_SPACING = 96.0;
	/** How far out from the star they stand: the crater's rim. */
	public static final double RIM_MIN = 7.0, RIM_MAX = 10.0;
	/** Star metal ingots for one tablet. */
	public static final int TABLET_PRICE = 3;
	/** Craters remembered per level at most (the oldest go first). */
	public static final int MAX_WAITING = 32;
	/** Lines in the lang file for each of their talks. */
	static final int GREET_LINES = 4, AGAIN_LINES = 3, HINT_LINES = 8, SOLD_LINES = 3, NAMES = 6;

	/** Powers' tag for a star-reader (Kingdoms knows it as the "star_reader" character). */
	public static final String TAG = "jaymospowers_star_reader";

	/** A crater still waiting for its reader, and when they're due. */
	public record Crater(BlockPos star, long due) {
		public static final Codec<Crater> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("star").forGetter(Crater::star),
			Codec.LONG.fieldOf("due").forGetter(Crater::due)).apply(i, Crater::new));
	}

	public static final AttachmentType<List<Crater>> WAITING = AttachmentRegistry.create(JaymosPowers.id("star_reader_craters"),
		builder -> builder.persistent(Crater.CODEC.listOf()));

	private StarReaders() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StarReaders::tick);
	}

	/** A star has come down at {@code star}: one in {@link #ONE_IN} will draw a reader. */
	static void crater(ServerLevel level, BlockPos star) {
		RandomSource random = level.getRandom();
		if (random.nextInt(ONE_IN) != 0 || !ZFighterNpcs.villagers()) {
			return;
		}
		List<Crater> waiting = new ArrayList<>(level.getAttachedOrElse(WAITING, List.of()));
		if (waiting.size() >= MAX_WAITING) {
			waiting.remove(0);
		}
		waiting.add(new Crater(star.immutable(), level.getGameTime() + DELAY + (long)(random.nextDouble() * DELAY_SPREAD)));
		level.setAttached(WAITING, waiting);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<Crater> waiting = level.getAttached(WAITING);
			if (waiting == null || waiting.isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			List<Crater> left = new ArrayList<>();
			boolean changed = false;
			for (Crater crater : waiting) {
				if (crater.due() > now || !playerNear(level, crater.star())) {
					left.add(crater);
					continue;
				}
				changed = true;
				if (!ZFighterNpcs.villagers() || readerNear(level, crater.star())) {
					continue;
				}
				come(level, crater.star());
			}
			if (changed) {
				level.setAttached(WAITING, left.isEmpty() ? null : left);
			}
		}
	}

	private static boolean playerNear(ServerLevel level, BlockPos star) {
		Vec3 centre = Vec3.atCenterOf(star);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(centre) < PLAYER_NEAR * PLAYER_NEAR) {
				return true;
			}
		}
		return false;
	}

	private static boolean readerNear(ServerLevel level, BlockPos star) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(star).inflate(READER_SPACING), e -> e.entityTags().contains(TAG)).isEmpty();
	}

	/** The reader comes: on the rim, looking in at the star. */
	private static void come(ServerLevel level, BlockPos star) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0, out = RIM_MIN + random.nextDouble() * (RIM_MAX - RIM_MIN);
		int x = star.getX() + (int)Math.round(Math.cos(angle) * out), z = star.getZ() + (int)Math.round(Math.sin(angle) * out);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
		float yaw = (float)(Mth.atan2(star.getZ() + 0.5 - at.z, star.getX() + 0.5 - at.x) * Mth.RAD_TO_DEG) - 90.0F;
		LivingEntity reader = ZFighterNpcs.villager(level, at, yaw, TAG, "npc.jaymospowers.star_reader." + random.nextInt(NAMES), random.nextBoolean());
		if (reader != null) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 0.8F);
		}
	}

	// ---------------------------------------------------------------- talk

	/** From MCA's "Talk about work" ({@link ZFighterNpcs#talk}): their talk screen. */
	public static void talk(ServerPlayer player, LivingEntity reader) {
		if (reader instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		}
		menu(player, reader, line("greet", GREET_LINES, reader));
	}

	private static void menu(ServerPlayer player, LivingEntity reader, Component words) {
		Runnable back = () -> menu(player, reader, line("again", AGAIN_LINES, reader));
		List<ZFighterNpcs.Option> backOnly = List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back));
		List<ZFighterNpcs.Option> options = new ArrayList<>();
		Item metal = Tinkering.STAR_METAL_INGOT;
		options.add(ZFighterNpcs.Option.of("talk.jaymospowers.star_reader.opt.tablet", metal != null && count(player, metal) >= TABLET_PRICE, () -> {
			if (count(player, metal) < TABLET_PRICE) {
				ZFighterNpcs.ask(player, reader, Component.translatable("talk.jaymospowers.star_reader.short"), backOnly);
				return;
			}
			take(player, metal, TABLET_PRICE);
			player.getInventory().placeItemBackInInventory(new ItemStack(XenoFinds.TABLET), net.minecraft.util.Prediction.SERVER_ONLY);
			player.level().playSound(null, reader.getX(), reader.getY(), reader.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
			ZFighterNpcs.ask(player, reader, line("sold", SOLD_LINES, reader), backOnly);
		}, TABLET_PRICE));
		boolean band = XenoformPower.has(player);
		options.add(ZFighterNpcs.Option.of("talk.jaymospowers.star_reader.opt.band", band, () -> ZFighterNpcs.ask(player, reader, reading(player), backOnly)));
		options.add(ZFighterNpcs.Option.of("talk.jaymospowers.star_reader.opt.sky", true,
			() -> ZFighterNpcs.ask(player, reader, line("hint", HINT_LINES, reader), backOnly)));
		ZFighterNpcs.ask(player, reader, words, options);
	}

	/** What they read in your band: how many it has yet to learn, put their way. */
	private static Component reading(ServerPlayer player) {
		XenoformState.Band band = XenoformState.band(player);
		int missing = 0;
		boolean dud = false;
		for (XenoForm form : XenoForms.all()) {
			if (!band.has(form.id)) {
				if (form.hidden()) {
					dud = true;
				} else {
					missing++;
				}
			}
		}
		String key = missing == 0 ? (dud ? "band.only_broken" : "band.all") : missing == 1 ? "band.one" : "band.some";
		return Component.translatable("talk.jaymospowers.star_reader." + key, missing);
	}

	private static Component line(String name, int count, LivingEntity reader) {
		return Component.translatable("talk.jaymospowers.star_reader." + name + "." + (1 + reader.getRandom().nextInt(count)));
	}

	private static int count(Player player, Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(Player player, Item item, int count) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int n = Math.min(count, stack.getCount());
				stack.shrink(n);
				count -= n;
			}
		}
	}
}
