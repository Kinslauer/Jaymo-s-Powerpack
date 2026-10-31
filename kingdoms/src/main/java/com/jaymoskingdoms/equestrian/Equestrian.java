package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.34) Jaymo's Equestrian, stage 1: riding. Every horse, skeleton horse and zombie horse is upgraded in place the
 * first time it's loaded (so the stables', cavalry's, caravans' and carriages' horses get it too): it gets a
 * {@link Breed} (by where it stands), five hidden stats ({@link Equine}) that foals inherit, and a new coat. Its rider
 * shifts through five {@link Gait}s on the scroll wheel (sprint for a burst at the gallop) and watches its stamina
 * ({@link Stride}) in the HUD: galloping drains it, walking refills it, and a blown horse drops to a trot until it gets
 * its wind back. A timid horse shies from explosions, fire and monsters at night, and may throw its rider. A tamed
 * horse that dies leaves its owner a lock of its mane (kept for a necromancer to come). Desert kingdoms keep
 * {@link WarElephant}s.
 */
public final class Equestrian {
	/** Stamina an average horse of capacity 1.0 holds (a stamina stat of 0.5). */
	public static final double STAMINA = 100.0;
	/** A blown horse gets its wind back at this share of its stamina. */
	public static final double WIND_BACK = 0.35;
	/** Sprinting at the gallop: this much faster, spending this much stamina a second. */
	public static final double SPRINT_SPEED = 1.25, SPRINT_DRAIN = -12.0;
	/** An ambling palfrey's trot and canter refill this much a second instead of costing anything. */
	public static final double AMBLE_REFILL = 0.5;
	/** Breed traits: a desert horse on sand, in the cold; a courser in anything heavier than leather barding. */
	public static final double SAND_SPEED = 1.15, COLD_SPEED = 0.9, HEAVY_ARMOUR_SPEED = 0.85;
	/** A desert horse's drain in the heat and in the cold; an undead horse's always. */
	public static final double HEAT_DRAIN = 0.35, COLD_DRAIN = 1.7, UNDEAD_DRAIN = 0.5;
	/** A mammoth's drain in the cold and in the heat. */
	public static final double MAMMOTH_COLD_DRAIN = 0.5, MAMMOTH_HEAT_DRAIN = 1.7;
	/** A coat's chance of being the breed's rare one. */
	public static final float RARE_COAT_CHANCE = 0.04F;
	/** How far a foal's stats may stray past its parents' (each side, on 0-1). */
	public static final double FOAL_LUCK = 0.15;
	/** Ridden horses are seen to every this many ticks; their nerve tested every {@link #FEAR_TICKS}. */
	static final int STEP = 5, FEAR_TICKS = 10;
	/** Fears (how hard each one tests a horse's nerve): an explosion, fire, a monster at night, a blow in a fight. */
	public static final double FEAR_BLAST = 1.0, FEAR_FIRE = 0.7, FEAR_MONSTER = 0.45, FEAR_BLOW = 0.35;
	/** How far a monster at night, fire and a blast are noticed. */
	static final double MONSTER_RANGE = 7.0, FIRE_RANGE = 2.0, BLAST_RANGE = 24.0;
	/** The odds of shying ({@code fear x (1 - bravery)² x this}) and then of throwing the rider ({@code fear x (1 - bravery) x this}). */
	public static final double SHY_ODDS = 1.6, THROW_ODDS = 0.6;
	/** After shying a horse keeps its head this long (ticks). */
	static final int SHY_COOLDOWN = 100;
	/** A charging war elephant: damage to what it tramples (more at a sprint), how hard it throws them, how often the same one is hit. */
	public static final float TRAMPLE_DAMAGE = 8.0F, CHARGE_DAMAGE = 12.0F;
	static final double TRAMPLE_KNOCKBACK = 1.4;
	static final int TRAMPLE_COOLDOWN = 20;

	/** What it is (breed, coat, stats). Saved; every client sees it. */
	public static final AttachmentType<Equine> EQUINE = AttachmentRegistry.create(JaymosKingdoms.id("equine"),
		builder -> builder.persistent(Equine.CODEC).syncWith(Equine.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Its gait and stamina. Saved; every client sees it (its rider's HUD). */
	public static final AttachmentType<Stride> STRIDE = AttachmentRegistry.create(JaymosKingdoms.id("stride"),
		builder -> builder.persistent(Stride.CODEC).syncWith(Stride.STREAM_CODEC, AttachmentSyncPredicate.all()));

	public static EntityType<WarElephant> WAR_ELEPHANT;
	public static Item MANE_LOCK, WAR_ELEPHANT_SPAWN_EGG;

	/** The client's say in its own horse's gait (its rider's choice shows there before the server hears of it). */
	public interface LocalRider {
		boolean controls(Player controller);

		Gait gait(AbstractHorse horse);

		boolean sprint(AbstractHorse horse);
	}

	public static @Nullable LocalRider localRider;

	/** Where each ridden horse was when last seen to (entity id), for how fast it's going. */
	private static final Map<Integer, Vec3> LAST = new HashMap<>();
	/** Horse id → game time it may shy again; trampled entity id → game time it may be trampled again. */
	private static final Map<Integer, Long> SHY = new HashMap<>(), TRAMPLED = new HashMap<>();

	private Equestrian() {
	}

	// ---- packets ---------------------------------------------------------------------------------------------------

	/** Client → server: its rider shifted gait or started or stopped sprinting. */
	public record SetGait(int gait, boolean sprint) implements CustomPacketPayload {
		public static final Type<SetGait> TYPE = new Type<>(JaymosKingdoms.id("set_gait"));
		public static final StreamCodec<FriendlyByteBuf, SetGait> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.gait);
				buf.writeBoolean(p.sprint);
			},
			buf -> new SetGait(buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<SetGait> type() {
			return TYPE;
		}
	}

	/** Server → rider: your horse shied (drop to a walk). */
	public record Shied(int horse) implements CustomPacketPayload {
		public static final Type<Shied> TYPE = new Type<>(JaymosKingdoms.id("shied"));
		public static final StreamCodec<FriendlyByteBuf, Shied> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeVarInt(p.horse),
			buf -> new Shied(buf.readVarInt()));

		@Override
		public Type<Shied> type() {
			return TYPE;
		}
	}

	// ---- setup -----------------------------------------------------------------------------------------------------

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("war_elephant"));
		WAR_ELEPHANT = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(WarElephant::new, MobCategory.CREATURE)
			.sized(1.5F, 2.45F)
			.passengerAttachments(2.5F)
			.clientTrackingRange(10)
			.build(key));
		FabricDefaultAttributeRegistry.register(WAR_ELEPHANT, WarElephant.createAttributes());
		ResourceKey<Item> lock = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("mane_lock"));
		MANE_LOCK = Registry.register(BuiltInRegistries.ITEM, lock, new Item(new Item.Properties().setId(lock).stacksTo(1)));
		ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("war_elephant_spawn_egg"));
		WAR_ELEPHANT_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(WAR_ELEPHANT)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.insertAfter(Items.WARDEN_SPAWN_EGG, WAR_ELEPHANT_SPAWN_EGG));
		JaymosKingdoms.LOGGER.debug("equestrian attachments: {} {}", EQUINE, STRIDE);

		PayloadTypeRegistry.serverboundPlay().register(SetGait.TYPE, SetGait.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Shied.TYPE, Shied.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetGait.TYPE, (payload, context) -> setGait(context.player(), payload));

		// a horse first loaded (a new one, or one from before 0.5.34) gets its breed
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractHorse horse && covered(horse) && !horse.hasAttached(EQUINE)) {
				upgrade(level, horse);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(Equestrian::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
			if (entity instanceof AbstractHorse horse && covered(horse) && taken > 0.0F && source.getEntity() instanceof LivingEntity
				&& horse.getControllingPassenger() instanceof ServerPlayer rider && horse.level() instanceof ServerLevel level) {
				shy(level, horse, rider, FEAR_BLOW, source.getEntity().position());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Equestrian::died);
		// testing (op): /kingdom horse <breed>: a tamed, saddled mount of that breed at your side
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("kingdom").then(net.minecraft.commands.Commands.literal("horse")
				.requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
				.then(net.minecraft.commands.Commands.argument("breed", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
						java.util.Arrays.stream(Breed.values()).map(Breed::id), builder))
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						Breed breed = Breed.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "breed"));
						AbstractHorse horse = spawn(player.level(), breed, player.blockPosition());
						if (horse == null) {
							return 0;
						}
						horse.tameWithName(player);
						horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
						c.getSource().sendSuccess(() -> Component.translatable(breed.key()), false);
						return 1;
					})))));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			SHY.clear();
			TRAMPLED.clear();
		});
	}

	/** One of the mounts it upgrades: horses, skeleton and zombie horses, war elephants (not donkeys, mules, llamas or camels). */
	public static boolean covered(Entity e) {
		// (0.7.444, Havens patch 8) donkeys and mules too: bond, roster, gaits and wheel, drawn as vanilla's (Better Horses')
		return e instanceof Horse || e instanceof SkeletonHorse || e instanceof ZombieHorse || e instanceof WarElephant || e instanceof Donkey
			|| e instanceof Mule;
	}

	public static boolean undead(Entity e) {
		return e instanceof SkeletonHorse || e instanceof ZombieHorse;
	}

	/** Its breed and stats (given them now if it hasn't any yet). */
	public static Equine equine(AbstractHorse horse) {
		Equine e = horse.getAttached(EQUINE);
		if (e == null) {
			e = horse.level() instanceof ServerLevel level ? upgrade(level, horse) : Equine.roll(Breed.PALFREY, horse.getRandom());
		}
		return e;
	}

	public static @Nullable Equine peek(Entity e) {
		return e instanceof AbstractHorse horse ? horse.getAttached(EQUINE) : null;
	}

	public static Stride stride(AbstractHorse horse) {
		Stride s = horse.getAttached(STRIDE);
		return s == null ? Stride.FRESH : s;
	}

	/**
	 * A horse without a breed gets one: an elephant is an elephant (a mammoth in the snow); a horse a breed for its land, keeping the speed
	 * and jump it already had (so a prized horse from before 0.5.34 stays prized); an undead one any horse breed.
	 */
	static Equine upgrade(ServerLevel level, AbstractHorse horse) {
		RandomSource random = horse.getRandom();
		Equine e;
		if (horse instanceof WarElephant) {
			e = Equine.roll(cold(horse) ? Breed.WOOLLY_MAMMOTH : Breed.WAR_ELEPHANT, random);
		} else if (undead(horse)) {
			e = Equine.roll(Breed.HORSES[random.nextInt(Breed.HORSES.length)], random);
		} else {
			e = Equine.roll(horse instanceof Donkey ? Breed.DONKEY : horse instanceof Mule ? Breed.MULE : breedAt(level, horse.blockPosition(), random),
				random);
			float speed = (float)clamp01((horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 0.1125) / 0.225);
			float jump = (float)clamp01((horse.getAttributeBaseValue(Attributes.JUMP_STRENGTH) - 0.4) / 0.6);
			e = new Equine(e.breed(), e.coat(), speed, e.stamina(), jump, e.courage(), e.temper());
		}
		apply(horse, e, horse.tickCount == 0);
		return e;
	}

	private static double clamp01(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}

	/** {@code horse} becomes {@code e}: its size, health, jump and wandering pace follow (healed to full if {@code heal}). */
	public static void apply(AbstractHorse horse, Equine e, boolean heal) {
		horse.setAttached(EQUINE, e);
		draftTag(horse, e);
		set(horse, Attributes.SCALE, e.breed().scale);
		set(horse, Attributes.MAX_HEALTH, e.maxHealth());
		set(horse, Attributes.JUMP_STRENGTH, Bonding.jump(horse, e));
		set(horse, Attributes.MOVEMENT_SPEED, e.topSpeed() * 0.75);
		horse.setHealth(heal ? horse.getMaxHealth() : Math.min(horse.getHealth(), horse.getMaxHealth()));
		horse.refreshDimensions();
	}

	/** (0.7.305) The entity tag Jaymo's Kitchen reads (it can't see our breeds): only a draft horse pulls its coach. */
	public static final String DRAFT_TAG = "jaymoskingdoms.draft";

	static void draftTag(AbstractHorse horse, Equine e) {
		if (e.breed() == Breed.DRAFT) {
			horse.addTag(DRAFT_TAG);
		} else {
			horse.removeTag(DRAFT_TAG);
		}
	}

	private static void set(LivingEntity e, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	/** The breed a wild horse born at {@code pos} is likely to be. */
	public static Breed breedAt(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		Holder<Biome> biome = level.getBiome(pos);
		float r = random.nextFloat();
		if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
			return Breed.DESERT_HORSE;
		} else if (biome.is(BiomeTags.IS_SAVANNA)) {
			return r < 0.6F ? Breed.STEPPE_PONY : Breed.DESERT_HORSE;
		} else if (biome.value().getBaseTemperature() < 0.2F) {
			return r < 0.5F ? Breed.DRAFT : Breed.STEPPE_PONY;
		} else if (biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.MEADOW)) {
			return r < 0.4F ? Breed.COURSER : r < 0.75F ? Breed.PALFREY : Breed.DRAFT;
		}
		return r < 0.5F ? Breed.PALFREY : r < 0.8F ? Breed.COURSER : Breed.DRAFT;
	}

	/** A new mount of {@code breed} (a war elephant or a horse), not yet in the world. */
	public static @Nullable AbstractHorse create(ServerLevel level, Breed breed, BlockPos at, EntitySpawnReason reason) {
		AbstractHorse horse = breed.elephant() ? WAR_ELEPHANT.create(level, reason) : breed == Breed.DONKEY ? EntityTypes.DONKEY.create(level, reason)
			: breed == Breed.MULE ? EntityTypes.MULE.create(level, reason) : EntityTypes.HORSE.create(level, reason);
		if (horse == null) {
			return null;
		}
		horse.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		horse.finalizeSpawn(level, level.getCurrentDifficultyAt(at), reason, null);
		apply(horse, Equine.roll(breed, level.getRandom()), true);
		return horse;
	}

	/** A mount of {@code breed} set down at {@code at} (a stable's new horses). */
	public static @Nullable AbstractHorse spawn(ServerLevel level, Breed breed, BlockPos at) {
		AbstractHorse horse = create(level, breed, at, EntitySpawnReason.SPAWN_ITEM_USE);
		if (horse != null) {
			level.addFreshEntity(horse);
		}
		return horse;
	}

	/** (From {@code AbstractHorseMixin}) a foal of two upgraded parents takes after them ({@link Equine#foal}). */
	public static void foal(AbstractHorse parent, Entity partner, AbstractHorse baby) {
		if (!covered(baby) || !covered(partner) || !(partner instanceof AbstractHorse other)) {
			return;
		}
		Equine e = Equine.foal(equine(parent), equine(other), baby.getRandom());
		// (0.7.444) a horse and a donkey make a mule (and two donkeys a donkey, whichever parent's breed it took)
		Breed kind = baby instanceof Mule ? Breed.MULE : baby instanceof Donkey ? Breed.DONKEY : null;
		if (kind != null && e.breed() != kind) {
			e = new Equine(kind, 0, e.speed(), e.stamina(), e.jump(), e.courage(), e.temper());
		}
		apply(baby, e, true);
		// (0.7.305) a filly or a colt
		Herding.born(baby, baby.getRandom());
	}

	// ---- riding ----------------------------------------------------------------------------------------------------

	/** (From {@code AbstractHorseMixin}) how fast its rider makes it go: its breed's top speed at his gait, and the land under it. */
	/** The gait its rider has it at (on the rider's own client, what he's chosen before the server hears of it). */
	public static Gait gaitFor(AbstractHorse horse, LivingEntity controller) {
		if (horse.level().isClientSide() && localRider != null && controller instanceof Player p && localRider.controls(p)) {
			return localRider.gait(horse);
		}
		return stride(horse).gaitOf();
	}

	public static float riddenSpeed(AbstractHorse horse, Player controller, float vanilla) {
		Equine e = horse.getAttached(EQUINE);
		if (e == null) {
			return vanilla;
		}
		Stride s = stride(horse);
		Gait gait;
		boolean sprint;
		if (horse.level().isClientSide() && localRider != null && localRider.controls(controller)) {
			gait = localRider.gait(horse);
			sprint = localRider.sprint(horse);
		} else {
			gait = s.gaitOf();
			sprint = s.sprint();
		}
		gait = allowed(e, s, gait);
		sprint &= gait == Gait.GALLOP && !s.blown();
		double speed = Bonding.topSpeed(horse, e) * gait.speed / e.breed().topGait.speed;
		if (sprint) {
			speed *= SPRINT_SPEED;
		}
		// (0.7.305) a Rider's gift from its breed
		speed *= BreedGifts.speed(horse, e, controller, sprint, speed);
		speed *= terrain(horse, e);
		// a speed effect (or slowness) still tells
		double base = horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		if (base > 0.0) {
			speed *= horse.getAttributeValue(Attributes.MOVEMENT_SPEED) / base;
		}
		return (float)speed;
	}

	/** The fastest {@code wanted} may be: no faster than its breed's top gait, nor a trot while it's blown. */
	public static Gait allowed(Equine e, Stride s, Gait wanted) {
		Gait top = s.blown() ? Gait.TROT : e.breed().topGait;
		return wanted.ordinal() > top.ordinal() ? top : wanted;
	}

	/** Its breed's way with the ground under it. */
	static double terrain(AbstractHorse horse, Equine e) {
		double f = 1.0;
		if (e.breed() == Breed.DESERT_HORSE) {
			BlockState under = horse.level().getBlockState(horse.getBlockPosBelowThatAffectsMyMovement());
			if (under.is(BlockTags.SAND)) {
				f *= SAND_SPEED;
			} else if (cold(horse)) {
				f *= COLD_SPEED;
			}
		} else if (e.breed() == Breed.COURSER) {
			ItemStack armour = horse.getItemBySlot(EquipmentSlot.BODY);
			if (!armour.isEmpty() && !armour.is(Items.LEATHER_HORSE_ARMOR)) {
				f *= HEAVY_ARMOUR_SPEED;
			}
		}
		// (0.5.44) its hooves and belly
		f *= Bonding.feet(horse, horse.level().getBlockState(horse.getBlockPosBelowThatAffectsMyMovement()));
		return f;
	}

	static boolean cold(Entity e) {
		BlockPos pos = e.blockPosition();
		return e.level().getBiome(pos).value().coldEnoughToSnow(pos, e.level().getSeaLevel()) || e.level().getBlockState(pos).is(Blocks.SNOW);
	}

	static boolean hot(Entity e) {
		return e.level().getBiome(e.blockPosition()).value().getBaseTemperature() >= 1.0F && !e.level().isRainingAt(e.blockPosition());
	}

	/** (From {@code AbstractHorseMixin}) how hard it is to break in. */
	public static int maxTemper(AbstractHorse horse, int vanilla) {
		Equine e = horse.getAttached(EQUINE);
		return e == null ? vanilla : e.maxTemper();
	}

	private static void setGait(ServerPlayer player, SetGait payload) {
		if (!(player.getVehicle() instanceof AbstractHorse horse) || !covered(horse) || horse.getControllingPassenger() != player) {
			return;
		}
		Equine e = equine(horse);
		Stride s = stride(horse);
		Gait gait = allowed(e, s, Gait.byId(payload.gait()));
		boolean sprint = payload.sprint() && gait == Gait.GALLOP && !s.blown();
		if (sprint && !s.sprint() && horse instanceof WarElephant elephant && player.level() instanceof ServerLevel level) {
			// the charge: it trumpets
			elephant.trumpet(level);
		}
		horse.setAttached(STRIDE, new Stride(gait.ordinal(), sprint, s.stamina(), s.blown(), s.at()));
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % STEP != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.getVehicle() instanceof AbstractHorse horse && covered(horse) && horse.getControllingPassenger() == player) {
				ride(level, horse, player, now);
			}
		}
		if (now % 1200 == 0) {
			LAST.keySet().removeIf(id -> !(level.getEntity(id) instanceof AbstractHorse h) || !h.isVehicle());
			SHY.values().removeIf(t -> t < now);
			TRAMPLED.values().removeIf(t -> t < now);
		}
	}

	/** A ridden horse seen to: its stamina spent or refilled by how it's going, its nerve tested, an elephant's charge. */
	private static void ride(ServerLevel level, AbstractHorse horse, ServerPlayer rider, long now) {
		Equine e = equine(horse);
		Stride s = stride(horse);
		double capacity = Bonding.capacity(horse, e);
		double stamina = s.stamina() < 0.0F ? capacity : s.stamina();
		long elapsed = now - s.at();
		if (s.at() == 0L || elapsed > STEP * 4L) {
			// it stood unridden since: rested
			stamina += Gait.HALT.perSecond * Math.max(0L, elapsed) / 20.0;
			elapsed = STEP;
		}
		Vec3 here = horse.position();
		Vec3 was = LAST.put(horse.getId(), here);
		double moved = was == null ? 0.0 : here.subtract(was).horizontalDistance() / elapsed;
		Gait gait = allowed(e, s, s.gaitOf());
		boolean sprint = s.sprint() && gait == Gait.GALLOP && !s.blown();
		double expected = Bonding.topSpeed(horse, e) * gait.speed / e.breed().topGait.speed;
		boolean going = moved > 0.03 && moved > expected * 0.3;
		double rate = going ? (sprint ? SPRINT_DRAIN : gait.perSecond) : Gait.HALT.perSecond;
		if (rate < 0.0) {
			if (e.breed() == Breed.PALFREY && !sprint && gait != Gait.GALLOP) {
				rate = AMBLE_REFILL;
			} else {
				rate *= drain(horse, e);
			}
		}
		stamina = Math.max(0.0, Math.min(capacity, stamina + rate * elapsed / 20.0));
		boolean blown = s.blown();
		if (!blown && stamina <= 0.0) {
			blown = true;
			sprint = false;
			gait = Gait.TROT;
			level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1.5F, 0.7F);
			level.sendParticles(ParticleTypes.CLOUD, horse.getX(), horse.getEyeY(), horse.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
		} else if (blown && stamina >= capacity * WIND_BACK) {
			blown = false;
		}
		horse.setAttached(STRIDE, new Stride(gait.ordinal(), sprint, (float)stamina, blown, now));
		if (now % FEAR_TICKS == 0) {
			fears(level, horse, rider);
		}
		if (horse instanceof WarElephant && going && gait.ordinal() >= Gait.CANTER.ordinal()) {
			trample(level, horse, rider, sprint, now);
		}
		// (0.5.44) what it learns, what it thinks of its rider, its feet
		if (horse.getControllingPassenger() == rider) {
			Bonding.ride(level, horse, rider, gait, sprint, going, moved * elapsed, elapsed);
		}
	}

	/** How fast it spends stamina: its breed's rate, its land, and the dead don't tire much. */
	static double drain(AbstractHorse horse, Equine e) {
		// (0.6.0) a Rider's horse tires slower
		double d = e.breed().drain * com.jaymoskingdoms.ClassPerks.stamina(horse.getControllingPassenger());
		if (undead(horse)) {
			return d * UNDEAD_DRAIN;
		}
		if (e.breed() == Breed.DESERT_HORSE) {
			if (hot(horse)) {
				d *= HEAT_DRAIN;
			} else if (cold(horse)) {
				d *= COLD_DRAIN;
			}
		} else if (e.breed() == Breed.WOOLLY_MAMMOTH) {
			if (cold(horse)) {
				d *= MAMMOTH_COLD_DRAIN;
			} else if (hot(horse)) {
				d *= MAMMOTH_HEAT_DRAIN;
			}
		}
		// (0.5.44) a thirsty horse tires twice as fast
		Bond b = horse.getAttached(Bonding.BOND);
		if (b != null && b.thirst() < Bonding.THIRSTY && Bonding.kept(horse)) {
			d *= Bonding.THIRSTY_DRAIN;
		}
		return d;
	}

	// ---- courage ---------------------------------------------------------------------------------------------------

	/** What frightens it here: fire close by, a monster about at night. */
	private static void fears(ServerLevel level, AbstractHorse horse, ServerPlayer rider) {
		if (horse.isOnFire()) {
			shy(level, horse, rider, FEAR_FIRE, horse.position());
			return;
		}
		AABB near = horse.getBoundingBox().inflate(FIRE_RANGE);
		if (level.getBlockStatesIfLoaded(near).anyMatch(st -> st.is(BlockTags.FIRE) || st.is(Blocks.LAVA))) {
			shy(level, horse, rider, FEAR_FIRE, near.getCenter());
			return;
		}
		if (level.isDarkOutside()) {
			List<Monster> monsters = level.getEntitiesOfClass(Monster.class, horse.getBoundingBox().inflate(MONSTER_RANGE), Monster::isAlive);
			if (!monsters.isEmpty()) {
				shy(level, horse, rider, FEAR_MONSTER, monsters.getFirst().position());
			}
		}
	}

	/** (From {@code ServerExplosionMixin}) horses near a blast: a ridden one may shy and throw its rider, a loose one rears. */
	public static void blast(ServerLevel level, Vec3 at, float radius) {
		double range = Math.min(BLAST_RANGE, 4.0 + radius * 4.0);
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, new AABB(at, at).inflate(range), Equestrian::covered)) {
			double fear = FEAR_BLAST * (1.0 - 0.5 * horse.position().distanceTo(at) / range);
			if (horse.getControllingPassenger() instanceof ServerPlayer rider) {
				shy(level, horse, rider, fear, at);
			} else if (!horse.isVehicle() && !undead(horse) && horse.getRandom().nextDouble() < fear * (1.0 - Bonding.bravery(horse, equine(horse)))) {
				horse.makeMad();
			}
		}
	}

	/** {@code horse} is frightened ({@code fear}, from {@code from}): a timid one shies (rears, drops to a walk), and may throw its rider. */
	static void shy(ServerLevel level, AbstractHorse horse, ServerPlayer rider, double fear, Vec3 from) {
		if (undead(horse)) {
			return;
		}
		Equine e = equine(horse);
		double bravery = e.breed() == Breed.STEPPE_PONY ? 1.0 : Bonding.bravery(horse, e);
		long now = level.getGameTime();
		Long until = SHY.get(horse.getId());
		if (bravery >= 1.0 || until != null && until > now) {
			return;
		}
		RandomSource random = horse.getRandom();
		if (random.nextDouble() >= fear * (1.0 - bravery) * (1.0 - bravery) * SHY_ODDS) {
			return;
		}
		SHY.put(horse.getId(), now + SHY_COOLDOWN);
		horse.makeMad();
		Stride s = stride(horse);
		horse.setAttached(STRIDE, new Stride(Gait.WALK.ordinal(), false, s.stamina(), s.blown(), s.at()));
		ServerPlayNetworking.send(rider, new Shied(horse.getId()));
		Vec3 away = horse.position().subtract(from).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, horse.getYRot()) : away.normalize();
		// (0.5.44) a war saddle's high cantle keeps its rider on through blows
		boolean seated = fear == FEAR_BLOW && horse.getItemBySlot(EquipmentSlot.SADDLE).is(Tackroom.WAR_SADDLE);
		if (!seated && random.nextDouble() < fear * (1.0 - bravery) * THROW_ODDS) {
			rider.stopRiding();
			horse.setDeltaMovement(away.scale(0.6).add(0.0, 0.2, 0.0));
			horse.syncVelocity = true;
			rider.sendSystemMessage(Component.translatable("message.jaymoskingdoms.horse_throws"), true);
		} else {
			rider.sendSystemMessage(Component.translatable("message.jaymoskingdoms.horse_shies"), true);
		}
	}

	// ---- war elephants -----------------------------------------------------------------------------------------------

	/** A charging elephant tramples the monsters in its way, and whatever's set on its rider. */
	private static void trample(ServerLevel level, AbstractHorse elephant, ServerPlayer rider, boolean charge, long now) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, elephant.getYRot());
		AABB box = elephant.getBoundingBox().move(ahead.scale(elephant.getBbWidth() * 0.6)).inflate(0.3, 0.0, 0.3);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && t != elephant && t != rider
			&& !t.isPassengerOfSameVehicle(rider) && (t instanceof Enemy || t instanceof Mob m && m.getTarget() == rider))) {
			Long until = TRAMPLED.get(target.getId());
			if (until != null && until > now) {
				continue;
			}
			TRAMPLED.put(target.getId(), now + TRAMPLE_COOLDOWN);
			DamageSource source = level.damageSources().mobAttack(elephant);
			float damage = charge ? CHARGE_DAMAGE : TRAMPLE_DAMAGE;
			target.hurtServer(level, source, damage);
			target.knockback(TRAMPLE_KNOCKBACK, -ahead.x, -ahead.z, source, damage);
			target.push(0.0, 0.35, 0.0);
			level.playSound(null, target.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.NEUTRAL, 1.2F, 0.7F);
		}
	}

	// ---- death -------------------------------------------------------------------------------------------------------

	/** A tamed horse that dies leaves a lock of its mane: there's no coming back (until a necromancer, one day). */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof AbstractHorse horse) || !covered(horse) || !horse.isTamed() || !(horse.level() instanceof ServerLevel level)) {
			return;
		}
		Bonding.dropTack(level, horse);
		Equine e = equine(horse);
		ItemStack lock = new ItemStack(MANE_LOCK);
		Component name = horse.hasCustomName() ? horse.getCustomName() : Component.translatable(e.breed().key());
		lock.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.mane_lock.of", name));
		lock.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable(e.breed().key()).withStyle(net.minecraft.ChatFormatting.GRAY),
			Component.translatable("coat.jaymoskingdoms." + e.coatName()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY))));
		// everything a necromancer would need to bring it back
		CompoundTag tag = new CompoundTag();
		tag.putString("entity", BuiltInRegistries.ENTITY_TYPE.getKey(horse.getType()).toString());
		Equine.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, e).result().ifPresent(t -> tag.put("equine", t));
		if (horse.hasCustomName()) {
			tag.putString("name", horse.getCustomName().getString());
		}
		horse.getOwner();
		if (horse.getOwnerReference() != null) {
			tag.putString("owner", horse.getOwnerReference().getUUID().toString());
		}
		lock.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		horse.spawnAtLocation(level, lock);
	}
}
