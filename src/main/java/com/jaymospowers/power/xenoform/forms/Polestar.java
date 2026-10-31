package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.96, Xeno-Band patch 4) Polestar, the lodestone alien (scanned from the Magnetic Giant): broad, banded red and
 * grey, horseshoe horns, 36 health, fists of 6. Everything it does is iron: iron armour (and chainmail), iron blocks,
 * iron golems, and arrows' tips.
 * <ul>
 * <li>R, Pull: what wears iron in front of you ({@link #PULL_REACH} blocks) is dragged to you, harder the more it wears;
 * loose iron on the ground flies to you.</li>
 * <li>G, Repel: a burst that throws back everything iron round you and hurts it by its iron; arrows in the air turn back.</li>
 * <li>V, Lift: tear an iron block out of the world (iron, raw iron, bars, trapdoors, anvils, cauldrons, chains, heavy pressure plates)
 * and hold it overhead; V again throws it, hurting what it lands on.</li>
 * <li>B (hold), Field: arrows that come near turn round and fly back at whoever shot them.</li>
 * </ul>
 */
public final class Polestar extends XenoForm {
	// ---- tuning ----
	public static final double PULL_REACH = 16.0, PULL_CONE = 0.6, PULL_BASE = 0.4, PULL_PER_PIECE = 0.25, PULL_GOLEM = 1.2, PULL_ITEM = 0.6;
	public static final int PULL_COOLDOWN = 40;
	public static final double REPEL_RADIUS = 8.0, REPEL_BASE = 0.6, REPEL_PER_PIECE = 0.35;
	public static final float REPEL_PER_PIECE_DAMAGE = 1.5F;
	public static final int REPEL_COOLDOWN = 80;
	public static final double LIFT_REACH = 6.0, THROW_SPEED = 1.6;
	/** What a thrown block does to what it lands on: damage a block fallen, and at most. */
	public static final float THROW_DAMAGE = 2.0F;
	public static final int THROW_MAX = 30, LIFT_COOLDOWN = 10, THROW_COOLDOWN = 60;
	public static final double FIELD_RADIUS = 6.0, FIELD_RETURN = 1.1;
	private static final Identifier MAGNETIC_GIANT = Identifier.fromNamespaceAndPath("jaymoskingdoms", "magnetic_giant");
	private static final Set<Block> IRON_BLOCKS = Set.of(Blocks.IRON_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.IRON_BARS,
		Blocks.IRON_TRAPDOOR, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL, Blocks.CAULDRON, Blocks.IRON_CHAIN,
		Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);

	public Polestar() {
		super("polestar", 1.2F, 36.0F, 6.0, -0.05, 0.7, 0.5, Items.LODESTONE, 0xD84A4A);
	}

	@Override
	public boolean scans(Entity creature) {
		return MAGNETIC_GIANT.equals(BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType()));
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		if (live.lifted != null) {
			// what it held drops where it stands
			drop(player, live, Vec3.ZERO);
		}
		live.field = false;
	}

	/** How many pieces of iron (or chainmail) {@code e} wears or holds; an iron golem counts as four. */
	public static int iron(LivingEntity e) {
		if (e instanceof IronGolem) {
			return 4;
		}
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (irony(e.getItemBySlot(slot))) {
				n++;
			}
		}
		return n;
	}

	static boolean irony(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("iron") || path.contains("chainmail");
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			if (live.field != down) {
				live.field = down;
				if (down) {
					sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.0F, 0.8F);
					cooldown(player, live, B, 1);
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					pull(player);
					cooldown(player, live, R, PULL_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					repel(player);
					cooldown(player, live, G, REPEL_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				if (live.lifted != null) {
					drop(player, live, player.getLookAngle().scale(THROW_SPEED));
					sound(player, SoundEvents.ANVIL_LAND, 0.6F, 1.6F);
					cooldown(player, live, V, THROW_COOLDOWN);
				} else if (lift(player, live)) {
					cooldown(player, live, V, LIFT_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	private static void pull(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		int pulled = 0;
		for (LivingEntity e : around(player, PULL_REACH)) {
			int n = iron(e);
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (n == 0 || to.normalize().dot(look) < PULL_CONE || !player.hasLineOfSight(e)) {
				continue;
			}
			double strength = e instanceof IronGolem ? PULL_GOLEM : PULL_BASE + PULL_PER_PIECE * n;
			Vec3 pull = to.normalize().scale(-strength);
			e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.25, pull.z));
			e.syncVelocity = true;
			beam(level, eye.subtract(0.0, 0.3, 0.0), e.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 10);
			pulled++;
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(PULL_REACH), i -> irony(i.getItem()))) {
			Vec3 to = player.position().add(0.0, 0.5, 0.0).subtract(item.position());
			item.setDeltaMovement(to.normalize().scale(PULL_ITEM).add(0.0, 0.2, 0.0));
			pulled++;
		}
		burst(level, ParticleTypes.ELECTRIC_SPARK, eye, 12, 0.3, 0.1);
		sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.0F, pulled > 0 ? 1.2F : 0.7F);
	}

	private static void repel(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity e : around(player, REPEL_RADIUS)) {
			int n = iron(e);
			if (n == 0) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), n * REPEL_PER_PIECE_DAMAGE);
			shove(e, player.position(), REPEL_BASE + REPEL_PER_PIECE * n, 0.4);
		}
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(REPEL_RADIUS), a -> a.getOwner() != player)) {
			turn(arrow, player);
		}
		ring(level, ParticleTypes.ELECTRIC_SPARK, player.position(), REPEL_RADIUS * 0.5, 24);
		ring(level, ParticleTypes.ELECTRIC_SPARK, player.position(), REPEL_RADIUS * 0.9, 32);
		sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), 8.0, 0.3F);
	}

	/** An arrow turns round and flies back the way it came, now the player's. */
	private static void turn(AbstractArrow arrow, ServerPlayer player) {
		Vec3 v = arrow.getDeltaMovement();
		Entity shooter = arrow.getOwner();
		Vec3 back = shooter != null && shooter != player ? shooter.getBoundingBox().getCenter().subtract(arrow.position()).normalize().scale(Math.max(0.8, v.length()) * FIELD_RETURN)
			: v.scale(-FIELD_RETURN);
		arrow.setOwner(player);
		arrow.setDeltaMovement(back);
		arrow.syncVelocity = true;
		player.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, arrow.getX(), arrow.getY(), arrow.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
	}

	private static boolean lift(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		BlockPos at = aimBlock(player, LIFT_REACH);
		BlockState state = at == null ? null : level.getBlockState(at);
		if (at == null || state == null || !IRON_BLOCKS.contains(state.getBlock()) || !player.mayBuild() || !level.mayInteract(player, at)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.polestar.no_iron").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		level.removeBlock(at, false);
		live.lifted = state;
		beam(level, Vec3.atCenterOf(at), player.getEyePosition().add(0.0, 0.8, 0.0), ParticleTypes.ELECTRIC_SPARK, 8);
		sound(player, SoundEvents.CHAIN_BREAK, 1.0F, 0.6F);
		return true;
	}

	/** Lets go of the held block: thrown with {@code velocity} (zero: it just drops). */
	private static void drop(ServerPlayer player, XenoformPower.Live live, Vec3 velocity) {
		BlockState state = live.lifted;
		live.lifted = null;
		if (state == null) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos over = BlockPos.containing(player.getX(), player.getEyeY() + 1.0, player.getZ());
		if (!level.getBlockState(over).isAir()) {
			over = player.blockPosition();
			if (!level.getBlockState(over).canBeReplaced()) {
				// nowhere for it: it comes back as an item
				Block.popResource(level, player.blockPosition(), new ItemStack(state.getBlock()));
				return;
			}
		}
		FallingBlockEntity block = FallingBlockEntity.fall(level, over, state);
		block.setHurtsEntities(THROW_DAMAGE, THROW_MAX);
		block.setDeltaMovement(velocity.add(0.0, velocity.lengthSqr() > 0.0 ? 0.2 : 0.0, 0.0));
		block.syncVelocity = true;
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.lifted != null && player.tickCount % 2 == 0) {
			Vec3 over = player.getEyePosition().add(0.0, 1.0, 0.0);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, live.lifted), over.x, over.y, over.z, 2, 0.3, 0.3, 0.3, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, over.x, over.y, over.z, 1, 0.4, 0.4, 0.4, 0.0);
		}
		if (live.field) {
			for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(FIELD_RADIUS),
				a -> a.getOwner() != player && a.getDeltaMovement().lengthSqr() > 0.04
					&& player.position().subtract(a.position()).dot(a.getDeltaMovement()) > 0.0)) {
				turn(arrow, player);
				sound(player, SoundEvents.CHAIN_BREAK, 0.6F, 1.6F);
			}
			if (player.tickCount % 6 == 0) {
				ring(level, ParticleTypes.ELECTRIC_SPARK, player.position().add(0.0, 0.8, 0.0), FIELD_RADIUS * 0.4, 10);
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		ring(level, ParticleTypes.ELECTRIC_SPARK, player.position(), 3.0, 40);
		sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.6F, 0.5F);
		return true;
	}
}
