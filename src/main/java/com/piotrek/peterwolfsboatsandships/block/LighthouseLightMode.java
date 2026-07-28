package com.piotrek.peterwolfsboatsandships.block;

import net.minecraft.util.StringRepresentable;

/** Right-click cycles: spot ray → blinking point light → off → spot ray… */
public enum LighthouseLightMode implements StringRepresentable {
	SPOT("spot"),
	FLASH("flash"),
	OFF("off");

	private final String name;

	LighthouseLightMode(final String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public LighthouseLightMode next() {
		LighthouseLightMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}
