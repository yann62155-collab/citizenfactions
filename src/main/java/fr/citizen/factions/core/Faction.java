package fr.citizen.factions.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Une faction (creee et administree uniquement par les administrateurs). */
public final class Faction {
    private final String id;
    private String name;
    private String description;
    private String emblemId;
    private DragonRule rule;
    private final List<Grade> grades = new ArrayList<>();
    private final long createdAt;

    public Faction(String id, String name, String description, String emblemId, DragonRule rule, long createdAt) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
        this.emblemId = emblemId == null ? "" : emblemId;
        this.rule = rule;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String emblemId() { return emblemId; }
    public DragonRule rule() { return rule; }
    public long createdAt() { return createdAt; }
    /** Grades du plus haut (index 0) au plus bas. */
    public List<Grade> grades() { return Collections.unmodifiableList(grades); }

    void name(String n) { name = n; }
    void description(String d) { description = d; }
    void emblemId(String e) { emblemId = e == null ? "" : e; }
    void rule(DragonRule r) { rule = r; }
    List<Grade> gradesMutable() { return grades; }

    public Grade grade(String gradeId) {
        for (Grade g : grades) if (g.id().equalsIgnoreCase(gradeId)) return g;
        return null;
    }

    /** Rang hierarchique : 0 = plus haut ; -1 si inconnu. */
    public int rank(String gradeId) {
        for (int i = 0; i < grades.size(); i++) if (grades.get(i).id().equalsIgnoreCase(gradeId)) return i;
        return -1;
    }

    public Grade lowestGrade() { return grades.isEmpty() ? null : grades.get(grades.size() - 1); }
}
