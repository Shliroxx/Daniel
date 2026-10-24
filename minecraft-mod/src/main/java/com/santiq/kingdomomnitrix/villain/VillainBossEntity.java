package com.santiq.kingdomomnitrix.villain;

import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.world.TraverseTown;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Schurke mit Bossleiste: sammelt die Kaempfer im Umkreis, skaliert Leben und Schaden mit der Gruppengroesse
 * (wie {@link PartyRules}), wechselt bei {@link #phaseFractions()} die Phase, spricht Saetze im Chat und belohnt beim
 * Sieg jeden Beteiligten (beim ersten Sieg mehr, gemerkt ueber eine Helden-Flagge).
 */
public abstract class VillainBossEntity extends VillainEntity {
	private static final double FIGHT_RANGE = 48.0;

	protected final ServerBossBar bossBar;
	protected final Set<UUID> fighters = new HashSet<>();
	protected int phase = 1;
	protected float damageFactor = 1.0f;
	private boolean scaled;
	private int lonelyTicks;

	/** Belohnung je Kaempfer; Gegenstaende sind Kopien. */
	public record Reward(int bolts, int experience, List<ItemStack> items) {
	}

	protected VillainBossEntity(EntityType<? extends HostileEntity> type, World world, BossBar.Color color) {
		super(type, world);
		this.bossBar = new ServerBossBar(getDisplayName(), color, BossBar.Style.NOTCHED_10);
		setPersistent();
	}

	/** Grundleben (ohne Gruppenzuschlag). */
	protected abstract double baseHealth();

	/** Lebensanteile, ab denen Phase 2, 3 … beginnt (absteigend). */
	protected abstract float[] phaseFractions();

	protected abstract String defeatedFlag();

	protected abstract Reward reward(boolean first);

	/** Bei Phasenwechsel (Phase ist schon erhoeht). */
	protected abstract void onPhase(ServerWorld world, int phase);

	/** KI des Kampfes, nur solange Kaempfer da sind. */
	protected abstract void fightTick(ServerWorld world, @Nullable PlayerEntity target);

	/** Kurzname fuer den Chat, z. B. „Vilgax:“. */
	protected abstract Text speaker();

	@Override
	protected void mobTick() {
		super.mobTick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		updateFighters(world);
		if (!scaled && age > 5) {
			scaleToGroup();
		}
		if (fighters.isEmpty()) {
			if (++lonelyTicks > 600) {
				discard(); // niemand kaempft mehr: der Schurke zieht ab
			}
			return;
		}
		lonelyTicks = 0;
		checkPhase(world);
		PlayerEntity target = nearestFighter(world);
		if (target != null) {
			setTarget(target);
		}
		fightTick(world, target);
		bossBar.setPercent(getHealth() / getMaxHealth());
	}

	protected float scaled(float damage) {
		return damage * damageFactor;
	}

	private void updateFighters(ServerWorld world) {
		fighters.clear();
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean near = player.isAlive() && !player.isSpectator() && player.squaredDistanceTo(this) < FIGHT_RANGE * FIGHT_RANGE;
			if (near) {
				fighters.add(player.getUuid());
				bossBar.addPlayer(player);
			} else {
				bossBar.removePlayer(player);
			}
		}
	}

	/** Mehr Spieler → mehr Leben und Schaden, einmal beim Start. */
	private void scaleToGroup() {
		scaled = true;
		int extra = Math.max(0, fighters.size() - 1);
		EntityAttributeInstance health = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(baseHealth() * (1.0 + PartyRules.HEALTH_PER_MEMBER * extra));
			setHealth(getMaxHealth());
		}
		damageFactor = 1.0f + (float) PartyRules.DAMAGE_PER_MEMBER * extra;
	}

	@Nullable
	protected PlayerEntity nearestFighter(ServerWorld world) {
		PlayerEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (UUID uuid : fighters) {
			PlayerEntity player = world.getPlayerByUuid(uuid);
			if (player != null && !player.isCreative()) {
				double distance = player.squaredDistanceTo(this);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = player;
				}
			}
		}
		return best;
	}

	private void checkPhase(ServerWorld world) {
		float fraction = getHealth() / getMaxHealth();
		float[] thresholds = phaseFractions();
		int next = 1;
		for (float threshold : thresholds) {
			if (fraction <= threshold) {
				next++;
			}
		}
		if (next > phase) {
			phase = next;
			onPhase(world, phase);
		}
	}

	/** Satz im Chat an alle Kaempfer (Schluessel vollstaendig angeben). */
	protected void say(String key) {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		Text line = speaker().copy().formatted(Formatting.RED, Formatting.BOLD).append(Text.literal(" "))
				.append(Text.translatable(key).formatted(Formatting.WHITE));
		for (UUID uuid : fighters) {
			PlayerEntity player = world.getPlayerByUuid(uuid);
			if (player != null) {
				player.sendMessage(line, false);
			}
		}
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		updateFighters(world);
		if (damageSource.isOf(DamageTypes.GENERIC_KILL) || damageSource.isOf(DamageTypes.OUT_OF_WORLD)) {
			bossBar.clearPlayers(); // per Befehl entfernt: keine Belohnung
			return;
		}
		for (UUID uuid : fighters) {
			if (world.getPlayerByUuid(uuid) instanceof ServerPlayerEntity player) {
				boolean first = !HeroDataAccess.get(player).hasFlag(defeatedFlag());
				Reward reward = reward(first);
				HeroDataAccess.update(player, data -> data.addBolts(reward.bolts()).withFlag(defeatedFlag(), true));
				HeroDataAccess.grantExperience(player, reward.experience());
				for (ItemStack stack : reward.items()) {
					player.getInventory().offerOrDrop(stack.copy());
				}
				player.sendMessage(Text.translatable(first ? "message.kingdomomnitrix.boss_first_win" : "message.kingdomomnitrix.boss_win",
						getDisplayName(), reward.bolts(), reward.experience()).formatted(Formatting.GOLD), false);
			}
		}
		bossBar.clearPlayers();
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		bossBar.clearPlayers();
	}

	/** Darf in der Stadt Traverse Town erscheinen (sonst entfernt der Stadtschutz Monster). */
	public void allowInTown() {
		addCommandTag(TraverseTown.ALLOWED_TAG);
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return true;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Phase", phase);
		nbt.putBoolean("Scaled", scaled);
		nbt.putFloat("DamageFactor", damageFactor);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		phase = Math.max(1, nbt.getInt("Phase"));
		scaled = nbt.getBoolean("Scaled");
		damageFactor = nbt.contains("DamageFactor") ? nbt.getFloat("DamageFactor") : 1.0f;
		if (hasCustomName()) {
			bossBar.setName(getDisplayName());
		}
	}
}
