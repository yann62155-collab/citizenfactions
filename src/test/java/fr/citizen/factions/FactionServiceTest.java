package fr.citizen.factions;

import fr.citizen.factions.core.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FactionServiceTest {
    final AtomicLong clock = new AtomicLong(1_700_000_000_000L);
    final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), carl = UUID.randomUUID();

    FactionService seeded() {
        FactionService s = new FactionService(clock::get);
        s.seedDefaults();
        return s;
    }

    @Test
    void defaultsMatchSpecification() {
        FactionService s = seeded();
        assertEquals(2, s.factions().size());
        Faction d = s.requireFaction("dragonnier"), c = s.requireFaction("chasseur");
        assertEquals("Dragonnier", d.name());
        assertEquals("Chasseur de dragon", c.name());
        assertEquals(DragonRule.NON_CORROMPUS, d.rule());
        assertEquals(DragonRule.CORROMPUS, c.rule());
        assertEquals("Primodiau", d.grades().get(0).name());
        assertEquals("Alpha", d.grades().get(1).name());
        assertEquals("Confirmé", d.grades().get(2).name());
        assertEquals("Petit", d.grades().get(3).name());
        assertNotNull(s.emblem(d.emblemId()));
        assertNotNull(s.emblem(c.emblemId()));
    }

    @Test
    void dragonRules() {
        assertTrue(DragonRule.NON_CORROMPUS.allows(false));
        assertFalse(DragonRule.NON_CORROMPUS.allows(true));
        assertTrue(DragonRule.CORROMPUS.allows(true));
        assertFalse(DragonRule.CORROMPUS.allows(false));
        assertFalse(DragonRule.AUCUN.allows(true));
    }

    @Test
    void onePlayerOneFaction() {
        FactionService s = seeded();
        s.addMember("dragonnier", alice, "Alice", null);
        assertEquals("petit", s.member(alice).gradeId());
        FactionException e = assertThrows(FactionException.class, () -> s.addMember("chasseur", alice, "Alice", null));
        assertTrue(e.getMessage().contains("une seule faction"));
        s.transfer(alice, "chasseur");
        assertEquals("chasseur", s.factionOf(alice).id());
        assertEquals("petit", s.member(alice).gradeId());
        s.removeMember(alice);
        assertNull(s.factionOf(alice));
    }

    @Test
    void requestsAndGradePermissions() {
        FactionService s = seeded();
        s.addMember("dragonnier", alice, "Alice", "alpha");
        s.addMember("dragonnier", carl, "Carl", "petit");
        s.requestJoin(bob, "Bob", "dragonnier");
        // un Petit n'a pas le droit de gerer les demandes
        assertThrows(FactionException.class, () -> s.acceptRequest(carl, false, bob));
        s.acceptRequest(alice, false, bob);
        assertEquals("dragonnier", s.factionOf(bob).id());
        assertNull(s.requestOf(bob));
        // Alpha peut promouvoir un Petit en Confirme, mais pas en Alpha ni Primodiau
        s.setGradeBy(alice, false, bob, "confirme");
        assertEquals("confirme", s.member(bob).gradeId());
        assertThrows(FactionException.class, () -> s.setGradeBy(alice, false, bob, "alpha"));
        // Alpha n'a pas EXCLURE par defaut
        assertThrows(FactionException.class, () -> s.kickBy(alice, false, bob));
        assertFalse(s.canManage(alice, false, s.member(bob), FactionPermission.EXCLURE));
        // un inferieur ne gere pas un superieur
        s.setPermission("dragonnier", "petit", FactionPermission.EXCLURE, true);
        assertThrows(FactionException.class, () -> s.kickBy(carl, false, alice));
        // l'admin peut tout
        s.kickBy(UUID.randomUUID(), true, alice);
        assertNull(s.member(alice));
    }

    @Test
    void gradeManagement() {
        FactionService s = seeded();
        Grade g = s.createGrade("chasseur", "Écuyer");
        assertEquals("ecuyer", g.id());
        Faction f = s.requireFaction("chasseur");
        assertEquals(4, f.rank("ecuyer"));
        s.moveGrade("chasseur", "ecuyer", true);
        assertEquals(3, f.rank("ecuyer"));
        s.addMember("chasseur", bob, "Bob", "ecuyer");
        s.deleteGrade("chasseur", "ecuyer");
        assertNotNull(f.grade(s.member(bob).gradeId()));
        assertEquals("petit", s.member(bob).gradeId());
        s.togglePermission("chasseur", "petit", FactionPermission.VOIR_DRAGONS);
        assertTrue(f.grade("petit").has(FactionPermission.VOIR_DRAGONS));
    }

    @Test
    void emblems() {
        FactionService s = seeded();
        Emblem e = s.createEmblem("griffe", "Griffe");
        s.editEmblem("griffe", null, "ROND", "CROIX", "#112233", "aabbcc", "Ω");
        assertEquals("ROND:CROIX:112233:AABBCC:Ω", e.encode());
        assertThrows(FactionException.class, () -> s.editEmblem("griffe", null, null, null, "rouge", null, null));
        s.assignEmblem("dragonnier", "griffe");
        assertEquals("griffe", s.requireFaction("dragonnier").emblemId());
        s.deleteEmblem("griffe");
        assertEquals("", s.requireFaction("dragonnier").emblemId());
    }

    @Test
    void primordialPermissionDefaultsAndMigration() {
        FactionService s = seeded();
        Faction d = s.requireFaction("dragonnier");
        assertTrue(d.grade("primodiau").has(FactionPermission.MONTER_PRIMORDIAL));
        assertFalse(d.grade("alpha").has(FactionPermission.MONTER_PRIMORDIAL));
        // ancienne sauvegarde (format 1) : le Primodiau recoit le nouveau droit
        String old = FactionStore.toJson(s).replace("\"format\":2", "\"format\":1").replace(",\"MONTER_PRIMORDIAL\"", "");
        assertFalse(old.contains("MONTER_PRIMORDIAL"));
        FactionService r = new FactionService(clock::get);
        FactionStore.fromJson(old, r);
        assertTrue(r.requireFaction("chasseur").grade("primodiau").has(FactionPermission.MONTER_PRIMORDIAL));
        assertFalse(r.requireFaction("chasseur").grade("petit").has(FactionPermission.MONTER_PRIMORDIAL));
    }

    @Test
    void deleteFactionFreesMembers() {
        FactionService s = seeded();
        s.addMember("chasseur", bob, "Bob", null);
        s.deleteFaction("chasseur");
        assertNull(s.factionOf(bob));
        assertNull(s.member(bob));
    }

    @Test
    void persistenceRoundTripAndBackup() throws Exception {
        Path dir = Files.createTempDirectory("cf");
        FactionService s = seeded();
        s.addMember("dragonnier", alice, "Alice", "primodiau");
        s.requestJoin(bob, "Bob", "chasseur");
        DragonRecord d = s.dragonRecord(UUID.randomUUID());
        d.describe("lightning", "Cuivre", true, "Ombre", alice, 4);
        d.bonds().put(alice, new Bond(42.5, 10, 20));
        FactionStore store = new FactionStore(dir);
        store.save(s);
        store.save(s); // cree aussi la copie .bak

        FactionService r = new FactionService(clock::get);
        assertTrue(store.load(r, m -> { }));
        assertEquals("primodiau", r.member(alice).gradeId());
        assertEquals("chasseur", r.requestOf(bob).factionId());
        DragonRecord rd = r.dragon(d.uuid());
        assertTrue(rd.corrupted());
        assertEquals(42.5, rd.bond(alice).value(), 1e-9);
        assertEquals(FactionStore.toJson(s), FactionStore.toJson(r));

        // fichier principal corrompu : restauration depuis la copie de secours
        Files.writeString(store.file(), "{ pas du json");
        FactionService r2 = new FactionService(clock::get);
        assertTrue(store.load(r2, m -> { }));
        assertEquals("primodiau", r2.member(alice).gradeId());
    }
}
