package com.santiq.kingdomomnitrix.dungeon;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.DnaSampleItem;
import com.santiq.kingdomomnitrix.dungeon.WaterwayLayout.Room;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.world.TraverseTown;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;

/**
 * Erster Dungeon: die Geheime Wasserstrasse unter Traverse Town (Kingdom Hearts). Ablauf nach AAA-Vorgabe:
 * Eingang → Erkundung (Kanal) → Raetsel (Hebel nach Wandbild) → Kampf (verriegelte Arena, 3 Wellen) → Geheimnis
 * (rissige Wand, Geheimkammer) → Miniboss (Torwaechter) → neuer Bereich (Bruecke ueber dem Schacht) → Schatz → Boss
 * (Schattenkoloss, zwei Beschwoerungs-Phasen). Danach Lichtsaeule zurueck an die Oberflaeche; der Dungeon setzt sich
 * nach {@link #RESET_MINUTES} Minuten zurueck (Tueren zu, Hebel aus, Truhen neu, neues Raetselmuster).
 *
 * <p>Gebaut wird einmal pro Welt, noerdlich ausserhalb der Stadt, 24 Bloecke unter dem Stadtplatz. Fortschritt der
 * laufenden Runde liegt im Speicher; beim Serverstart wird zurueckgesetzt (stimmige Tueren/Truhen).</p>
 */
public final class WaterwayDungeon {
	public static final String MOB_TAG = "kingdomomnitrix_dungeon";
	public static final int RESET_MINUTES = 20;
	private static final int DEPTH = 24;
	private static final String STATE_KEY = "kingdomomnitrix_secret_waterway";

	/** Abschnitte der Runde (in Reihenfolge). */
	public enum Stage {
		PUZZLE, ARENA, GUARDIAN, BOSS, CLEARED
	}

	private static final class State extends PersistentState {
		private BlockPos origin;

		static State fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			State state = new State();
			if (nbt.contains("X")) {
				state.origin = new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			if (origin != null) {
				nbt.putInt("X", origin.getX());
				nbt.putInt("Y", origin.getY());
				nbt.putInt("Z", origin.getZ());
			}
			return nbt;
		}
	}

	private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::fromNbt, null);

	// --- Laufende Runde (Speicher) ---
	private static Stage stage = Stage.PUZZLE;
	private static boolean[] pattern = new boolean[4];
	private static int arenaWave;
	private static final Set<UUID> alive = new HashSet<>();
	private static final Set<UUID> participants = new HashSet<>();
	private static UUID guardian;
	private static UUID colossus;
	private static int colossusPhase;
	private static long clearedAt = -1L;
	private static long returnAt = -1L;
	private static boolean dirty = true;
	private static final ServerBossBar BAR = new ServerBossBar(Text.empty(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
	private static final Set<String> announced = new HashSet<>();

	private WaterwayDungeon() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey().equals(TraverseTown.WORLD)) {
				tick(world);
			}
		});
	}

	/** Ursprung (Mitte der Eingangshalle, Boden), falls gebaut. */
	public static Optional<BlockPos> origin(ServerWorld world) {
		State state = world.getPersistentStateManager().get(TYPE, STATE_KEY);
		return Optional.ofNullable(state != null ? state.origin : null);
	}

	/** Eingang an der Oberflaeche (vor dem Kanalhaeuschen). */
	public static BlockPos surfaceEntrance(BlockPos origin) {
		return origin.add(0, DEPTH, 6);
	}

	/** Baut den Dungeon einmal (Aufruf aus {@link TraverseTown#ensure}); liefert den Ursprung. */
	public static BlockPos ensure(ServerWorld world, BlockPos townCenter) {
		State state = world.getPersistentStateManager().getOrCreate(TYPE, STATE_KEY);
		if (state.origin != null) {
			return state.origin;
		}
		long started = System.currentTimeMillis();
		BlockPos origin = new BlockPos(townCenter.getX(), townCenter.getY() - DEPTH, townCenter.getZ() - TraverseTown.RADIUS - 12);
		WaterwayBuilder.build(world, origin);
		state.origin = origin;
		state.markDirty();
		dirty = true;
		KingdomOmnitrix.LOGGER.info("Geheime Wasserstrasse bei {} errichtet ({} ms)", origin, System.currentTimeMillis() - started);
		return origin;
	}

	public static Stage stage() {
		return stage;
	}

	// --- Ablauf -----------------------------------------------------------------------------------

	private static void tick(ServerWorld world) {
		Optional<BlockPos> found = origin(world);
		if (found.isEmpty()) {
			return;
		}
		BlockPos o = found.get();
		long now = world.getTime();
		if (dirty) {
			reset(world, o);
		}
		if (now % 10 != 0) {
			return;
		}
		List<ServerPlayerEntity> inside = new ArrayList<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator() && player.isAlive() && WaterwayLayout.roomAt(o, player.getX(), player.getY(), player.getZ()) != null) {
				inside.add(player);
				participants.add(player.getUuid());
				announceRoom(player, WaterwayLayout.roomAt(o, player.getX(), player.getY(), player.getZ()));
			}
		}
		alive.removeIf(uuid -> world.getEntity(uuid) == null || !world.getEntity(uuid).isAlive());
		switch (stage) {
			case PUZZLE -> {
				if (puzzleSolved(world, o)) {
					WaterwayBuilder.openDoor(world, o, WaterwayLayout.ARENA.startZ());
					world.playSound(null, o.add(0, 1, WaterwayLayout.ARENA.startZ()), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.5f, 0.6f);
					message(inside, "message.kingdomomnitrix.dungeon.puzzle_solved", Formatting.AQUA);
					stage = Stage.ARENA;
				}
			}
			case ARENA -> tickArena(world, o, inside);
			case GUARDIAN -> tickGuardian(world, o, inside);
			case BOSS -> tickBoss(world, o, inside);
			case CLEARED -> {
				if (returnAt > 0 && now >= returnAt) {
					returnAt = -1L;
					BlockPos up = surfaceEntrance(o);
					for (ServerPlayerEntity player : world.getPlayers()) {
						if (WaterwayLayout.BOSS.contains(o, player.getX(), player.getY(), player.getZ())) {
							player.teleport(world, up.getX() + 0.5, up.getY(), up.getZ() + 0.5, 180.0f, 0.0f);
						}
					}
				}
				if (clearedAt > 0 && now - clearedAt >= RESET_MINUTES * 1200L && inside.isEmpty()) {
					dirty = true;
				}
			}
		}
		updateBar(world, o);
	}

	private static boolean puzzleSolved(ServerWorld world, BlockPos o) {
		for (int i = 0; i < 4; i++) {
			BlockState lever = world.getBlockState(WaterwayBuilder.leverPos(o, i));
			if (!lever.isOf(Blocks.LEVER) || lever.get(LeverBlock.POWERED) != pattern[i]) {
				return false;
			}
		}
		return true;
	}

	private static void tickArena(ServerWorld world, BlockPos o, List<ServerPlayerEntity> inside) {
		boolean playerInArena = inside.stream().anyMatch(p -> WaterwayLayout.ARENA.contains(o, p.getX(), p.getY(), p.getZ())
				&& p.getZ() < o.getZ() + WaterwayLayout.ARENA.startZ() - 2);
		if (arenaWave == 0) {
			if (playerInArena) {
				WaterwayBuilder.closeDoor(world, o, WaterwayLayout.ARENA.startZ());
				world.playSound(null, o.add(0, 1, WaterwayLayout.ARENA.startZ()), SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.5f, 0.5f);
				message(inside, "message.kingdomomnitrix.dungeon.arena_locked", Formatting.RED);
				arenaWave = 1;
				spawnWave(world, o, 1);
			}
			return;
		}
		if (!alive.isEmpty()) {
			return;
		}
		if (arenaWave < 3) {
			arenaWave++;
			message(inside, "message.kingdomomnitrix.dungeon.wave", Formatting.GOLD, arenaWave);
			spawnWave(world, o, arenaWave);
			return;
		}
		WaterwayBuilder.openDoor(world, o, WaterwayLayout.ARENA.startZ());
		WaterwayBuilder.openDoor(world, o, WaterwayLayout.GUARDIAN.startZ());
		world.playSound(null, o.add(0, 1, WaterwayLayout.ARENA.endZ()), ModSounds.ARENA_VICTORY, SoundCategory.PLAYERS, 1.0f, 1.0f);
		message(inside, "message.kingdomomnitrix.dungeon.arena_cleared", Formatting.GREEN);
		stage = Stage.GUARDIAN;
	}

	private static void spawnWave(ServerWorld world, BlockPos o, int wave) {
		BlockPos center = o.add(0, 0, (WaterwayLayout.ARENA.startZ() + WaterwayLayout.ARENA.endZ()) / 2);
		switch (wave) {
			case 1 -> spawnMany(world, ModEntities.SHADOW, center, 5, 5.0);
			case 2 -> {
				spawnMany(world, ModEntities.SOLDIER, center, 3, 5.0);
				spawnMany(world, ModEntities.AIR_SOLDIER, center.up(3), 2, 4.0);
			}
			default -> {
				spawnMany(world, ModEntities.LARGE_BODY, center, 1, 3.0);
				spawnMany(world, ModEntities.SHADOW, center, 4, 6.0);
			}
		}
	}

	private static void spawnMany(ServerWorld world, EntityType<? extends HostileEntity> type, BlockPos center, int count, double radius) {
		for (int i = 0; i < count; i++) {
			double angle = i * Math.PI * 2 / count + world.random.nextDouble() * 0.4;
			spawn(world, type, Vec3d.ofBottomCenter(center).add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius), 1.0f, 1.0f)
					.ifPresent(mob -> alive.add(mob.getUuid()));
		}
	}

	/** Gegner mit Dungeon-Markierung; {@code scale} vergroessert, {@code health} vervielfacht die Lebenspunkte. */
	private static Optional<HostileEntity> spawn(ServerWorld world, EntityType<? extends HostileEntity> type, Vec3d at, float scale,
			float health) {
		HostileEntity mob = type.create(world);
		if (mob == null) {
			return Optional.empty();
		}
		mob.refreshPositionAndAngles(at.x, at.y, at.z, world.random.nextFloat() * 360.0f, 0.0f);
		mob.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.EVENT, null);
		mob.addCommandTag(MOB_TAG);
		mob.addCommandTag(TraverseTown.ALLOWED_TAG);
		mob.setPersistent();
		if (scale != 1.0f && mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE) != null) {
			mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(scale);
		}
		if (health != 1.0f && mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
			var max = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			max.setBaseValue(max.getBaseValue() * health);
			mob.setHealth(mob.getMaxHealth());
		}
		world.spawnEntity(mob);
		world.spawnParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 14, 0.3, 0.5, 0.3, 0.02);
		return Optional.of(mob);
	}

	private static void tickGuardian(ServerWorld world, BlockPos o, List<ServerPlayerEntity> inside) {
		if (guardian == null) {
			boolean entered = inside.stream().anyMatch(p -> WaterwayLayout.GUARDIAN.contains(o, p.getX(), p.getY(), p.getZ())
					&& p.getZ() < o.getZ() + WaterwayLayout.GUARDIAN.startZ() - 2);
			if (entered) {
				Vec3d at = Vec3d.ofBottomCenter(o.add(0, 0, WaterwayLayout.GUARDIAN.endZ() + 3));
				spawn(world, ModEntities.SOLDIER, at, 1.9f, 8.0f).ifPresent(mob -> {
					mob.setCustomName(Text.translatable("entity.kingdomomnitrix.dungeon_guardian"));
					guardian = mob.getUuid();
				});
				world.playSound(null, BlockPos.ofFloored(at), ModSounds.HEARTLESS_SPAWN, SoundCategory.HOSTILE, 2.0f, 0.6f);
				message(inside, "message.kingdomomnitrix.dungeon.guardian", Formatting.RED);
			}
			return;
		}
		Entity boss = world.getEntity(guardian);
		if (boss == null || !boss.isAlive()) {
			guardian = null;
			WaterwayBuilder.openDoor(world, o, WaterwayLayout.BRIDGE.startZ());
			world.playSound(null, o.add(0, 1, WaterwayLayout.BRIDGE.startZ()), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.5f, 0.6f);
			message(inside, "message.kingdomomnitrix.dungeon.guardian_down", Formatting.GREEN);
			stage = Stage.BOSS;
		}
	}

	private static void tickBoss(ServerWorld world, BlockPos o, List<ServerPlayerEntity> inside) {
		if (colossus == null) {
			boolean entered = inside.stream().anyMatch(p -> WaterwayLayout.BOSS.contains(o, p.getX(), p.getY(), p.getZ())
					&& p.getZ() < o.getZ() + WaterwayLayout.BOSS.startZ() - 4);
			if (entered) {
				WaterwayBuilder.closeDoor(world, o, WaterwayLayout.BOSS.startZ());
				Vec3d at = Vec3d.ofBottomCenter(o.add(0, 0, (WaterwayLayout.BOSS.startZ() + WaterwayLayout.BOSS.endZ()) / 2 - 3));
				spawn(world, ModEntities.LARGE_BODY, at, 2.6f, 9.0f).ifPresent(mob -> {
					mob.setCustomName(Text.translatable("entity.kingdomomnitrix.dungeon_colossus"));
					var damage = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
					if (damage != null) {
						damage.setBaseValue(damage.getBaseValue() * 1.8);
					}
					colossus = mob.getUuid();
				});
				colossusPhase = 0;
				world.playSound(null, BlockPos.ofFloored(at), ModSounds.BOSS_STOMP, SoundCategory.HOSTILE, 3.0f, 0.5f);
				message(inside, "message.kingdomomnitrix.dungeon.boss", Formatting.DARK_PURPLE);
			}
			return;
		}
		Entity entity = world.getEntity(colossus);
		if (entity instanceof HostileEntity boss && boss.isAlive()) {
			float health = boss.getHealth() / boss.getMaxHealth();
			int phase = health <= 0.33f ? 2 : health <= 0.66f ? 1 : 0;
			if (phase > colossusPhase) {
				colossusPhase = phase;
				message(inside, "message.kingdomomnitrix.dungeon.boss_phase", Formatting.DARK_PURPLE);
				spawnMany(world, ModEntities.SHADOW, boss.getBlockPos(), 3 + phase, 4.0);
				if (phase == 2) {
					spawnMany(world, ModEntities.DARKBALL, boss.getBlockPos().up(3), 2, 3.0);
				}
				world.spawnParticles(ParticleTypes.SQUID_INK, boss.getX(), boss.getY() + 2, boss.getZ(), 80, 2.0, 2.0, 2.0, 0.1);
			}
			return;
		}
		if (entity != null && !world.isChunkLoaded(entity.getBlockPos())) {
			return;
		}
		colossus = null;
		complete(world, o, inside);
	}

	private static void complete(ServerWorld world, BlockPos o, List<ServerPlayerEntity> inside) {
		stage = Stage.CLEARED;
		// beschworene Schatten vergehen mit ihrem Herrn
		for (HostileEntity mob : world.getEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(HostileEntity.class),
				e -> e.getCommandTags().contains(MOB_TAG))) {
			world.spawnParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.8, mob.getZ(), 16, 0.3, 0.5, 0.3, 0.05);
			mob.discard();
		}
		alive.clear();
		clearedAt = world.getTime();
		returnAt = world.getTime() + 200L;
		WaterwayBuilder.openDoor(world, o, WaterwayLayout.BOSS.startZ());
		BlockPos pillar = o.add(0, 0, WaterwayLayout.BOSS.endZ() + 2);
		world.spawnParticles(ParticleTypes.END_ROD, pillar.getX() + 0.5, pillar.getY() + 4, pillar.getZ() + 0.5, 200, 0.4, 4.0, 0.4, 0.05);
		world.playSound(null, pillar, ModSounds.ARENA_VICTORY, SoundCategory.PLAYERS, 1.5f, 1.0f);
		for (UUID uuid : participants) {
			if (world.getServer().getPlayerManager().getPlayer(uuid) instanceof ServerPlayerEntity player) {
				HeroDataAccess.update(player, data -> data.addBolts(1000));
				HeroDataAccess.grantExperience(player, 2000);
				player.sendMessage(Text.translatable("message.kingdomomnitrix.dungeon.cleared", 1000, 2000).formatted(Formatting.GOLD), false);
			}
		}
		message(inside, "message.kingdomomnitrix.dungeon.return", Formatting.YELLOW);
	}

	/** Runde zuruecksetzen: Tueren, Hebel, Truhen, Raetselmuster; Dungeon-Gegner entfernen. */
	public static void reset(ServerWorld world, BlockPos o) {
		dirty = false;
		for (HostileEntity mob : world.getEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(HostileEntity.class),
				e -> e.getCommandTags().contains(MOB_TAG))) {
			mob.discard();
		}
		alive.clear();
		participants.clear();
		announced.clear();
		guardian = null;
		colossus = null;
		colossusPhase = 0;
		arenaWave = 0;
		clearedAt = -1L;
		returnAt = -1L;
		Random random = world.getRandom();
		do {
			for (int i = 0; i < 4; i++) {
				pattern[i] = random.nextBoolean();
			}
		} while (!pattern[0] && !pattern[1] && !pattern[2] && !pattern[3]);
		WaterwayBuilder.resetRound(world, o, pattern, random);
		stage = Stage.PUZZLE;
		BAR.clearPlayers();
	}

	/** Befehl: Runde sofort zuruecksetzen. */
	public static boolean resetNow(ServerWorld world) {
		Optional<BlockPos> o = origin(world);
		o.ifPresent(origin -> reset(world, origin));
		return o.isPresent();
	}

	private static void updateBar(ServerWorld world, BlockPos o) {
		HostileEntity tracked = null;
		if (colossus != null && world.getEntity(colossus) instanceof HostileEntity boss) {
			tracked = boss;
		} else if (guardian != null && world.getEntity(guardian) instanceof HostileEntity boss) {
			tracked = boss;
		}
		if (tracked == null) {
			BAR.clearPlayers();
			return;
		}
		BAR.setName(tracked.getDisplayName());
		BAR.setColor(colossus != null ? BossBar.Color.PURPLE : BossBar.Color.RED);
		BAR.setPercent(Math.max(0.0f, tracked.getHealth() / tracked.getMaxHealth()));
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(tracked) < 48 * 48) {
				BAR.addPlayer(player);
			} else {
				BAR.removePlayer(player);
			}
		}
	}

	private static void announceRoom(ServerPlayerEntity player, Room room) {
		if (room == null || !announced.add(player.getUuid() + room.id())) {
			return;
		}
		player.sendMessage(Text.translatable("dungeon.kingdomomnitrix.secret_waterway." + room.id()).formatted(Formatting.AQUA), true);
	}

	private static void message(List<ServerPlayerEntity> players, String key, Formatting color, Object... args) {
		for (ServerPlayerEntity player : players) {
			player.sendMessage(Text.translatable(key, args).formatted(color), false);
		}
	}

	/** Truhenbeute: Bolts, Material, Heiltrank, selten DNA. */
	static List<ItemStack> treasure(ServerWorld world, Random random, boolean rich) {
		List<ItemStack> loot = new ArrayList<>();
		loot.add(new ItemStack(ModItems.BOLT, 16 + random.nextInt(32)));
		loot.add(new ItemStack(ModItems.MYTHRIL_SHARD, 2 + random.nextInt(3)));
		loot.add(new ItemStack(ModItems.HI_POTION, 1 + random.nextInt(2)));
		if (rich) {
			loot.add(new ItemStack(ModItems.ORICHALCUM, 1));
			loot.add(new ItemStack(ModItems.RARITANIUM, 3 + random.nextInt(3)));
			List<Identifier> aliens = AlienRegistry.sortedIds(world.getRegistryManager());
			if (!aliens.isEmpty() && random.nextFloat() < 0.5f) {
				loot.add(DnaSampleItem.create(aliens.get(random.nextInt(aliens.size()))));
			}
		}
		return loot;
	}

	static void fill(ServerWorld world, BlockPos pos, List<ItemStack> loot) {
		if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
			container.clear();
			for (int i = 0; i < loot.size() && i < container.size(); i++) {
				container.setStack(i * 2 % container.size(), loot.get(i));
			}
		}
	}

	/** Fuer die Statusanzeige */
	public static boolean[] pattern() {
		return pattern.clone();
	}
}
