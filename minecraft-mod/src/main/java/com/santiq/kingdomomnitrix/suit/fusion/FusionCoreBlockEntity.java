package com.santiq.kingdomomnitrix.suit.fusion;

import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Fusionskern: haelt das Mittelstueck und die Energiekerne und steuert die Fusion.
 *
 * <p>Start (Shift-Rechtsklick): passendes Rezept, Altar mindestens der Rezeptstufe ({@link FusionAltar}), genug
 * unbeschaedigte Injektoren mindestens der Rezeptstufe mit der richtigen Platte (Reichweite {@value #RANGE}, Hoehe
 * ±{@value #RANGE_Y}) und genug Energiekerne.
 *
 * <p>Waehrend der Fusion (nach dem Vorbild der Thaumcraft-Infusion und des Draconic-Reaktors):
 * <ul>
 *   <li><b>Instabilitaet</b> = Rezept-Instabilitaet minus Stabilitaet (Altarstufe, symmetrisch gegenueberstehende
 *       Injektoren). Jede Sekunde kann ein Ereignis eintreten: Funkenschlag, herausgeschleuderte Platte, Blitz,
 *       durchgebrannter Injektor oder – bei sehr hoher Instabilitaet – der Kollaps.</li>
 *   <li><b>Hitze</b> steigt je nach Stufe und wird von Eis, Packeis, Blauem Eis und Kryo-Kuehlern rund um den Kern
 *       gesenkt; eine Kryo-Zelle (Rechtsklick) senkt sie sofort. Bei 100 % schmilzt die Fusion.</li>
 *   <li>Fehlt eine Platte oder ist ein Injektor beschaedigt, <b>stockt</b> die Fusion (die Hitze steigt weiter);
 *       nach {@value #STALL_LIMIT} Ticks bricht sie zusammen.</li>
 * </ul>
 * Fehlschlag: Platten und Energiekerne sind verloren, das Mittelstueck bleibt, bis zu drei Injektoren brennen durch.
 */
public class FusionCoreBlockEntity extends BlockEntity {
	public static final int RANGE = 8;
	public static final int RANGE_Y = 2;
	public static final int STALL_LIMIT = 300;
	public static final float COOLANT = 30.0f;
	private static final float COOLING_PER_POINT = 0.006f;

	private ItemStack center = ItemStack.EMPTY;
	private ItemStack cores = ItemStack.EMPTY;
	private int progress;
	private int total;
	/** Platte des laufenden Rezepts (Netherit-Teile passen auf beide Linien, die Platte entscheidet) */
	private Item plate = Items.AIR;
	/** Weltzeit des letzten Abschlusses (fuer Blitz und Schockwelle auf dem Client) */
	private long finishedAt = Long.MIN_VALUE;
	/** Weltzeit des letzten Fehlschlags */
	private long failedAt = Long.MIN_VALUE;
	/** am Vorgang beteiligte Injektoren (fuer Strahlen auf dem Client) */
	private final List<BlockPos> active = new ArrayList<>();
	private float heat;
	private int instability;
	private int stalled;
	private int cooling;
	/** Client: zuletzt erkannte Altarstufe (fuer die Anzeige) */
	private int clientTier = 1;

	public FusionCoreBlockEntity(BlockPos pos, BlockState state) {
		super(SuitFusion.CORE_ENTITY, pos, state);
	}

	public ItemStack center() {
		return center;
	}

	public ItemStack cores() {
		return cores;
	}

	public float progress() {
		return total <= 0 ? 0.0f : progress / (float) total;
	}

	public boolean working() {
		return total > 0;
	}

	public List<BlockPos> active() {
		return active;
	}

	public Item plate() {
		return plate;
	}

	public long finishedAt() {
		return finishedAt;
	}

	public long failedAt() {
		return failedAt;
	}

	/** Hitze 0..100 */
	public float heat() {
		return heat;
	}

	/** wirksame Instabilitaet (Rezept minus Stabilitaet, mindestens 0) */
	public int instability() {
		return instability;
	}

	public boolean stalled() {
		return stalled > 0;
	}

	public int stallTicks() {
		return stalled;
	}

	public int clientTier() {
		return clientTier;
	}

	// --- Bedienung ----------------------------------------------------------------------------------------------------

	/**
	 * Rechtsklick mit Gegenstand: Kryo-Zelle kuehlt waehrend der Fusion, Energiekerne in den Kernspeicher, sonst als
	 * Mittelstueck einlegen.
	 */
	public boolean insert(ItemStack held, PlayerEntity player) {
		if (held.isEmpty()) {
			return false;
		}
		if (working()) {
			if (held.isOf(SuitFusion.CRYO_CELL) && heat > 0.0f) {
				held.decrementUnlessCreative(1, player);
				heat = Math.max(0.0f, heat - COOLANT);
				world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 1.6f);
				if (world instanceof ServerWorld server) {
					Vec3d c = Vec3d.ofCenter(pos).add(0, 0.75, 0);
					server.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 40, 0.5, 0.4, 0.5, 0.05);
				}
				player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.coolant", (int) COOLANT).formatted(Formatting.AQUA), true);
				sync();
				return true;
			}
			return false;
		}
		if (Ben10Items.CORE_COLORS.containsKey(held.getItem())) {
			if (cores.isEmpty()) {
				cores = held.split(held.getCount());
			} else if (ItemStack.areItemsAndComponentsEqual(cores, held)) {
				int move = Math.min(held.getCount(), cores.getMaxCount() - cores.getCount());
				cores.increment(move);
				held.decrement(move);
			} else {
				return false;
			}
			sync();
			return true;
		}
		if (!center.isEmpty()) {
			return false;
		}
		center = held.split(1);
		sync();
		return true;
	}

	/** Rechtsklick mit leerer Hand: erst das Mittelstueck, dann die Energiekerne zurueckgeben. */
	public ItemStack take() {
		if (working()) {
			return ItemStack.EMPTY;
		}
		ItemStack out;
		if (!center.isEmpty()) {
			out = center;
			center = ItemStack.EMPTY;
		} else {
			out = cores;
			cores = ItemStack.EMPTY;
		}
		sync();
		return out;
	}

	/** Shift-Rechtsklick: Fusion pruefen und starten; fehlt etwas, sagt der Kern was. */
	public void start(PlayerEntity player) {
		if (working() || world == null) {
			return;
		}
		List<FusionInjectorBlockEntity> injectors = injectors();
		Optional<FusionRecipes.Recipe> found = FusionRecipes.forCenter(center,
				injectors.stream().map(FusionInjectorBlockEntity::stack).toList());
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.no_recipe").formatted(Formatting.RED), true);
			return;
		}
		FusionRecipes.Recipe recipe = found.get();
		int altar = FusionAltar.tier(world, pos);
		if (altar < recipe.tier()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.altar_tier",
					Text.translatable(FusionCodexItem.TITLES[recipe.tier()]), Text.translatable(FusionCodexItem.TITLES[altar])).formatted(Formatting.RED),
					true);
			return;
		}
		List<FusionInjectorBlockEntity> holding = injectors.stream().filter(i -> i.stack().isOf(recipe.plate())).toList();
		long damaged = holding.stream().filter(FusionInjectorBlockEntity::damaged).count();
		if (damaged > 0) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.damaged", damaged).formatted(Formatting.RED), true);
			return;
		}
		List<FusionInjectorBlockEntity> usable = holding.stream().filter(i -> i.tier() >= recipe.tier()).toList();
		if (usable.size() < recipe.plates()) {
			if (holding.size() >= recipe.plates()) {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.injector_tier", recipe.tier()).formatted(Formatting.RED), true);
			} else {
				player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.plates", usable.size(), recipe.plates(),
						recipe.plate().getName()).formatted(Formatting.RED), true);
			}
			return;
		}
		if (cores.getCount() < recipe.cores()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.cores", cores.getCount(), recipe.cores())
					.formatted(Formatting.RED), true);
			return;
		}
		active.clear();
		usable.stream().limit(recipe.plates()).forEach(i -> active.add(i.getPos()));
		plate = recipe.plate();
		total = recipe.ticks();
		progress = 0;
		heat = 0.0f;
		stalled = 0;
		cooling = FusionAltar.cooling(world, pos);
		int stability = FusionAltar.stability(altar) + symmetry();
		instability = Math.max(0, recipe.instability() - stability);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.6f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.start", recipe.result().getName()).formatted(Formatting.AQUA), true);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion.status", Text.translatable(FusionCodexItem.TITLES[altar]), stability,
				recipe.instability(), cooling).formatted(instability > 10 ? Formatting.GOLD : Formatting.GRAY), false);
		sync();
	}

	/** Symmetrie wie bei Thaumcraft: +1 je Injektorpaar gegenueber mit gleicher Platte, -1 je Injektor ohne Partner. */
	private int symmetry() {
		int score = 0;
		for (BlockPos at : active) {
			BlockPos mirror = pos.multiply(2).subtract(at).withY(at.getY());
			score += active.contains(mirror) ? 1 : -2;
		}
		return score / 2;
	}

	private List<FusionInjectorBlockEntity> injectors() {
		List<FusionInjectorBlockEntity> found = new ArrayList<>();
		for (BlockPos at : BlockPos.iterate(pos.add(-RANGE, -RANGE_Y, -RANGE), pos.add(RANGE, RANGE_Y, RANGE))) {
			if (world.getBlockEntity(at) instanceof FusionInjectorBlockEntity injector && !injector.stack().isEmpty()) {
				found.add(injector);
			}
		}
		return found;
	}

	// --- Ablauf -------------------------------------------------------------------------------------------------------

	public static void tick(World world, BlockPos pos, BlockState state, FusionCoreBlockEntity core) {
		if (world.isClient() || !core.working()) {
			return;
		}
		ServerWorld server = (ServerWorld) world;
		Optional<FusionRecipes.Recipe> found = FusionRecipes.forCenter(core.center, List.of(new ItemStack(core.plate)))
				.filter(r -> r.plate() == core.plate);
		if (found.isEmpty() || core.cores.getCount() < found.get().cores()) {
			core.fail(server, "message.kingdomomnitrix.fusion.fail_instability");
			return;
		}
		FusionRecipes.Recipe recipe = found.get();
		List<FusionInjectorBlockEntity> ready = new ArrayList<>();
		for (BlockPos at : core.active) {
			if (world.getBlockEntity(at) instanceof FusionInjectorBlockEntity injector && injector.stack().isOf(recipe.plate())
					&& !injector.damaged()) {
				ready.add(injector);
			}
		}
		if (world.getTime() % 40 == 0) {
			core.cooling = FusionAltar.cooling(world, pos);
		}
		core.heat = Math.max(0.0f, core.heat + recipe.heatRate() - core.cooling * COOLING_PER_POINT);
		Vec3d target = Vec3d.ofCenter(pos).add(0, 0.75, 0);

		if (ready.size() < core.active.size()) {
			// Stocken: kein Fortschritt, Hitze steigt weiter
			core.stalled++;
			if (core.stalled % 20 == 1) {
				world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.0f, 0.4f);
				server.spawnParticles(ParticleTypes.SMOKE, target.x, target.y, target.z, 10, 0.3, 0.3, 0.3, 0.02);
			}
			if (core.stalled >= STALL_LIMIT) {
				core.fail(server, "message.kingdomomnitrix.fusion.fail_stalled");
				return;
			}
		} else {
			core.stalled = 0;
			core.progress++;
		}
		if (core.heat >= 100.0f) {
			core.fail(server, "message.kingdomomnitrix.fusion.fail_heat");
			return;
		}
		float t = core.progress / (float) core.total;
		if (core.progress % 4 == 0) {
			// Funken entlang der Strahlen (die Strahlen selbst zeichnet der Client)
			for (FusionInjectorBlockEntity injector : ready) {
				Vec3d from = Vec3d.ofCenter(injector.getPos()).add(0, 0.65, 0);
				Vec3d point = from.add(target.subtract(from).multiply(world.random.nextDouble()));
				server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.02);
			}
		}
		if (world.getTime() % 20 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.2f, 0.6f + t);
			server.spawnParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 6 + (int) (t * 20), 0.3, 0.3, 0.3, 0.05);
			if (core.heat >= 75.0f) {
				world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 0.6f);
				server.spawnParticles(ParticleTypes.LAVA, target.x, target.y, target.z, 4, 0.3, 0.2, 0.3, 0.0);
				core.notify(server, Text.translatable("message.kingdomomnitrix.fusion.warn_heat", (int) core.heat).formatted(Formatting.GOLD));
			}
			if (core.stalled == 0 && !core.event(server, ready, target)) {
				return;
			}
		}
		if (core.progress >= core.total) {
			core.finish(server, recipe, ready);
			return;
		}
		if (world.getTime() % 10 == 0) {
			core.sync();
		}
	}

	/** Instabilitaets-Ereignis wuerfeln; false, wenn die Fusion dabei kollabiert ist. */
	private boolean event(ServerWorld world, List<FusionInjectorBlockEntity> ready, Vec3d target) {
		if (instability <= 0 || world.random.nextInt(100) >= instability * 3 / 2 || ready.isEmpty()) {
			return true;
		}
		FusionInjectorBlockEntity victim = ready.get(world.random.nextInt(ready.size()));
		Vec3d at = Vec3d.ofCenter(victim.getPos()).add(0, 0.6, 0);
		int severity = world.random.nextInt(100) + instability * 2;
		if (severity < 70) {
			// Funkenschlag: Schaden fuer alle in der Naehe
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y, target.z, 40, 1.0, 0.6, 1.0, 0.4);
			world.playSound(null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.BLOCKS, 1.2f, 0.7f);
			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(5), p -> !p.isSpectator())) {
				player.damage(world.getDamageSources().magic(), 2.0f);
			}
			notify(world, Text.translatable("message.kingdomomnitrix.fusion.event_spark").formatted(Formatting.YELLOW));
		} else if (severity < 95) {
			// Platte wird herausgeschleudert: zuruecklegen, sonst kollabiert die Fusion
			ItemStack thrown = victim.stack();
			victim.setStack(ItemStack.EMPTY);
			ItemEntity item = new ItemEntity(world, at.x, at.y + 0.3, at.z, thrown);
			item.setVelocity((world.random.nextDouble() - 0.5) * 0.6, 0.45, (world.random.nextDouble() - 0.5) * 0.6);
			item.setToDefaultPickupDelay();
			world.spawnEntity(item);
			world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			world.playSound(null, victim.getPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1.2f, 0.6f);
			notify(world, Text.translatable("message.kingdomomnitrix.fusion.event_eject").formatted(Formatting.GOLD));
		} else if (severity < 115) {
			// Entladung: Blitz in einen Injektor, Hitzestoss
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
			if (bolt != null) {
				bolt.refreshPositionAfterTeleport(at.x, victim.getPos().getY() + 1.0, at.z);
				bolt.setCosmetic(true);
				world.spawnEntity(bolt);
			}
			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, new Box(victim.getPos()).expand(2), p -> !p.isSpectator())) {
				player.damage(world.getDamageSources().lightningBolt(), 5.0f);
			}
			heat += 15.0f;
			notify(world, Text.translatable("message.kingdomomnitrix.fusion.event_lightning").formatted(Formatting.GOLD));
		} else if (severity < 130) {
			// Injektor brennt durch: Reparatur mit Raritanium-Barren, bis dahin stockt die Fusion
			victim.setDamaged(true);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20, 0.2, 0.3, 0.2, 0.03);
			world.spawnParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
			world.playSound(null, victim.getPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.2f, 0.5f);
			notify(world, Text.translatable("message.kingdomomnitrix.fusion.event_damage").formatted(Formatting.RED));
		} else {
			fail(world, "message.kingdomomnitrix.fusion.fail_instability");
			return false;
		}
		sync();
		return true;
	}

	private void finish(ServerWorld world, FusionRecipes.Recipe recipe, List<FusionInjectorBlockEntity> injectors) {
		for (FusionInjectorBlockEntity injector : injectors) {
			injector.setStack(ItemStack.EMPTY);
		}
		cores.decrement(recipe.cores());
		ItemStack result = new ItemStack(recipe.result());
		if (center.contains(DataComponentTypes.ENCHANTMENTS)) {
			result.set(DataComponentTypes.ENCHANTMENTS, center.get(DataComponentTypes.ENCHANTMENTS));
		}
		if (center.contains(DataComponentTypes.CUSTOM_NAME)) {
			result.set(DataComponentTypes.CUSTOM_NAME, center.get(DataComponentTypes.CUSTOM_NAME));
		}
		center = result;
		reset();
		finishedAt = world.getTime();
		Vec3d c = Vec3d.ofCenter(pos).add(0, 0.75, 0);
		world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 0.2, 0.2, 0.2, 0.4);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 60, 0.4, 0.4, 0.4, 0.6);
		world.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 1.4f);
		world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.0f, 1.0f);
		sync();
	}

	/** Fehlschlag: Platten und Energiekerne verloren, Mittelstueck bleibt, bis zu drei Injektoren brennen durch. */
	private void fail(ServerWorld world, String reason) {
		FusionRecipes.Recipe recipe = FusionRecipes.forCenter(center, List.of(new ItemStack(plate))).orElse(null);
		List<FusionInjectorBlockEntity> burned = new ArrayList<>();
		for (BlockPos at : active) {
			if (world.getBlockEntity(at) instanceof FusionInjectorBlockEntity injector) {
				if (injector.stack().isOf(plate)) {
					injector.setStack(ItemStack.EMPTY);
				}
				burned.add(injector);
			}
		}
		Collections.shuffle(burned, new Random(world.random.nextLong()));
		for (int i = 0; i < Math.min(burned.size(), 1 + world.random.nextInt(3)); i++) {
			burned.get(i).setDamaged(true);
		}
		cores.decrement(recipe != null ? recipe.cores() : cores.getCount());
		reset();
		failedAt = world.getTime();
		Vec3d c = Vec3d.ofCenter(pos).add(0, 0.75, 0);
		world.createExplosion(null, world.getDamageSources().magic(), null, c.x, c.y, c.z, 2.5f, false, World.ExplosionSourceType.NONE);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 60, 0.8, 0.8, 0.8, 0.05);
		world.spawnParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 20, 0.6, 0.4, 0.6, 0.0);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.5f);
		world.playSound(null, pos, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.BLOCKS, 0.8f, 0.6f);
		notify(world, Text.translatable(reason).formatted(Formatting.DARK_RED));
		sync();
	}

	private void reset() {
		progress = 0;
		total = 0;
		active.clear();
		plate = Items.AIR;
		heat = 0.0f;
		instability = 0;
		stalled = 0;
	}

	/** Meldung in der Aktionsleiste aller Spieler im Umkreis von 24 Bloecken. */
	private void notify(ServerWorld world, Text text) {
		for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 24 * 24)) {
			player.sendMessage(text, true);
		}
	}

	void sync() {
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	// --- Speichern ----------------------------------------------------------------------------------------------------

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (!center.isEmpty()) {
			nbt.put("Center", center.encode(registries));
		}
		if (!cores.isEmpty()) {
			nbt.put("Cores", cores.encode(registries));
		}
		nbt.putInt("Progress", progress);
		nbt.putInt("Total", total);
		NbtList list = new NbtList();
		active.forEach(p -> list.add(NbtLong.of(p.asLong())));
		nbt.put("Active", list);
		nbt.putString("Plate", Registries.ITEM.getId(plate).toString());
		nbt.putLong("Finished", finishedAt);
		nbt.putLong("Failed", failedAt);
		nbt.putFloat("Heat", heat);
		nbt.putInt("Instability", instability);
		nbt.putInt("Stalled", stalled);
		nbt.putInt("Cooling", cooling);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		center = nbt.contains("Center") ? ItemStack.fromNbt(registries, nbt.getCompound("Center")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		cores = nbt.contains("Cores") ? ItemStack.fromNbt(registries, nbt.getCompound("Cores")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		progress = nbt.getInt("Progress");
		total = nbt.getInt("Total");
		plate = nbt.contains("Plate") ? Registries.ITEM.get(Identifier.of(nbt.getString("Plate"))) : Items.AIR;
		finishedAt = nbt.contains("Finished") ? nbt.getLong("Finished") : Long.MIN_VALUE;
		failedAt = nbt.contains("Failed") ? nbt.getLong("Failed") : Long.MIN_VALUE;
		heat = nbt.getFloat("Heat");
		instability = nbt.getInt("Instability");
		stalled = nbt.getInt("Stalled");
		cooling = nbt.getInt("Cooling");
		active.clear();
		for (NbtElement e : nbt.getList("Active", NbtElement.LONG_TYPE)) {
			active.add(BlockPos.fromLong(((NbtLong) e).longValue()));
		}
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	/** Client: Fortschritt zwischen den Updates weiterzaehlen (fuer fluessige Strahlen), Altarstufe erkennen. */
	public void clientTick() {
		if (total > 0 && progress < total && stalled == 0) {
			progress++;
		}
		if (world != null && world.getTime() % 20 == 0) {
			clientTier = FusionAltar.tier(world, pos);
		}
	}
}
