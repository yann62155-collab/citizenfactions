package fr.citizen.factions.core;

import java.util.UUID;

/** Appartenance d'un joueur (connecte ou non) a une faction. Un joueur n'a qu'une seule fiche. */
public final class Member {
    private final UUID uuid;
    private String name;
    private String factionId;
    private String gradeId;
    private long joinedAt;
    private long lastSeen;

    public Member(UUID uuid, String name, String factionId, String gradeId, long joinedAt, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.factionId = factionId;
        this.gradeId = gradeId;
        this.joinedAt = joinedAt;
        this.lastSeen = lastSeen;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public String factionId() { return factionId; }
    public String gradeId() { return gradeId; }
    public long joinedAt() { return joinedAt; }
    public long lastSeen() { return lastSeen; }

    void name(String n) { if (n != null && !n.isBlank()) name = n; }
    void faction(String f, String g, long now) { factionId = f; gradeId = g; joinedAt = now; }
    void grade(String g) { gradeId = g; }
    void lastSeen(long t) { lastSeen = t; }
}
