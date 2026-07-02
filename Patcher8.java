import java.nio.file.*;
import java.io.*;

public class Patcher8 {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src/main/java/com/rpsunucusu/courier/CourierMod.java");
        String content = new String(Files.readAllBytes(path), "UTF-8");

        String commands = 
            "    private int playerKuryeCagir(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {\n" +
            "        net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();\n" +
            "        if (p == null) return 0;\n" +
            "        for (PlayerCallRequest req : callRequests) {\n" +
            "            if (req.playerId.equals(p.getUuid()) && req.type.equals(\"KURYE\")) {\n" +
            "                p.sendMessage(net.minecraft.text.Text.literal(P + \"§cZaten aktif bir kurye isteğiniz var!\"));\n" +
            "                return 0;\n" +
            "            }\n" +
            "        }\n" +
            "        int para = getPlayerPara(context.getSource().getServer(), p);\n" +
            "        if (para < MIN_UCRET) {\n" +
            "            p.sendMessage(net.minecraft.text.Text.literal(P + \"§cYetersiz bakiye! Kurye çağırmak için en az \" + (int)MIN_UCRET + \" TL gerekiyor.\"));\n" +
            "            return 0;\n" +
            "        }\n" +
            "        LocationData loc = new LocationData(p.getGameProfile().getName() + \" Konumu\", p.getBlockPos().getX(), p.getBlockPos().getY(), p.getBlockPos().getZ(), p.getWorld().getRegistryKey().getValue().toString());\n" +
            "        callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), \"KURYE\", System.currentTimeMillis(), loc, null));\n" +
            "        p.sendMessage(net.minecraft.text.Text.literal(P + \"§aİsteğiniz kuryelere iletildi, en kısa sürede biri kabul edecektir.\"));\n" +
            "        return 1;\n" +
            "    }\n\n" +
            "    private int playerTaksiCagir(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {\n" +
            "        net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();\n" +
            "        if (p == null) return 0;\n" +
            "        String hedefAdi = com.mojang.brigadier.arguments.StringArgumentType.getString(context, \"hedef_adi\");\n" +
            "        LocationData hedef = null;\n" +
            "        for (LocationData loc : data.taksiNoktalari) {\n" +
            "            if (loc.name.equalsIgnoreCase(hedefAdi)) { hedef = loc; break; }\n" +
            "        }\n" +
            "        if (hedef == null) {\n" +
            "            p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §cBelirtilen hedef bulunamadı!\"));\n" +
            "            return 0;\n" +
            "        }\n" +
            "        for (PlayerCallRequest req : callRequests) {\n" +
            "            if (req.playerId.equals(p.getUuid()) && req.type.equals(\"TAKSI\")) {\n" +
            "                p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §cZaten aktif bir taksi isteğiniz var!\"));\n" +
            "                return 0;\n" +
            "            }\n" +
            "        }\n" +
            "        double dist = Math.sqrt(Math.pow(p.getBlockPos().getX() - hedef.x, 2) + Math.pow(p.getBlockPos().getZ() - hedef.z, 2));\n" +
            "        double ucret = Math.max(MIN_UCRET, Math.floor(dist * data.taksiCarpan));\n" +
            "        int para = getPlayerPara(context.getSource().getServer(), p);\n" +
            "        if (para < ucret) {\n" +
            "            p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §cYetersiz bakiye! Bu yolculuk \" + (int)ucret + \" TL tutuyor, sende \" + para + \" TL var.\"));\n" +
            "            return 0;\n" +
            "        }\n" +
            "        LocationData loc = new LocationData(p.getGameProfile().getName() + \" Konumu\", p.getBlockPos().getX(), p.getBlockPos().getY(), p.getBlockPos().getZ(), p.getWorld().getRegistryKey().getValue().toString());\n" +
            "        callRequests.add(new PlayerCallRequest(p.getUuid(), p.getGameProfile().getName(), \"TAKSI\", System.currentTimeMillis(), loc, hedef));\n" +
            "        p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §aİsteğiniz taksicilere iletildi (Tahmini Ücret: \" + (int)ucret + \" TL).\"));\n" +
            "        return 1;\n" +
            "    }\n\n" +
            "    private int acceptKurye(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {\n" +
            "        net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();\n" +
            "        if (p == null) return 0;\n" +
            "        String jobId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, \"jobId\");\n" +
            "        JobOffer offer = availableOffers.get(jobId);\n" +
            "        if (offer == null || !offer.type.equals(\"KURYE\")) { p.sendMessage(net.minecraft.text.Text.literal(P + \"§cBu iş artık mevcut değil.\")); return 0; }\n" +
            "        if (activeMissions.containsKey(p.getUuid())) { p.sendMessage(net.minecraft.text.Text.literal(P + \"§cZaten aktif bir göreviniz var.\")); return 0; }\n" +
            "        PlayerMission pm = new PlayerMission();\n" +
            "        pm.type = \"KURYE\";\n" +
            "        pm.missionStartTime = System.currentTimeMillis();\n" +
            "        if (offer.isPlayerRequest) {\n" +
            "            pm.isPlayerJob = true;\n" +
            "            pm.customerId = offer.playerRequest.playerId;\n" +
            "            pm.musteriLoc = offer.playerRequest.location;\n" +
            "            LocationData closestHub = null;\n" +
            "            double minD = Double.MAX_VALUE;\n" +
            "            for(LocationData hub : data.dagitimNoktalari) {\n" +
            "                double d = p.getBlockPos().getSquaredDistance(hub.x, p.getBlockPos().getY(), hub.z);\n" +
            "                if (d < minD) { minD = d; closestHub = hub; }\n" +
            "            }\n" +
            "            pm.dagitimLoc = closestHub != null ? closestHub : data.dagitimNoktalari.get(0);\n" +
            "            p.sendMessage(net.minecraft.text.Text.literal(P + \"§aBir oyuncunun kurye isteğini kabul ettiniz. Önce paketi almak için Dağıtım Merkezine gidin.\"));\n" +
            "            net.minecraft.server.network.ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);\n" +
            "            if (customer != null) customer.sendMessage(net.minecraft.text.Text.literal(P + \"§aKuryeniz yola çıktı!\"));\n" +
            "            callRequests.remove(offer.playerRequest);\n" +
            "        } else {\n" +
            "            pm.isPlayerJob = false;\n" +
            "            pm.dagitimLoc = offer.npcMissionPair.dagitim;\n" +
            "            pm.musteriLoc = offer.npcMissionPair.musteri;\n" +
            "            p.sendMessage(net.minecraft.text.Text.literal(P + \"§aNPC görevini kabul ettiniz. Dağıtım Merkezine gidin.\"));\n" +
            "        }\n" +
            "        activeMissions.put(p.getUuid(), pm);\n" +
            "        availableOffers.remove(jobId);\n" +
            "        return 1;\n" +
            "    }\n\n" +
            "    private int acceptTaksi(CommandContext<net.minecraft.server.command.ServerCommandSource> context) {\n" +
            "        net.minecraft.server.network.ServerPlayerEntity p = context.getSource().getPlayer();\n" +
            "        if (p == null) return 0;\n" +
            "        String jobId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, \"jobId\");\n" +
            "        JobOffer offer = availableOffers.get(jobId);\n" +
            "        if (offer == null || !offer.type.equals(\"TAKSI\")) { p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §cBu iş artık mevcut değil.\")); return 0; }\n" +
            "        if (activeMissions.containsKey(p.getUuid())) { p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §cZaten aktif bir göreviniz var.\")); return 0; }\n" +
            "        PlayerMission pm = new PlayerMission();\n" +
            "        pm.type = \"TAKSI\";\n" +
            "        pm.missionStartTime = System.currentTimeMillis();\n" +
            "        if (offer.isPlayerRequest) {\n" +
            "            pm.isPlayerJob = true;\n" +
            "            pm.customerId = offer.playerRequest.playerId;\n" +
            "            pm.dagitimLoc = offer.playerRequest.location;\n" +
            "            pm.musteriLoc = offer.playerRequest.targetLocation;\n" +
            "            p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §aBir oyuncunun taksi isteğini kabul ettiniz. Müşteriyi almaya gidin.\"));\n" +
            "            net.minecraft.server.network.ServerPlayerEntity customer = p.getServer().getPlayerManager().getPlayer(pm.customerId);\n" +
            "            if (customer != null) customer.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §aTaksiniz yola çıktı!\"));\n" +
            "            callRequests.remove(offer.playerRequest);\n" +
            "        } else {\n" +
            "            pm.isPlayerJob = false;\n" +
            "            pm.dagitimLoc = offer.npcTaksiPickup;\n" +
            "            pm.musteriLoc = offer.npcTaksiDropoff;\n" +
            "            p.sendMessage(net.minecraft.text.Text.literal(\"§6[Taksi] §aNPC taksi görevini kabul ettiniz. Durağa gidin.\"));\n" +
            "        }\n" +
            "        activeMissions.put(p.getUuid(), pm);\n" +
            "        availableOffers.remove(jobId);\n" +
            "        return 1;\n" +
            "    }\n\n";

        if (!content.contains("private int acceptKurye")) {
            content = content.replace("private int cancelMission(CommandContext<ServerCommandSource> context) {", commands + "    private int cancelMission(CommandContext<ServerCommandSource> context) {");
        }

        String regKurye = ".then(CommandManager.literal(\"siralama\").executes(this::showKuryeSiralama))";
        if (!content.contains("kurye_cagir")) {
            content = content.replace(regKurye, regKurye + "\n        );\n        dispatcher.register(CommandManager.literal(\"kurye_cagir\").executes(this::playerKuryeCagir));\n        dispatcher.register(CommandManager.literal(\"kurye_accept\").then(CommandManager.argument(\"jobId\", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(this::acceptKurye))");
        }

        String regTaksi = ".then(CommandManager.literal(\"siralama\").executes(this::showTaksiSiralama))";
        if (!content.contains("taksi_cagir")) {
            content = content.replace(regTaksi, regTaksi + "\n        );\n        dispatcher.register(CommandManager.literal(\"taksi_cagir\").then(CommandManager.argument(\"hedef_adi\", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(this::playerTaksiCagir)));\n        dispatcher.register(CommandManager.literal(\"taksi_accept\").then(CommandManager.argument(\"jobId\", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(this::acceptTaksi))");
        }

        Files.write(path, content.getBytes("UTF-8"));
        System.out.println("Patcher8 applied.");
    }
}
