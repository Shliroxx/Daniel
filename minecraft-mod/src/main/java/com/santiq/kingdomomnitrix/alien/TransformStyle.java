package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

/**
 * Verwandlungs-Signatur eines Aliens (Phase O, Datenfeld {@code traits.transform_style}): zusaetzlich zum gemeinsamen
 * Omnitrix-Blitz bekommt jede Verwandlung ein eigenes Bild und einen eigenen Klang — Heatblast entzuendet sich, XLR8
 * zieht Geschwindigkeitsschlieren, Vierarms Landung erzeugt eine Druckwelle, usw. Nur Vanilla-Partikel und -Klaenge
 * (keine Alien-Evolution-Sounds). Rein kosmetisch, fuer alle Spieler in Sichtweite.
 */
public enum TransformStyle implements StringIdentifiable {
	/** nur der gemeinsame Omnitrix-Blitz */
	STANDARD,
	/** Feuer: Flammenring am Boden, Funkenfontaene, Zuenden */
	FIRE,
	/** Tempo: waagerechte Schlieren, Windstoss */
	SPEED,
	/** Wucht: Druckwelle aus Bodenbrocken, dumpfer Aufprall */
	SLAM,
	/** Kristall: Splitter schiessen nach aussen, Klirren */
	CRYSTAL,
	/** Klein: Puff nach innen, heller Ton */
	SHRINK,
	/** Bestie: Erde und Laub fliegen, Knurren */
	BEAST,
	/** Insekt: gruene Sporenwolke, Fluegelschwirren */
	INSECT,
	/** Wasser: Fontaene und Blasen, Platschen */
	WATER,
	/** Technik: Funkenbogen und Schaltkreis-Glitzern, Summen */
	TECH,
	/** Geist: Seelen steigen auf, kalter Hauch */
	GHOST,
	/** Panzerkugel: Schalenplatten klappen als weiss-goldener Ring zu, Metallklappern, schwerer Aufsatz */
	ROLL,
	/** Duese: roter Strahlkegel nach unten, Windstoss, Neuroschock-Funken an den Augen */
	JET;

	public static final Codec<TransformStyle> CODEC = StringIdentifiable.createCodec(TransformStyle::values);

	@Override
	public String asString() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Signatur am Spieler abspielen (Server). */
	public void play(ServerWorld world, ServerPlayerEntity player) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		double mid = player.getBodyY(0.5);
		switch (this) {
			case STANDARD -> {
			}
			case FIRE -> {
				ring(world, ParticleTypes.FLAME, x, y + 0.1, z, 1.4, 28, 0.02);
				world.spawnParticles(ParticleTypes.LAVA, x, mid, z, 10, 0.3, 0.5, 0.3, 0.0);
				world.spawnParticles(ParticleTypes.FLAME, x, y + 0.2, z, 40, 0.15, 0.0, 0.15, 0.12);
				sound(world, player, SoundEvents.ITEM_FIRECHARGE_USE, 0.9f, 0.8f);
				sound(world, player, SoundEvents.ENTITY_BLAZE_SHOOT, 0.6f, 0.7f);
			}
			case SPEED -> {
				float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
				double bx = MathHelper.sin(yaw);
				double bz = -MathHelper.cos(yaw);
				for (int i = 0; i < 18; i++) {
					double h = y + 0.3 + (i % 6) * 0.3;
					double side = ((i / 6) - 1) * 0.35;
					world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x + bz * side, h, z - bx * side, 0, bx, 0.0, bz, 0.9);
				}
				world.spawnParticles(ParticleTypes.CLOUD, x, y + 0.1, z, 14, 0.4, 0.05, 0.4, 0.08);
				sound(world, player, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 0.8f, 1.5f);
			}
			case SLAM -> {
				ring(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(player.getBlockPos().down()).isAir()
						? Blocks.STONE.getDefaultState() : world.getBlockState(player.getBlockPos().down())), x, y + 0.1, z, 2.2, 36, 0.25);
				world.spawnParticles(ParticleTypes.EXPLOSION, x, y + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
				world.spawnParticles(ParticleTypes.POOF, x, y + 0.1, z, 20, 1.2, 0.05, 1.2, 0.02);
				sound(world, player, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
				sound(world, player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.35f, 1.4f);
			}
			case CRYSTAL -> {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState()),
						x, mid, z, 40, 0.5, 0.7, 0.5, 0.3);
				world.spawnParticles(ParticleTypes.END_ROD, x, mid, z, 16, 0.2, 0.4, 0.2, 0.12);
				sound(world, player, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.8f);
				sound(world, player, SoundEvents.BLOCK_GLASS_BREAK, 0.6f, 1.3f);
			}
			case SHRINK -> {
				world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, mid, z, 40, 0.6, 0.6, 0.6, 0.05);
				world.spawnParticles(ParticleTypes.POOF, x, y + 0.2, z, 8, 0.15, 0.1, 0.15, 0.01);
				sound(world, player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
			}
			case BEAST -> {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState()), x, y + 0.2, z, 30, 0.6, 0.1, 0.6, 0.2);
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.getDefaultState()), x, mid, z, 18, 0.5, 0.5, 0.5, 0.1);
				sound(world, player, SoundEvents.ENTITY_WOLF_GROWL, 1.0f, 0.6f);
			}
			case INSECT -> {
				world.spawnParticles(ParticleTypes.ITEM_SLIME, x, mid, z, 24, 0.5, 0.5, 0.5, 0.05);
				world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, mid, z, 30, 0.8, 0.6, 0.8, 0.0);
				sound(world, player, SoundEvents.ENTITY_BEE_LOOP_AGGRESSIVE, 1.0f, 0.7f);
				sound(world, player, SoundEvents.ENTITY_SLIME_SQUISH, 0.7f, 0.8f);
			}
			case WATER -> {
				world.spawnParticles(ParticleTypes.SPLASH, x, y + 0.3, z, 60, 0.5, 0.2, 0.5, 0.3);
				world.spawnParticles(ParticleTypes.BUBBLE_POP, x, mid, z, 30, 0.4, 0.6, 0.4, 0.05);
				ring(world, ParticleTypes.FALLING_WATER, x, y + 1.6, z, 1.0, 20, 0.0);
				sound(world, player, SoundEvents.ENTITY_GENERIC_SPLASH, 1.0f, 0.9f);
			}
			case TECH -> {
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, mid, z, 40, 0.5, 0.8, 0.5, 0.25);
				world.spawnParticles(ParticleTypes.WAX_ON, x, mid, z, 20, 0.4, 0.6, 0.4, 0.5);
				sound(world, player, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
				sound(world, player, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.4f, 2.0f);
			}
			case ROLL -> {
				net.minecraft.particle.DustParticleEffect shell = new net.minecraft.particle.DustParticleEffect(
						new org.joml.Vector3f(0.95f, 0.85f, 0.35f), 1.6f);
				net.minecraft.particle.DustParticleEffect plate = new net.minecraft.particle.DustParticleEffect(
						new org.joml.Vector3f(0.95f, 0.95f, 0.92f), 1.4f);
				// zwei gegenlaeufige Ringe um die Brust: die Schale schliesst sich
				ring(world, shell, x, mid, z, 1.1, 24, -0.08);
				ring(world, plate, x, mid + 0.4, z, 1.0, 20, -0.06);
				ring(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(player.getBlockPos().down()).isAir()
						? Blocks.STONE.getDefaultState() : world.getBlockState(player.getBlockPos().down())), x, y + 0.1, z, 1.6, 24, 0.18);
				world.spawnParticles(ParticleTypes.CRIT, x, mid, z, 20, 0.5, 0.5, 0.5, 0.2);
				sound(world, player, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.7f);
				sound(world, player, SoundEvents.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
				sound(world, player, SoundEvents.ENTITY_IRON_GOLEM_STEP, 1.0f, 0.6f);
			}
			case JET -> {
				net.minecraft.particle.DustParticleEffect red = new net.minecraft.particle.DustParticleEffect(
						new org.joml.Vector3f(0.95f, 0.2f, 0.18f), 1.5f);
				// Duesenkegel: Partikel schiessen unter dem Koerper nach unten weg
				for (int i = 0; i < 18; i++) {
					double angle = i * MathHelper.TAU / 18;
					world.spawnParticles(red, x + Math.cos(angle) * 0.35, y + 0.4, z + Math.sin(angle) * 0.35, 0,
							Math.cos(angle) * 0.3, -1.0, Math.sin(angle) * 0.3, 0.5);
				}
				ring(world, ParticleTypes.CLOUD, x, y + 0.1, z, 1.2, 20, 0.2);
				world.spawnParticles(ParticleTypes.GUST, x, mid, z, 2, 0.4, 0.3, 0.4, 0.0);
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, player.getEyeY(), z, 16, 0.25, 0.1, 0.25, 0.15);
				sound(world, player, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 1.0f, 0.9f);
				sound(world, player, SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 0.7f);
				sound(world, player, SoundEvents.ENTITY_GUARDIAN_ATTACK, 0.3f, 2.0f);
			}
			case GHOST -> {
				world.spawnParticles(ParticleTypes.SCULK_SOUL, x, y + 0.3, z, 14, 0.4, 0.3, 0.4, 0.04);
				world.spawnParticles(ParticleTypes.SMOKE, x, mid, z, 30, 0.4, 0.7, 0.4, 0.02);
				world.spawnParticles(ParticleTypes.SOUL, x, mid, z, 10, 0.5, 0.6, 0.5, 0.02);
				sound(world, player, SoundEvents.ENTITY_VEX_CHARGE, 1.0f, 0.5f);
				sound(world, player, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), 1.0f, 0.8f);
			}
		}
	}

	private static void ring(ServerWorld world, ParticleEffect particle, double x, double y, double z, double radius, int count,
			double outward) {
		for (int i = 0; i < count; i++) {
			double angle = i * MathHelper.TAU / count;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			world.spawnParticles(particle, x + dx * radius, y, z + dz * radius, 0, dx, 0.05, dz, outward);
		}
	}

	private static void sound(ServerWorld world, ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
