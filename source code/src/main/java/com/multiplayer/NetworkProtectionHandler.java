package com.multiplayer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = "multiplayer", value = Dist.CLIENT)
public class NetworkProtectionHandler {

    // АВТО-ВЕРСИЯ: Мод сам считывает самую последнюю версию из build.gradle / mods.toml!
    private static final String PROTOCOL_VERSION = ModList.get().getModContainerById("multiplayer")
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("LATEST");
    
    // Официальный сетевой канал Forge для проверки версии мода у заходящих игроков
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("multiplayer", "main_channel"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals, 
        PROTOCOL_VERSION::equals
    );

    // Метод регистрации
    public static void initNetwork() {
        INSTANCE.registerMessage(0, NetworkPacket.class, NetworkPacket::encode, NetworkPacket::decode, NetworkPacket::handle);
        MinecraftForge.EVENT_BUS.register(NetworkProtectionHandler.class);
    }

    // Проверка игрока при спавне на сервере
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {

        if (!com.multiplayer.ModConfig.ENABLE_MOD.get()) return;

        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            try {
                INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new NetworkPacket());
            } catch (Exception e) {
                // Выводим в ошибку самую актуальную версию, которая требуется для входа
                player.connection.disconnect(Component.literal(
                    "§c[MCMultiplayer] Mod Required!\n§7Please install MCMultiplayer v" + PROTOCOL_VERSION + " to join."
                ));
            }
        }
    }

    // Структура проверочного пакета мода
    public static class NetworkPacket {
        public NetworkPacket() {}
        public static void encode(NetworkPacket msg, net.minecraft.network.FriendlyByteBuf buf) {}
        public static NetworkPacket decode(net.minecraft.network.FriendlyByteBuf buf) { return new NetworkPacket(); }
        public static void handle(NetworkPacket msg, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
        }
    }
}