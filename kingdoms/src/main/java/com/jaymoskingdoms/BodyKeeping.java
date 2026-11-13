package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Keeping a body fresh (Andy's pick E4). Two ways, both leaving a mark on the Kitchen's body that says how many days it is good for:
 * <ul>
 * <li><b>The rite of keeping</b> (a ritual spell, ten seconds with a book): every body within reach of the one who says it is
 * kept ten days. The cleric of a settlement says it for the dead near them, unasked, and every few days again.</li>
 * <li><b>The salt-and-herb wrap</b>: use it on a body, kept six days; or use it with food in the other hand, and the food keeps
 * (the Kitchen's preserving).</li>
 * </ul>
 * A kept body does not rot, and does not get up (a player's, a zombie's); the Kitchen's meat clock stands still. A necromancer
 * cannot raise it ({@code KitchenCompat#isRaisableCorpse}). It is kept for burial ({@link Graves}) and for the rite of a new
 * body ({@link Rebirths}); {@link #isPreserved} is the question anything that speaks with the dead asks.
 */
public final class BodyKeeping {
	// ---- tuning ----
	/** The wrap keeps a body this many days; the rite, this many. */
	static final int WRAP_DAYS = 6, RITE_DAYS = 10;
	/** The rite reaches this far (blocks) from the one who says it. */
	static final double RITE_REACH = 6.0;
	/** A cleric looks for bodies to tend this often (ticks) and this far (blocks); a body they kept is tended again with this much left (days). */
	static final int TEND_EVERY = 200;
	static final double TEND_REACH = 14.0;
	static final int TEND_DAYS = 4, TEND_LEFT = 2;
	static final int SIGIL = 0xB9D6A0;

	/** The wrap. */
	public static Item WRAP;

	private BodyKeeping() {
	}

	/** Whether {@code body} is kept fresh by a rite or a wrap (a body of the Kitchen's; anything else is not). */
	public static boolean isPreserved(Entity body) {
		return DeadBodies.kept(body);
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("salt_herb_wrap"));
		WRAP = net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(WRAP));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(level instanceof ServerLevel server) || !player.getItemInHand(hand).is(WRAP) || !DeadBodies.isBody(entity)) {
				return InteractionResult.PASS;
			}
			if (DeadBodies.kept(entity)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.keeping.already"));
				return InteractionResult.SUCCESS;
			}
			keep(server, entity, WRAP_DAYS);
			if (!player.getAbilities().instabuild) {
				player.getItemInHand(hand).shrink(1);
			}
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.keeping.wrapped"));
			return InteractionResult.SUCCESS;
		});
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if (!(level instanceof ServerLevel server) || !stack.is(WRAP) || other.isEmpty() || !other.has(net.minecraft.core.component.DataComponents.FOOD)) {
				return InteractionResult.PASS;
			}
			DeadBodies.preserve(other);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			server.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 0.9F);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(BodyKeeping::tend);
	}

	/** Marks {@code body} kept for {@code days} from today, with the rite's look. */
	static void keep(ServerLevel level, Entity body, int days) {
		DeadBodies.keep(body, DeadBodies.today(level) + days);
		Fx.ring(level, body.position().add(0.0, 0.1, 0.0), 1.4F, SIGIL);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 0.4, body.getZ(), 8, 0.4, 0.2, 0.4, 0.0);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 0.8F);
	}

	/** The rite of keeping: the bodies near {@code caster} are kept; false if there are none to keep. */
	static boolean cast(ServerLevel level, Player caster) {
		List<Entity> bodies = level.getEntities(caster, caster.getBoundingBox().inflate(RITE_REACH), DeadBodies::isBody);
		if (bodies.isEmpty()) {
			return false;
		}
		for (Entity body : bodies) {
			keep(level, body, RITE_DAYS);
		}
		return true;
	}

	/** The cleric of a settlement keeps the dead near them (a player's, a villager's), a few days at a time. */
	private static void tend(MinecraftServer server) {
		if (server.getTickCount() % TEND_EVERY != 0 || !DeadBodies.present()) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (Entity body : level.getEntities((Entity) null, player.getBoundingBox().inflate(TEND_REACH * 2.0), DeadBodies::isBody)) {
					Villager cleric = clericNear(level, body.position());
					if (cleric == null || DeadBodies.kept(body) && DeadBodies.today(level) + TEND_LEFT < lastDayOf(level, body)) {
						continue;
					}
					if (!DeadBodies.isPlayerBody(body) && !(level.getRandom().nextInt(4) == 0)) {
						continue;
					}
					keep(level, body, TEND_DAYS);
					Speech.say(level, cleric, Component.translatable("speech.jaymoskingdoms.cleric.tends"), SIGIL, 12.0);
				}
			}
		}
	}

	/** The last day {@code body} is kept to (from its mark), or -1. */
	private static long lastDayOf(Level level, Entity body) {
		long last = -1L;
		for (String tag : body.entityTags()) {
			if (tag.startsWith("jaymoskitchen_kept_")) {
				try {
					last = Math.max(last, Long.parseLong(tag.substring("jaymoskitchen_kept_".length())));
				} catch (NumberFormatException e) {
					// (an unreadable mark)
				}
			}
		}
		return last;
	}

	/** A cleric of the settlements within reach of {@code at}, or null. */
	static @Nullable Villager clericNear(ServerLevel level, Vec3 at) {
		return clericNear(level, at, TEND_REACH);
	}

	static @Nullable Villager clericNear(ServerLevel level, Vec3 at, double reach) {
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(at, at).inflate(reach), v -> v.isAlive() && !v.isBaby()
			&& v.getVillagerData().profession().is(VillagerProfession.CLERIC))) {
			double d = v.position().distanceToSqr(at);
			if (d < bestD) {
				best = v;
				bestD = d;
			}
		}
		return best;
	}
}
