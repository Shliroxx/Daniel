package com.santiq.kingdomomnitrix.client.render.toon;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Laedt alle Cartoon-Netze aus {@code assets/<ns>/toon/<name>.bin} bei jedem Ressourcen-Neuladen.
 * Schluessel ist das Alien-Modell, z. B. {@code kingdomomnitrix:heatblast}. Defekte Dateien werden protokolliert und
 * uebersprungen — das Alien faellt dann auf den Wuerfel-Koerper zurueck.
 */
public final class ToonMeshes {
	private static final String FOLDER = "toon";
	private static Map<Identifier, ToonMesh> meshes = Map.of();

	private ToonMeshes() {
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return KingdomOmnitrix.id("toon_meshes");
			}

			@Override
			public void reload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		Map<Identifier, ToonMesh> loaded = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry : manager.findResources(FOLDER, id -> id.getPath().endsWith(".bin")).entrySet()) {
			Identifier file = entry.getKey();
			String name = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".bin".length());
			Identifier model = Identifier.of(file.getNamespace(), name);
			try (InputStream stream = entry.getValue().getInputStream()) {
				ToonMesh mesh = ToonMesh.read(stream);
				loaded.put(model, mesh);
				KingdomOmnitrix.LOGGER.info("Cartoon-Netz {} geladen ({} Dreiecke)", model, mesh.triangles());
			} catch (IOException | RuntimeException e) {
				KingdomOmnitrix.LOGGER.error("Cartoon-Netz {} konnte nicht geladen werden", file, e);
			}
		}
		meshes = Map.copyOf(loaded);
	}

	public static ToonMesh get(Identifier model) {
		return meshes.get(model);
	}
}
