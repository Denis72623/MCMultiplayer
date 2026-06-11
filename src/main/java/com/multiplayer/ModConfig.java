package com.multiplayer;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec SPEC;
    
    public static final ForgeConfigSpec.BooleanValue ENABLE_MOD;
    public static final ForgeConfigSpec.BooleanValue ONLINE_MODE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTH;

    static {
        ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
        BUILDER.comment("MCMultiplayer Configuration Settings").push("general");
        
        ENABLE_MOD = BUILDER.comment("Enable or disable the main mod features").define("enableMod", true);
        ONLINE_MODE = BUILDER.comment("Enable online mode license verification check").define("onlineMode", false);
        ENABLE_AUTH = BUILDER.comment("Enable new server account for players and account security").define("enablePasswordSecurity", false);
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}