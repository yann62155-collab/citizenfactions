package fr.citizen.factions.core;

import java.util.EnumSet;
import java.util.Set;

/** Grade d'une faction. L'ordre hierarchique est la position dans la liste de la faction (0 = le plus haut). */
public final class Grade {
    private final String id;
    private String name;
    private final EnumSet<FactionPermission> perms;

    public Grade(String id, String name, Set<FactionPermission> perms) {
        this.id = id;
        this.name = name;
        this.perms = perms.isEmpty() ? EnumSet.noneOf(FactionPermission.class) : EnumSet.copyOf(perms);
    }

    public String id() { return id; }
    public String name() { return name; }
    void name(String n) { this.name = n; }
    public Set<FactionPermission> perms() { return java.util.Collections.unmodifiableSet(perms); }
    public boolean has(FactionPermission p) { return perms.contains(p); }
    void toggle(FactionPermission p) { if (!perms.remove(p)) perms.add(p); }
    public void set(FactionPermission p, boolean on) { if (on) perms.add(p); else perms.remove(p); }
}
