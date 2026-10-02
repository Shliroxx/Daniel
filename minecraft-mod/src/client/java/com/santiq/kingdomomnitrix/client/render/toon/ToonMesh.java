package com.santiq.kingdomomnitrix.client.render.toon;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Cartoon-Netz eines Aliens: Dreiecke je Knochen, Koordinaten wie die Wuerfel der .geo.json (1/16 Block, vorne = -z,
 * rechte Koerperseite = -x), Farbe und Leuchten pro Dreieck. Erzeugt von {@code tools/generate_toon_meshes.py}.
 */
public final class ToonMesh {
	private static final int VERSION = 1;

	/** Dreiecke eines Knochens; Arrays je Dreieck: 9 Positionen, 9 Normalen, 1 Farbe (RGB), 1 Leucht-Flag. */
	public record Part(int count, float[] positions, float[] normals, int[] colors, boolean[] glow) {
	}

	private final Map<String, Part> parts;
	private final int triangles;

	private ToonMesh(Map<String, Part> parts, int triangles) {
		this.parts = parts;
		this.triangles = triangles;
	}

	public Part part(String bone) {
		return parts.get(bone);
	}

	public int triangles() {
		return triangles;
	}

	public static ToonMesh read(InputStream stream) throws IOException {
		DataInputStream in = new DataInputStream(stream);
		byte[] magic = new byte[4];
		in.readFully(magic);
		if (magic[0] != 'T' || magic[1] != 'O' || magic[2] != 'O' || magic[3] != 'N') {
			throw new IOException("kein Cartoon-Netz (Kennung fehlt)");
		}
		int version = in.readInt();
		if (version != VERSION) {
			throw new IOException("Cartoon-Netz Version " + version + " wird nicht unterstuetzt (erwartet " + VERSION + ")");
		}
		int partCount = in.readInt();
		if (partCount < 0 || partCount > 512) {
			throw new IOException("ungueltige Teilanzahl " + partCount);
		}
		Map<String, Part> parts = new HashMap<>();
		int total = 0;
		for (int p = 0; p < partCount; p++) {
			String bone = in.readUTF();
			int count = in.readInt();
			if (count < 0 || count > 200_000) {
				throw new IOException("ungueltige Dreieckszahl " + count + " fuer " + bone);
			}
			float[] positions = new float[count * 9];
			float[] normals = new float[count * 9];
			int[] colors = new int[count];
			boolean[] glow = new boolean[count];
			for (int t = 0; t < count; t++) {
				for (int i = 0; i < 9; i++) {
					positions[t * 9 + i] = in.readFloat();
				}
				for (int i = 0; i < 9; i++) {
					normals[t * 9 + i] = in.readByte() / 127.0f;
				}
				colors[t] = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
				glow[t] = (in.readUnsignedByte() & 1) != 0;
			}
			parts.put(bone, new Part(count, positions, normals, colors, glow));
			total += count;
		}
		return new ToonMesh(parts, total);
	}
}
