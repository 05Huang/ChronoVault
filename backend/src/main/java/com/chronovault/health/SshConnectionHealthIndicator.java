package com.chronovault.health;

import com.chronovault.entity.Server;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SshConnectionHealthIndicator implements HealthIndicator {

    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;

    @Override
    public Health health() {
        try {
            List<Server> servers = serverRepository.findAll();
            int reachable = 0;
            int unreachable = 0;

            for (Server server : servers) {
                try {
                    sshManager.getConnection(server);
                    reachable++;
                } catch (Exception e) {
                    unreachable++;
                }
            }

            return Health.up()
                    .withDetail("totalServers", servers.size())
                    .withDetail("reachable", reachable)
                    .withDetail("unreachable", unreachable)
                    .build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}