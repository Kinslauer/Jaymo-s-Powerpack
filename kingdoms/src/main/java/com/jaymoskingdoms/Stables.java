package com.jaymoskingdoms;

import com.jaymoskingdoms.equestrian.Bond;
import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Discipline;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import com.jaymoskingdoms.equestrian.Tack;
import com.jaymoskingdoms.equestrian.Tackroom;
import com.jaymoskingdoms.equestrian.WarElephant;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.47) Equestrian stage 3: a settlement's stable at work. Its {@link Folk#STABLEHAND} boards players' horses (fed,
 * watered and brushed while they're away, {@link #WEEKLY_FEE} emeralds for each week begun, paid when the horse is
 * taken back out), sells horses (the breed by the land and the realm's wealth, the quality by its wealth) and buys
 * them back; its {@link Folk#FARRIER} shoes horses and sells tack, better tack in wealthier kingdoms. Walk up to either
 * and they say what they offer (clickable), or use {@code /kingdom stable} and {@code /kingdom farrier} beside them.
 * <p>Besides: a realm's cavalry rides better horses for its stables ({@link #equip}); a knight (or a raider) cut down
 * by a player leaves his horse to go wild ({@link #riderFell}), its war training kept; barbarian raiders ride steppe
 * ponies ({@link #pony}); rifling a caravan horse's saddlebags is theft ({@link #rifle}); and a stablehand's and a
 * king's horse quests (break one in, bring a stud, ride to another town against the clock).
 */
public final class Stables {
	/** Stable workers keep within this of their stable's middle. */
	static final int ROAM = 10;
	/** A farrier comes once the settlement has this many people. */
	static final int FARRIER_AT = 8;
	/** A stable worker goes to a horse this often (ticks, plus up to as many again). */
	static final int TEND_TICKS = 300;
	/** A player this close to a stable worker is greeted with what it offers, once per {@link #GREET_COOLDOWN} ticks. */
	static final double GREET_RANGE = 3.5;
	static final int GREET_COOLDOWN = 3600;
	/** The stable commands work this close to a stable worker; your horse must be this close to you. */
	static final double MENU_RANGE = 8.0, HORSE_RANGE = 12.0;
	/** Boarding: emeralds for each week begun, the days in a week. */
	static final int WEEKLY_FEE = 4, WEEK_DAYS = 7;
	/** A boarded horse further than this from its stall is walked back; further than {@link #STALL_FAR} it's put back. */
	static final double STALL_NEAR = 3.0, STALL_FAR = 8.0;
	/** Every this many ticks boarded horses are seen to (and races checked). */
	static final int UPKEEP_TICKS = 100;
	/** Every this many ticks a settlement with a stable and nobody working it takes someone on. */
	static final int STAFF_TICKS = 2400;
	/** How long a coat shines after the stablehand's brushing. */
	static final long SHINE_TICKS = 24000L;
	/** The market: horses on offer a day by wealth (poor, prosperous, wealthy). */
	static final int[] OFFERS = {2, 3, 4};
	/** A horse's price at middling quality, by breed (in {@link Breed} order); quality moves it from half to one and a half times this. */
	static final int[] BREED_PRICE = {24, 22, 12, 12, 10, 18, 60, 70};
	/** What a stable pays for a horse against its price; a foal's share of a grown horse's. */
	static final double SELL_SHARE = 0.5, FOAL_SHARE = 0.6;
	/** The farrier: shoeing a horse. */
	static final int SHOE_PRICE = 3;
	/** Cavalry with stables: speed added per stable (on the realm's {@link Cavalry#HORSE_SPEED}), the most stables that count. */
	static final double STABLE_SPEED = 0.015;
	static final int MAX_LEVEL = 3;
	/** A caravan horse's saddlebags: emeralds in them (up to), and the goods. */
	static final int BAG_EMERALDS = 4;
	static final List<String> BAG_GOODS = List.of("minecraft:bread*2-5", "minecraft:wheat*4-12", "minecraft:leather*1-4", "minecraft:iron_ingot*1-3",
		"minecraft:cooked_beef*2-4", "minecraft:apple*2-6", "minecraft:string*2-6", "minecraft:gold_nugget*3-9");
	/** Barbarian raiders: one in this many rides a steppe pony. */
	static final int RAIDER_RIDES_ONE_IN = 2;
	/** Quests: a stud must be at least this good (percent, up to as much again as {@link #STUD_SPREAD}); a race's pace (blocks a second) and slack (seconds). */
	static final int STUD_MIN = 45, STUD_SPREAD = 20;
	static final double RACE_PACE = 6.5, RACE_FINISH = 24.0;
	static final int RACE_SLACK = 40;
	/** A race goes to a settlement at least this far, and at most this far. */
	static final double RACE_MIN = 150.0, RACE_MAX = 900.0;
	/** A stablehand's quest is a horse one this often; a king with a stable asks a horse quest this often. */
	static final float STABLEHAND_HORSE_QUEST = 0.75F, KING_HORSE_QUEST = 0.2F;

	/** Tags: a horse bought at a stable (no wild one to break in), one whose saddlebags are emptied. */
	static final String BOUGHT = "jaymoskingdoms.bought", RIFLED = "jaymoskingdoms.bags_rifled";

	/** A boarded horse: the settlement keeping it, the day it came in, and its stall. */
	public record Stall(int faction, long day, BlockPos stall) {
		public static final Codec<Stall> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("faction").forGetter(Stall::faction),
				Codec.LONG.fieldOf("day").forGetter(Stall::day),
				BlockPos.CODEC.fieldOf("stall").forGetter(Stall::stall))
			.apply(i, Stall::new));
	}

	public static final AttachmentType<Stall> STALL = AttachmentRegistry.create(JaymosKingdoms.id("stall"), b -> b.persistent(Stall.CODEC));

	/** One of the tack shop's wares: the item, how many, its price, and the least wealth that stocks it. */
	private record Ware(Item item, int count, int price, Wealth.Tier tier) {
	}

	/** One of the market's horses. */
	record Offer(Equine equine, int price) {
	}

	private static final class Job {
		@Nullable UUID horse;
		long next;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	/** When each player was last greeted by each stable worker. */
	private static final Map<String, Long> GREETED = new HashMap<>();
	/** Market horses sold (faction:day:index). */
	private static final Map<String, Boolean> SOLD = new HashMap<>();

	private Stables() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % UPKEEP_TICKS == 0) {
				upkeep(server);
			}
		});
		UseEntityCallback.EVENT.register(Stables::use);
		StableNet.register();
		ServerPlayNetworking.registerGlobalReceiver(StableNet.Act.TYPE, (payload, context) -> act(context.player(), payload));
		// a boarded horse can't smother in its stall's walls (a tall breed, a low roof)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof AbstractHorse && source.is(DamageTypes.IN_WALL)
			&& boarded(entity)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			GREETED.clear();
			SOLD.clear();
		});
	}

	// ---- the stables ------------------------------------------------------------------------------------------------

	/** {@code f}'s standing stables. */
	static List<Works.Built> stables(MinecraftServer server, Faction f) {
		return Works.of(server, f.id()).built().stream().filter(b -> b.entry().equals("stable") && b.ruined() == 0).toList();
	}

	static boolean has(MinecraftServer server, Faction f) {
		return !stables(server, f).isEmpty();
	}

	/** How good its stables are (0 none; one for each standing stable, up to {@link #MAX_LEVEL}). */
	static int level(MinecraftServer server, Faction f) {
		return Math.min(MAX_LEVEL, stables(server, f).size());
	}

	private static BlockPos middle(Works.Built b) {
		return new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY() + 1, (b.min().getZ() + b.max().getZ()) / 2);
	}

	private static AABB box(Works.Built b) {
		return new AABB(Vec3.atLowerCornerOf(b.min()), Vec3.atLowerCornerOf(b.max()).add(1.0, 1.0, 1.0));
	}

	/** The stable nearest {@code pos}, or null. */
	private static Works.@Nullable Built stableNear(MinecraftServer server, Faction f, BlockPos pos) {
		return stables(server, f).stream().min(Comparator.comparingDouble(b -> middle(b).distSqr(pos))).orElse(null);
	}

	// ---- the workers ------------------------------------------------------------------------------------------------

	/** It wants a stablehand (any settlement with a stable) or a farrier (one with {@link #FARRIER_AT} people besides). */
	static boolean wanted(MinecraftServer server, Faction f, int role) {
		if (f.fallen() || f.type() == FactionType.BARBARIAN || !has(server, f)) {
			return false;
		}
		int want = role == Folk.STABLEHAND ? 1 : role == Folk.FARRIER && f.holdings().population() >= FARRIER_AT ? 1 : 0;
		int have = f.holdings().count(role) + f.holdings().vacancies().getOrDefault(Folk.roleName(role), 0);
		return have < want;
	}

	/** A stablehand or farrier for {@code f} (counted in its roster), at its stable; {@code from} where they come from. */
	static @Nullable Worker spawn(ServerLevel level, Faction f, int role, @Nullable BlockPos from) {
		List<Works.Built> stables = stables(level.getServer(), f);
		if (stables.isEmpty()) {
			return null;
		}
		BlockPos mid = middle(stables.getFirst());
		BlockPos at = Construction.standable(level, mid, 3);
		Worker w = Workers.body(level, f, role, from != null ? from : at, mid, ROAM);
		if (w == null) {
			return null;
		}
		w.setSite(mid);
		Factions.update(level.getServer(), f.id(), x -> x.withHoldings(x.holdings().adjust(role, 1)));
		JaymosKingdoms.LOGGER.debug("{} took on a {}", f.name(), Folk.roleName(role));
		return w;
	}

	/** A stable's just been finished: its stablehand comes at once. */
	static void built(ServerLevel level, Faction f) {
		if (wanted(level.getServer(), f, Folk.STABLEHAND)) {
			Faction now = Factions.get(level.getServer(), f.id());
			if (now != null) {
				spawn(level, now, Folk.STABLEHAND, Economy.recruit(level, now));
			}
		}
	}

	static boolean isStableWorker(int role) {
		return role == Folk.STABLEHAND || role == Folk.FARRIER;
	}

	/** A stable worker's day: about the stable, going from horse to horse (brushing them, or picking their hooves), greeting whoever comes by. */
	static void tick(ServerLevel level, Worker w) {
		if (w.ticksAlive() % 10 != 0) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, w.factionId());
		Works.Built stable = f == null ? null : stableNear(server, f, w.blockPosition());
		if (f == null || stable == null) {
			w.setTask("idle");
			return;
		}
		BlockPos mid = middle(stable);
		if (!w.getHomePosition().equals(mid)) {
			w.setHomeTo(mid, ROAM);
			w.setSite(mid);
		}
		if (w.ticksAlive() % 20 == 0) {
			greet(level, w, f);
		}
		long now = level.getGameTime();
		Job job = JOBS.computeIfAbsent(w.getUUID(), k -> new Job());
		AbstractHorse horse = job.horse == null ? null : level.getEntity(job.horse) instanceof AbstractHorse h && h.isAlive() ? h : null;
		if (horse == null && now >= job.next) {
			List<AbstractHorse> horses = level.getEntitiesOfClass(AbstractHorse.class, box(stable).inflate(4.0),
				h -> h.isAlive() && Equestrian.covered(h) && !h.isVehicle());
			if (!horses.isEmpty()) {
				horse = horses.get(level.getRandom().nextInt(horses.size()));
				job.horse = horse.getUUID();
			}
			job.next = now + TEND_TICKS + level.getRandom().nextInt(TEND_TICKS);
		}
		if (horse == null || horse.distanceToSqr(Vec3.atCenterOf(mid)) > (ROAM + 6.0) * (ROAM + 6.0)) {
			job.horse = null;
			if (w.distanceToSqr(Vec3.atCenterOf(mid)) > ROAM * ROAM) {
				w.getNavigation().moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5, 0.6);
			}
			w.setTask("idle");
			return;
		}
		w.setTask(w.role() == Folk.FARRIER ? "seeing to hooves" : "tending horses");
		if (w.mob().distanceTo(horse) > 2.5F) {
			w.getNavigation().moveTo(horse, 0.6);
			return;
		}
		w.getNavigation().stop();
		w.getLookControl().setLookAt(horse);
		w.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		tend(level, w, horse, f);
		job.horse = null;
	}

	/** {@code w} sees to {@code horse}: a boarded one's needs met (the stablehand) or its hooves picked (the farrier). */
	private static void tend(ServerLevel level, Worker w, AbstractHorse horse, Faction f) {
		Stall stall = horse.getAttached(STALL);
		boolean boarded = stall != null && stall.faction() == f.id();
		if (w.role() == Folk.FARRIER) {
			level.playSound(null, horse.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.35F, 1.7F);
			level.sendParticles(ParticleTypes.CRIT, horse.getX(), horse.getY() + 0.3, horse.getZ(), 6, 0.4, 0.1, 0.4, 0.05);
			if (boarded && hooved(horse)) {
				Bond b = Bonding.bond(horse);
				horse.setAttached(Bonding.BOND, b.withFeet(Bond.FULL, b.shoes()));
			}
			return;
		}
		level.playSound(null, horse.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 0.8F, 1.0F);
		level.sendParticles(ParticleTypes.WAX_OFF, horse.getX(), horse.getY() + horse.getBbHeight() * 0.7, horse.getZ(), 10, 0.5, 0.3, 0.5, 0.0);
		if (boarded) {
			care(level, horse);
		}
	}

	/** A boarded horse fed, watered and brushed. */
	private static void care(ServerLevel level, AbstractHorse horse) {
		Bond b = Bonding.bond(horse).withNeeds(Bond.FULL, Bond.FULL);
		long now = level.getGameTime();
		if (b.shine() < now) {
			b = b.withBrush(now + SHINE_TICKS, now);
		}
		horse.setAttached(Bonding.BOND, b);
	}

	private static boolean hooved(AbstractHorse horse) {
		return !(horse instanceof WarElephant) && !Equestrian.undead(horse);
	}

	// ---- talking to them --------------------------------------------------------------------------------------------

	private static void greet(ServerLevel level, Worker w, Faction f) {
		long now = level.getGameTime();
		for (ServerPlayer player : level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(w.position()) < GREET_RANGE * GREET_RANGE)) {
			String key = player.getUUID() + ":" + w.getUUID();
			Long last = GREETED.get(key);
			if (last != null && now - last < GREET_COOLDOWN) {
				continue;
			}
			GREETED.put(key, now);
			hint(player, w, f);
		}
	}

	/** A word from {@code w} as {@code player} comes by: who they are, and how to open their screen (or a click on the line). */
	private static void hint(ServerPlayer player, Worker w, Faction f) {
		if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			return;
		}
		String role = w.role() == Folk.FARRIER ? "farrier" : "stable";
		Component how = Component.translatable(McaCompat.isMca(w.mob()) ? "message.jaymoskingdoms.stable.how_mca" : "message.jaymoskingdoms.stable.how");
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms." + role + ".greet", w.mob().getName(), f.name(), WEEKLY_FEE).withStyle(ChatFormatting.GOLD));
		option(player, Component.translatable("message.jaymoskingdoms.stable.open", w.mob().getName(), how), "/kingdom " + role);
	}

	/** Tells {@code player} {@code text}: on the screen they pressed a button on, else in chat. */
	private static void tell(ServerPlayer player, Component text) {
		if (screen != null && screen.player == player) {
			screen.said.add(text);
		} else {
			player.sendSystemMessage(text);
		}
	}

	private static void option(ServerPlayer player, Component text, String command) {
		tell(player, Component.literal("  ").append(text.copy().withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(command))))));
	}

	/** The stable worker of {@code role} nearest {@code player} (within {@link #MENU_RANGE}), or null (and they're told). */
	private static @Nullable Worker near(ServerPlayer player, int role) {
		if (screen != null && screen.player == player && screen.worker.role() == role) {
			return screen.worker;
		}
		ServerLevel level = player.level();
		List<Worker> found = Workers.near(level, player.getBoundingBox().inflate(MENU_RANGE), w -> w.role() == role && w.isAlive());
		if (found.isEmpty()) {
			tell(player, Component.translatable("message.jaymoskingdoms." + (role == Folk.FARRIER ? "farrier" : "stable") + ".none_near")
				.withStyle(ChatFormatting.GRAY));
			return null;
		}
		found.sort(Comparator.comparingDouble(w -> w.distanceToSqr(player.position())));
		return found.getFirst();
	}

	private static @Nullable Faction served(ServerPlayer player, Worker w) {
		Faction f = Factions.get(player.level().getServer(), w.factionId());
		if (f == null || f.fallen()) {
			return null;
		}
		if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.refused", w.mob().getName()).withStyle(ChatFormatting.RED));
			return null;
		}
		return f;
	}

	/**
	 * The horse {@code player} means: the one they ride, else one on their lead, else their nearest (within
	 * {@link #HORSE_RANGE}); a boarded one only if {@code boarded}. Null if none.
	 */
	private static @Nullable AbstractHorse yourHorse(ServerPlayer player, boolean boarded) {
		if (player.getVehicle() instanceof AbstractHorse h && mine(h, player) && boarded(h) == boarded) {
			return h;
		}
		List<AbstractHorse> horses = player.level().getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(HORSE_RANGE),
			h -> h.isAlive() && mine(h, player) && boarded(h) == boarded && (!h.isVehicle() || h.getControllingPassenger() == player));
		for (AbstractHorse h : horses) {
			if (h.isLeashed() && h.getLeashHolder() == player) {
				return h;
			}
		}
		return horses.stream().min(Comparator.comparingDouble(h -> h.distanceToSqr(player))).orElse(null);
	}

	private static boolean mine(AbstractHorse horse, Player player) {
		return Equestrian.covered(horse) && horse.isTamed() && Bonding.owner(horse, player);
	}

	private static @Nullable AbstractHorse horseById(ServerPlayer player, int id) {
		return player.level().getEntity(id) instanceof AbstractHorse h && h.isAlive() && mine(h, player) && h.distanceTo(player) <= HORSE_RANGE + 8.0 ? h : null;
	}

	// ---- boarding ---------------------------------------------------------------------------------------------------

	/** {@code e} is boarded at a stable. */
	public static boolean boarded(Entity e) {
		return e.hasAttached(STALL);
	}

	private static List<AbstractHorse> boardedHere(ServerLevel level, ServerPlayer player, Faction f) {
		List<AbstractHorse> out = new ArrayList<>();
		for (Works.Built b : stables(level.getServer(), f)) {
			out.addAll(level.getEntitiesOfClass(AbstractHorse.class, box(b).inflate(STALL_FAR + 2.0), h -> h.isAlive() && Bonding.owner(h, player)
				&& h.getAttached(STALL) != null && h.getAttached(STALL).faction() == f.id() && !out.contains(h)));
		}
		return out;
	}

	/** What's owed for {@code horse}'s boarding so far: {@link #WEEKLY_FEE} for each week begun. */
	static int fee(ServerLevel level, AbstractHorse horse) {
		Stall s = horse.getAttached(STALL);
		if (s == null) {
			return 0;
		}
		long days = Math.max(0L, Production.day(level.getServer()) - s.day());
		return (int)(days / WEEK_DAYS + 1) * WEEKLY_FEE;
	}

	/** A free spot by one of the stable's hay blocks (the stalls'), else its middle. */
	private static BlockPos stall(ServerLevel level, Works.Built b, Faction f) {
		List<BlockPos> hay = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(b.min(), b.max())) {
			if (level.getBlockState(p).is(Blocks.HAY_BLOCK)) {
				hay.add(p.immutable());
			}
		}
		int taken = level.getEntitiesOfClass(AbstractHorse.class, box(b).inflate(STALL_FAR), h -> h.getAttached(STALL) != null).size();
		if (hay.isEmpty()) {
			return Construction.standable(level, middle(b), 3);
		}
		for (int i = 0; i < hay.size(); i++) {
			// (beside the hay, room for a horse's height: on top of it, under a stall's roof, it would smother)
			BlockPos h = hay.get((taken + i) % hay.size());
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos n = h.relative(d);
				if (clear(level, n) && !level.getBlockState(n.below()).getCollisionShape(level, n.below()).isEmpty()) {
					return n;
				}
			}
		}
		return Construction.standable(level, middle(b), 3);
	}

	/** Room for a horse to stand at {@code pos}: three blocks clear. */
	private static boolean clear(ServerLevel level, BlockPos pos) {
		for (int y = 0; y < 3; y++) {
			if (!level.getBlockState(pos.above(y)).getCollisionShape(level, pos.above(y)).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Boards horse {@code id} ({@code -1}: the one {@code player} rides or leads, else their nearest). */
	private static int board(ServerPlayer player, int id) {
		ServerLevel level = player.level();
		Worker w = near(player, Folk.STABLEHAND);
		Faction f = w == null ? null : served(player, w);
		Works.Built stable = f == null ? null : stableNear(level.getServer(), f, w.blockPosition());
		if (stable == null) {
			return 0;
		}
		AbstractHorse horse = id < 0 ? yourHorse(player, false) : horseById(player, id);
		if (horse == null || boarded(horse)) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.no_horse").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		BlockPos at = stall(level, stable, f);
		horse.ejectPassengers();
		if (horse.isLeashed()) {
			horse.dropLeash();
		}
		horse.getNavigation().stop();
		horse.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		horse.setAttached(STALL, new Stall(f.id(), Production.day(level.getServer()), at));
		care(level, horse);
		level.playSound(null, at, SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
		tell(player, Component.translatable("message.jaymoskingdoms.stable.boarded", Bonding.name(horse), w.mob().getName(), WEEKLY_FEE)
			.withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	private static int collect(ServerPlayer player, int id) {
		ServerLevel level = player.level();
		Worker w = near(player, Folk.STABLEHAND);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		AbstractHorse horse = player.level().getEntity(id) instanceof AbstractHorse h && h.isAlive() && Bonding.owner(h, player) ? h : null;
		Stall s = horse == null ? null : horse.getAttached(STALL);
		if (s == null || s.faction() != f.id()) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.not_here").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		int fee = fee(level, horse);
		if (!pay(player, f, fee)) {
			return 0;
		}
		horse.removeAttached(STALL);
		BlockPos at = Construction.standable(level, player.blockPosition(), 3);
		horse.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.playSound(null, at, SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.1F);
		tell(player, Component.translatable("message.jaymoskingdoms.stable.collected", Bonding.name(horse), fee).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	/** Boarded horses kept in their stalls (and seen to while there's a stablehand); races checked. */
	private static void upkeep(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		for (AbstractHorse horse : level.getEntities(EntityTypeTest.forClass(AbstractHorse.class), h -> h.isAlive() && h.hasAttached(STALL))) {
			Stall s = horse.getAttached(STALL);
			Faction f = Factions.get(server, s.faction());
			if (f == null || f.fallen() || !has(server, f)) {
				// (its stable's gone: it's free to go)
				horse.removeAttached(STALL);
				continue;
			}
			if (horse.isVehicle()) {
				for (Entity rider : horse.getPassengers()) {
					if (rider instanceof ServerPlayer p) {
						p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stable.pay_first", Bonding.name(horse), fee(level, horse))
							.withStyle(ChatFormatting.GRAY), true);
					}
				}
				horse.ejectPassengers();
			}
			Vec3 stall = Vec3.atBottomCenterOf(s.stall());
			double d = horse.position().distanceTo(stall);
			if (d > STALL_FAR) {
				horse.getNavigation().stop();
				horse.teleportTo(stall.x, stall.y, stall.z);
			} else if (d > STALL_NEAR) {
				horse.getNavigation().moveTo(stall.x, stall.y, stall.z, 1.0);
			}
			if (f.holdings().count(Folk.STABLEHAND) > 0) {
				care(level, horse);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			races(level, player);
		}
		if (level.getGameTime() % STAFF_TICKS == 0) {
			// (a stable built before 0.5.47, or a worker lost: its people come whether or not the settlement's short-handed)
			for (Faction f : Factions.all(server)) {
				for (int role : new int[] {Folk.STABLEHAND, Folk.FARRIER}) {
					if (level.isPositionEntityTicking(f.seat()) && wanted(server, f, role) && !Economy.underAttack(server, f)) {
						spawn(level, f, role, Economy.recruit(level, f));
						f = Factions.get(server, f.id());
						if (f == null) {
							break;
						}
					}
				}
			}
		}
	}

	/** {@code player} pays {@code cost} emeralds into {@code f}'s treasury (nothing in creative); false (and told) if they can't. */
	static boolean pay(ServerPlayer player, Faction f, int cost) {
		if (player.isCreative() || cost <= 0) {
			return true;
		}
		if (Purse.count(player, Items.EMERALD) < cost) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.cant_pay", cost).withStyle(ChatFormatting.RED));
			return false;
		}
		Purse.take(player, Items.EMERALD, cost);
		Factions.update(player.level().getServer(), f.id(), x -> x.withTreasury(x.treasury() + cost));
		return true;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
	}

	private static void giveEmeralds(ServerPlayer player, int n) {
		while (n > 0) {
			int k = Math.min(64, n);
			give(player, new ItemStack(Items.EMERALD, k));
			n -= k;
		}
	}

	// ---- the market -------------------------------------------------------------------------------------------------

	/** What a horse is worth: its breed's price, from half to one and a half times by its quality. */
	static int value(Equine e) {
		return (int)Math.round(BREED_PRICE[e.breed().ordinal()] * (0.5 + quality(e)));
	}

	/** How good a horse is (0-1): its speed, stamina, jump and courage. */
	static double quality(Equine e) {
		return (e.speed() + e.stamina() + e.jump() + e.courage()) / 4.0;
	}

	private static int sellPrice(AbstractHorse horse) {
		double price = value(Equestrian.equine(horse)) * SELL_SHARE * (horse.isBaby() ? FOAL_SHARE : 1.0);
		return Math.max(1, (int)Math.round(price));
	}

	/** Today's horses at {@code f}'s stable (the same all day for everyone). */
	static List<Offer> offers(ServerLevel level, Faction f) {
		Wealth.Tier tier = Wealth.of(level.getServer(), f);
		long day = Production.day(level.getServer());
		RandomSource random = RandomSource.create(day * 7919L + f.id() * 104729L);
		List<Offer> out = new ArrayList<>();
		for (int i = 0; i < OFFERS[tier.ordinal()]; i++) {
			Breed breed = offerBreed(level, f, tier, random);
			Equine rolled = Equine.roll(breed, random);
			Equine e = new Equine(breed, rolled.coat(), stat(tier, random), stat(tier, random), stat(tier, random), stat(tier, random), rolled.temper());
			out.add(new Offer(e, value(e)));
		}
		return out;
	}

	/** A stat for a horse a stable of this wealth sells: a poor one's are middling at best, a wealthy one's the better of two. */
	private static float stat(Wealth.Tier tier, RandomSource random) {
		float a = (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3.0F;
		return switch (tier) {
			case POOR -> a * 0.8F;
			case PROSPEROUS -> a;
			case WEALTHY -> Math.max(a, (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3.0F);
		};
	}

	/** The breeds a stable sells: a poor one palfreys, ponies and drafts; richer ones the land's own, coursers, and (the wealthy) destriers. */
	private static Breed offerBreed(ServerLevel level, Faction f, Wealth.Tier tier, RandomSource random) {
		Climate climate = Climate.at(level, f.seat());
		float x = random.nextFloat();
		if (tier == Wealth.Tier.POOR) {
			if (climate.arid() || climate == Climate.SAVANNA) {
				return x < 0.6F ? Breed.STEPPE_PONY : Breed.PALFREY;
			}
			return x < 0.5F ? Breed.PALFREY : x < 0.75F ? Breed.STEPPE_PONY : Breed.DRAFT;
		}
		boolean wealthy = tier == Wealth.Tier.WEALTHY;
		if (climate.arid()) {
			return x < 0.6F ? Breed.DESERT_HORSE : wealthy && x < 0.85F ? Breed.DESTRIER : Breed.PALFREY;
		} else if (climate == Climate.SAVANNA) {
			return x < 0.5F ? Breed.STEPPE_PONY : x < 0.8F ? Breed.DESERT_HORSE : Breed.COURSER;
		} else if (climate == Climate.SNOWY) {
			return x < 0.5F ? Breed.DRAFT : wealthy && x < 0.8F ? Breed.DESTRIER : Breed.STEPPE_PONY;
		}
		if (wealthy) {
			return x < 0.35F ? Breed.DESTRIER : x < 0.7F ? Breed.COURSER : x < 0.85F ? Breed.PALFREY : Breed.DRAFT;
		}
		return x < 0.35F ? Breed.PALFREY : x < 0.65F ? Breed.COURSER : x < 0.85F ? Breed.DRAFT : Breed.STEPPE_PONY;
	}

	private static String soldKey(ServerLevel level, Faction f, int index) {
		return f.id() + ":" + Production.day(level.getServer()) + ":" + index;
	}

	private static int market(ServerPlayer player) {
		ServerLevel level = player.level();
		Worker w = near(player, Folk.STABLEHAND);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		List<Offer> offers = offers(level, f);
		tell(player, Component.translatable("message.jaymoskingdoms.stable.market", f.name()).withStyle(ChatFormatting.GOLD));
		int shown = 0;
		for (int i = 0; i < offers.size(); i++) {
			if (SOLD.containsKey(soldKey(level, f, i))) {
				continue;
			}
			Equine e = offers.get(i).equine();
			MutableComponent line = Component.translatable("message.jaymoskingdoms.stable.offer", Component.translatable(e.breed().key()),
				Component.translatable("coat.jaymoskingdoms." + e.coatName()), pct(e.speed()), pct(e.stamina()), pct(e.jump()), pct(e.courage()),
				offers.get(i).price());
			option(player, line, "/kingdom stable buy " + i);
			shown++;
		}
		if (shown == 0) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.sold_out").withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int pct(float stat) {
		return Math.round(stat * 100.0F);
	}

	private static int buy(ServerPlayer player, int index) {
		ServerLevel level = player.level();
		Worker w = near(player, Folk.STABLEHAND);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		List<Offer> offers = offers(level, f);
		String key = soldKey(level, f, index);
		if (index < 0 || index >= offers.size() || SOLD.containsKey(key)) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.gone").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Offer offer = offers.get(index);
		if (!pay(player, f, offer.price())) {
			return 0;
		}
		BlockPos at = Construction.standable(level, w.blockPosition(), 3);
		AbstractHorse horse = Equestrian.create(level, offer.equine().breed(), at, EntitySpawnReason.SPAWN_ITEM_USE);
		if (horse == null) {
			return 0;
		}
		Equestrian.apply(horse, offer.equine(), true);
		horse.setPersistenceRequired();
		horse.addTag(BOUGHT);
		level.addFreshEntity(horse);
		horse.tameWithName(player);
		Bonding.tamed(horse, player);
		SOLD.put(key, true);
		level.playSound(null, at, SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
		tell(player, Component.translatable("message.jaymoskingdoms.stable.bought", Component.translatable(offer.equine().breed().key()),
			offer.price()).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	private static int sell(ServerPlayer player, int id) {
		ServerLevel level = player.level();
		Worker w = near(player, Folk.STABLEHAND);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		AbstractHorse horse = horseById(player, id);
		if (horse == null || boarded(horse) || horse instanceof WarElephant) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.no_horse").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		int price = sellPrice(horse);
		Faction now = Factions.get(level.getServer(), f.id());
		if (now == null || now.treasury() < price) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.too_poor", w.mob().getName(), price).withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Factions.update(level.getServer(), f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - price)));
		giveEmeralds(player, price);
		Component name = Bonding.name(horse);
		handOver(level, player, horse, f, w.blockPosition());
		tell(player, Component.translatable("message.jaymoskingdoms.stable.sold", name, price).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	/** {@code horse} becomes {@code f}'s: its tack back to {@code player}, then one of the realm's horses at its stable (a mount for its cavalry). */
	static void handOver(ServerLevel level, ServerPlayer player, AbstractHorse horse, Faction f, BlockPos near) {
		horse.ejectPassengers();
		if (horse.isLeashed()) {
			horse.dropLeash();
		}
		Tack t = Bonding.tack(horse);
		if (!t.bridle().isEmpty()) {
			give(player, t.bridle().copy());
		}
		if (!t.blanket().isEmpty()) {
			give(player, t.blanket().copy());
		}
		horse.setAttached(Bonding.TACK, Tack.NONE);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.SADDLE, EquipmentSlot.BODY}) {
			ItemStack worn = horse.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				give(player, worn.copy());
				horse.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		horse.setOwner(null);
		horse.setTamed(true);
		horse.removeTag(BOUGHT);
		horse.addTag(Cavalry.TAG);
		horse.addTag(Cavalry.OF + f.id());
		horse.setPersistenceRequired();
		Works.Built stable = stableNear(level.getServer(), f, near);
		BlockPos at = stable != null ? Construction.standable(level, middle(stable), 3) : Construction.standable(level, near, 3);
		horse.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	// ---- the farrier ------------------------------------------------------------------------------------------------

	/** The tack shop: what a farrier stocks, the better wares only in wealthier kingdoms (plate barding in wealthy ones). */
	private static List<Ware> wares() {
		return List.of(
			new Ware(Tackroom.HORSESHOES, 1, 2, Wealth.Tier.POOR),
			new Ware(Tackroom.HOOF_PICK, 1, 2, Wealth.Tier.POOR),
			new Ware(Tackroom.OATS, 8, 2, Wealth.Tier.POOR),
			new Ware(Items.LEAD, 1, 1, Wealth.Tier.POOR),
			new Ware(Items.SADDLE, 1, 6, Wealth.Tier.POOR),
			new Ware(Tackroom.BRIDLE, 1, 4, Wealth.Tier.POOR),
			new Ware(Items.BRUSH, 1, 2, Wealth.Tier.PROSPEROUS),
			new Ware(Tackroom.HORSE_BLANKET, 1, 4, Wealth.Tier.PROSPEROUS),
			new Ware(Tackroom.RACING_SADDLE, 1, 10, Wealth.Tier.PROSPEROUS),
			new Ware(Tackroom.CHAIN_BARDING, 1, 14, Wealth.Tier.PROSPEROUS),
			new Ware(Tackroom.WAR_SADDLE, 1, 14, Wealth.Tier.WEALTHY),
			new Ware(Tackroom.PLATE_BARDING, 1, 28, Wealth.Tier.WEALTHY));
	}

	private static List<Ware> stock(MinecraftServer server, Faction f) {
		return wares().stream().filter(w -> Wealth.atLeast(server, f, w.tier())).toList();
	}

	private static int tack(ServerPlayer player) {
		Worker w = near(player, Folk.FARRIER);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		tell(player, Component.translatable("message.jaymoskingdoms.farrier.tack", f.name()).withStyle(ChatFormatting.GOLD));
		MinecraftServer server = player.level().getServer();
		List<Ware> all = wares();
		for (int i = 0; i < all.size(); i++) {
			Ware ware = all.get(i);
			if (!Wealth.atLeast(server, f, ware.tier())) {
				continue;
			}
			option(player, Component.translatable("message.jaymoskingdoms.farrier.ware", ware.count(), new ItemStack(ware.item()).getHoverName(), ware.price()),
				"/kingdom farrier buy " + i);
		}
		if (stock(server, f).size() < all.size()) {
			tell(player, Component.translatable("message.jaymoskingdoms.farrier.richer").withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int buyTack(ServerPlayer player, int index) {
		Worker w = near(player, Folk.FARRIER);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		List<Ware> all = wares();
		if (index < 0 || index >= all.size() || !Wealth.atLeast(server, f, all.get(index).tier())) {
			return 0;
		}
		Ware ware = all.get(index);
		if (!pay(player, f, ware.price())) {
			return 0;
		}
		ItemStack stack = new ItemStack(ware.item(), ware.count());
		if (ware.item() == Tackroom.HORSE_BLANKET) {
			// (in the realm's colour)
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(f.styleColor(server).getTextureDiffuseColor() & 0xFFFFFF));
		}
		tell(player, Component.translatable("message.jaymoskingdoms.farrier.bought", stack.getHoverName(), ware.price()).withStyle(ChatFormatting.YELLOW));
		give(player, stack);
		return 1;
	}

	private static int shoe(ServerPlayer player, int id) {
		ServerLevel level = player.level();
		Worker w = near(player, Folk.FARRIER);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		AbstractHorse horse = horseById(player, id);
		if (horse == null || !hooved(horse)) {
			tell(player, Component.translatable("message.jaymoskingdoms.stable.no_horse").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!pay(player, f, SHOE_PRICE)) {
			return 0;
		}
		Bond b = Bonding.bond(horse);
		horse.setAttached(Bonding.BOND, b.withFeet(Bond.FULL, Bond.FULL));
		level.playSound(null, horse.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.5F);
		level.sendParticles(ParticleTypes.CRIT, horse.getX(), horse.getY() + 0.3, horse.getZ(), 12, 0.4, 0.1, 0.4, 0.05);
		tell(player, Component.translatable("message.jaymoskingdoms.farrier.shod", w.mob().getName(), Bonding.name(horse), SHOE_PRICE)
			.withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	// ---- commands ---------------------------------------------------------------------------------------------------

	/** {@code /kingdom stable [board | collect <horse> | market | buy <n> | sell <horse>]}: what the clickable offers run. */
	static LiteralArgumentBuilder<CommandSourceStack> stableCommand() {
		return Commands.literal("stable")
			.executes(c -> open(c.getSource().getPlayerOrException(), Folk.STABLEHAND))
			.then(Commands.literal("board").executes(c -> board(c.getSource().getPlayerOrException(), -1)))
			.then(Commands.literal("collect").then(Commands.argument("horse", IntegerArgumentType.integer())
				.executes(c -> collect(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "horse")))))
			.then(Commands.literal("market").executes(c -> market(c.getSource().getPlayerOrException())))
			.then(Commands.literal("buy").then(Commands.argument("n", IntegerArgumentType.integer(0))
				.executes(c -> buy(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n")))))
			.then(Commands.literal("sell").then(Commands.argument("horse", IntegerArgumentType.integer())
				.executes(c -> sell(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "horse")))));
	}

	/** {@code /kingdom farrier [shoe <horse> | tack | buy <n>]}. */
	static LiteralArgumentBuilder<CommandSourceStack> farrierCommand() {
		return Commands.literal("farrier")
			.executes(c -> open(c.getSource().getPlayerOrException(), Folk.FARRIER))
			.then(Commands.literal("shoe").then(Commands.argument("horse", IntegerArgumentType.integer())
				.executes(c -> shoe(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "horse")))))
			.then(Commands.literal("tack").executes(c -> tack(c.getSource().getPlayerOrException())))
			.then(Commands.literal("buy").then(Commands.argument("n", IntegerArgumentType.integer(0))
				.executes(c -> buyTack(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n")))));
	}

	private static int open(ServerPlayer player, int role) {
		Worker w = near(player, role);
		Faction f = w == null ? null : served(player, w);
		if (f == null) {
			return 0;
		}
		show(player, w, f, List.of());
		return 1;
	}

	// ---- the screen -------------------------------------------------------------------------------------------------

	/** A button being pressed on {@code worker}'s screen: what's said goes on the screen, not in chat. */
	private static final class Pressing {
		final ServerPlayer player;
		final Worker worker;
		final List<Component> said = new ArrayList<>();

		Pressing(ServerPlayer player, Worker worker) {
			this.player = player;
			this.worker = worker;
		}
	}

	private static @Nullable Pressing screen;

	/** A click on {@code w} opens its screen: a plain one on folk, a sneaking one on an MCA villager (a plain one is MCA's own). */
	private static boolean opens(ServerPlayer player, Worker w) {
		return McaCompat.isMca(w.mob()) == player.isSecondaryUseActive() && w.factionId() >= 0;
	}

	/** Sends {@code player} {@code w}'s screen, {@code said} (what the last button did) under it. */
	private static void show(ServerPlayer player, Worker w, Faction f, List<Component> said) {
		if (!ServerPlayNetworking.canSend(player, StableNet.Open.TYPE)) {
			// (no mod on their side: the old way, a word and a click)
			hint(player, w, f);
			return;
		}
		ServerLevel level = player.level();
		boolean farrier = w.role() == Folk.FARRIER;
		Component title = Component.translatable("gui.jaymoskingdoms." + (farrier ? "farrier" : "stable") + ".title", w.mob().getName(), f.name());
		int purse = player.getInventory().countItem(Items.EMERALD);
		Component subtitle = farrier ? Component.translatable("gui.jaymoskingdoms.farrier.subtitle", SHOE_PRICE, purse)
			: Component.translatable("gui.jaymoskingdoms.stable.subtitle", WEEKLY_FEE, purse);
		MutableComponent note = Component.empty();
		for (Component c : said) {
			note.append(note.getSiblings().isEmpty() ? Component.empty() : Component.literal(" ")).append(c);
		}
		List<StableNet.Section> sections = farrier ? farrierScreen(player, f) : stableScreen(level, player, f);
		ServerPlayNetworking.send(player, new StableNet.Open(w.mob().getId(), title, subtitle, note, sections));
	}

	private static List<StableNet.Section> stableScreen(ServerLevel level, ServerPlayer player, Faction f) {
		List<StableNet.Row> yours = new ArrayList<>();
		for (AbstractHorse horse : yours(player, false)) {
			boolean sellable = !(horse instanceof WarElephant);
			yours.add(new StableNet.Row(icon(Equestrian.equine(horse).breed()), Optional.of(Equestrian.equine(horse)), label(horse), stats(Equestrian.equine(horse)), List.of(
				new StableNet.Button(Component.translatable("gui.jaymoskingdoms.stable.board"), StableNet.BOARD, horse.getId(), true),
				new StableNet.Button(Component.translatable("gui.jaymoskingdoms.stable.sell", sellPrice(horse)), StableNet.SELL, horse.getId(), sellable))));
		}
		List<StableNet.Row> kept = new ArrayList<>();
		for (AbstractHorse horse : boardedHere(level, player, f)) {
			int fee = fee(level, horse);
			kept.add(new StableNet.Row(icon(Equestrian.equine(horse).breed()), Optional.of(Equestrian.equine(horse)), label(horse), Component.translatable("gui.jaymoskingdoms.stable.owed", fee),
				List.of(new StableNet.Button(Component.translatable("gui.jaymoskingdoms.stable.collect", fee), StableNet.COLLECT, horse.getId(), true))));
		}
		List<StableNet.Row> market = new ArrayList<>();
		List<Offer> offers = offers(level, f);
		for (int i = 0; i < offers.size(); i++) {
			if (SOLD.containsKey(soldKey(level, f, i))) {
				continue;
			}
			Equine e = offers.get(i).equine();
			Component name = Component.translatable("gui.jaymoskingdoms.stable.offer", Component.translatable(e.breed().key()),
				Component.translatable("coat.jaymoskingdoms." + e.coatName()));
			market.add(new StableNet.Row(icon(e.breed()), Optional.of(e), name, stats(e), List.of(
				new StableNet.Button(Component.translatable("gui.jaymoskingdoms.stable.buy", offers.get(i).price()), StableNet.BUY, i, true))));
		}
		return List.of(
			new StableNet.Section(Component.translatable("gui.jaymoskingdoms.stable.yours"), Component.translatable("gui.jaymoskingdoms.stable.yours_none"), yours),
			new StableNet.Section(Component.translatable("gui.jaymoskingdoms.stable.kept"), Component.translatable("gui.jaymoskingdoms.stable.kept_none"), kept),
			new StableNet.Section(Component.translatable("gui.jaymoskingdoms.stable.market"), Component.translatable("message.jaymoskingdoms.stable.sold_out"), market));
	}

	private static List<StableNet.Section> farrierScreen(ServerPlayer player, Faction f) {
		List<StableNet.Row> horses = new ArrayList<>();
		List<AbstractHorse> all = new ArrayList<>(yours(player, false));
		all.addAll(yours(player, true));
		for (AbstractHorse horse : all) {
			if (!hooved(horse)) {
				continue;
			}
			Bond b = Bonding.bond(horse);
			Component feet = Component.translatable("gui.jaymoskingdoms.farrier.feet", Math.round(b.hooves() / Bond.FULL * 100.0F),
				Math.round(b.shoes() / Bond.FULL * 100.0F));
			horses.add(new StableNet.Row(icon(Equestrian.equine(horse).breed()), Optional.of(Equestrian.equine(horse)), label(horse), feet, List.of(
				new StableNet.Button(Component.translatable("gui.jaymoskingdoms.farrier.shoe", SHOE_PRICE), StableNet.SHOE, horse.getId(), true))));
		}
		MinecraftServer server = player.level().getServer();
		List<StableNet.Row> shop = new ArrayList<>();
		List<Ware> wares = wares();
		for (int i = 0; i < wares.size(); i++) {
			Ware ware = wares.get(i);
			ItemStack stack = new ItemStack(ware.item(), ware.count());
			boolean stocked = Wealth.atLeast(server, f, ware.tier());
			Component detail = stocked ? Component.translatable("gui.jaymoskingdoms.farrier.stocked")
				: Component.translatable("gui.jaymoskingdoms.farrier.locked." + ware.tier().name().toLowerCase(java.util.Locale.ROOT));
			shop.add(new StableNet.Row(stack, Optional.empty(), stack.getHoverName(), detail, List.of(
				new StableNet.Button(Component.translatable("gui.jaymoskingdoms.farrier.buy", ware.price()), StableNet.BUY_TACK, i, stocked))));
		}
		return List.of(
			new StableNet.Section(Component.translatable("gui.jaymoskingdoms.farrier.horses"), Component.translatable("gui.jaymoskingdoms.stable.yours_none"), horses),
			new StableNet.Section(Component.translatable("gui.jaymoskingdoms.farrier.shop"), Component.empty(), shop));
	}

	/** {@code player}'s horses about (within {@link #HORSE_RANGE}), boarded or not, nearest first. */
	private static List<AbstractHorse> yours(ServerPlayer player, boolean boarded) {
		List<AbstractHorse> horses = new ArrayList<>(player.level().getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(HORSE_RANGE),
			h -> h.isAlive() && mine(h, player) && boarded(h) == boarded));
		horses.sort(Comparator.comparingDouble(h -> h.distanceToSqr(player)));
		return horses;
	}

	private static ItemStack icon(Breed breed) {
		return new ItemStack(breed.elephant() ? Equestrian.WAR_ELEPHANT_SPAWN_EGG : Items.HORSE_SPAWN_EGG);
	}

	private static Component stats(Equine e) {
		return Component.translatable("gui.jaymoskingdoms.stable.stats", pct(e.speed()), pct(e.stamina()), pct(e.jump()), pct(e.courage()));
	}

	/** A horse's name, and its breed if it has a name of its own. */
	private static Component label(AbstractHorse horse) {
		Equine e = Equestrian.equine(horse);
		return horse.hasCustomName() ? Component.translatable("gui.jaymoskingdoms.stable.named", Bonding.name(horse), Component.translatable(e.breed().key()))
			: Bonding.name(horse);
	}

	/** {@code player} pressed a button on a stable worker's screen: it's done as the command would, and the screen comes again with what was said. */
	private static void act(ServerPlayer player, StableNet.Act act) {
		if (!(player.level().getEntity(act.workerId()) instanceof Worker w) || !isStableWorker(w.role()) || !w.isAlive()
			|| w.distanceToSqr(player.position()) > (MENU_RANGE + 4.0) * (MENU_RANGE + 4.0)) {
			return;
		}
		if (act.action() == StableNet.QUESTS) {
			Quests.open(player, w.mob());
			return;
		}
		Pressing pressing = new Pressing(player, w);
		screen = pressing;
		try {
			switch (act.action()) {
				case StableNet.BOARD -> board(player, act.arg());
				case StableNet.COLLECT -> collect(player, act.arg());
				case StableNet.BUY -> buy(player, act.arg());
				case StableNet.SELL -> sell(player, act.arg());
				case StableNet.SHOE -> shoe(player, act.arg());
				case StableNet.BUY_TACK -> buyTack(player, act.arg());
				default -> {
				}
			}
		} finally {
			screen = null;
		}
		Faction f = Factions.get(player.level().getServer(), w.factionId());
		if (f == null || f.fallen() || Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			pressing.said.forEach(player::sendSystemMessage);
			return;
		}
		show(player, w, f, pressing.said);
	}

	// ---- riders -----------------------------------------------------------------------------------------------------

	/**
	 * (From {@link Cavalry#mount}) A realm with stables mounts its guards and archers better: a war saddle and a blanket
	 * in the realm's colour; for each stable (up to {@link #MAX_LEVEL}) a better horse, a little faster, more war
	 * training; and with two or more, chain barding (plate in a wealthy realm).
	 */
	static void equip(ServerLevel level, AbstractHorse horse, Faction f, Folk rider) {
		MinecraftServer server = level.getServer();
		int lvl = f.type().isRealm() ? level(server, f) : 0;
		if (lvl == 0 || horse instanceof WarElephant || rider.role() != Folk.GUARD && rider.role() != Folk.ARCHER) {
			return;
		}
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Tackroom.WAR_SADDLE));
		ItemStack blanket = new ItemStack(Tackroom.HORSE_BLANKET);
		blanket.set(DataComponents.DYED_COLOR, new DyedItemColor(f.styleColor(server).getTextureDiffuseColor() & 0xFFFFFF));
		horse.setAttached(Bonding.TACK, new Tack(lvl >= 2 ? new ItemStack(Tackroom.BRIDLE) : ItemStack.EMPTY, blanket));
		if (lvl >= 2) {
			horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Wealth.of(server, f) == Wealth.Tier.WEALTHY ? Tackroom.PLATE_BARDING : Tackroom.CHAIN_BARDING));
		}
		Equine e = Equestrian.equine(horse);
		RandomSource random = level.getRandom();
		for (int i = 0; i < lvl; i++) {
			e = new Equine(e.breed(), e.coat(), better(e.speed(), random), better(e.stamina(), random), better(e.jump(), random), better(e.courage(), random), e.temper());
		}
		Equestrian.apply(horse, e, true);
		horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Cavalry.HORSE_SPEED + STABLE_SPEED * lvl);
		Bond b = Bonding.bond(horse);
		horse.setAttached(Bonding.BOND, b.withXp(Discipline.WAR, Math.max(b.war(), Discipline.LEVELS[Math.min(lvl, Discipline.LEVELS.length - 1)])));
	}

	private static float better(float stat, RandomSource random) {
		return Math.max(stat, (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3.0F);
	}

	/** (From {@link Raids#start}) A barbarian raider on a steppe pony (saddled, no barding), gone with him. */
	static void pony(ServerLevel level, Folk raider, Faction camp, int i) {
		if (i % RAIDER_RIDES_ONE_IN != 0) {
			return;
		}
		AbstractHorse horse = Cavalry.mount(level, raider, camp, true, Breed.STEPPE_PONY);
		if (horse != null) {
			horse.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
		}
	}

	/**
	 * (From {@link Folk#die}) A rider cut down by a player: a knight's (a realm's guard's or archer's, a ruler's) or a
	 * raider's horse goes wild where it stands, keeping its tack and its war training, for whoever earns its trust.
	 * A caravan's horses go home with the caravan.
	 */
	static void riderFell(ServerLevel level, Folk rider, DamageSource source) {
		AbstractHorse horse = Cavalry.horse(rider);
		if (horse == null || !(source.getEntity() instanceof Player killer) || horse instanceof WarElephant || !horse.entityTags().contains(Cavalry.TAG)
			|| horse.entityTags().contains(Caravans.TAG)) {
			return;
		}
		int role = rider.role();
		boolean knight = role == Folk.GUARD || role == Folk.ARCHER || role == Folk.KING || role == Folk.LORD;
		if (!knight && role != Folk.BARBARIAN) {
			return;
		}
		horse.removeTag(Cavalry.TAG);
		horse.removeTag(Cavalry.OF + rider.factionId());
		horse.removeTag(Cavalry.LENT);
		horse.setTamed(false);
		horse.setOwner(null);
		horse.setPersistenceRequired();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			horse.setDropChance(slot, 2.0F);
		}
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (killer instanceof ServerPlayer p) {
			p.sendSystemMessage(Component.translatable(knight ? "message.jaymoskingdoms.stable.destrier_loose" : "message.jaymoskingdoms.stable.pony_loose",
				Component.translatable(Equestrian.equine(horse).breed().key())).withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- saddlebags -------------------------------------------------------------------------------------------------

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (entity instanceof Worker w && isStableWorker(w.role()) && w.isAlive() && opens(sp, w)) {
			// (an MCA villager's own screen on a plain click; theirs on a sneaking one)
			Faction f = served(sp, w);
			if (f != null) {
				show(sp, w, f, List.of());
			}
			return InteractionResult.SUCCESS;
		}
		if (!(entity instanceof AbstractHorse horse)) {
			return InteractionResult.PASS;
		}
		if (boarded(horse) && player.getMainHandItem().isEmpty() && !player.isSecondaryUseActive()) {
			// (no riding off without paying)
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stable.pay_first", Bonding.name(horse), fee(server, horse))
				.withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.FAIL;
		}
		if (player.isSecondaryUseActive() && player.getMainHandItem().isEmpty() && horse.entityTags().contains(Caravans.TAG)) {
			rifle(server, sp, horse);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** {@code player} rifles a caravan horse's saddlebags: a little coin and some goods, once a horse, and theft where anyone sees. */
	static void rifle(ServerLevel level, ServerPlayer player, AbstractHorse horse) {
		if (horse.entityTags().contains(RIFLED)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.caravan.bags_empty"), true);
			return;
		}
		horse.addTag(RIFLED);
		RandomSource random = level.getRandom();
		int emeralds = random.nextInt(BAG_EMERALDS + 1);
		if (emeralds > 0) {
			give(player, new ItemStack(Items.EMERALD, emeralds));
		}
		for (int i = 0, n = 1 + random.nextInt(2); i < n; i++) {
			String spec = BAG_GOODS.get(random.nextInt(BAG_GOODS.size()));
			int star = spec.indexOf('*');
			String[] range = spec.substring(star + 1).split("-");
			int min = Integer.parseInt(range[0]), max = Integer.parseInt(range[1]);
			net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.tryParse(spec.substring(0, star)))
				.ifPresent(item -> give(player, new ItemStack(item, min + random.nextInt(max - min + 1))));
		}
		level.playSound(null, horse.blockPosition(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 1.0F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.caravan.bags_rifled"), true);
		Faction f = null;
		for (String tag : horse.entityTags()) {
			if (tag.startsWith(Cavalry.OF)) {
				try {
					f = Factions.get(level.getServer(), Integer.parseInt(tag.substring(Cavalry.OF.length())));
				} catch (NumberFormatException ignored) {
					// (not one of ours)
				}
			}
		}
		if (f != null && !player.isCreative()) {
			Theft.commit(player, level, f, Theft.Crime.SADDLEBAG, horse.blockPosition(), null, true);
		}
	}

	// ---- quests -----------------------------------------------------------------------------------------------------

	/** A stablehand's horse quest: break in a wild horse, or bring a fine horse of a breed (a stud). */
	static Quest stablehandQuest(ServerLevel level, Faction f, Quest.Giver who, RandomSource random) {
		Quest.Reward reward = new Quest.Reward(0, List.of("minecraft:golden_carrot*" + (3 + random.nextInt(4))), Optional.empty(), Optional.empty(),
			QuestBoard.HEARTS + 6, QuestBoard.REPUTATION + random.nextInt(11), QuestBoard.STANDING);
		if (random.nextBoolean()) {
			Quest.Objective o = new Quest.Objective("", 1, 0, Optional.empty(), Optional.empty(), "", f.name(), false);
			return new Quest(UUID.randomUUID().toString(), Quest.BREAK_IN, "stable.break_in", who, o,
				withEmeralds(reward, 10 + random.nextInt(7)), 0L);
		}
		Breed breed = Breed.HORSES[random.nextInt(Breed.HORSES.length)];
		int quality = STUD_MIN + random.nextInt(STUD_SPREAD + 1);
		Quest.Objective o = new Quest.Objective(breed.id(), quality, 0, Optional.empty(), Optional.empty(), "", f.name(), false);
		return new Quest(UUID.randomUUID().toString(), Quest.STUD, "stable.stud", who, o,
			withEmeralds(reward, BREED_PRICE[breed.ordinal()] * (50 + quality) / 100 + 6 + random.nextInt(6)), 0L);
	}

	private static Quest.Reward withEmeralds(Quest.Reward r, int emeralds) {
		return new Quest.Reward(emeralds, r.items(), r.book(), r.relic(), r.hearts(), r.reputation(), r.standing());
	}

	/** A king's horse quest: a race against the clock to another town, else a wild horse broken in for him. Null if there's no stable. */
	static @Nullable Quest kingQuest(ServerLevel level, Faction f, Quest.Giver who, Quest.Reward royal, RandomSource random) {
		if (!has(level.getServer(), f)) {
			return null;
		}
		List<Faction> towns = new ArrayList<>();
		for (Faction other : Factions.all(level.getServer())) {
			double d = Math.sqrt(other.seat().distSqr(f.seat()));
			if (other.id() != f.id() && !other.fallen() && other.type() != FactionType.BARBARIAN && d >= RACE_MIN && d <= RACE_MAX) {
				towns.add(other);
			}
		}
		if (!towns.isEmpty() && random.nextFloat() < 0.65F) {
			Faction to = towns.get(random.nextInt(towns.size()));
			double d = Math.sqrt(to.seat().distSqr(f.seat()));
			int seconds = (int)Math.round(d / RACE_PACE) + RACE_SLACK;
			Quest.Objective o = new Quest.Objective("faction:" + to.id(), 1, 0, Optional.of(to.seat()), Optional.empty(), Integer.toString(seconds), to.name(), false);
			return new Quest(UUID.randomUUID().toString(), Quest.RACE, "king.race", who, o, royal, 0L);
		}
		Quest.Objective o = new Quest.Objective("", 1, 0, Optional.empty(), Optional.empty(), "", f.name(), false);
		return new Quest(UUID.randomUUID().toString(), Quest.BREAK_IN, "king.break_in", who, o, royal, 0L);
	}

	/** A race's time allowed (seconds). */
	static int raceSeconds(Quest q) {
		try {
			return Integer.parseInt(q.objective().otherName());
		} catch (NumberFormatException e) {
			return 300;
		}
	}

	/** Seconds left in a race (0 when it's up). */
	static int raceLeft(ServerLevel level, Quest q) {
		return (int)Math.max(0L, raceSeconds(q) - (level.getGameTime() - q.givenAt()) / 20L);
	}

	/** The horse {@code player} brings for {@code q} (break-in: one of their own they broke in; stud: one of the breed, good enough), or null. */
	static @Nullable AbstractHorse questHorse(ServerPlayer player, Quest q) {
		List<AbstractHorse> horses = player.level().getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(HORSE_RANGE),
			h -> h.isAlive() && mine(h, player) && !h.isBaby() && !boarded(h) && !(h instanceof WarElephant) && !Equestrian.undead(h) && fits(h, q));
		return horses.stream().min(Comparator.comparingDouble(h -> h.distanceToSqr(player))).orElse(null);
	}

	private static boolean fits(AbstractHorse horse, Quest q) {
		if (q.kind() == Quest.BREAK_IN) {
			return !horse.entityTags().contains(BOUGHT);
		}
		Equine e = Equestrian.equine(horse);
		return e.breed().id().equals(q.objective().target()) && quality(e) * 100.0 >= q.objective().count();
	}

	/** {@code q} turned in: the horse it asked for handed over to the giver's stable. */
	static void questTurnIn(ServerLevel level, ServerPlayer player, LivingEntity giver, Faction f, Quest q) {
		AbstractHorse horse = questHorse(player, q);
		if (horse != null) {
			handOver(level, player, horse, f, giver.blockPosition());
		}
	}

	/** {@code player}'s races: one ridden into its town in time is won (back to the king for the reward); one out of time is lost. */
	private static void races(ServerLevel level, ServerPlayer player) {
		for (Quest q : Quests.log(player)) {
			if (q.kind() != Quest.RACE || q.counted()) {
				continue;
			}
			if (raceLeft(level, q) <= 0) {
				Quests.abandon(player, q.id());
				player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.race.lost", q.objective().place()).withStyle(ChatFormatting.RED));
				continue;
			}
			BlockPos to = q.objective().pos().orElse(null);
			if (to != null && player.getVehicle() instanceof AbstractHorse && player.level() == level
				&& Vec3.atCenterOf(to).distanceToSqr(player.getX(), to.getY() + 0.5, player.getZ()) < RACE_FINISH * RACE_FINISH) {
				Quests.put(player, q.withProgress(1));
				Quests.sync(player, true);
				player.sendSystemMessage(Component.translatable(Rulers.her("quest.jaymoskingdoms.race.won", Factions.get(level.getServer(), q.giver().faction())), q.objective().place(), raceLeft(level, q))
					.withStyle(ChatFormatting.GOLD));
				level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
		}
	}
}
