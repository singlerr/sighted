package com.kgb_8375.sighted.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfiguration {

    public final ForgeConfigSpec.BooleanValue   enabled;
    public final ForgeConfigSpec.BooleanValue   noBlockEntities;
    public final ForgeConfigSpec.IntValue       unloadDelaySecs;
    public final ForgeConfigSpec.IntValue       maxRenderDistance;

    protected ClientConfiguration(final ForgeConfigSpec.Builder builder) {
        builder.push("Sighted Configuration Settings");

        builder.comment("Mod enabled");
        enabled = builder.define("enabled", true);

        builder.comment("Do not load block entities (e.g. chests) in fake chunks.\\nThese need updating every tick which can add up.\\n\\nEnabled by default because the render distance for block entities is usually smaller than the server-view distance anyway.");
        noBlockEntities = builder.define("noBlockEntities", true);

        builder.comment("Delays the unloading of chunks which are outside your view distance.\nSaves you from having to reload all chunks when leaving the area for a short moment (e.g. cut scenes).\nDoes not work across dimensions.");
        unloadDelaySecs = builder.defineInRange("unloadDelaySecs", 60, 1, 3600);

        builder.comment("Changes the maximum value configurable for Render Distance.\n\n Requires Optifine");
        maxRenderDistance = builder.defineInRange("maxRenderDistance", 32, 6, 64);

        builder.pop();
    }
}
