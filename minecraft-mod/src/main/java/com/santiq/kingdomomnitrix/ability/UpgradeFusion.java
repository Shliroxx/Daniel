package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.AlienModes;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.vfx.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.Oxidizable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Upgrades Fusion mit Bloecken (Kraft „Techno-Fusion“, Blick auf einen Block in Reichweite). Welche Bloecke wozu
 * taugen, steht in Block-Tags {@code kingdomomnitrix:upgrade_fusion/<art>}; die Wirkung je Art steht hier.
 *
 * <ul>
 *   <li><b>Selbst-Fusion</b> (Kraft-Taste mit Blick auf den Block): Upgrade verschmilzt kurz mit dem Block und uebernimmt seine Funktion
 *       fuer {@link #SELF_TICKS} Ticks — Blitzableiter = Blitze schiessen, Werfer = Pfeilsalven, TNT = Sprengladungen,
 *       Ofen = Feuerbaelle und brennende Treffer, Glocke = Schallstoss, Beobachter = alle Lebewesen leuchten, Redstone
 *       = Ueberladung, Trichter = Magnet, Eis = Omnitrix kuehlen und Frost-Treffer, Metallbloecke = Panzerung,
 *       Leuchtfeuer = Kraftfeld fuer die Gruppe. Arten mit Ladungen feuern beim naechsten Druck ins Leere.</li>
 *   <li><b>Block verbessern</b> (Schleichen + Rechtsklick mit leerer Hand — Schleichen + Kraft-Taste waere schon
 *       Kraft 4–6; bei Bloecken ohne Selbst-Fusion auch ueber die Kraft-Taste): Maschinen laufen
 *       vierfach (Ofen, Brauer, Trichter), Werfer wird zum Wachgeschuetz, Blitzableiter zur Blitzfalle, Ambosse werden
 *       repariert, Kupfer entrostet.</li>
 * </ul>
 */
final class UpgradeFusion {
	static final int SELF_TICKS = 600;
	private static final double REACH = 5.0;
	private static final int NEON = 0x38FF14;

	/** Selbst-Fusionen: Tag, Ladungen (0 = Dauerwirkung), Uebersetzung, Farbe der Funken. */
	enum Self {
		// wie AE: Upgrade verschmilzt ganz mit dem Blitzableiter (eigenes Modell), der Block kommt danach zurueck
		LIGHTNING("lightning", 8, "fusion.kingdomomnitrix.lightning", 0xB8E4FF, true),
		TURRET("turret", 8, "fusion.kingdomomnitrix.turret", 0xC8C8C8, false),
		EXPLOSIVE("explosive", 3, "fusion.kingdomomnitrix.explosive", 0xFF4A2A, true),
		HEAT("heat", 6, "fusion.kingdomomnitrix.heat", 0xFF8A1E, false),
		SONIC("sonic", 4, "fusion.kingdomomnitrix.sonic", 0x5CE1E6, false),
		SCANNER("scanner", 0, "fusion.kingdomomnitrix.scanner", 0x7CFF4A, false),
		OVERLOAD("overload", 0, "fusion.kingdomomnitrix.overload", 0xFF2020, false),
		MAGNET("magnet", 0, "fusion.kingdomomnitrix.magnet", 0x9AA4AE, false),
		COOLANT("coolant", 0, "fusion.kingdomomnitrix.coolant", 0x9FE8FF, false),
		PLATING("plating", 0, "fusion.kingdomomnitrix.plating", 0xD8DEE4, false),
		BEACON("beacon", 0, "fusion.kingdomomnitrix.beacon", 0x8FFFF0, false);

		final TagKey<Block> tag;
		final int charges;
		final String name;
		final int color;
		/** Block wird bei der Fusion verbraucht (TNT) */
		final boolean consumes;

		Self(String path, int charges, String name, int color, boolean consumes) {
			this.tag = TagKey.of(RegistryKeys.BLOCK, KingdomOmnitrix.id("upgrade_fusion/" + path));
			this.charges = charges;
			this.name = name;
			this.color = color;
			this.consumes = consumes;
		}
	}

	/** Verbesserungen eines Blocks: Tag (oder Sonderfall), Dauer in Ticks (0 = sofort), Uebersetzung. */
	enum Improve {
		OVERCLOCK("overclock", 1200, "fusion.kingdomomnitrix.overclock"),
		SENTRY("sentry", 800, "fusion.kingdomomnitrix.sentry"),
		STORM_TRAP("storm_trap", 1200, "fusion.kingdomomnitrix.storm_trap"),
		RESTORE("restore", 0, "fusion.kingdomomnitrix.restore"),
		DERUST("derust", 0, "fusion.kingdomomnitrix.derust");

		final TagKey<Block> tag;
		final int ticks;
		final String name;

		Improve(String path, int ticks, String name) {
			this.tag = TagKey.of(RegistryKeys.BLOCK, KingdomOmnitrix.id("upgrade_fusion/" + path));
			this.ticks = ticks;
			this.name = name;
		}
	}

	private record Fused(Self kind, long until, int[] charges) {
	}

	private record Upgraded(RegistryKey<World> world, BlockPos pos, Improve kind, UUID owner, long until) {
	}

	private static final Map<UUID, Fused> FUSED = new HashMap<>();
	private static final List<Upgraded> UPGRADED = new ArrayList<>();
	/** Hand-Auflegen (Block verbessern): naechster moeglicher Welt-Tick je Spieler */
	private static final Map<UUID, Long> TOUCH_COOLDOWN = new HashMap<>();
	/** hoechstens so viele verbesserte Bloecke gleichzeitig (je Server) */
	private static final int MAX_UPGRADED = 64;

	private UpgradeFusion() {
	}

	/** Ergebnis fuer die Kraft: nicht zustaendig (alte Techno-Fusion mit Werkzeug/Ruestung) oder erledigt. */
	enum Outcome {NOT_HANDLED, SUCCESS, FAILED}

	static Outcome use(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HitResult hit = player.raycast(REACH, 1.0f, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			Optional<Self> self = self(state);
			Optional<Improve> improve = improve(state);
			if (self.isEmpty()) {
				// duenne Bloecke (Blitzableiter): Blick knapp daneben auf den Boden zaehlt fuer den Block davor
				BlockPos front = pos.offset(blockHit.getSide());
				if (self(world.getBlockState(front)).isPresent()) {
					pos = front;
					state = world.getBlockState(front);
					self = self(state);
					improve = improve(state);
				}
			}
			if (self.isPresent()) {
				fuse(world, player, pos, state, self.get());
				return Outcome.SUCCESS;
			}
			if (improve.isPresent()) {
				return improve(world, player, pos, state, improve.get()) ? Outcome.SUCCESS : Outcome.FAILED;
			}
		}
		Fused fused = FUSED.get(player.getUuid());
		if (fused != null && fused.charges()[0] > 0) {
			fire(world, player, fused);
			return Outcome.SUCCESS;
		}
		return Outcome.NOT_HANDLED;
	}

	static Optional<Self> self(BlockState state) {
		for (Self kind : Self.values()) {
			if (state.isIn(kind.tag)) {
				return Optional.of(kind);
			}
		}
		return Optional.empty();
	}

	static Optional<Improve> improve(BlockState state) {
		if (Oxidizable.getDecreasedOxidationBlock(state.getBlock()).isPresent()) {
			return Optional.of(Improve.DERUST);
		}
		for (Improve kind : Improve.values()) {
			if (kind != Improve.DERUST && state.isIn(kind.tag)) {
				return Optional.of(kind);
			}
		}
		return Optional.empty();
	}

	static Optional<Self> active(ServerPlayerEntity player) {
		Fused fused = FUSED.get(player.getUuid());
		return fused == null ? Optional.empty() : Optional.of(fused.kind());
	}

	// --- Selbst-Fusion -----------------------------------------------------------------------------

	private static void fuse(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, Self kind) {
		long now = world.getTime();
		FUSED.put(player.getUuid(), new Fused(kind, now + SELF_TICKS, new int[] {kind.charges}));
		if (kind.consumes) {
			world.removeBlock(pos, false);
		}
		circuit(world, pos, kind.color);
		Vec3d body = new Vec3d(player.getX(), player.getBodyY(0.5), player.getZ());
		Fx.beam(world, Vec3d.ofCenter(pos), body, NEON, 0.15f, 8);
		Fx.sparks(world, body, kind.color, 0.35f, 24);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.9f, 1.7f);
		world.playSound(null, pos, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, SoundCategory.PLAYERS, 1.0f, 0.6f);
		switch (kind) {
			case LIGHTNING -> AlienModes.set(player, UpgradeAbilities.ROD_MODE, SELF_TICKS);
			case SCANNER -> {
				for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(48.0), e -> e != player && e.isAlive())) {
					near.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 300, 0, false, false), player);
				}
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, SELF_TICKS, 0, false, false));
			}
			case OVERLOAD -> {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1, false, true));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 400, 1, false, true));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 400, 0, false, true));
				UpgradeAbilities.addSync(player, 20.0f);
			}
			case COOLANT -> {
				OmnitrixCore.setHeat(player, 0.0f);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, SELF_TICKS, 0, false, true));
			}
			case PLATING -> {
				int level = state.isOf(Blocks.NETHERITE_BLOCK) ? 2 : state.isOf(Blocks.DIAMOND_BLOCK) ? 1 : 0;
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, SELF_TICKS, level, false, true));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, SELF_TICKS, level + 1, false, true));
				if (level == 2) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, SELF_TICKS, 0, false, true));
				}
			}
			case BEACON -> {
				for (ServerPlayerEntity ally : world.getEntitiesByClass(ServerPlayerEntity.class, player.getBoundingBox().expand(12.0),
						p -> p == player || !PartyRules.canHarm(player, p))) {
					ally.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, SELF_TICKS, 1, false, true));
					ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, SELF_TICKS, 0, false, true));
					ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, SELF_TICKS, 1, false, true));
				}
			}
			case HEAT -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, SELF_TICKS, 0, false, true));
			default -> {
			}
		}
		UpgradeAbilities.addSync(player, 10.0f);
		message(player, kind, kind.charges);
	}

	private static void message(ServerPlayerEntity player, Self kind, int charges) {
		Text name = Text.translatable(kind.name);
		player.sendMessage((kind.charges > 0
				? Text.translatable("message.kingdomomnitrix.fusion_charges", name, charges)
				: Text.translatable("message.kingdomomnitrix.fusion_self", name)).formatted(Formatting.GREEN), true);
	}

	/** Eine Ladung der Selbst-Fusion abfeuern (Blick-Richtung). */
	private static void fire(ServerWorld world, ServerPlayerEntity player, Fused fused) {
		fused.charges()[0]--;
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		switch (fused.kind()) {
			case LIGHTNING -> {
				Vec3d at = aim(world, player, 48.0);
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
				if (bolt != null) {
					bolt.refreshPositionAfterTeleport(at);
					bolt.setChanneler(player);
					world.spawnEntity(bolt);
				}
				Fx.beam(world, eye.add(look), at, 0xB8E4FF, 0.12f, 6);
			}
			case TURRET -> {
				for (int i = 0; i < 3; i++) {
					ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), null);
					arrow.setVelocity(player, player.getPitch(), player.getYaw() + (i - 1) * 4.0f, 0.0f, 3.2f, 0.5f);
					arrow.setDamage(4.0);
					arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
					world.spawnEntity(arrow);
				}
				sound(world, player, SoundEvents.BLOCK_DISPENSER_LAUNCH, 1.2f);
			}
			case EXPLOSIVE -> {
				TntEntity tnt = new TntEntity(world, eye.x + look.x, eye.y + look.y, eye.z + look.z, player);
				tnt.setFuse(30);
				tnt.setVelocity(look.multiply(1.3).add(0.0, 0.2, 0.0));
				world.spawnEntity(tnt);
				sound(world, player, SoundEvents.ENTITY_TNT_PRIMED, 1.0f);
			}
			case HEAT -> {
				SmallFireballEntity fireball = new SmallFireballEntity(world, player, look.multiply(1.6));
				fireball.setPosition(eye.add(look));
				world.spawnEntity(fireball);
				sound(world, player, SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f);
			}
			case SONIC -> sonicBoom(world, player, eye, look);
			default -> {
			}
		}
		message(player, fused.kind(), fused.charges()[0]);
	}

	private static Vec3d aim(ServerWorld world, ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(range));
		BlockHitResult block = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
		EntityHitResult entity = net.minecraft.entity.projectile.ProjectileUtil.raycast(player, eye, stop,
				new Box(eye, stop).expand(1.0), e -> e instanceof LivingEntity && e.isAlive() && PartyRules.canHarm(player, e), range * range);
		return entity != null ? entity.getEntity().getPos() : stop;
	}

	/** Schallstoss wie AEs Schallklatschen / der Waechter: durchschlaegt Ruestung, schleudert alles in der Linie weg. */
	private static void sonicBoom(ServerWorld world, ServerPlayerEntity player, Vec3d eye, Vec3d look) {
		for (int i = 1; i <= 15; i++) {
			Vec3d p = eye.add(look.multiply(i));
			world.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		Box box = new Box(eye, eye.add(look.multiply(15.0))).expand(1.5);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			Vec3d to = target.getPos().add(0, target.getHeight() / 2.0, 0).subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0 || to.subtract(look.multiply(along)).length() > 1.6) {
				continue;
			}
			target.damage(world.getDamageSources().sonicBoom(player), 10.0f * UpgradeAbilities.power(player));
			target.addVelocity(look.x * 1.8, 0.4, look.z * 1.8);
			target.velocityModified = true;
		}
		sound(world, player, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.0f);
	}

	/** Nahkampf waehrend einer Selbst-Fusion. */
	static void onMelee(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
		Fused fused = FUSED.get(player.getUuid());
		if (fused == null) {
			return;
		}
		switch (fused.kind()) {
			case LIGHTNING -> {
				target.timeUntilRegen = 0;
				target.damage(world.getDamageSources().lightningBolt(), 3.0f);
				Fx.sparks(world, target.getPos().add(0, target.getHeight() / 2.0, 0), 0xB8E4FF, 0.4f, 10);
			}
			case HEAT -> target.setOnFireFor(4.0f);
			case COOLANT -> {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), player);
				target.setFrozenTicks(Math.min(target.getMinFreezeDamageTicks() + 40, target.getFrozenTicks() + 80));
			}
			default -> {
			}
		}
	}

	// --- Bloecke verbessern ------------------------------------------------------------------------

	/** Schleichen + Rechtsklick mit leerer Hand: Upgrade legt die Hand auf den Block und verbessert ihn. */
	static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient() || hand != Hand.MAIN_HAND || !player.isSneaking() || !player.getMainHandStack().isEmpty()
					|| !(player instanceof ServerPlayerEntity server) || !UpgradeAbilities.isUpgradePlayer(server)) {
				return ActionResult.PASS;
			}
			ServerWorld serverWorld = (ServerWorld) world;
			BlockState state = world.getBlockState(hit.getBlockPos());
			Optional<Improve> kind = improve(state);
			if (kind.isEmpty()) {
				return ActionResult.PASS;
			}
			long now = serverWorld.getTime();
			if (TOUCH_COOLDOWN.getOrDefault(server.getUuid(), 0L) > now) {
				return ActionResult.FAIL;
			}
			TOUCH_COOLDOWN.put(server.getUuid(), now + 40);
			server.swingHand(Hand.MAIN_HAND, true);
			return improve(serverWorld, server, hit.getBlockPos(), state, kind.get()) ? ActionResult.SUCCESS : ActionResult.FAIL;
		});
	}

	private static boolean improve(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, Improve kind) {
		switch (kind) {
			case RESTORE -> {
				if (!(state.getBlock() instanceof AnvilBlock) || state.isOf(Blocks.ANVIL)) {
					return false;
				}
				world.setBlockState(pos, Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, state.get(AnvilBlock.FACING)));
			}
			case DERUST -> {
				BlockState fresh = Oxidizable.getUnaffectedOxidationState(state);
				if (fresh == state) {
					return false;
				}
				world.setBlockState(pos, fresh);
			}
			default -> {
				UPGRADED.removeIf(u -> u.pos().equals(pos) && u.world().equals(world.getRegistryKey()));
				if (UPGRADED.size() >= MAX_UPGRADED) {
					UPGRADED.remove(0);
				}
				UPGRADED.add(new Upgraded(world.getRegistryKey(), pos.toImmutable(), kind, player.getUuid(), world.getTime() + kind.ticks));
			}
		}
		circuit(world, pos, NEON);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.8f, 1.8f);
		world.playSound(null, pos, SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.BLOCKS, 0.8f, 1.2f);
		UpgradeAbilities.addSync(player, 5.0f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion_block", Text.translatable(kind.name), state.getBlock().getName())
				.formatted(Formatting.GREEN), true);
		return true;
	}

	/** Gruene Leiterbahnen ueberziehen den Block. */
	private static void circuit(ServerWorld world, BlockPos pos, int color) {
		Vec3d c = Vec3d.ofCenter(pos);
		for (Direction d : Direction.values()) {
			Fx.sparks(world, c.add(d.getOffsetX() * 0.52, d.getOffsetY() * 0.52, d.getOffsetZ() * 0.52), color, 0.08f, 4);
		}
		Fx.sparks(world, c, NEON, 0.2f, 10);
	}

	// --- Tick --------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		if (!FUSED.isEmpty()) {
			Iterator<Map.Entry<UUID, Fused>> it = FUSED.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Fused> entry = it.next();
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				Fused fused = entry.getValue();
				if (player == null || !UpgradeAbilities.isUpgradePlayer(player)) {
					it.remove();
					if (player != null && fused.kind() == Self.LIGHTNING) {
						leaveRod(player.getServerWorld(), player);
					}
					continue;
				}
				ServerWorld world = player.getServerWorld();
				long now = world.getTime();
				boolean spent = fused.kind().charges > 0 && fused.charges()[0] <= 0;
				// Blitzableiter-Form endet auch, wenn eine andere Kraft den Modus wechselt (Waffen, Pfuetze)
				boolean leftRod = fused.kind() == Self.LIGHTNING && !UpgradeAbilities.ROD_MODE.equals(AlienModes.id(player));
				if (now >= fused.until() || spent || leftRod) {
					it.remove();
					if (fused.kind() == Self.LIGHTNING) {
						leaveRod(world, player);
					}
					player.sendMessage(Text.translatable("message.kingdomomnitrix.fusion_end", Text.translatable(fused.kind().name))
							.formatted(Formatting.GRAY), true);
					continue;
				}
				if (now % 10 == 0) {
					Fx.sparks(world, new Vec3d(player.getX(), player.getBodyY(0.6), player.getZ()), fused.kind().color, 0.15f, 3);
				}
				if (fused.kind() == Self.MAGNET) {
					magnet(world, player);
				}
			}
		}
		if (!UPGRADED.isEmpty()) {
			tickUpgraded(server);
		}
	}

	/** Blitzableiter-Form verlassen: Upgrade loest sich, der Blitzableiter steht wieder da (wie AE), sonst als Gegenstand. */
	private static void leaveRod(ServerWorld world, ServerPlayerEntity player) {
		if (UpgradeAbilities.ROD_MODE.equals(AlienModes.id(player))) {
			AlienModes.clear(player);
		}
		BlockPos pos = player.getBlockPos();
		if (world.getBlockState(pos).isReplaceable()) {
			world.setBlockState(pos, Blocks.LIGHTNING_ROD.getDefaultState());
		} else {
			player.getInventory().offerOrDrop(new ItemStack(Items.LIGHTNING_ROD));
		}
		Fx.sparks(world, Vec3d.ofCenter(pos), 0xB8E4FF, 0.3f, 16);
		world.playSound(null, pos, SoundEvents.BLOCK_COPPER_PLACE, SoundCategory.PLAYERS, 1.0f, 0.8f);
	}

	private static void magnet(ServerWorld world, ServerPlayerEntity player) {
		Vec3d to = new Vec3d(player.getX(), player.getBodyY(0.5), player.getZ());
		Box box = player.getBoundingBox().expand(12.0);
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, e -> e.isAlive())) {
			item.setVelocity(to.subtract(item.getPos()).normalize().multiply(0.45));
			item.velocityModified = true;
		}
		for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, box, e -> e.isAlive())) {
			orb.setVelocity(to.subtract(orb.getPos()).normalize().multiply(0.45));
			orb.velocityModified = true;
		}
	}

	private static void tickUpgraded(MinecraftServer server) {
		Iterator<Upgraded> it = UPGRADED.iterator();
		while (it.hasNext()) {
			Upgraded u = it.next();
			ServerWorld world = server.getWorld(u.world());
			if (world == null || !world.isChunkLoaded(u.pos().getX() >> 4, u.pos().getZ() >> 4)) {
				it.remove();
				continue;
			}
			long now = world.getTime();
			BlockState state = world.getBlockState(u.pos());
			if (now >= u.until() || !state.isIn(u.kind().tag)) {
				it.remove();
				continue;
			}
			if (now % 20 == 0) {
				Fx.sparks(world, Vec3d.ofCenter(u.pos()).add(0, 0.55, 0), NEON, 0.1f, 3);
			}
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(u.owner());
			switch (u.kind()) {
				case OVERCLOCK -> overclock(world, u.pos(), state);
				case SENTRY -> {
					if (now % 15 == 0) {
						sentry(world, u.pos(), state, owner);
					}
				}
				case STORM_TRAP -> {
					if (now % 40 == 0) {
						stormTrap(world, u.pos(), owner);
					}
				}
				default -> {
				}
			}
		}
	}

	/** Maschine laeuft vierfach: drei zusaetzliche Durchlaeufe ihres eigenen Block-Tickers je Tick. */
	@SuppressWarnings("unchecked")
	private static <T extends BlockEntity> void overclock(ServerWorld world, BlockPos pos, BlockState state) {
		BlockEntity entity = world.getBlockEntity(pos);
		if (entity == null) {
			return;
		}
		BlockEntityTicker<T> ticker = state.getBlockEntityTicker(world, (net.minecraft.block.entity.BlockEntityType<T>) entity.getType());
		if (ticker == null) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			ticker.tick(world, pos, world.getBlockState(pos), (T) entity);
		}
	}

	/** Wachgeschuetz: schiesst auf den naechsten Feind in 16 Bloecken, aus der Werfer-Oeffnung. */
	private static void sentry(ServerWorld world, BlockPos pos, BlockState state, ServerPlayerEntity owner) {
		Direction facing = state.contains(DispenserBlock.FACING) ? state.get(DispenserBlock.FACING) : Direction.NORTH;
		Vec3d muzzle = Vec3d.ofCenter(pos).add(facing.getOffsetX() * 0.7, facing.getOffsetY() * 0.7, facing.getOffsetZ() * 0.7);
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(16.0),
				e -> e instanceof Monster && e.isAlive() && (owner == null || PartyRules.canHarm(owner, e)))) {
			double d = e.squaredDistanceTo(muzzle);
			Vec3d eyes = e.getEyePos();
			boolean visible = world.raycast(new RaycastContext(muzzle, eyes, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
					net.minecraft.block.ShapeContext.absent())).getType() == HitResult.Type.MISS;
			if (visible && d < best) {
				best = d;
				target = e;
			}
		}
		if (target == null) {
			return;
		}
		ArrowEntity arrow = owner != null ? new ArrowEntity(world, owner, new ItemStack(Items.ARROW), null)
				: new ArrowEntity(world, muzzle.x, muzzle.y, muzzle.z, new ItemStack(Items.ARROW), null);
		arrow.setPosition(muzzle);
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.6, 0).subtract(muzzle);
		arrow.setVelocity(to.x, to.y + to.horizontalLength() * 0.08, to.z, 2.6f, 0.5f);
		arrow.setDamage(3.0);
		arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		world.spawnEntity(arrow);
		world.playSound(null, pos, SoundEvents.BLOCK_DISPENSER_LAUNCH, SoundCategory.BLOCKS, 0.8f, 1.3f);
	}

	/** Blitzfalle: alle 2 s trifft ein Blitz einen Feind in 10 Bloecken um den Blitzableiter. */
	private static void stormTrap(ServerWorld world, BlockPos pos, ServerPlayerEntity owner) {
		List<LivingEntity> foes = world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(10.0),
				e -> e instanceof Monster && e.isAlive() && (owner == null || PartyRules.canHarm(owner, e)));
		if (foes.isEmpty()) {
			return;
		}
		LivingEntity target = foes.get(world.getRandom().nextInt(foes.size()));
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(target.getPos());
			if (owner != null) {
				bolt.setChanneler(owner);
			}
			world.spawnEntity(bolt);
		}
		Fx.beam(world, Vec3d.ofCenter(pos).add(0, 0.6, 0), target.getPos().add(0, 1, 0), 0xB8E4FF, 0.1f, 6);
	}

	private static void sound(ServerWorld world, ServerPlayerEntity player, SoundEvent sound, float pitch) {
		world.playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, 1.0f, pitch);
	}

	static void forget(UUID player) {
		FUSED.remove(player);
		TOUCH_COOLDOWN.remove(player);
	}
}
