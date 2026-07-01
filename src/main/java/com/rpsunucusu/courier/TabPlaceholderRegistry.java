package com.rpsunucusu.courier;

import me.neznamy.tab.api.TabAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Map;
import java.util.UUID;

public class TabPlaceholderRegistry {
    public static void register(MinecraftServer[] serverRef, Map<UUID, CourierMod.PlayerMission> activeMissions, java.util.function.BiFunction<MinecraftServer, ServerPlayerEntity, Integer> getParaFunc) {
        try {
            // Register placeholder for baslik
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%gorev_baslik%", 500, tabPlayer -> {
                CourierMod.PlayerMission pm = activeMissions.get(tabPlayer.getUniqueId());
                if (pm == null) return "&7/kurye al &8- görev al";
                return pm.type.equals("TAKSI") ? "&e🚕 Taksi Görevi" : "&a📦 Kurye Görevi";
            });

            // Register placeholder for durum
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%gorev_durum%", 500, tabPlayer -> {
                CourierMod.PlayerMission pm = activeMissions.get(tabPlayer.getUniqueId());
                if (pm == null) return "&7/taksi al &8- görev al";
                if (pm.type.equals("TAKSI")) {
                    return pm.state.equals("TOPLAMA") ? "  &eYolcuyu Al" : "  &eHedefe Götür";
                } else {
                    return pm.state.equals("TOPLAMA") ? "  &ePaketi Al" : "  &eTeslim Et";
                }
            });

            // Register placeholder for hedef
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%gorev_hedef%", 500, tabPlayer -> {
                CourierMod.PlayerMission pm = activeMissions.get(tabPlayer.getUniqueId());
                if (pm == null) return "";
                String hedef = pm.state.equals("TOPLAMA") ? pm.dagitimLoc.name : pm.musteriLoc.name;
                return "  &b➤ &f" + hedef;
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
