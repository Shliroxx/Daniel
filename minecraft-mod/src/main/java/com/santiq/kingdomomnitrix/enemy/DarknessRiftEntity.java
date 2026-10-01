package com.santiq.kingdomomnitrix.enemy;

import com.santiq.kingdomomnitrix.registry.ModSounds;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.DnaSampleItem;
import com.santiq.kingdomomnitrix.magic.SpellCrystalItem;
import com.santiq.kingdomomnitrix.magic.SpellRegistry;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Dunkelheitsriss: oeffnet sich in der Welt und entlaesst Wellen von Herzlosen. Sind alle Wellen besiegt,
 * schliesst er sich und hinterlaesst eine Belohnung. Ohne Spieler in der Naehe verschwindet er nach einiger Zeit.
 * Fortschritt (Welle, lebende Herzlose) wird gespeichert und uebersteht Neustarts.
 */
public class DarknessRiftEntity extends Entity {
	private static final double PLAYER_RANGE = 40.0;
	private static final int ABANDON_TICKS = 2400;
	private static final int WAVE_DELAY = 60;
	private static final int SPAWN_RADIUS = 5;

	private Identifier riftId = KingdomOmnitrix.id("standard");
	private int wave = -1;
	private int waveDelay = 40;
	private int level = 1;
	private int idleTicks;
	private int waveSize;
	private final Set<UUID> alive = new HashSet<>();
	private final Set<UUID> participants = new HashSet<>();
	private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("entity.kingdomomnitrix.darkness_rift"),
			BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);

	public DarknessRiftEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	public void configure(Identifier riftId, int playerLevel) {
		this.riftId = riftId;
		this.level = Math.max(1, playerLevel);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		Optional<RiftDefinition> definition = RiftRegistry.get(world.getRegistryManager(), riftId);
		if (definition.isEmpty()) {
			KingdomOmnitrix.LOGGER.warn("Riss {} hat keine Definition mehr, wird entfernt", riftId);
			close(world, false);
			return;
		}
		if (age % 3 == 0) {
			spawnRiftParticles(world);
		}
		if (age % 20 == 0) {
			updatePlayers(world);
		}
		if (bossBar.getPlayers().isEmpty()) {
			if (++idleTicks > ABANDON_TICKS) {
				close(world, false);
			}
			return;
		}
		idleTicks = 0;

		alive.removeIf(uuid -> !(world.getEntity(uuid) instanceof LivingEntity living) || !living.isAlive());
		RiftDefinition rift = definition.get();
		if (alive.isEmpty()) {
			if (wave + 1 >= rift.waves().size()) {
				if (wave >= 0) {
					complete(world, rift);
				} else {
					startNextWave(world, rift);
				}
				return;
			}
			if (--waveDelay <= 0) {
				startNextWave(world, rift);
			}
		}
		updateBar(rift);
	}

	private void startNextWave(ServerWorld world, RiftDefinition rift) {
		wave++;
		waveDelay = WAVE_DELAY;
		waveSize = 0;
		for (RiftDefinition.Group group : rift.waves().get(wave)) {
			Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOrEmpty(group.entity());
			if (type.isEmpty()) {
				KingdomOmnitrix.LOGGER.warn("Riss {}: unbekannter Gegner {}", riftId, group.entity());
				continue;
			}
			for (int i = 0; i < group.count(); i++) {
				spawnMember(world, type.get(), rift);
			}
		}
		world.playSound(null, getBlockPos(), ModSounds.HEARTLESS_SPAWN, SoundCategory.HOSTILE, 1.0f, 0.8f);
		for (ServerPlayerEntity player : bossBar.getPlayers()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.rift_wave", wave + 1, rift.waves().size())
					.formatted(Formatting.DARK_PURPLE), true);
		}
	}

	private void spawnMember(ServerWorld world, EntityType<?> type, RiftDefinition rift) {
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = 1.5 + random.nextDouble() * SPAWN_RADIUS;
		int x = MathHelper.floor(getX() + Math.cos(angle) * distance);
		int z = MathHelper.floor(getZ() + Math.sin(angle) * distance);
		BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		if (Math.abs(ground.getY() - getY()) > 6) {
			ground = getBlockPos();
		}
		Entity entity = type.create(world, null, ground, SpawnReason.EVENT, false, false);
		if (entity == null) {
			return;
		}
		if (entity instanceof HeartlessEntity heartless) {
			heartless.applyScaling(level, random.nextFloat() < rift.eliteChance());
		}
		if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
			mob.setPersistent();
			ServerPlayerEntity nearest = bossBar.getPlayers().stream().findFirst().orElse(null);
			if (nearest != null) {
				mob.setTarget(nearest);
			}
		}
		world.spawnEntity(entity);
		alive.add(entity.getUuid());
		waveSize++;
	}

	private void complete(ServerWorld world, RiftDefinition rift) {
		RiftDefinition.Rewards rewards = rift.rewards();
		List<ItemStack> loot = new ArrayList<>();
		int bolts = rewards.boltsMax() > 0 ? rewards.boltsMin() + random.nextInt(Math.max(1, rewards.boltsMax() - rewards.boltsMin() + 1)) : 0;
		while (bolts > 0) {
			int stack = Math.min(64, bolts);
			loot.add(new ItemStack(ModItems.BOLT, stack));
			bolts -= stack;
		}
		if (rewards.hearts() > 0) {
			loot.add(new ItemStack(ModItems.HEART, rewards.hearts()));
		}
		if (random.nextFloat() < rewards.crystalChance()) {
			List<Identifier> spells = SpellRegistry.sortedIds(world.getRegistryManager());
			if (!spells.isEmpty()) {
				loot.add(SpellCrystalItem.create(spells.get(random.nextInt(spells.size()))));
			}
		}
		if (random.nextFloat() < rewards.dnaChance()) {
			List<Identifier> aliens = AlienRegistry.sortedIds(world.getRegistryManager());
			if (!aliens.isEmpty()) {
				loot.add(DnaSampleItem.create(aliens.get(random.nextInt(aliens.size()))));
			}
		}
		for (ItemStack stack : loot) {
			ItemEntity item = new ItemEntity(world, getX(), getY() + 0.5, getZ(), stack);
			item.setToDefaultPickupDelay();
			world.spawnEntity(item);
		}
		ExperienceOrbEntity.spawn(world, getPos(), 20 + 5 * wave);
		for (UUID uuid : participants) {
			if (world.getEntity(uuid) instanceof ServerPlayerEntity player) {
				HeroDataAccess.grantExperience(player, rewards.experience());
				player.sendMessage(Text.translatable("message.kingdomomnitrix.rift_closed", rewards.experience())
						.formatted(Formatting.LIGHT_PURPLE), false);
			}
		}
		world.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 60, 1.0, 1.0, 1.0, 0.15);
		world.playSound(null, getBlockPos(), ModSounds.ARENA_VICTORY, SoundCategory.PLAYERS, 0.8f, 1.0f);
		close(world, true);
	}

	private void close(ServerWorld world, boolean cleared) {
		bossBar.clearPlayers();
		if (!cleared) {
			world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 30, 0.8, 0.8, 0.8, 0.05);
		}
		discard();
	}

	private void updatePlayers(ServerWorld world) {
		double rangeSq = PLAYER_RANGE * PLAYER_RANGE;
		for (ServerPlayerEntity player : new ArrayList<>(bossBar.getPlayers())) {
			if (player.isRemoved() || player.getWorld() != world || player.squaredDistanceTo(this) > rangeSq) {
				bossBar.removePlayer(player);
			}
		}
		for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(this) <= rangeSq && !p.isSpectator())) {
			bossBar.addPlayer(player);
			participants.add(player.getUuid());
		}
	}

	private void updateBar(RiftDefinition rift) {
		int total = rift.waves().size();
		float waveProgress = waveSize > 0 ? 1.0f - (float) alive.size() / waveSize : 0.0f;
		bossBar.setPercent(MathHelper.clamp((Math.max(0, wave) + waveProgress) / total, 0.0f, 1.0f));
		bossBar.setName(Text.translatable("entity.kingdomomnitrix.darkness_rift.wave", Math.max(1, wave + 1), total, alive.size()));
	}

	private void spawnRiftParticles(ServerWorld world) {
		double radius = 1.2 + 0.2 * Math.sin(age * 0.1);
		for (int i = 0; i < 6; i++) {
			double angle = age * 0.15 + i * Math.PI / 3;
			world.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(angle) * radius, getY() + 1.2 + Math.sin(angle) * radius,
					getZ(), 1, 0, 0, 0, 0.0);
		}
		world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.2, getZ(), 2, 0.3, 0.5, 0.3, 0.0);
	}

	@Override
	public void onRemoved() {
		super.onRemoved();
		bossBar.clearPlayers();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		Identifier id = Identifier.tryParse(nbt.getString("Rift"));
		if (id != null) {
			riftId = id;
		}
		wave = nbt.getInt("Wave");
		level = Math.max(1, nbt.getInt("Level"));
		waveSize = nbt.getInt("WaveSize");
		alive.clear();
		for (NbtElement element : nbt.getList("Alive", NbtElement.INT_ARRAY_TYPE)) {
			alive.add(NbtHelper.toUuid(element));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putString("Rift", riftId.toString());
		nbt.putInt("Wave", wave);
		nbt.putInt("Level", level);
		nbt.putInt("WaveSize", waveSize);
		NbtList list = new NbtList();
		for (UUID uuid : alive) {
			list.add(NbtHelper.fromUuid(uuid));
		}
		nbt.put("Alive", list);
	}
}
