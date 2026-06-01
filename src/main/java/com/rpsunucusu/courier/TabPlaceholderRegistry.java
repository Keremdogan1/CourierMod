package com.rpsunucusu.courier;

import me.neznamy.tab.api.TabAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Map;
import java.util.UUID;

public class TabPlaceholderRegistry {
    public static void register(MinecraftServer[] serverRef, Map<UUID, CourierMod.PlayerMission> activeMissions, java.util.function.BiFunction<MinecraftServer, ServerPlayerEntity, Integer> getParaFunc) {
        try {
            // Register player placeholders for kurye_aktif
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%kurye_aktif%", 500, tabPlayer -> {
                UUID uuid = tabPlayer.getUniqueId();
                return activeMissions.containsKey(uuid) ? "1" : "0";
            });
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%objective:kurye_aktif%", 500, tabPlayer -> {
                UUID uuid = tabPlayer.getUniqueId();
                return activeMissions.containsKey(uuid) ? "1" : "0";
            });

            // Register player placeholders for kurye_hedef
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%kurye_hedef%", 500, tabPlayer -> {
                UUID uuid = tabPlayer.getUniqueId();
                CourierMod.PlayerMission pm = activeMissions.get(uuid);
                if (pm == null) return "";
                return pm.state.equals("TOPLAMA") ? pm.dagitimLoc.name : pm.musteriLoc.name;
            });
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%objective:kurye_hedef%", 500, tabPlayer -> {
                UUID uuid = tabPlayer.getUniqueId();
                CourierMod.PlayerMission pm = activeMissions.get(uuid);
                if (pm == null) return "";
                return pm.state.equals("TOPLAMA") ? pm.dagitimLoc.name : pm.musteriLoc.name;
            });

            // Register player placeholders for kurye_durum
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%kurye_durum%", 500, tabPlayer -> {
                UUID uuid = tabPlayer.getUniqueId();
                CourierMod.PlayerMission pm = activeMissions.get(uuid);
                if (pm == null) return "";
                return pm.state.equals("TOPLAMA") ? "Paketi Al" : "Teslim Et";
            });
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%objective:kurye_durum%", 500, tabPlayer -> {
                UUID uuid = tabPlayer.getUniqueId();
                CourierMod.PlayerMission pm = activeMissions.get(uuid);
                if (pm == null) return "";
                return pm.state.equals("TOPLAMA") ? "Paketi Al" : "Teslim Et";
            });

            // Register player placeholders for para
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%kurye_para%", 500, tabPlayer -> {
                MinecraftServer server = serverRef[0];
                if (server == null) return "0";
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(tabPlayer.getUniqueId());
                if (p == null) return "0";
                return String.valueOf(getParaFunc.apply(server, p));
            });
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%objective:para%", 500, tabPlayer -> {
                MinecraftServer server = serverRef[0];
                if (server == null) return "0";
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(tabPlayer.getUniqueId());
                if (p == null) return "0";
                return String.valueOf(getParaFunc.apply(server, p));
            });
            
            System.out.println("[CourierMod] TAB Placeholders registered successfully!");
        } catch (Throwable t) {
            System.err.println("[CourierMod] Error registering TAB placeholders: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
