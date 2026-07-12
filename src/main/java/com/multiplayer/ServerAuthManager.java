package com.multiplayer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.*;
import java.util.*;

@Mod.EventBusSubscriber(modid = "multiplayer", value = Dist.CLIENT)
public class ServerAuthManager {

    private static final Set<UUID> authenticatedPlayers = new HashSet<>();
    private static final Map<UUID, Integer> authAttempts = new HashMap<>();
    private static final Map<String, String> userDatabase = new HashMap<>(); 
    private static final File databaseFile = new File("mcmultiplayer_auth.txt");

    public static void initAuth() {
        loadDatabase();
        MinecraftForge.EVENT_BUS.register(ServerAuthManager.class);
    }

    private static void loadDatabase() {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        if (!databaseFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(databaseFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    userDatabase.put(parts[0].toLowerCase(), parts[1]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveToDatabase(String username, String password) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        userDatabase.put(username.toLowerCase(), password);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(databaseFile, true))) {
            writer.write(username.toLowerCase() + ":" + password);
            writer.newLine();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            
            // НАСТОЯЩЕЕ УПРАВЛЕНИЕ: Защита сработает ТОЛЬКО если тумблер в меню стоит на ENABLED
            if (com.multiplayer.ModConfig.ENABLE_AUTH.get() && !authenticatedPlayers.contains(player.getUUID())) {
                
                // БЕСКОНЕЧНЫЙ ТЕЛЕПОРТ БЕЗ ПЕРЕМЕННЫХ: Привязываем к дефолтному спавну мира
                net.minecraft.core.BlockPos worldSpawn = player.level().getSharedSpawnPos();
                
                // Если игрок отошёл от центра спавна дальше чем на 1.5 блока — швыряем обратно!
                if (player.distanceToSqr(worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ()) > 2.25) {
                    player.teleportTo(worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5);
                }
                
                // Твои оригинальные английские сообщения раз в 3 секунды (60 тиков)
                if (player.tickCount % 60 == 0) {
                    String name = player.getGameProfile().getName().toLowerCase();
                    if (userDatabase.containsKey(name)) {
                        player.sendSystemMessage(Component.literal("§c[MCMultiplayer] This username is taken! Enter password: /login <password>"));
                    } else {
                        player.sendSystemMessage(Component.literal("§e[MCMultiplayer] Create an account: /register <password> <repeat_password>"));
                    }
                }
            }
        }
    }


    public static boolean handleAuthCommands(ServerPlayer player, String message) {
        String[] args = message.split(" ");
        if (args.length == 0) return false;
        
        String cmd = args[0].toLowerCase();
        String name = player.getGameProfile().getName().toLowerCase();
        UUID uuid = player.getUUID();

        if (authenticatedPlayers.contains(uuid)) return false;

        // --- ЛОГИКА РЕГИСТРАЦИИ (/register <пароль> <повтор>) ---
        if (cmd.equals("/register")) {
            if (userDatabase.containsKey(name)) {
                player.sendSystemMessage(Component.literal("§cError: Nickname already taken! Use /login"));
                return true;
            }
            if (args.length < 3) {
                player.sendSystemMessage(Component.literal("§cUsage: /register <password> <repeat_password>"));
                return true;
            }
            if (!args[1].equals(args[2])) {
                player.sendSystemMessage(Component.literal("§cError: Passwords do not match!"));
                return true;
            }
            
            saveToDatabase(name, args[1]);
            authenticatedPlayers.add(uuid);
            player.sendSystemMessage(Component.literal("§a[SUCCESS] Account created! Access granted."));
            return true;
        }

        // --- ЛОГИКА ВХОДА (/login <пароль>) ---
        if (cmd.equals("/login")) {
            if (!userDatabase.containsKey(name)) {
                player.sendSystemMessage(Component.literal("§cYou haven't created an account yet! Use /register"));
                return true;
            }
            if (args.length < 2) {
                player.sendSystemMessage(Component.literal("§cUsage: /login <password>"));
                return true;
            }
            
            String correctPassword = userDatabase.get(name);
            if (correctPassword.equals(args[1])) {
                authenticatedPlayers.add(uuid);
                authAttempts.remove(uuid);
                player.sendSystemMessage(Component.literal("§a[SUCCESS] The password is correct! Enjoy the game."));
            } else {
                int attempts = authAttempts.getOrDefault(uuid, 0) + 1;
                authAttempts.put(uuid, attempts);
                if (attempts >= 3) {
                    player.connection.disconnect(Component.literal("§cYou have been kicked for 3 incorrect password entries!"));
                } else {
                    player.sendSystemMessage(Component.literal("§cIncorrect password! Remaining attempts: " + (3 - attempts)));
                }
            }
            return true;
        }

        return false;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        // Очищаем сессию при выходе. Если зайти снова — мод опять строго спросит пароль!
        authenticatedPlayers.remove(event.getEntity().getUUID());
        authAttempts.remove(event.getEntity().getUUID());
    }
}