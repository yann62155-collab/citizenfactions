package fr.citizen.factions.client;

import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.platform.BondPayload;
import fr.citizen.factions.platform.ClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Barre de lien affichee au-dessus de la barre d'inventaire pendant que le joueur monte son dragon. */
public final class BondHud {
    private static final int WIDTH = 120;
    private static final long TIMEOUT_MS = 3_000L;

    private static BondPayload current;
    private static long receivedAt;
    private static float shown = -1;

    private BondHud() { }

    public static void update(BondPayload p) {
        current = p.show() ? p : null;
        receivedAt = System.currentTimeMillis();
        if (current == null) shown = -1;
    }

    public static void register(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "bond_hud"), BondHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        BondPayload p = current;
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.getVehicle() == null) return;
        if (System.currentTimeMillis() - receivedAt > TIMEOUT_MS) { current = null; return; }

        float target = Math.max(0, Math.min(100, p.bond()));
        shown = shown < 0 ? target : shown + (target - shown) * 0.15f; // animation douce
        Font font = mc.font;
        int offset;
        try { offset = ClientConfig.HUD_OFFSET_Y.get(); } catch (IllegalStateException notLoaded) { offset = 50; }
        int x = (g.guiWidth() - WIDTH) / 2;
        int y = g.guiHeight() - 22 - offset;

        String label = "Lien — " + p.name();
        String pct = String.format(java.util.Locale.ROOT, "%.0f%%", target) + "  " + p.tier();
        label = font.plainSubstrByWidth(label, WIDTH - font.width(pct) - 6);

        g.fill(x - 3, y - 11, x + WIDTH + 3, y + 7, 0xB0120C08);
        g.fill(x - 3, y - 11, x + WIDTH + 3, y - 10, 0xFFB8892B);
        g.fill(x - 3, y + 6, x + WIDTH + 3, y + 7, 0xFFB8892B);
        g.drawString(font, label, x, y - 9, 0xFFF2D27A, true);
        g.drawString(font, pct, x + WIDTH - font.width(pct), y - 9, 0xFFE9D8AE, true);

        g.fill(x, y, x + WIDTH, y + 5, 0xFF2B1B0E);
        int fillW = Math.round(WIDTH * shown / 100f);
        g.fill(x, y, x + fillW, y + 5, color(target));
        g.fill(x, y, x + fillW, y + 1, 0x40FFFFFF);
        for (int i = 1; i < 4; i++) g.fill(x + WIDTH * i / 4, y, x + WIDTH * i / 4 + 1, y + 5, 0x60000000);
    }

    private static int color(float bond) {
        if (bond < 25) return 0xFF9B1C1C;
        if (bond < 50) return 0xFFC0661A;
        if (bond < 75) return 0xFFD4A537;
        return 0xFF4E9A2E;
    }
}
