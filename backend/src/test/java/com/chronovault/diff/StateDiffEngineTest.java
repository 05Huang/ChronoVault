package com.chronovault.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StateDiffEngine — the core diff logic comparing two state.json snapshots.
 */
class StateDiffEngineTest {

    private StateDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        diffEngine = new StateDiffEngine(new ObjectMapper());
    }

    @Test
    void diff_withNullInputs_returnsEmpty() {
        StateDiffEngine.StateDiffResult result = diffEngine.diff(null, null);
        assertNotNull(result);
        assertNotNull(result.summary());
        assertEquals(0, result.summary().packagesAdded);
    }

    @Test
    void diff_withEmptyJson_returnsEmpty() {
        StateDiffEngine.StateDiffResult result = diffEngine.diff("{}", "{}");
        assertNotNull(result);
        assertNotNull(result.summary());
    }

    @Test
    void diff_detectsAddedPackages() {
        String stateA = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.22.0", "manager": "apt"}
                  ],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.22.0", "manager": "apt"},
                    {"name": "curl", "version": "7.88.1", "manager": "apt"}
                  ],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertNotNull(result.packages());
        assertEquals(1, result.packages().added.size());
        assertEquals("curl", result.packages().added.get(0).name());
        assertEquals("7.88.1", result.packages().added.get(0).version());
        assertEquals(0, result.packages().removed.size());
        assertEquals(1, result.summary().packagesAdded);
    }

    @Test
    void diff_detectsRemovedPackages() {
        String stateA = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.22.0", "manager": "apt"},
                    {"name": "curl", "version": "7.88.1", "manager": "apt"}
                  ],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.22.0", "manager": "apt"}
                  ],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.packages().removed.size());
        assertEquals("curl", result.packages().removed.get(0).name());
        assertEquals(1, result.summary().packagesRemoved);
    }

    @Test
    void diff_detectsUpgradedPackages() {
        String stateA = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.22.0", "manager": "apt"}
                  ],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.24.0", "manager": "apt"}
                  ],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.packages().upgraded.size());
        assertEquals("nginx", result.packages().upgraded.get(0).name());
        assertEquals("1.22.0", result.packages().upgraded.get(0).fromVersion());
        assertEquals("1.24.0", result.packages().upgraded.get(0).toVersion());
        assertEquals(1, result.summary().packagesUpgraded);
    }

    @Test
    void diff_detectsServiceChanges() {
        String stateA = """
                {
                  "packages": [],
                  "services": [
                    {"name": "nginx", "status": "active", "enabled": true, "pid": 1234}
                  ],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [
                    {"name": "nginx", "status": "inactive", "enabled": false, "pid": 0}
                  ],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.services().changed.size());
        assertEquals("nginx", result.services().changed.get(0).name);
        assertEquals("active", result.services().changed.get(0).fromStatus);
        assertEquals("inactive", result.services().changed.get(0).toStatus);
        assertEquals(1, result.summary().servicesChanged);
    }

    @Test
    void diff_detectsNewService() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [
                    {"name": "redis", "status": "active", "enabled": true, "pid": 5678}
                  ],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.services().added.size());
        assertEquals("redis", result.services().added.get(0));
        assertEquals(1, result.summary().servicesChanged);
    }

    @Test
    void diff_detectsPortChanges() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"},
                    {"port": 8080, "protocol": "tcp", "process": "node", "state": "LISTEN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.ports().added.size());
        assertTrue(result.ports().added.contains("8080/tcp"));
        assertEquals(1, result.summary().portsChanged);
    }

    @Test
    void diff_detectsPortRemoval() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"},
                    {"port": 8080, "protocol": "tcp", "process": "node", "state": "LISTEN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.ports().removed.size());
        assertTrue(result.ports().removed.contains("8080/tcp"));
        assertEquals(1, result.summary().portsChanged);
    }

    @Test
    void diff_detectsUdpPorts() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [
                    {"port": 53, "protocol": "udp", "process": "named", "state": "UNCONN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [
                    {"port": 53, "protocol": "udp", "process": "named", "state": "UNCONN"},
                    {"port": 123, "protocol": "udp", "process": "ntpd", "state": "UNCONN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.ports().added.size());
        assertTrue(result.ports().added.contains("123/udp"));
    }

    @Test
    void diff_withMalformedJson_returnsEmpty() {
        StateDiffEngine.StateDiffResult result = diffEngine.diff("not json", "{ valid json }");
        assertNotNull(result);
        assertNotNull(result.summary());
        assertEquals(0, result.summary().packagesAdded);
    }

    @Test
    void diff_withMissingFields_handlesGracefully() {
        String stateA = """
                {
                  "packages": [{"name": "nginx", "version": "1.22.0"}],
                  "services": [],
                  "ports": [],
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [{"name": "nginx", "version": "1.24.0"}],
                  "services": [],
                  "ports": [],
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);
        assertNotNull(result);
        assertEquals(1, result.packages().upgraded.size());
    }

    @Test
    void diff_dockerContainerAdded() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {
                    "containers": [],
                    "compose_files": []
                  },
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {
                    "containers": [
                      {"id": "new123", "name": "new-app", "image": "python:3.11", "status": "running", "ports": ["8000:8000"]}
                    ],
                    "compose_files": []
                  },
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.docker().containersAdded.size());
        assertEquals("new-app", result.docker().containersAdded.get(0));
    }

    @Test
    void diff_dockerContainerRemoved() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {
                    "containers": [
                      {"id": "abc123", "name": "old-app", "image": "node:18", "status": "running", "ports": ["3000:3000"]}
                    ],
                    "compose_files": []
                  },
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {
                    "containers": [],
                    "compose_files": []
                  },
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.docker().containersRemoved.size());
        assertEquals("old-app", result.docker().containersRemoved.get(0));
    }

    @Test
    void diff_configAdded() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [
                    {"path": "/etc/new.conf", "sha256": "abc123", "size": 512}
                  ],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.configs().added.size());
        assertEquals("/etc/new.conf", result.configs().added.get(0));
    }

    @Test
    void diff_configRemoved() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [
                    {"path": "/etc/old.conf", "sha256": "abc123", "size": 512}
                  ],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.configs().removed.size());
        assertEquals("/etc/old.conf", result.configs().removed.get(0));
    }

    @Test
    void diff_crontabAdded() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": [
                    {"user": "root", "schedule": "0 3 * * *", "command": "/opt/backup.sh"}
                  ]
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.crontab().added.size());
        assertTrue(result.crontab().added.contains("root 0 3 * * * /opt/backup.sh"));
    }

    @Test
    void diff_complexScenario_multipleChanges() {
        String stateA = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.22.0", "manager": "apt"},
                    {"name": "curl", "version": "7.88.1", "manager": "apt"}
                  ],
                  "services": [
                    {"name": "nginx", "status": "active", "enabled": true, "pid": 1234},
                    {"name": "mysql", "status": "active", "enabled": true, "pid": 5678}
                  ],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"},
                    {"port": 3306, "protocol": "tcp", "process": "mysql", "state": "LISTEN"}
                  ],
                  "docker": {
                    "containers": [
                      {"id": "abc123", "name": "web-app", "image": "node:18", "status": "running", "ports": ["3000:3000"]}
                    ],
                    "compose_files": []
                  },
                  "configs": [
                    {"path": "/etc/nginx/nginx.conf", "sha256": "aaa", "size": 1024},
                    {"path": "/etc/mysql/my.cnf", "sha256": "bbb", "size": 512}
                  ],
                  "crontab": [
                    {"user": "root", "schedule": "0 2 * * *", "command": "/opt/backup.sh"}
                  ]
                }
                """;

        String stateB = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.24.0", "manager": "apt"},
                    {"name": "git", "version": "2.42.0", "manager": "apt"}
                  ],
                  "services": [
                    {"name": "nginx", "status": "active", "enabled": true, "pid": 1234},
                    {"name": "redis", "status": "active", "enabled": true, "pid": 9012}
                  ],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"},
                    {"port": 6379, "protocol": "tcp", "process": "redis", "state": "LISTEN"}
                  ],
                  "docker": {
                    "containers": [
                      {"id": "abc123", "name": "web-app", "image": "node:20", "status": "running", "ports": ["3000:3000"]},
                      {"id": "def456", "name": "api-server", "image": "go:1.21", "status": "running", "ports": ["8080:8080"]}
                    ],
                    "compose_files": []
                  },
                  "configs": [
                    {"path": "/etc/nginx/nginx.conf", "sha256": "ccc", "size": 1024}
                  ],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        StateDiffEngine.DiffSummary s = result.summary();
        assertEquals(1, s.packagesAdded);       // git
        assertEquals(1, s.packagesRemoved);      // curl
        assertEquals(1, s.packagesUpgraded);     // nginx 1.22 -> 1.24
        assertEquals(2, s.servicesChanged);      // mysql removed, redis added
        assertEquals(2, s.portsChanged);         // 6379 added, 3306 removed
        assertEquals(2, s.configsChanged);       // nginx.conf hash changed + mysql/my.cnf removed
        assertEquals(1, s.crontabChanged);       // cron entry removed
        assertEquals(1, s.dockerChanged);        // api-server added (image-only changes not tracked)
    }

    @Test
    void diff_detectsConfigChanges() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [
                    {"path": "/etc/nginx/nginx.conf", "sha256": "aaa111", "size": 1024}
                  ],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [
                    {"path": "/etc/nginx/nginx.conf", "sha256": "bbb222", "size": 1024}
                  ],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        assertEquals(1, result.configs().changed.size());
        assertEquals("/etc/nginx/nginx.conf", result.configs().changed.get(0));
        assertEquals(1, result.summary().configsChanged);
    }

    @Test
    void diff_detectsDockerContainerChanges() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {
                    "containers": [
                      {"id": "abc123", "name": "my-app", "image": "node:18", "status": "running", "ports": ["3000:3000"]}
                    ],
                    "compose_files": []
                  },
                  "configs": [],
                  "crontab": []
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {
                    "containers": [
                      {"id": "abc123", "name": "my-app", "image": "node:20", "status": "running", "ports": ["3000:3000"]}
                    ],
                    "compose_files": []
                  },
                  "configs": [],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        // Image changed but status didn't change (both "running") — diff engine tracks container status changes only
        assertEquals(0, result.docker().containersChanged.size());
    }

    @Test
    void diff_detectsCrontabChanges() {
        String stateA = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": [
                    {"user": "root", "schedule": "0 2 * * *", "command": "/opt/backup.sh"}
                  ]
                }
                """;

        String stateB = """
                {
                  "packages": [],
                  "services": [],
                  "ports": [],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [],
                  "crontab": [
                    {"user": "root", "schedule": "0 3 * * *", "command": "/opt/backup.sh"}
                  ]
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        // Old entry removed, new entry added
        assertEquals(1, result.crontab().removed.size());
        assertEquals(1, result.crontab().added.size());
        assertEquals(2, result.summary().crontabChanged);
    }

    @Test
    void diff_summaryCountsEverything() {
        String stateA = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.22.0", "manager": "apt"},
                    {"name": "old-pkg", "version": "1.0", "manager": "apt"}
                  ],
                  "services": [
                    {"name": "nginx", "status": "active", "enabled": true, "pid": 100}
                  ],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [
                    {"path": "/etc/nginx/nginx.conf", "sha256": "aaa", "size": 100}
                  ],
                  "crontab": [
                    {"user": "root", "schedule": "0 2 * * *", "command": "/opt/cron.sh"}
                  ]
                }
                """;

        String stateB = """
                {
                  "packages": [
                    {"name": "nginx", "version": "1.24.0", "manager": "apt"},
                    {"name": "new-pkg", "version": "2.0", "manager": "apt"}
                  ],
                  "services": [
                    {"name": "redis", "status": "active", "enabled": true, "pid": 200}
                  ],
                  "ports": [
                    {"port": 80, "protocol": "tcp", "process": "nginx", "state": "LISTEN"},
                    {"port": 6379, "protocol": "tcp", "process": "redis", "state": "LISTEN"}
                  ],
                  "docker": {"containers": [], "compose_files": []},
                  "configs": [
                    {"path": "/etc/nginx/nginx.conf", "sha256": "bbb", "size": 100},
                    {"path": "/etc/redis/redis.conf", "sha256": "ccc", "size": 200}
                  ],
                  "crontab": []
                }
                """;

        StateDiffEngine.StateDiffResult result = diffEngine.diff(stateA, stateB);

        StateDiffEngine.DiffSummary s = result.summary();
        assertEquals(1, s.packagesAdded);       // new-pkg
        assertEquals(1, s.packagesRemoved);      // old-pkg
        assertEquals(1, s.packagesUpgraded);     // nginx 1.22 -> 1.24
        assertEquals(2, s.servicesChanged);      // nginx removed, redis added
        assertEquals(1, s.portsChanged);         // 6379 added
        assertEquals(2, s.configsChanged);       // nginx.conf changed, redis.conf added
        assertEquals(1, s.crontabChanged);       // cron entry removed
    }
}