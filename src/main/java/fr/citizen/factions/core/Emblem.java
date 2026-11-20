package fr.citizen.factions.core;

/**
 * Embleme dessine par le client a partir de cette description (aucune texture a fournir) :
 * forme, motif, deux couleurs et un symbole d'un caractere.
 */
public final class Emblem {
    public enum Shape {
        ECU("Écu"), ROND("Rond"), LOSANGE("Losange"), BANNIERE("Bannière");
        private final String label;
        Shape(String l) { label = l; }
        public String label() { return label; }
        public Shape next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Pattern {
        UNI("Uni"), BANDE("Bande"), CROIX("Croix"), CHEVRON("Chevron"), MOITIE("Mi-parti");
        private final String label;
        Pattern(String l) { label = l; }
        public String label() { return label; }
        public Pattern next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private final String id;
    private String name;
    private Shape shape;
    private Pattern pattern;
    private int primary;
    private int secondary;
    private String symbol;

    public Emblem(String id, String name, Shape shape, Pattern pattern, int primary, int secondary, String symbol) {
        this.id = id;
        this.name = name;
        this.shape = shape;
        this.pattern = pattern;
        this.primary = primary & 0xFFFFFF;
        this.secondary = secondary & 0xFFFFFF;
        this.symbol = cleanSymbol(symbol);
    }

    public String id() { return id; }
    public String name() { return name; }
    public Shape shape() { return shape; }
    public Pattern pattern() { return pattern; }
    public int primary() { return primary; }
    public int secondary() { return secondary; }
    public String symbol() { return symbol; }

    void name(String n) { name = n; }
    void shape(Shape s) { shape = s; }
    void pattern(Pattern p) { pattern = p; }
    void primary(int c) { primary = c & 0xFFFFFF; }
    void secondary(int c) { secondary = c & 0xFFFFFF; }
    void symbol(String s) { symbol = cleanSymbol(s); }

    static String cleanSymbol(String s) {
        if (s == null || s.isBlank()) return "";
        int cp = s.strip().codePointAt(0);
        return new String(Character.toChars(cp));
    }

    /** Format compact envoye au client : FORME:MOTIF:RRGGBB:RRGGBB:symbole */
    public String encode() {
        return shape.name() + ":" + pattern.name() + ":" + hex(primary) + ":" + hex(secondary) + ":" + symbol;
    }

    public static String hex(int c) { return String.format("%06X", c & 0xFFFFFF); }

    public static int parseColor(String s) {
        if (s == null) throw new FactionException("Couleur manquante.");
        String t = s.strip();
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.matches("[0-9a-fA-F]{6}")) throw new FactionException("Couleur invalide : « " + s + " » (format attendu : #RRGGBB).");
        return Integer.parseInt(t, 16);
    }
}
