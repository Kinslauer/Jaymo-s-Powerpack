package com.jaymoskitchen.corpse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A dead player who got back up: a zombie wearing their face (clients draw the player's skin, grey-blue and in
 * tatters), holding what was in their hand when they died and carrying the rest of their unclaimed hotbar, which it
 * drops when it's put down again. In the spirit of the Zombie Players mod.
 */
public class ZombiePlayer extends Zombie {
	private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(ZombiePlayer.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(ZombiePlayer.class, EntityDataSerializers.STRING);

	private final List<ItemStack> carried = new ArrayList<>();

	/**
	 * Freshly turned, its senses are still sharp: it notices players from much further away than a zombie, and hears
	 * one sprinting even out of sight.
	 */
	static final double FOLLOW_RANGE = 64.0;
	private static final double HEAR_SPRINTING = 24.0;
	/**
	 * A mob's ground speed goes with the square of its speed attribute (it sets both its speed and its forward input
	 * from it). 0.305 squared is about 0.093, against a walking player's 0.098: barely slower than you walk.
	 */
	static final double WALK_SPEED = 0.305;
	/** Sprinting after a sprinting player: x1.19 makes it about 5.8 blocks a second, a little faster than you sprint. */
	private static final AttributeModifier SPRINT = new AttributeModifier(JaymosKitchen.id("zombie_player_sprint"), 0.19,
		AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

	public static AttributeSupplier.Builder createZombiePlayerAttributes() {
		return Zombie.createAttributes()
			.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
			.add(Attributes.MOVEMENT_SPEED, WALK_SPEED);
	}

	public ZombiePlayer(EntityType<? extends Zombie> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, "");
		builder.define(OWNER_NAME, "");
	}

	/** The player had a slim-armed skin: a higher voice. */
	private boolean slim;

	public void setSlim(boolean slim) {
		this.slim = slim;
	}

	/** Mixed under its groans now and then, so you never quite know what you're hearing in the dark. */
	private static final SoundEvent[] UNDERTONES = {
		SoundEvents.GHAST_AMBIENT, SoundEvents.GHAST_SCREAM, SoundEvents.GHAST_HURT, SoundEvents.ENDERMAN_SCREAM,
		SoundEvents.PHANTOM_AMBIENT, SoundEvents.ZOGLIN_AMBIENT, SoundEvents.VEX_AMBIENT, SoundEvents.WARDEN_AMBIENT,
		SoundEvents.WITHER_SKELETON_AMBIENT, SoundEvents.HUSK_AMBIENT, SoundEvents.ZOMBIE_VILLAGER_AMBIENT,
		SoundEvents.RAVAGER_ROAR, SoundEvents.AMBIENT_CAVE.value()
	};
	/** How often a groan, hurt cry or death rattle gets an undertone. */
	private static final float UNDERTONE_CHANCE = 0.6F;

	@Override
	public void makeSound(SoundEvent sound) {
		super.makeSound(sound);
		if (sound != null && !level().isClientSide() && !isSilent() && random.nextFloat() < UNDERTONE_CHANCE) {
			SoundEvent undertone = UNDERTONES[random.nextInt(UNDERTONES.length)];
			// lower and quieter than the groan it hides under
			level().playSound(null, getX(), getY(), getZ(), undertone, getSoundSource(), 0.35F + random.nextFloat() * 0.3F,
				getVoicePitch() * (0.65F + random.nextFloat() * 0.3F));
		}
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * (slim ? PlayerCorpses.SLIM_PITCH : 1.0F);
	}

	public void setOwner(UUID owner, String name) {
		this.entityData.set(OWNER, owner.toString());
		this.entityData.set(OWNER_NAME, name);
	}

	public Optional<UUID> owner() {
		try {
			return Optional.of(UUID.fromString(this.entityData.get(OWNER)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public String ownerName() {
		return this.entityData.get(OWNER_NAME);
	}

	/**
	 * Server, every tick: it sprints at its target only while that player is sprinting (and faster than they can), and
	 * a player sprinting nearby gets its attention even unseen.
	 */
	private void chase() {
		if (getTarget() == null && tickCount % 10 == 0) {
			Player heard = level().getNearestPlayer(this, HEAR_SPRINTING);
			if (heard != null && heard.isSprinting() && !heard.isCreative() && !heard.isSpectator()) {
				setTarget(heard);
			}
		}
		boolean sprint = getTarget() instanceof Player player && player.isSprinting() && player.isAlive();
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (sprint && !speed.hasModifier(SPRINT.id())) {
			speed.addTransientModifier(SPRINT);
		} else if (!sprint && speed.hasModifier(SPRINT.id())) {
			speed.removeModifier(SPRINT.id());
		}
		setSprinting(sprint);
	}

	/** Gives it what was in the dead player's hand, and the rest of their hotbar to carry. */
	public void arm(ItemStack held, List<ItemStack> rest) {
		if (!held.isEmpty()) {
			setItemSlot(EquipmentSlot.MAINHAND, held);
			setDropChance(EquipmentSlot.MAINHAND, 2.0F);
		}
		for (ItemStack stack : rest) {
			if (!stack.isEmpty()) {
				carried.add(stack.copy());
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			chase();
		}
		if (!level().isClientSide() && tickCount == 5) {
			// make sure everyone watching knows whose face it wears from the start
			this.entityData.set(OWNER, this.entityData.get(OWNER), true);
			this.entityData.set(OWNER_NAME, this.entityData.get(OWNER_NAME), true);
			// older ones still had a zombie's dull senses and shamble
			getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW_RANGE);
			getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(WALK_SPEED);
			// older ones were named after the player: the face is enough
			if (getCustomName() != null && getCustomName().getString().equals(ownerName())) {
				setCustomName(null);
			}
		}
	}

	@Override
	protected boolean convertsInWater() {
		// stays itself: no turning into a drowned
		return false;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (ItemStack stack : carried) {
			spawnAtLocation(level, stack);
		}
		carried.clear();
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Owner", this.entityData.get(OWNER));
		output.putString("OwnerName", ownerName());
		output.putBoolean("Slim", slim);
		output.store("Carried", ItemStack.CODEC.listOf(), carried);
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(OWNER, input.getStringOr("Owner", ""));
		this.entityData.set(OWNER_NAME, input.getStringOr("OwnerName", ""));
		this.slim = input.getBooleanOr("Slim", false);
		carried.clear();
		carried.addAll(input.read("Carried", ItemStack.CODEC.listOf()).orElse(List.of()));
	}
}
