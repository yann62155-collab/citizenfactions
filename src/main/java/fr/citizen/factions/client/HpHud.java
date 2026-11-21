package fr.citizen.factions.client;

import fr.citizen.factions.platform.HpPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Remplace les coeurs par un compteur de PV (« 4321 / 5000 ») a la place exacte des coeurs vanilla. */
public final class HpHud {
    private static volatile float maxHp; // 0 = pas de compteur (serveur sans le mod ou systeme desactive)
    private static float shownRatio = -1;

    private HpHud() { }

    public static void update(HpPayload p) { maxHp = p.maxHp(); }

    public static void reset() { maxHp = 0; shownRatio = -1; }

    public static void register(RegisterGuiLayersEvent e) { }

    /**
     * Remplace le calque des coeurs : le compteur est dessine a sa place, puis on reserve sa ligne (leftHeight)
     * pour que la barre d'armure se place au-dessus comme en vanilla, au lieu de recouvrir le compteur.
     */
    public static void onLayer(RenderGuiLayerEvent.Pre e) {
        if (maxHp <= 0 || !VanillaGuiLayers.PLAYER_HEALTH.equals(e.getName())) return;
        e.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        render(e.getGuiGraphics(), mc.gui.leftHeight);
        mc.gui.leftHeight += 10;
    }

    private static void render(GuiGraphics g, int leftHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        float max = maxHp;
        if (max <= 0 || p == null) return;

        float ratio = p.getMaxHealth() <= 0 ? 0 : Math.max(0, Math.min(1, p.getHealth() / p.getMaxHealth()));
        float absorb = p.getMaxHealth() <= 0 ? 0 : p.getAbsorptionAmount() / p.getMaxHealth();
        shownRatio = shownRatio < 0 ? ratio : shownRatio + (ratio - shownRatio) * 0.2F;
        int hp = Math.round(ratio * max);
        int extra = Math.round(absorb * max);

        int w = 81, x = g.guiWidth() / 2 - 91, y = g.guiHeight() - leftHeight;
        // cadre
        g.fill(x, y, x + w, y + 9, 0xFF1A0E0A);
        g.fill(x + 1, y + 1, x + w - 1, y + 8, 0xFF3A1410);
        // barre (rouge ; verte si empoisonne, noire si wither, bleutee si gel)
        int color = p.hasEffect(MobEffects.WITHER) ? 0xFF3A3A3A : p.hasEffect(MobEffects.POISON) ? 0xFF6E9B2A
                : p.isFullyFrozen() ? 0xFF7FB8D8 : 0xFFC0281E;
        int fill = Math.round((w - 2) * shownRatio);
        g.fill(x + 1, y + 1, x + 1 + fill, y + 8, color);
        g.fill(x + 1, y + 1, x + 1 + fill, y + 3, 0x50FFFFFF);
        if (absorb > 0) g.fill(x + 1, y + 6, x + 1 + Math.round((w - 2) * Math.min(1, absorb)), y + 8, 0xFFE8C040);

        Font font = mc.font;
        String txt = "❤ " + hp + (extra > 0 ? " +" + extra : "") + " / " + Math.round(max);
        float scale = font.width(txt) > w - 4 ? (w - 4) / (float) font.width(txt) : 1F;
        g.pose().pushPose();
        g.pose().translate(x + w / 2F, y + 1F + (1 - scale) * 4, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, txt, -font.width(txt) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }
}
