package io.contextmesh.shared.development;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/v1/development/workspace")
public class DevelopmentWorkspaceController {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID WORKSPACE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private final JdbcTemplate jdbc;

    public DevelopmentWorkspaceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    @Transactional
    public WorkspaceResponse getOrCreate() {
        jdbc.update("""
                insert into users(id, email, display_name) values (?, 'local@contextmesh.test', 'Local developer')
                on conflict (id) do nothing
                """, USER_ID);
        jdbc.update("""
                insert into workspaces(id, owner_user_id, name) values (?, ?, 'Local workspace')
                on conflict (id) do nothing
                """, WORKSPACE_ID, USER_ID);
        return new WorkspaceResponse(WORKSPACE_ID, "Local workspace");
    }

    public record WorkspaceResponse(UUID id, String name) {}
}
