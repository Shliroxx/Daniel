package com.santiq.kingdomomnitrix.worldevent;

import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.DnaSampleItem;
import com.santiq.kingdomomnitrix.boss.NefariousEntity;
import com.santiq.kingdomomnitrix.enemy.RiftSpawner;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

/**
 * Ein laufendes Welt-Ereignis: Ablauf je {@link WorldEventDefinition.Type}, Bossleiste fuer Spieler in der Naehe,
 * Belohnung fuer alle Beteiligten. Veraenderte Bloecke des Schreins werden am Ende zurueckgesetzt; Krater und Erz von
 * Meteor/Absturz bleiben (sie sind die Beute).
 */
public final class ActiveWorldEvent {
	/** Spieler in diesem Umkreis sehen die Bossleiste und gelten als beteiligt */
	static final double RANGE = 64.0;
	private static final int FALL_TICKS = 60;
	private static final int SHRINE_RADIUS = 5;
	private static final int SHRINE_HOLD_SECONDS = 60;
	private static final int SHRINE_MAX_HEARTLESS = 6;
	/** Kraterradius: Meteor groesser als die Kapsel */
	static final int METEOR_RADIUS = 7;
	static final int CRASH_RADIUS = 6;
	/** Schaden der Druckwelle im Zentrum (halbe Herzen) */
	private static final float SHOCK_DAMAGE = 16.0f;

	public enum Outcome {
		RUNNING, SUCCESS, TIMEOUT, CANCELLED
	}

	private final Identifier id;
	private final WorldEventDefinition definition;
	private final ServerWorld world;
	private final BlockPos pos;
	private final long start;
	private final long end;
	private final int level;
	private final ServerBossBar bar;
	private final Set<UUID> participants = new HashSet<>();
	private final Set<UUID> guards = new HashSet<>();
	/** vom Ereignis gesetzte Bloecke mit ihrem vorherigen Zustand (Schrein) */
	private final Map<BlockPos, BlockState> restore = new LinkedHashMap<>();
	private UUID tracked;
	private boolean landed;
	private int shrineSeconds;
	private Outcome outcome = Outcome.RUNNING;

	ActiveWorldEvent(Identifier id, WorldEventDefinition definition, ServerWorld world, BlockPos pos, int level) {
		this.id = id;
		this.definition = definition;
		this.world = world;
		this.pos = pos;
		this.level = level;
		this.start = world.getTime();
		this.end = start + definition.durationSeconds() * 20L;
		this.bar = new ServerBossBar(name(), color(definition.type()), BossBar.Style.NOTCHED_10);
	}

	public Identifier id() {
		return id;
	}

	public WorldEventDefinition definition() {
		return definition;
	}

	public ServerWorld world() {
		return world;
	}

	public BlockPos pos() {
		return pos;
	}

	public Outcome outcome() {
		return outcome;
	}

	public Text name() {
		return Text.translatable("world_event." + id.getNamespace() + "." + id.getPath());
	}

	public long remainingTicks() {
		return Math.max(0L, end - world.getTime());
	}

	private static BossBar.Color color(WorldEventDefinition.Type type) {
		return switch (type) {
			case HEARTLESS_INVASION, DARK_RIFT -> BossBar.Color.PURPLE;
			case ALIEN_CRASH -> BossBar.Color.GREEN;
			case RARITANIUM_METEOR -> BossBar.Color.BLUE;
			case BOSS_SPAWN -> BossBar.Color.RED;
			case KEYBLADE_SHRINE -> BossBar.Color.YELLOW;
		};
	}

	/** Beginn: Riss/Boss erscheinen sofort, Meteor und Kapsel fallen erst ({@link #FALL_TICKS}), Schrein wird gebaut. */
	boolean begin() {
		switch (definition.type()) {
			case HEARTLESS_INVASION, DARK_RIFT -> {
				Optional<? extends Entity> rift = RiftSpawner.openAt(world, pos, definition.rift().orElseThrow(), level);
				if (rift.isEmpty()) {
					return false;
				}
				tracked = rift.get().getUuid();
			}
			case BOSS_SPAWN -> {
				NefariousEntity boss = ModEntities.NEFARIOUS.create(world);
				if (boss == null) {
					return false;
				}
				boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
				boss.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);
				world.spawnEntity(boss);
				tracked = boss.getUuid();
				world.playSound(null, pos, ModSounds.BOSS_STOMP, SoundCategory.HOSTILE, 2.0f, 0.7f);
			}
			case KEYBLADE_SHRINE -> buildShrine();
			case ALIEN_CRASH, RARITANIUM_METEOR -> world.playSound(null, pos, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.AMBIENT, 4.0f, 0.4f);
		}
		return true;
	}

	/** Jeden Tick (Server). */
	void tick() {
		long now = world.getTime();
		if (now % 20 == 0) {
			updateBar();
		}
		switch (definition.type()) {
			case HEARTLESS_INVASION, DARK_RIFT, BOSS_SPAWN -> {
				Entity entity = tracked == null ? null : world.getEntity(tracked);
				if (entity == null || !entity.isAlive()) {
					// Riss geschlossen (gibt selbst Beute) bzw. Boss besiegt — nur, wenn er nicht einfach entladen wurde
					if (entity == null && !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
						break;
					}
					finish(Outcome.SUCCESS);
					return;
				}
			}
			case ALIEN_CRASH, RARITANIUM_METEOR -> {
				if (!landed) {
					fall(now - start);
					if (now - start >= FALL_TICKS) {
						impact();
					}
				} else if (definition.type() == WorldEventDefinition.Type.ALIEN_CRASH) {
					guards.removeIf(uuid -> world.getEntity(uuid) == null || !world.getEntity(uuid).isAlive());
					if (guards.isEmpty()) {
						finish(Outcome.SUCCESS);
						return;
					}
				} else if (now - start >= FALL_TICKS + 100) {
					// Meteor: nach dem Einschlag gilt das Ereignis als erledigt, das Erz bleibt liegen
					finish(Outcome.SUCCESS);
					return;
				}
			}
			case KEYBLADE_SHRINE -> {
				if (now % 20 == 0 && tickShrine()) {
					finish(Outcome.SUCCESS);
					return;
				}
			}
		}
		if (now >= end) {
			finish(Outcome.TIMEOUT);
		}
	}

	private void updateBar() {
		double rangeSq = RANGE * RANGE;
		Vec3d center = Vec3d.ofCenter(pos);
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean near = !player.isSpectator() && player.squaredDistanceTo(center) <= rangeSq;
			if (near) {
				bar.addPlayer(player);
				participants.add(player.getUuid());
			} else {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayerEntity player : new ArrayList<>(bar.getPlayers())) {
			if (player.isRemoved() || player.getWorld() != world) {
				bar.removePlayer(player);
			}
		}
		if (definition.type() == WorldEventDefinition.Type.KEYBLADE_SHRINE) {
			bar.setPercent(MathHelper.clamp(shrineSeconds / (float) SHRINE_HOLD_SECONDS, 0.0f, 1.0f));
		} else {
			bar.setPercent(MathHelper.clamp(remainingTicks() / (float) (end - start), 0.0f, 1.0f));
		}
	}

	// --- Meteor und Absturz -----------------------------------------------------------------------

	/** Feuerschweif vom Himmel schraeg auf den Einschlagpunkt. */
	private void fall(long age) {
		double t = MathHelper.clamp(age / (double) FALL_TICKS, 0.0, 1.0);
		double height = 80.0 * (1.0 - t);
		double x = pos.getX() + 0.5 + 40.0 * (1.0 - t);
		double y = pos.getY() + height;
		double z = pos.getZ() + 0.5;
		boolean crash = definition.type() == WorldEventDefinition.Type.ALIEN_CRASH;
		// Feuerball mit langem Rauchschweif (Meteor groesser als die Kapsel)
		double size = crash ? 1.2 : 1.8;
		world.spawnParticles(ParticleTypes.FLAME, x, y, z, 40, size, size, size, 0.04);
		world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, crash ? 4 : 12, size * 0.6, size * 0.6, size * 0.6, 0.02);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, x + 1.5, y + 1.5, z, 20, size + 0.6, size + 0.6, size + 0.6, 0.01);
		world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + 3.0, y + 3.0, z, 4, 1.0, 1.0, 1.0, 0.0);
		world.spawnParticles(crash ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.LAVA, x, y, z, 10, size, size, size, 0.05);
		if (age % 10 == 0) {
			world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.AMBIENT, 3.0f, 0.5f);
		}
	}

	private void impact() {
		landed = true;
		boolean meteor = definition.type() == WorldEventDefinition.Type.RARITANIUM_METEOR;
		int radius = meteor ? METEOR_RADIUS : CRASH_RADIUS;
		Vec3d center = Vec3d.ofCenter(pos);
		for (int i = 0; i < 4; i++) {
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x + (world.random.nextDouble() - 0.5) * radius, center.y + 0.5,
					center.z + (world.random.nextDouble() - 0.5) * radius, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.FLASH, center.x, center.y + 2, center.z, 3, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.LAVA, center.x, center.y + 1, center.z, 120, radius * 0.5, 1.0, radius * 0.5, 0.4);
		world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.x, center.y + 1, center.z, 60, radius * 0.6, 1.0, radius * 0.6, 0.03);
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.AMBIENT, 8.0f, 0.4f);
		world.playSound(null, pos, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.AMBIENT, 6.0f, 0.5f);
		world.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 8.0f, 0.6f);
		shockwave(center, radius);
		if (world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
			crater(radius);
			debris(radius);
			ignite(radius);
		}
		if (meteor) {
			placeOre();
		} else {
			placePod();
			for (int i = 0; i < 4 + world.random.nextInt(3); i++) {
				spawnHeartless(i % 3 == 0 ? ModEntities.SOLDIER : ModEntities.SHADOW, radius + 2.0).ifPresent(guards::add);
			}
		}
	}

	/** Druckwelle: Schaden und Rueckstoss nach Entfernung (Mitte bis {@link #SHOCK_DAMAGE}, am Rand 0). */
	private void shockwave(Vec3d center, int radius) {
		double reach = radius + 9.0;
		for (net.minecraft.entity.LivingEntity living : world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
				new net.minecraft.util.math.Box(center, center).expand(reach), e -> e.isAlive() && !e.isSpectator())) {
			double distance = living.getPos().distanceTo(center);
			double strength = 1.0 - distance / reach;
			if (strength <= 0.0) {
				continue;
			}
			living.damage(world.getDamageSources().explosion(null, null), (float) (SHOCK_DAMAGE * strength));
			Vec3d push = living.getPos().subtract(center).normalize().multiply(1.6 * strength);
			living.addVelocity(push.x, 0.5 + 0.6 * strength, push.z);
			living.velocityModified = true;
		}
	}

	/** Trümmer: Blöcke vom Kraterrand fliegen als fallende Blöcke nach außen. */
	private void debris(int radius) {
		for (int i = 0; i < radius * 3; i++) {
			double angle = world.random.nextDouble() * Math.PI * 2;
			BlockPos at = pos.add((int) Math.round(Math.cos(angle) * radius), -1, (int) Math.round(Math.sin(angle) * radius));
			BlockState state = world.getBlockState(at);
			if (state.isAir() || !natural(state) || state.isOf(Blocks.BEDROCK)) {
				continue;
			}
			net.minecraft.entity.FallingBlockEntity block = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(world, at,
					world.random.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK.getDefaultState() : state);
			block.setVelocity(Math.cos(angle) * 0.7, 0.8 + world.random.nextDouble() * 0.6, Math.sin(angle) * 0.7);
			block.dropItem = false;
		}
	}

	/** Brände rund um den Krater (nur auf brennbarem Boden mit Luft darueber). */
	private void ignite(int radius) {
		for (int i = 0; i < radius * 5; i++) {
			double angle = world.random.nextDouble() * Math.PI * 2;
			double distance = radius + 1 + world.random.nextDouble() * 4;
			BlockPos column = BlockPos.ofFloored(pos.getX() + Math.cos(angle) * distance, pos.getY(), pos.getZ() + Math.sin(angle) * distance);
			BlockPos top = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
			if (world.getBlockState(top).isAir() && world.getBlockState(top.down()).isSideSolidFullSquare(world, top.down(), net.minecraft.util.math.Direction.UP)) {
				world.setBlockState(top, Blocks.FIRE.getDefaultState());
			}
		}
	}

	/**
	 * Schuesselfoermiger Krater aus natuerlichem Boden (Tiefe etwa halber Radius); Wand aus Schwarzstein, Basalt und Magma,
	 * im Grund ein Glutkern mit Lava. Bloecke mit Inhalt und Bauwerke bleiben.
	 */
	private void crater(int radius) {
		int depth = Math.max(3, radius / 2 + 1);
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				double flat = Math.sqrt(dx * dx + dz * dz);
				// Schuessel: Tiefe nimmt zum Rand quadratisch ab
				double bowl = depth * (1.0 - (flat / radius) * (flat / radius));
				for (int dy = -depth - 1; dy <= 2; dy++) {
					BlockPos at = pos.add(dx, dy, dz);
					BlockState state = world.getBlockState(at);
					if (!natural(state) || state.isOf(Blocks.BEDROCK)) {
						continue;
					}
					if (flat <= radius && dy > -bowl - 0.5) {
						world.setBlockState(at, Blocks.AIR.getDefaultState());
					} else if (flat <= radius + 1.2 && dy <= 0 && dy > -bowl - 2.0 && !state.isAir()) {
						int roll = world.random.nextInt(10);
						world.setBlockState(at, roll < 3 ? Blocks.MAGMA_BLOCK.getDefaultState() : roll < 6 ? Blocks.BASALT.getDefaultState()
								: Blocks.BLACKSTONE.getDefaultState());
					}
				}
			}
		}
		// Glutkern: Lava-Taschen im Grund, von Magma eingefasst (fliesst nicht aus dem Krater)
		BlockPos bottom = pos.down(depth);
		for (int i = 0; i < 2 + radius / 3; i++) {
			BlockPos at = bottom.add(world.random.nextInt(3) - 1, 0, world.random.nextInt(3) - 1);
			if (natural(world.getBlockState(at)) || world.getBlockState(at).isOf(Blocks.MAGMA_BLOCK) || world.getBlockState(at).isOf(Blocks.BLACKSTONE)
					|| world.getBlockState(at).isOf(Blocks.BASALT)) {
				for (net.minecraft.util.math.Direction side : net.minecraft.util.math.Direction.values()) {
					if (side != net.minecraft.util.math.Direction.UP && !world.getBlockState(at.offset(side)).isAir()) {
						world.setBlockState(at.offset(side), Blocks.MAGMA_BLOCK.getDefaultState());
					}
				}
				world.setBlockState(at, Blocks.LAVA.getDefaultState());
			}
		}
	}

	static boolean natural(BlockState state) {
		if (state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.DIRT_PATH)) {
			return false;
		}
		return state.isAir() || state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isIn(BlockTags.BASE_STONE_OVERWORLD)
				|| state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW) || state.isOf(Blocks.SHORT_GRASS) || state.isOf(Blocks.TALL_GRASS)
				|| state.isIn(BlockTags.FLOWERS);
	}

	/** Boden der Mulde (erster fester Block von oben). */
	private BlockPos floor() {
		BlockPos at = pos.up(2);
		for (int i = 0; i < 8 && world.getBlockState(at.down()).isAir(); i++) {
			at = at.down();
		}
		return at;
	}

	private void placeOre() {
		BlockPos center = floor();
		int count = 9 + world.random.nextInt(6);
		for (int i = 0; i < count; i++) {
			BlockPos at = center.add(world.random.nextInt(5) - 2, world.random.nextInt(2) - 1, world.random.nextInt(5) - 2);
			boolean rare = world.random.nextFloat() < 0.12f;
			world.setBlockState(at, (rare ? ModBlocks.ORICHALCUM_ORE : ModBlocks.RARITANIUM_ORE).getDefaultState());
		}
	}

	private void placePod() {
		BlockPos center = floor();
		world.setBlockState(center.down(), Blocks.IRON_BLOCK.getDefaultState());
		world.setBlockState(center, Blocks.CHEST.getDefaultState());
		if (world.getBlockEntity(center) instanceof ChestBlockEntity chest) {
			List<ItemStack> loot = loot();
			for (int i = 0; i < loot.size() && i < chest.size(); i++) {
				chest.setStack(i, loot.get(i));
			}
		}
		world.setBlockState(center.east(), Blocks.LIGHTNING_ROD.getDefaultState());
	}

	// --- Schrein ----------------------------------------------------------------------------------

	private void buildShrine() {
		BlockPos base = pos;
		place(base.down(), Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					place(base.add(dx, -1, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
				}
			}
		}
		place(base, Blocks.GOLD_BLOCK.getDefaultState());
		place(base.up(), Blocks.END_ROD.getDefaultState());
		place(base.up(2), Blocks.SEA_LANTERN.getDefaultState());
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2.0f, 1.2f);
	}

	private void place(BlockPos at, BlockState state) {
		restore.putIfAbsent(at.toImmutable(), world.getBlockState(at));
		world.setBlockState(at, state);
	}

	/** Eine Sekunde Schrein: Fortschritt, solange ein Spieler nah ist; Herzlose greifen an. true = gehalten. */
	private boolean tickShrine() {
		Vec3d center = Vec3d.ofCenter(pos);
		boolean held = world.getPlayers().stream().anyMatch(p -> !p.isSpectator() && p.isAlive()
				&& p.squaredDistanceTo(center) <= SHRINE_RADIUS * SHRINE_RADIUS);
		world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y + 2.5, center.z, held ? 8 : 2, 0.3, 1.2, 0.3, 0.02);
		if (held) {
			shrineSeconds++;
		}
		guards.removeIf(uuid -> world.getEntity(uuid) == null || !world.getEntity(uuid).isAlive());
		if (shrineSeconds > 0 && shrineSeconds % 8 == 0 && guards.size() < SHRINE_MAX_HEARTLESS) {
			for (int i = 0; i < 2; i++) {
				spawnHeartless(world.random.nextInt(4) == 0 ? ModEntities.SOLDIER : ModEntities.SHADOW, 9.0).ifPresent(guards::add);
			}
		}
		return shrineSeconds >= SHRINE_HOLD_SECONDS;
	}

	private Optional<UUID> spawnHeartless(net.minecraft.entity.EntityType<? extends HostileEntity> type, double distance) {
		HostileEntity mob = type.create(world);
		if (mob == null) {
			return Optional.empty();
		}
		double angle = world.random.nextDouble() * Math.PI * 2;
		BlockPos column = BlockPos.ofFloored(pos.getX() + Math.cos(angle) * distance, pos.getY(), pos.getZ() + Math.sin(angle) * distance);
		BlockPos ground = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
		mob.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
		mob.initialize(world, world.getLocalDifficulty(ground), SpawnReason.EVENT, null);
		world.spawnEntity(mob);
		world.spawnParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		return Optional.of(mob.getUuid());
	}

	// --- Ende und Belohnung -----------------------------------------------------------------------

	private List<ItemStack> loot() {
		WorldEventDefinition.Rewards rewards = definition.rewards();
		List<ItemStack> loot = new ArrayList<>();
		if (rewards.raritanium() > 0) {
			loot.add(new ItemStack(ModItems.RARITANIUM, rewards.raritanium()));
		}
		if (rewards.mythril() > 0) {
			loot.add(new ItemStack(ModItems.MYTHRIL_SHARD, rewards.mythril()));
		}
		if (rewards.orichalcum() > 0) {
			loot.add(new ItemStack(ModItems.ORICHALCUM, rewards.orichalcum()));
		}
		if (rewards.dna()) {
			List<Identifier> aliens = AlienRegistry.sortedIds(world.getRegistryManager());
			if (!aliens.isEmpty()) {
				loot.add(DnaSampleItem.create(aliens.get(world.random.nextInt(aliens.size()))));
			}
		}
		return loot;
	}

	/** Ereignis beenden: Belohnung bei Erfolg, Schrein zuruecksetzen, Waechter/Boss bei Abbruch entfernen. */
	void finish(Outcome result) {
		if (outcome != Outcome.RUNNING) {
			return;
		}
		outcome = result;
		WorldEventDefinition.Rewards rewards = definition.rewards();
		if (result == Outcome.SUCCESS) {
			// Absturz: die Beute liegt in der Kiste; Schrein: Beute erscheint am Schrein
			if (definition.type() == WorldEventDefinition.Type.KEYBLADE_SHRINE) {
				for (ItemStack stack : loot()) {
					ItemEntity item = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, stack);
					item.setToDefaultPickupDelay();
					world.spawnEntity(item);
				}
			}
			for (UUID uuid : participants) {
				if (world.getServer().getPlayerManager().getPlayer(uuid) instanceof ServerPlayerEntity player) {
					if (rewards.bolts() > 0) {
						HeroDataAccess.update(player, data -> data.addBolts(rewards.bolts()));
					}
					if (rewards.experience() > 0) {
						HeroDataAccess.grantExperience(player, rewards.experience());
					}
					player.sendMessage(Text.translatable("message.kingdomomnitrix.world_event.success", name(), rewards.bolts(),
							rewards.experience()).formatted(Formatting.GOLD), false);
				}
			}
			world.playSound(null, pos, ModSounds.ARENA_VICTORY, SoundCategory.PLAYERS, 1.0f, 1.0f);
		} else {
			for (UUID uuid : guards) {
				Entity guard = world.getEntity(uuid);
				if (guard != null) {
					guard.discard();
				}
			}
			// Boss bzw. Riss verschwinden mit dem Ereignis (Riss-Waechter raeumt der Riss selbst nicht ab: Herzlose bleiben)
			if (tracked != null && (definition.type() == WorldEventDefinition.Type.BOSS_SPAWN || definition.type().usesRift())) {
				Entity owner = world.getEntity(tracked);
				if (owner != null) {
					world.spawnParticles(ParticleTypes.PORTAL, owner.getX(), owner.getY() + 1, owner.getZ(), 60, 0.6, 1.0, 0.6, 0.3);
					owner.discard();
				}
			}
			for (ServerPlayerEntity player : bar.getPlayers()) {
				player.sendMessage(Text.translatable(result == Outcome.TIMEOUT ? "message.kingdomomnitrix.world_event.timeout"
						: "message.kingdomomnitrix.world_event.cancelled", name()).formatted(Formatting.GRAY), false);
			}
		}
		restoreBlocks();
		bar.clearPlayers();
	}

	private void restoreBlocks() {
		List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(restore.entrySet());
		for (int i = entries.size() - 1; i >= 0; i--) {
			world.setBlockState(entries.get(i).getKey(), entries.get(i).getValue());
		}
		restore.clear();
	}

	/** Fuer Tests und Statusanzeige: Fortschritt des Schreins in Sekunden. */
	int shrineSeconds() {
		return shrineSeconds;
	}
}
