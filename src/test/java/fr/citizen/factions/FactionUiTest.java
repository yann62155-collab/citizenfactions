package fr.citizen.factions;

import fr.citizen.factions.core.*;
import fr.citizen.factions.ui.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FactionUiTest {
    final UUID admin = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();
    final Set<UUID> online = new HashSet<>();
    final List<String> notes = new ArrayList<>();
    final FactionService s = new FactionService(System::currentTimeMillis);
    final FactionUi ui;

    FactionUiTest() {
        s.seedDefaults();
        ui = new FactionUi(s, new UiEnv() {
            public boolean online(UUID p) { return online.contains(p); }
            public PlayerRef resolve(String n) { return n.equalsIgnoreCase("Bob") ? new PlayerRef(bob, "Bob") : null; }
            public BondSettings bond() { return BondSettings.DEFAULT; }
            public void notify(UUID p, String m) { notes.add(m); }
            public void notifyFaction(String f, FactionPermission p, String m) { notes.add(m); }
        });
    }

    Map<String, String> args(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    String text(View v) {
        StringBuilder sb = new StringBuilder(v.title()).append('\n').append(v.message()).append('\n');
        for (View.Line l : v.lines()) sb.append(l.text()).append('\n');
        return sb.toString();
    }

    @Test
    void factionlessPlayerSeesWarningAndCanRequest() {
        Actor b = new Actor(bob, "Bob", false);
        View v = ui.handle(b, "open", args("to", "main"));
        assertTrue(text(v).contains("aucune faction"));
        View r = ui.handle(b, "join", args("fid", "chasseur", "_v", "factions"));
        assertEquals("ok", r.kind());
        assertEquals("chasseur", s.requestOf(bob).factionId());
    }

    @Test
    void nonAdminCannotUseAdminActions() {
        Actor b = new Actor(bob, "Bob", false);
        View v = ui.handle(b, "admin_delete", args("fid", "dragonnier", "_v", "admin"));
        assertEquals("err", v.kind());
        assertNotNull(s.faction("dragonnier"));
        View w = ui.handle(b, "open", args("to", "admin"));
        assertEquals("err", w.kind());
    }

    @Test
    void adminFlowAndPagination() {
        Actor a = new Actor(admin, "Admin", true);
        View created = ui.handle(a, "admin_create", args("nid", "garde", "nname", "Garde royale", "_v", "admin"));
        assertEquals("ok", created.kind(), created.message());
        assertEquals("admin_faction", created.id());
        View added = ui.handle(a, "admin_add", args("fid", "garde", "pname", "Bob", "_v", "admin_faction"));
        assertEquals("ok", added.kind(), added.message());
        assertEquals("garde", s.factionOf(bob).id());
        for (int i = 0; i < 25; i++) s.addMember("dragonnier", UUID.randomUUID(), "Joueur" + i, null);
        online.add(s.members("dragonnier").get(0).uuid());
        View p1 = ui.handle(a, "open", args("to", "members", "fid", "dragonnier"));
        assertTrue(text(p1).contains("page 1 / 3"));
        assertTrue(p1.buttons().stream().anyMatch(btn -> btn.label().contains("Suivant")));
        View p3 = ui.handle(a, "open", args("to", "members", "fid", "dragonnier", "p", "2"));
        assertTrue(p3.buttons().stream().anyMatch(btn -> btn.label().contains("Précédent")));
        View on = ui.handle(a, "open", args("to", "members", "fid", "dragonnier", "f", "on"));
        assertTrue(text(on).contains("1 en ligne"));
        // vue JSON aller-retour
        View back = ViewCodec.fromJson(ViewCodec.toJson(p1));
        assertEquals(p1.lines().size(), back.lines().size());
        assertEquals(p1.emblem(), back.emblem());
    }

    @Test
    void memberCannotSeeOtherFactionMembers() {
        s.addMember("dragonnier", alice, "Alice", "petit");
        View v = ui.handle(new Actor(alice, "Alice", false), "open", args("to", "members", "fid", "chasseur"));
        assertEquals("err", v.kind());
        View own = ui.handle(new Actor(alice, "Alice", false), "open", args("to", "members", "fid", "dragonnier"));
        assertEquals("members", own.id());
    }
}
