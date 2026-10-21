package com.santiq.kingdomomnitrix.omnitrix.workbench;

import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.world.content.Ben10Items;
import java.util.Optional;
import net.minecraft.item.ItemStack;

/**
 * Was die Omnitrix-Werkbank aus ihren drei Plaetzen macht (reine Logik, ohne Welt):
 *
 * <ul>
 *   <li><b>Zusammenbau</b>: Huelle + Konsole + Energiekern → Omnitrix; Basis-Kern = Klassisch Gruen, farbiger Kern = seine
 *       Farbe.</li>
 *   <li><b>Umfaerben</b>: fertiges Omnitrix im Huellen-Platz + farbiger Kern (Konsolen-Platz leer) → dasselbe Omnitrix
 *       in der neuen Farbe.</li>
 * </ul>
 */
public final class WorkbenchRecipe {
	public static final int SHELL = 0;
	public static final int CONSOLE = 1;
	public static final int CORE = 2;
	public static final int OUTPUT = 3;
	public static final int SIZE = 4;
	/** Dauer der Zusammenbau-Animation und des Umfaerbens (Ticks) */
	public static final int ASSEMBLE_TICKS = 120;
	public static final int RECOLOR_TICKS = 50;
	/** Farbe eines Basis-Kerns */
	public static final String BASE_COLOR = "green";

	public enum Kind {
		ASSEMBLE, RECOLOR
	}

	/** Ergebnis eines gueltigen Belegens: Art, Dauer, fertiger Stapel. */
	public record Plan(Kind kind, int ticks, ItemStack result) {
	}

	private WorkbenchRecipe() {
	}

	/** Farbe eines Energiekerns (Basis-Kern: Gruen), leer fuer alles andere. */
	public static Optional<String> coreColor(ItemStack stack) {
		if (stack.isOf(Ben10Items.ENERGY_CORE)) {
			return Optional.of(BASE_COLOR);
		}
		return Optional.ofNullable(Ben10Items.CORE_COLORS.get(stack.getItem()));
	}

	public static boolean isCore(ItemStack stack) {
		return coreColor(stack).isPresent();
	}

	/** Gueltiger Plan fuer die Belegung, sonst leer. */
	public static Optional<Plan> plan(ItemStack shell, ItemStack console, ItemStack core) {
		Optional<String> color = coreColor(core);
		if (color.isEmpty()) {
			return Optional.empty();
		}
		if (shell.isOf(Ben10Items.OMNITRIX_SHELL) && console.isOf(Ben10Items.OMNITRIX_CONSOLE)) {
			ItemStack result = new ItemStack(ModItems.OMNITRIX);
			result.set(ModComponents.OMNITRIX_CORE, color.get());
			return Optional.of(new Plan(Kind.ASSEMBLE, ASSEMBLE_TICKS, result));
		}
		if (shell.isOf(ModItems.OMNITRIX) && console.isEmpty() && !core.isOf(Ben10Items.ENERGY_CORE)
				&& !color.get().equals(shell.get(ModComponents.OMNITRIX_CORE))) {
			ItemStack result = shell.copyWithCount(1);
			result.set(ModComponents.OMNITRIX_CORE, color.get());
			return Optional.of(new Plan(Kind.RECOLOR, RECOLOR_TICKS, result));
		}
		return Optional.empty();
	}

	/** Darf der Stapel in den Platz? (Ausgabe nimmt nichts an.) */
	public static boolean accepts(int slot, ItemStack stack) {
		return switch (slot) {
			case SHELL -> stack.isOf(Ben10Items.OMNITRIX_SHELL) || stack.isOf(ModItems.OMNITRIX);
			case CONSOLE -> stack.isOf(Ben10Items.OMNITRIX_CONSOLE);
			case CORE -> isCore(stack);
			default -> false;
		};
	}
}
