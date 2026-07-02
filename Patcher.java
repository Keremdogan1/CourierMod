import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Patcher {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src/main/java/com/rpsunucusu/courier/CourierMod.java");
        String content = new String(Files.readAllBytes(path), "UTF-8");

        // Add ActiveMission attributes
        if (!content.contains("public boolean isPlayerJob")) {
            content = content.replace("public LocationData musteriLoc;", "public LocationData musteriLoc;\n    public boolean isPlayerJob;\n    public UUID customerId;");
        }

        // Add Job classes
        if (!content.contains("public static class PlayerCallRequest")) {
            String classes = "    public static class PlayerCallRequest {\n" +
                             "        public UUID playerId; public String playerName; public String type; public long timestamp; public LocationData location; public LocationData targetLocation;\n" +
                             "        public PlayerCallRequest(UUID id, String name, String type, long time, LocationData loc, LocationData targetLoc) {\n" +
                             "            this.playerId = id; this.playerName = name; this.type = type; this.timestamp = time; this.location = loc; this.targetLocation = targetLoc;\n" +
                             "        }\n" +
                             "    }\n" +
                             "    public static class JobOffer {\n" +
                             "        public String jobId; public String type; public boolean isPlayerRequest; public PlayerCallRequest playerRequest;\n" +
                             "        public MissionPair npcMissionPair; public LocationData npcTaksiPickup; public LocationData npcTaksiDropoff; public long expireTime;\n" +
                             "    }\n" +
                             "    private List<PlayerCallRequest> callRequests = new ArrayList<>();\n" +
                             "    private Map<String, JobOffer> availableOffers = new HashMap<>();\n";
            content = content.replace("private final List<String> activityLog = new ArrayList<>();", "private final List<String> activityLog = new ArrayList<>();\n" + classes);
        }

        // Add timeout logic in tick
        String tickFind = "        ServerTickEvents.END_SERVER_TICK.register(server -> {\n            tickCounter++;";
        String tickReplace = "        ServerTickEvents.END_SERVER_TICK.register(server -> {\n" +
                             "            long now = System.currentTimeMillis();\n" +
                             "            callRequests.removeIf(req -> {\n" +
                             "                if (now - req.timestamp > 60000) {\n" +
                             "                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(req.playerId);\n" +
                             "                    if (p != null) p.sendMessage(net.minecraft.text.Text.literal(\"§cHiçbir kurye/taksi isteğinize dönüş vermedi.\"));\n" +
                             "                    return true;\n" +
                             "                }\n" +
                             "                return false;\n" +
                             "            });\n" +
                             "            for (java.util.Iterator<Map.Entry<UUID, PlayerMission>> it = activeMissions.entrySet().iterator(); it.hasNext();) {\n" +
                             "                Map.Entry<UUID, PlayerMission> entry = it.next();\n" +
                             "                PlayerMission pm = entry.getValue();\n" +
                             "                if (pm.isPlayerJob && pm.customerId != null) {\n" +
                             "                    ServerPlayerEntity customer = server.getPlayerManager().getPlayer(pm.customerId);\n" +
                             "                    if (customer == null || customer.isDisconnected()) {\n" +
                             "                        ServerPlayerEntity driver = server.getPlayerManager().getPlayer(entry.getKey());\n" +
                             "                        if (driver != null) driver.sendMessage(net.minecraft.text.Text.literal(\"§cMüşteri oyundan çıktığı için görev iptal oldu!\"));\n" +
                             "                        it.remove();\n" +
                             "                    } else {\n" +
                             "                        pm.musteriLoc = new LocationData(customer.getName().getString(), customer.getBlockPos().getX(), customer.getBlockPos().getY(), customer.getBlockPos().getZ(), customer.getWorld().getRegistryKey().getValue().toString());\n" +
                             "                    }\n" +
                             "                }\n" +
                             "            }\n" +
                             "            tickCounter++;";
        content = content.replace(tickFind, tickReplace);

        // Update Kurye Cagir with eco
        content = content.replace("callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), \"KURYE\", System.currentTimeMillis(), loc, null));\n        p.sendMessage(Text.literal(P + \"\\u00a7a\\u0130ste\\u011finiz gerekli birime iletildi, en yak\\u0131n zamanda geri d\\u00f6n\\u00fc\\u015f yapacaklar.\"));",
                                  "p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), \"eco take \" + p.getName().getString() + \" 100\");\n" +
                                  "        p.sendMessage(Text.literal(P + \"§cKurye çağırma ücreti olarak 100 kesildi.\"));\n" +
                                  "        callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), \"KURYE\", System.currentTimeMillis(), loc, null));\n" +
                                  "        p.sendMessage(Text.literal(P + \"\\u00a7a\\u0130ste\\u011finiz gerekli birime iletildi, en yak\\u0131n zamanda geri d\\u00f6n\\u00fc\\u015f yapacaklar.\"));");

        // Update Taksi Cagir with eco
        content = content.replace("callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), \"TAKSI\", System.currentTimeMillis(), loc, hedef));\n        p.sendMessage(Text.literal(\"\\u00a76[Taksi] \\u00a7a\\u0130ste\\u011finiz gerekli birime iletildi (\\u00a7bHedef: \" + hedef.name + \"\\u00a7a).\"));",
                                  "p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), \"eco take \" + p.getName().getString() + \" 100\");\n" +
                                  "        p.sendMessage(Text.literal(\"§6[Taksi] §cTaksi çağırma ücreti olarak 100 kesildi.\"));\n" +
                                  "        callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), \"TAKSI\", System.currentTimeMillis(), loc, hedef));\n" +
                                  "        p.sendMessage(Text.literal(\"\\u00a76[Taksi] \\u00a7a\\u0130ste\\u011finiz gerekli birime iletildi (\\u00a7bHedef: \" + hedef.name + \"\\u00a7a).\"));");

        // Register Taksi Bitir command
        content = content.replace("dispatcher.register(CommandManager.literal(\"taksi_accept\")", "dispatcher.register(CommandManager.literal(\"taksi_bitir\").executes(this::taksiBitir));\n        dispatcher.register(CommandManager.literal(\"taksi_accept\")");

        // Add taksiBitir logic
        String bitirMethod = "\n    private int taksiBitir(CommandContext<ServerCommandSource> context) {\n" +
                             "        ServerPlayerEntity p = context.getSource().getPlayer();\n" +
                             "        if (p == null) return 0;\n" +
                             "        PlayerMission pm = activeMissions.get(p.getUuid());\n" +
                             "        if (pm == null || !pm.type.equals(\"TAKSI\")) { p.sendMessage(Text.literal(\"§6[Taksi] §cAktif bir taksi göreviniz yok!\")); return 0; }\n" +
                             "        if (pm.isPlayerJob && pm.customerId != null) {\n" +
                             "            ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);\n" +
                             "            if (customer != null) {\n" +
                             "                double distSq = p.getBlockPos().getSquaredDistance(customer.getBlockPos());\n" +
                             "                if (distSq <= 100.0) {\n" +
                             "                    addXp(p, \"TAKSI\", 50.0);\n" +
                             "                    activeMissions.remove(p.getUuid());\n" +
                             "                    customer.sendMessage(Text.literal(\"§6[Taksi] §aHedefe ulaştınız. Yolculuk bitti.\"));\n" +
                             "                    p.sendMessage(Text.literal(\"§6[Taksi] §aMüşteriyi hedefine ulaştırdınız.\"));\n" +
                             "                } else {\n" +
                             "                    p.sendMessage(Text.literal(\"§6[Taksi] §cMüşteriye yeterince yakın değilsiniz!\"));\n" +
                             "                }\n" +
                             "            } else {\n" +
                             "                p.sendMessage(Text.literal(\"§6[Taksi] §cMüşteri çevrimiçi değil, görev iptal edildi.\"));\n" +
                             "                activeMissions.remove(p.getUuid());\n" +
                             "            }\n" +
                             "        } else {\n" +
                             "            p.sendMessage(Text.literal(\"§6[Taksi] §cBu görev bir NPC görevi, hedef noktaya giderek otomatik bitirebilirsiniz.\"));\n" +
                             "        }\n" +
                             "        return 1;\n" +
                             "    }\n";
        content = content.replace("// === Mission Logic ===", "// === Mission Logic ===\n" + bitirMethod);

        // Update Kurye Accept customer notice
        content = content.replace("pm.musteriLoc = offer.playerRequest.location;", "pm.musteriLoc = offer.playerRequest.location;\n            pm.isPlayerJob = true;\n            pm.customerId = offer.playerRequest.playerId;");
        content = content.replace("p.sendMessage(Text.literal(P + \"\\u00a7aOyuncu m\\u00fc\\u015fteriye y\\u00f6nlendiriliyorsunuz.\"));", "p.sendMessage(Text.literal(P + \"\\u00a7aOyuncu m\\u00fc\\u015fteriye y\\u00f6nlendiriliyorsunuz.\"));\n            ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);\n            if (customer != null) customer.sendMessage(Text.literal(P + \"§aKuryeniz yola çıktı!\"));");

        // Update Taksi Accept customer notice
        content = content.replace("pm.isPlayerJob = true;", "pm.isPlayerJob = true;\n            pm.customerId = offer.playerRequest.playerId;");
        content = content.replace("p.sendMessage(Text.literal(\"\\u00a76[Taksi] \\u00a7aOyuncu m\\u00fc\\u015fteriye y\\u00f6nlendiriliyorsunuz.\"));", "p.sendMessage(Text.literal(\"\\u00a76[Taksi] \\u00a7aOyuncu m\\u00fc\\u015fteriye y\\u00f6nlendiriliyorsunuz.\"));\n            ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);\n            if (customer != null) customer.sendMessage(Text.literal(\"§6[Taksi] §aTaksiniz yola çıktı!\"));");

        Files.write(path, content.getBytes("UTF-8"));
        System.out.println("Patch applied successfully.");
    }
}
