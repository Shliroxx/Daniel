package com.santiq.kingdomomnitrix.client.space;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Glattes Dreiecksnetz der Aphelion aus {@code assets/kingdomomnitrix/ship/aphelion.bin}, erzeugt von
 * {@code tools/generate_ship_mesh.py}. Koordinaten in Bloecken, +z = Nase, +x = linke Schiffsseite.
 * Wird bei jedem Ressourcen-Neuladen gelesen; ein defektes Netz wird protokolliert und nicht gezeichnet.
 */
public final class ShipMesh {
	public static final int SOLID = 0;
	public static final int GLASS = 1;
	public static final int GLOW = 2;

	private static final Identifier FILE = KingdomOmnitrix.id("ship/aphelion.bin");
	private static final int VERSION = 1;
	private static final int MAX_PARTS = 1024;
	private static final int MAX_VERTICES = 500_000;

	/** Ein Teil: Ecken (je 3 Position, 3 Normale, 2 UV) und Dreiecke (Eckindizes). */
	public record Part(String name, int layer, float[] positions, float[] normals, float[] uvs, int[] triangles) {
		public int triangleCount() {
			return triangles.length / 3;
		}
	}

	@Nullable
	private static ShipMesh current;

	private final List<Part> parts;

	private ShipMesh(List<Part> parts) {
		this.parts = List.copyOf(parts);
	}

	public List<Part> parts() {
		return parts;
	}

	public static Optional<ShipMesh> get() {
		return Optional.ofNullable(current);
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return KingdomOmnitrix.id("ship_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				current = load(manager);
			}
		});
	}

	@Nullable
	private static ShipMesh load(ResourceManager manager) {
		Optional<Resource> resource = manager.getResource(FILE);
		if (resource.isEmpty()) {
			KingdomOmnitrix.LOGGER.error("Schiffsnetz {} fehlt", FILE);
			return null;
		}
		try (InputStream stream = resource.get().getInputStream()) {
			ShipMesh mesh = read(stream);
			int triangles = mesh.parts.stream().mapToInt(Part::triangleCount).sum();
			KingdomOmnitrix.LOGGER.info("Schiffsnetz geladen: {} Teile, {} Dreiecke", mesh.parts.size(), triangles);
			return mesh;
		} catch (IOException | RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("Schiffsnetz {} konnte nicht gelesen werden", FILE, e);
			return null;
		}
	}

	static ShipMesh read(InputStream stream) throws IOException {
		DataInputStream in = new DataInputStream(stream);
		byte[] magic = new byte[4];
		in.readFully(magic);
		if (magic[0] != 'S' || magic[1] != 'H' || magic[2] != 'I' || magic[3] != 'P') {
			throw new IOException("kein Schiffsnetz (Kennung fehlt)");
		}
		int version = in.readInt();
		if (version != VERSION) {
			throw new IOException("Schiffsnetz Version " + version + " wird nicht unterstuetzt (erwartet " + VERSION + ")");
		}
		int count = in.readInt();
		if (count < 0 || count > MAX_PARTS) {
			throw new IOException("ungueltige Teilanzahl " + count);
		}
		List<Part> parts = new ArrayList<>(count);
		for (int p = 0; p < count; p++) {
			String name = in.readUTF();
			int layer = in.readByte();
			if (layer < SOLID || layer > GLOW) {
				throw new IOException("unbekannte Ebene " + layer + " in " + name);
			}
			int vertices = in.readInt();
			if (vertices < 0 || vertices > MAX_VERTICES) {
				throw new IOException("ungueltige Eckenzahl " + vertices + " in " + name);
			}
			float[] positions = new float[vertices * 3];
			float[] normals = new float[vertices * 3];
			float[] uvs = new float[vertices * 2];
			for (int v = 0; v < vertices; v++) {
				positions[v * 3] = in.readFloat();
				positions[v * 3 + 1] = in.readFloat();
				positions[v * 3 + 2] = in.readFloat();
				normals[v * 3] = in.readByte() / 127.0f;
				normals[v * 3 + 1] = in.readByte() / 127.0f;
				normals[v * 3 + 2] = in.readByte() / 127.0f;
				uvs[v * 2] = in.readFloat();
				uvs[v * 2 + 1] = in.readFloat();
			}
			int triangles = in.readInt();
			if (triangles < 0 || triangles > MAX_VERTICES * 2) {
				throw new IOException("ungueltige Dreieckszahl " + triangles + " in " + name);
			}
			int[] indices = new int[triangles * 3];
			for (int i = 0; i < indices.length; i++) {
				int index = in.readInt();
				if (index < 0 || index >= vertices) {
					throw new IOException("Eckindex " + index + " ausserhalb in " + name);
				}
				indices[i] = index;
			}
			parts.add(new Part(name, layer, positions, normals, uvs, indices));
		}
		return new ShipMesh(parts);
	}
}
