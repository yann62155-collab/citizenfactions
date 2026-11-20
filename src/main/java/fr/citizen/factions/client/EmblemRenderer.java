package fr.citizen.factions.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Dessine un embleme a partir de sa description « FORME:MOTIF:RRGGBB:RRGGBB:symbole » envoyee par le serveur.
 * Tout est dessine par rectangles : aucune texture a fournir, et n'importe quel embleme cree par un admin s'affiche.
 */
public final class EmblemRenderer {
    private static final int OUTLINE = 0xFF120C08;

    private EmblemRenderer() { }

    public static void draw(GuiGraphics g, Font font, String code, int x, int y, int size) {
        String[] p = code.split(":", 5);
        if (p.length < 4) return;
        String shape = p[0], pattern = p[1];
        int c1 = 0xFF000000 | parse(p[2]), c2 = 0xFF000000 | parse(p[3]);
        String symbol = p.length > 4 ? p[4] : "";

        boolean[][] mask = new boolean[size][size];
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++)
                mask[j][i] = inside(shape, (i + 0.5) / size, (j + 0.5) / size);

        for (int j = 0; j < size; j++) {
            int i = 0;
            while (i < size) {
                if (!mask[j][i]) { i++; continue; }
                // regroupe les pixels voisins de meme couleur en un seul rectangle
                int col = color(mask, i, j, size, pattern, c1, c2);
                int start = i;
                while (i < size && mask[j][i] && color(mask, i, j, size, pattern, c1, c2) == col) i++;
                g.fill(x + start, y + j, x + i, y + j + 1, col);
            }
        }
        if (!symbol.isEmpty() && size >= 10) {
            int sc = luminance(c1) > 140 ? 0xFF1A120A : 0xFFF2D27A;
            if (pattern.equals("UNI")) sc = luminance(c1) > 140 ? 0xFF1A120A : (0xFF000000 | c2);
            int tw = font.width(symbol);
            g.drawString(font, symbol, x + (size - tw) / 2 + (size % 2 == 0 ? 0 : 1), y + (size - 8) / 2 + (shape.equals("ECU") ? 0 : 1), sc, true);
        }
    }

    private static int color(boolean[][] m, int i, int j, int size, String pattern, int c1, int c2) {
        if (edge(m, i, j, size)) return OUTLINE;
        double u = (i + 0.5) / size, v = (j + 0.5) / size;
        boolean second = switch (pattern) {
            case "BANDE" -> Math.abs(u - v) < 0.16;
            case "CROIX" -> Math.abs(u - 0.5) < 0.11 || Math.abs(v - 0.42) < 0.11;
            case "CHEVRON" -> { double d = v - (0.78 - Math.abs(u - 0.5)); yield d > 0 && d < 0.22; }
            case "MOITIE" -> u >= 0.5;
            default -> false;
        };
        return second ? c2 : c1;
    }

    private static boolean edge(boolean[][] m, int i, int j, int size) {
        return i == 0 || j == 0 || i == size - 1 || j == size - 1
                || !m[j][i - 1] || !m[j][i + 1] || !m[j - 1][i] || !m[j + 1][i];
    }

    private static boolean inside(String shape, double u, double v) {
        double du = u - 0.5, dv = v - 0.5;
        return switch (shape) {
            case "ROND" -> du * du + dv * dv <= 0.25;
            case "LOSANGE" -> Math.abs(du) + Math.abs(dv) <= 0.5;
            case "BANNIERE" -> u >= 0.12 && u <= 0.88 && !(v > 0.74 && Math.abs(du) < (v - 0.74) * 1.6);
            default -> { // ECU : haut droit, bas arrondi en pointe
                if (v < 0.5) yield true;
                double t = (v - 0.5) / 0.5;
                yield Math.abs(du) <= 0.5 * Math.sqrt(Math.max(0, 1 - t * t * t));
            }
        };
    }

    private static int parse(String hex) {
        try { return Integer.parseInt(hex, 16) & 0xFFFFFF; } catch (NumberFormatException e) { return 0x777777; }
    }

    private static int luminance(int c) {
        int r = (c >> 16) & 0xFF, gg = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + gg * 587 + b * 114) / 1000;
    }
}
