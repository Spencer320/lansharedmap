package dev.lansharedmap.client.map;

public final class Waypoint {
	private String name;
	private String dimension;
	private int x;
	private int z;

	public Waypoint() {
	}

	public Waypoint(String name, String dimension, int x, int z) {
		this.name = name;
		this.dimension = dimension;
		this.x = x;
		this.z = z;
	}

	public String name() {
		return this.name;
	}

	public String dimension() {
		return this.dimension;
	}

	public int x() {
		return this.x;
	}

	public int z() {
		return this.z;
	}
}
