package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.net.Payloads;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.4.30) Butcher villagers (vanilla's and MCA's) butcher passive animals for you, for emeralds. Bring one a body (in
 * your arms or your pocket) and right-click them: a screen shows what it costs. Pay, and they take the body, walk out
 * back (to a butcher block nearby, or somewhere out of the way behind their smoker), chop it up, and walk back to hand
 * you the cuts. Each butcher has a skill of their own, from about what you'd get on a butcher block up to three times
 * that, and it has nothing to do with their price or trade level: you only know how good one is once you've heard it in
 * gossip ({@link #gossip}, which Jaymo's Kingdoms' townsfolk tell) or seen their work for yourself.
 */
public final class Butchers {
	/** A butcher's skill, fixed for each one: anywhere between these, evenly spread. */
	private static final float MIN_SKILL = 1.0F, MAX_SKILL = 3.0F;
	/**
	 * Emeralds per job for a small body and for a big carcass (a cow, a horse), times what this butcher thinks they're
	 * worth (1x to {@code MAX_ASKING}x, their own, unrelated to how good they are).
	 */
	private static final float PRICE_SMALL = 1.0F, PRICE_BIG = 2.0F;
	private static final float MAX_ASKING = 3.0F;
	/** Gossip: butchers within this of whoever's talking are their own village's; further, up to the next, word travels. */
	private static final double LOCAL_RADIUS = 64.0;
	private static final double FAR_RADIUS = 3000.0;
	/** How close you must stand to strike the deal. */
	private static final double DEAL_REACH_SQ = 6.0 * 6.0;
	/** Out back: a butcher block within this many blocks, or else a spot this far from his smoker, away from you. */
	private static final int BLOCK_SEARCH = 14;
	private static final int[] OUT_BACK_DISTANCES = {9, 7, 12, 5};
	private static final int[] OUT_BACK_ANGLES = {0, 35, -35, 70, -70, 110, -110, 180};
	/** Walking speed (of his usual), how close is there, and how long he tries before making do where he stands. */
	private static final float WALK_SPEED = 0.6F;
	private static final int ARRIVED = 2;
	private static final int NUDGE_EVERY = 40;
	private static final int OUT_TICKS = 600;
	/** The chopping: this long, a cleaver stroke every so often. */
	private static final int CHOP_TICKS = 100;
	private static final int CHOP_EVERY = 16;
	/** Coming back: hands them over this close; gives up following after this long and waits for you to come by. */
	private static final double HAND_OVER_SQ = 2.5 * 2.5;
	private static final int BACK_TICKS = 900;
	/** Waiting with your cuts: comes over when you're this close again. */
	private static final double NOTICE_SQ = 20.0 * 20.0;

	private static final int OUT = 0, CHOP = 1, BACK = 2, WAIT = 3;

	/** One job: whose body it was, the cuts (worked out when the deal was struck), and how far along it is. */
	public record Job(UUID owner, List<ItemStackTemplate> cuts, float skill, int phase, BlockPos spot, int ticks, Optional<UUID> body) {
		public static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Job::owner),
			ItemStackTemplate.CODEC.listOf().fieldOf("cuts").forGetter(Job::cuts),
			Codec.FLOAT.fieldOf("skill").forGetter(Job::skill),
			Codec.INT.fieldOf("phase").forGetter(Job::phase),
			BlockPos.CODEC.fieldOf("spot").forGetter(Job::spot),
			Codec.INT.fieldOf("ticks").forGetter(Job::ticks),
			UUIDUtil.CODEC.optionalFieldOf("body").forGetter(Job::body)
		).apply(i, Job::new));

		Job next(int phase) {
			return new Job(owner, cuts, skill, phase, spot, 0, body);
		}

		Job tick() {
			return new Job(owner, cuts, skill, phase, spot, ticks + 1, body);
		}
	}

	/** The job a butcher is on, saved with him (so a restart mid-job doesn't lose your cuts). Server only. */
	public static final AttachmentType<Job> JOB = AttachmentRegistry.create(JaymosKitchen.id("butcher_job"),
		builder -> builder.persistent(Job.CODEC));

	/** The butchers a player has heard about or seen at work (so the screen tells them how good they are). Kept for life. */
	public static final AttachmentType<List<UUID>> KNOWN = AttachmentRegistry.create(JaymosKitchen.id("known_butchers"),
		builder -> builder.persistent(UUIDUtil.CODEC.listOf()).copyOnDeath());

	/** Where a butcher was last seen, and by what name, for gossip about far-off butchers. */
	public record Seen(String name, BlockPos pos) {
		public static final Codec<Seen> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Seen::name),
			BlockPos.CODEC.fieldOf("pos").forGetter(Seen::pos)
		).apply(i, Seen::new));
	}

	/** Every butcher this dimension has seen loaded (by villager id), saved with the level. */
	public static final AttachmentType<Map<String, Seen>> LEDGER = AttachmentRegistry.create(JaymosKitchen.id("butcher_ledger"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Seen.CODEC)));

	/** Butchers on a job in loaded chunks. */
	private static final Set<Villager> BUSY = new HashSet<>();

	private Butchers() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(Butchers::onUse);
		ServerTickEvents.END_SERVER_TICK.register(Butchers::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager && villager.hasAttached(JOB)) {
				BUSY.add(villager);
			}
			if (isButcher(entity)) {
				note(level, (Villager) entity);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager villager && villager.level() instanceof ServerLevel level) {
				Job job = villager.getAttached(JOB);
				if (job != null) {
					// the cuts were paid for: they're left where he fell (and the body, being his work now, with him)
					for (ItemStack cut : stacks(job)) {
						villager.spawnAtLocation(level, cut, 0.5F);
					}
					discardBody(level, job);
					villager.removeAttached(JOB);
				}
				forget(level, villager);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.ButcherDeal.TYPE, (payload, ctx) -> deal(ctx.player(), payload.villager()));
	}

	/** A grown villager whose trade is butchering (vanilla's butcher; MCA's villagers share the profession). */
	public static boolean isButcher(Entity entity) {
		return entity instanceof Villager villager && !villager.isBaby()
			&& villager.getVillagerData().profession().is(VillagerProfession.BUTCHER);
	}

	/** How much more a butcher gets out of a body than you would on a block: 1x to 3x, born with it. */
	public static float skill(UUID butcher) {
		return MIN_SKILL + traits(butcher).nextFloat() * (MAX_SKILL - MIN_SKILL);
	}

	public static float skill(Villager villager) {
		return skill(villager.getUUID());
	}

	/** What they ask for their work: their own idea of their worth, nothing to do with their skill. */
	private static float asking(UUID butcher) {
		java.util.Random traits = traits(butcher);
		traits.nextFloat();
		return 1.0F + traits.nextFloat() * (MAX_ASKING - 1.0F);
	}

	private static java.util.Random traits(UUID id) {
		return new java.util.Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ 0x5DEECE66DL);
	}

	/** 0 (clumsy) to 4 (master), a fifth of butchers each, for the screen and for gossip. */
	public static int tier(float skill) {
		return Mth.clamp((int) ((skill - MIN_SKILL) / ((MAX_SKILL - MIN_SKILL) / 5.0F)), 0, 4);
	}

	public static int price(Villager butcher, boolean big) {
		return Math.max(1, Math.round(asking(butcher.getUUID()) * (big ? PRICE_BIG : PRICE_SMALL)));
	}

	/** Whether {@code player} has heard how good this butcher is (or seen for themselves). */
	public static boolean knows(Player player, UUID butcher) {
		return player.getAttachedOrElse(KNOWN, List.of()).contains(butcher);
	}

	private static void learn(Player player, UUID butcher) {
		List<UUID> known = player.getAttachedOrElse(KNOWN, List.of());
		if (!known.contains(butcher)) {
			List<UUID> more = new ArrayList<>(known);
			more.add(butcher);
			player.setAttached(KNOWN, more);
		}
	}

	private static void note(ServerLevel level, Villager butcher) {
		Map<String, Seen> ledger = level.getAttachedOrElse(LEDGER, Map.of());
		String id = butcher.getUUID().toString();
		Seen seen = new Seen(butcher.getDisplayName().getString(), butcher.blockPosition());
		Seen was = ledger.get(id);
		if (was == null || !was.name().equals(seen.name()) || !was.pos().closerThan(seen.pos(), 16.0)) {
			Map<String, Seen> more = new HashMap<>(ledger);
			more.put(id, seen);
			level.setAttached(LEDGER, more);
		}
	}

	private static void forget(ServerLevel level, Villager villager) {
		Map<String, Seen> ledger = level.getAttachedOrElse(LEDGER, Map.of());
		if (ledger.containsKey(villager.getUUID().toString())) {
			Map<String, Seen> less = new HashMap<>(ledger);
			less.remove(villager.getUUID().toString());
			level.setAttached(LEDGER, less);
		}
	}

	/**
	 * A bit of gossip about a butcher, for {@code listener} from {@code speaker} (Jaymo's Kingdoms' townsfolk call this, by
	 * reflection): trash talk or praise for one of their own village's butchers, or word of a master far off, or of one to
	 * steer clear of. The listener knows that butcher's skill from then on. Ones they haven't heard of come first. Null if
	 * there's nothing to tell.
	 */
	public static @Nullable Component gossip(ServerPlayer listener, Entity speaker) {
		if (!(speaker.level() instanceof ServerLevel level)) {
			return null;
		}
		List<UUID> local = new ArrayList<>();
		for (Villager butcher : level.getEntitiesOfClass(Villager.class, speaker.getBoundingBox().inflate(LOCAL_RADIUS),
			v -> v != speaker && isButcher(v) && v.isAlive())) {
			note(level, butcher);
			local.add(butcher.getUUID());
		}
		Map<String, Seen> ledger = level.getAttachedOrElse(LEDGER, Map.of());
		List<UUID> far = new ArrayList<>();
		for (Map.Entry<String, Seen> entry : ledger.entrySet()) {
			UUID id = UUID.fromString(entry.getKey());
			double d = Math.sqrt(entry.getValue().pos().distSqr(speaker.blockPosition()));
			int tier = tier(skill(id));
			// only the best and the worst are talked about far from home
			if (d > LOCAL_RADIUS && d <= FAR_RADIUS && !local.contains(id) && (tier <= 1 || tier >= 3)) {
				far.add(id);
			}
		}
		List<UUID> fresh = new ArrayList<>(local);
		fresh.addAll(far);
		fresh.removeIf(id -> knows(listener, id));
		List<UUID> pool = fresh.isEmpty() ? local : fresh;
		if (pool.isEmpty()) {
			return null;
		}
		UUID chosen = pool.get(speaker.getRandom().nextInt(pool.size()));
		learn(listener, chosen);
		int tier = tier(skill(chosen));
		int variant = speaker.getRandom().nextInt(2);
		if (local.contains(chosen) && level.getEntity(chosen) instanceof Villager butcher) {
			return Component.translatable("gossip.jaymoskitchen.butcher.local." + tier + "." + variant, butcher.getDisplayName());
		}
		Seen seen = ledger.get(chosen.toString());
		if (seen == null) {
			return null;
		}
		Vec3 off = Vec3.atCenterOf(seen.pos()).subtract(speaker.position());
		int blocks = Math.max(100, (int) Math.round(off.horizontalDistance() / 100.0) * 100);
		Component way = Component.translatable("gossip.jaymoskitchen.dir." + compass(off));
		return Component.translatable("gossip.jaymoskitchen.butcher.far." + (tier >= 3 ? "good" : "bad") + "." + variant,
			seen.name(), blocks, way);
	}

	/** n, ne, e, se, s, sw, w or nw, for a horizontal offset (north is -z). */
	private static String compass(Vec3 off) {
		String[] points = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		double degrees = Math.toDegrees(Math.atan2(-off.x, off.z));
		return points[Math.floorMod((int) Math.round(degrees / 45.0), 8)];
	}

	/** Right-clicking a butcher with a body on you opens the deal; going back to one holding your cuts collects them. */
	private static InteractionResult onUse(Player player, net.minecraft.world.level.Level level, InteractionHand hand, Entity entity,
		net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !isButcher(entity)) {
			return InteractionResult.PASS;
		}
		Villager butcher = (Villager) entity;
		ItemStack held = player.getMainHandItem();
		boolean carrying = CorpseEntity.carriedBy(player) != null;
		boolean pocketed = held.is(Corpses.CORPSE_ITEM) && held.has(Corpses.CORPSE_DATA);
		if (level.isClientSide()) {
			// the job lives on the server, so the client can't tell a pickup from a trade: only a body on you is ours
			return carrying || pocketed ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		ServerLevel server = (ServerLevel) level;
		Job job = butcher.getAttached(JOB);
		if (job != null && job.owner().equals(player.getUUID()) && (job.phase() == BACK || job.phase() == WAIT)) {
			handOver(server, butcher, job, player);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!carrying && !pocketed) {
			return InteractionResult.PASS;
		}
		if (job != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.butcher.busy", butcher.getDisplayName()));
			return InteractionResult.SUCCESS_SERVER;
		}
		CorpseEntity body = offered(server, player);
		if (body == null) {
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!body.isButcherable()) {
			butcher.playSound(SoundEvents.VILLAGER_NO);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.butcher.wont", butcher.getDisplayName()));
			return InteractionResult.SUCCESS_SERVER;
		}
		note(server, butcher);
		boolean known = knows(player, butcher.getUUID());
		float skill = skill(butcher);
		EntityType<?> type = body.sourceType();
		Component what = type != null ? type.getDescription() : Component.translatable("message.jaymoskitchen.butcher.carcass");
		// how good they are stays hidden until you've heard (or seen)
		ServerPlayNetworking.send((ServerPlayer) player, new Payloads.OpenButcher(butcher.getId(), butcher.getDisplayName(), what,
			known ? skill : 0.0F, known ? tier(skill) : -1, price(butcher, body.isBigCarcass())));
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * The body the player is offering: the one in their arms, or (not yet in the world) the one in the bag in their hand.
	 * Null if there's none.
	 */
	private static @Nullable CorpseEntity offered(ServerLevel level, Player player) {
		CorpseEntity carried = CorpseEntity.carriedBy(player);
		if (carried != null) {
			return carried;
		}
		ItemStack held = player.getMainHandItem();
		CorpseData data = held.is(Corpses.CORPSE_ITEM) ? held.get(Corpses.CORPSE_DATA) : null;
		if (data == null) {
			return null;
		}
		CorpseEntity body = new CorpseEntity(Corpses.CORPSE, level);
		body.restore(data);
		return body;
	}

	/** The player said yes on the screen: check it all again, take the emeralds and the body, and send him out back. */
	private static void deal(ServerPlayer player, int villagerId) {
		ServerLevel level = player.level();
		if (!(level.getEntity(villagerId) instanceof Villager butcher) || !isButcher(butcher) || !butcher.isAlive()
			|| butcher.hasAttached(JOB) || butcher.distanceToSqr(player) > DEAL_REACH_SQ) {
			return;
		}
		boolean fromBag = CorpseEntity.carriedBy(player) == null;
		CorpseEntity body = offered(level, player);
		if (body == null || !body.isButcherable()) {
			return;
		}
		float skill = skill(butcher);
		int price = price(butcher, body.isBigCarcass());
		if (!player.hasInfiniteMaterials()) {
			if (ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), price, true) < price) {
				butcher.playSound(SoundEvents.VILLAGER_NO);
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.butcher.cant_pay", price));
				return;
			}
			ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), price, false);
		}

		// the body changes hands: from your arms to his, or out of the bag and into his arms
		if (fromBag) {
			player.getMainHandItem().consume(1, player);
			body.snapTo(butcher.getX(), butcher.getY(), butcher.getZ(), butcher.getYRot(), 0.0F);
			level.addFreshEntity(body);
		}
		body.handTo(butcher);

		List<ItemStackTemplate> cuts = new ArrayList<>();
		for (ItemStack drop : body.butcheredDrops()) {
			int count = scaled(drop.getCount(), skill, butcher);
			while (count > 0) {
				int n = Math.min(count, drop.getMaxStackSize());
				cuts.add(ItemStackTemplate.fromNonEmptyStack(drop.copyWithCount(n)));
				count -= n;
			}
		}
		butcher.setAttached(JOB, new Job(player.getUUID(), cuts, skill, OUT, outBack(level, butcher, player), 0, Optional.of(body.getUUID())));
		BUSY.add(butcher);
		butcher.playSound(SoundEvents.VILLAGER_YES);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 1.0F, 0.9F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.butcher.taken", butcher.getDisplayName()));
	}

	/** {@code count} times the skill, the fraction left over a chance at one more. */
	private static int scaled(int count, float skill, Villager butcher) {
		float exact = count * skill;
		int whole = (int) exact;
		return whole + (butcher.getRandom().nextFloat() < exact - whole ? 1 : 0);
	}

	/**
	 * Where he goes to do it: a butcher block nearby if there is one he can reach, otherwise somewhere out of the way on
	 * the far side of his smoker (or of him, if he has none) from you. Where he stands, if there's nowhere better.
	 */
	private static BlockPos outBack(ServerLevel level, Villager butcher, Player player) {
		BlockPos home = butcher.blockPosition();
		BlockPos block = null;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-BLOCK_SEARCH, -4, -BLOCK_SEARCH), home.offset(BLOCK_SEARCH, 4, BLOCK_SEARCH))) {
			if (Corpses.isButcherBlock(level.getBlockState(pos)) && (block == null || pos.distSqr(home) < block.distSqr(home))) {
				block = pos.immutable();
			}
		}
		if (block != null && reachable(butcher, block, ARRIVED)) {
			return block;
		}

		BlockPos anchor = butcher.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(site -> site.dimension() == level.dimension() && site.pos().closerThan(home, 32.0))
			.map(GlobalPos::pos).orElse(home);
		Vec3 away = Vec3.atBottomCenterOf(anchor).subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 0.01) {
			away = Vec3.directionFromRotation(0.0F, butcher.getYRot());
		}
		away = away.normalize();
		for (int distance : OUT_BACK_DISTANCES) {
			for (int angle : OUT_BACK_ANGLES) {
				Vec3 dir = away.yRot(angle * Mth.DEG_TO_RAD);
				int x = Mth.floor(anchor.getX() + 0.5 + dir.x * distance);
				int z = Mth.floor(anchor.getZ() + 0.5 + dir.z * distance);
				for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
					BlockPos spot = new BlockPos(x, anchor.getY() + dy, z);
					if (standable(level, spot) && reachable(butcher, spot, 1)) {
						return spot;
					}
				}
			}
		}
		return home;
	}

	private static boolean standable(ServerLevel level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.isFaceSturdy(level, pos.below(), Direction.UP)
			&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()
			&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
	}

	private static boolean reachable(Villager butcher, BlockPos pos, int within) {
		Path path = butcher.getNavigation().createPath(pos, within);
		return path != null && path.canReach();
	}

	private static void tick(MinecraftServer server) {
		if (BUSY.isEmpty()) {
			return;
		}
		for (Villager butcher : List.copyOf(BUSY)) {
			Job job = butcher.getAttached(JOB);
			if (butcher.isRemoved() || job == null || !(butcher.level() instanceof ServerLevel level)) {
				// unloaded (picked up again when it loads), dead, or done
				BUSY.remove(butcher);
				continue;
			}
			step(level, butcher, job.tick());
		}
	}

	private static void step(ServerLevel level, Villager butcher, Job job) {
		if (job.phase() != WAIT && butcher.isSleeping()) {
			butcher.stopSleeping();
		}
		CorpseEntity body = job.body().map(level::getEntity).filter(e -> e instanceof CorpseEntity).map(e -> (CorpseEntity) e).orElse(null);
		switch (job.phase()) {
			case OUT -> {
				if (body != null && body.carrier() != butcher) {
					// dropped on the way (or the world was reloaded): picked back up
					body.handTo(butcher);
				}
				walkTo(butcher, new WalkTarget(job.spot(), WALK_SPEED, ARRIVED));
				if (butcher.blockPosition().closerThan(job.spot(), ARRIVED + 1.0) || job.ticks() > OUT_TICKS) {
					job = job.next(CHOP);
				}
			}
			case CHOP -> {
				butcher.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				butcher.getNavigation().stop();
				if (body != null && body.carrier() != butcher) {
					body.handTo(butcher);
				}
				if (job.ticks() % CHOP_EVERY == 0) {
					butcher.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					Vec3 at = body != null ? body.position() : butcher.position();
					play(level, at, job.ticks() % (CHOP_EVERY * 2) == 0 ? SoundEvents.AXE_STRIP.value() : SoundEvents.VILLAGER_WORK_BUTCHER,
						0.8F + butcher.getRandom().nextFloat() * 0.3F);
					level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 6, 0.3, 0.1, 0.3, 0.1);
					if (body != null) {
						body.jolt(true);
					}
				}
				if (job.ticks() >= CHOP_TICKS) {
					Vec3 at = body != null ? body.position() : butcher.position();
					level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 20, 0.4, 0.2, 0.4, 0.2);
					play(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F);
					discardBody(level, job);
					job = new Job(job.owner(), job.cuts(), job.skill(), BACK, job.spot(), 0, Optional.empty());
				}
			}
			case BACK -> {
				Player owner = level.getPlayerByUUID(job.owner());
				if (owner == null || !owner.isAlive() || owner.distanceToSqr(butcher) > NOTICE_SQ * 4.0 || job.ticks() > BACK_TICKS) {
					// gone off, or can't get to them: he hangs on to the cuts until they come by
					butcher.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
					job = job.next(WAIT);
				} else if (owner.distanceToSqr(butcher) <= HAND_OVER_SQ) {
					handOver(level, butcher, job, owner);
					return;
				} else {
					walkTo(butcher, new WalkTarget(owner, WALK_SPEED, 1));
				}
			}
			default -> {
				// waiting with the cuts, going about his day; comes over when they're back
				if (job.ticks() % 20 == 0) {
					Player owner = level.getPlayerByUUID(job.owner());
					if (owner != null && owner.isAlive() && owner.distanceToSqr(butcher) <= NOTICE_SQ) {
						job = job.next(BACK);
					}
				}
			}
		}
		butcher.setAttached(JOB, job);
	}

	private static void walkTo(Villager butcher, WalkTarget target) {
		butcher.getBrain().setMemory(MemoryModuleType.WALK_TARGET, target);
		// in case his brain isn't the kind that walks to a walk target (some other mod's villager): steer him directly
		if (butcher.tickCount % NUDGE_EVERY == 0 && butcher.getNavigation().isDone()) {
			BlockPos to = target.getTarget().currentBlockPosition();
			if (!butcher.blockPosition().closerThan(to, target.getCloseEnoughDist() + 1.0)) {
				butcher.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, WALK_SPEED);
			}
		}
	}

	/** The cuts go into the owner's pockets (what doesn't fit, at their feet), and the job's done. */
	private static void handOver(ServerLevel level, Villager butcher, Job job, Player owner) {
		for (ItemStack cut : stacks(job)) {
			owner.getInventory().placeItemBackInInventory(cut, Prediction.SERVER_ONLY);
		}
		butcher.removeAttached(JOB);
		BUSY.remove(butcher);
		butcher.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		butcher.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		butcher.playSound(SoundEvents.VILLAGER_YES);
		play(level, owner.position(), SoundEvents.ITEM_PICKUP, 1.0F);
		// seen their work for yourself: you know them now
		learn(owner, butcher.getUUID());
		owner.sendOverlayMessage(Component.translatable("message.jaymoskitchen.butcher.done", butcher.getDisplayName(),
			String.format(java.util.Locale.ROOT, "%.1f", job.skill())));
	}

	private static List<ItemStack> stacks(Job job) {
		List<ItemStack> stacks = new ArrayList<>();
		for (ItemStackTemplate cut : job.cuts()) {
			stacks.add(cut.create());
		}
		return stacks;
	}

	private static void discardBody(ServerLevel level, Job job) {
		job.body().map(level::getEntity).filter(e -> e instanceof CorpseEntity).ifPresent(Entity::discard);
	}

	private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1.0F, pitch);
	}
}
