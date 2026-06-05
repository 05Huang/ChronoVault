# ChronoVault Security Documentation

> Security architecture, threat model, and known security constraints

---

## Table of Contents

- [Security Overview](#security-overview)
- [Security Architecture](#security-architecture)
- [Authentication and Authorization](#authentication-and-authorization)
- [Data Encryption](#data-encryption)
- [Network Security](#network-security)
- [SSH Security](#ssh-security)
- [API Security](#api-security)
- [Container Security](#container-security)
- [Threat Model](#threat-model)
- [Security Best Practices](#security-best-practices)
- [Known Security Constraints](#known-security-constraints)
- [Security Updates](#security-updates)
- [Reporting Security Issues](#reporting-security-issues)

---

## Security Overview

ChronoVault is designed with security as a core principle. This document describes the security architecture, threat model, and best practices for deploying ChronoVault securely.

### Security Principles

1. **Defense in Depth**: Multiple layers of security controls
2. **Least Privilege**: Components run with minimal required permissions
3. **Encryption at Rest**: All sensitive data is encrypted
4. **Encryption in Transit**: All communications use TLS/HTTPS
5. **Audit Logging**: All actions are logged for accountability

---

## Security Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Browser                             │
│                    (HTTPS + WSS)                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Frontend (Vue 3 SPA)                          │
│  • JWT stored in memory (not localStorage)                      │
│  • Auto-logout on token expiry                                  │
│  • CSRF protection via SameSite cookies                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                Backend (Spring Boot)                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                  Security Filters                         │  │
│  │  • JWT Authentication Filter                              │  │
│  │  • API Key Authentication Filter                          │  │
│  │  • Rate Limiting Filter                                   │  │
│  │  • CORS Filter                                            │  │
│  │  • Content Security Policy                                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │               Encryption Layer                            │  │
│  │  • AES-256-GCM for credentials                           │  │
│  │  • JWT HS256 for tokens                                  │  │
│  │  • Bcrypt for passwords                                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │               Audit Logging                               │  │
│  │  • All API actions logged                                 │  │
│  │  • User, action, resource, timestamp                      │  │
│  │  • IP address and User-Agent                              │  │
│  └──────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                           │
│  • Credentials encrypted at rest                                │
│  • Connection pooling with SSL                                  │
│  • Regular backups                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Authentication and Authorization

### JWT Authentication

**Token Generation:**
- Algorithm: HS256 (HMAC-SHA256)
- Expiry: 24 hours
- Claims: user ID, email, role, issued-at, expires-at

**Token Storage:**
- Frontend: In-memory (JavaScript variable)
- Token refresh: Automatic before expiry
- Logout: Token discarded from memory

**Token Validation:**
```java
// Backend validation
@Component
public class JwtTokenProvider {
    
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

### Role-Based Access Control (RBAC)

**Roles Hierarchy:**
```
OWNER > ADMIN > MEMBER > VIEWER
```

**Role Permissions:**

| Action | OWNER | ADMIN | MEMBER | VIEWER |
|--------|-------|-------|--------|--------|
| Create Server | ✅ | ✅ | ✅ | ❌ |
| Delete Server | ✅ | ✅ | ❌ | ❌ |
| Create Snapshot | ✅ | ✅ | ✅ | ❌ |
| Delete Snapshot | ✅ | ✅ | ❌ | ❌ |
| Rollback | ✅ | ✅ | ✅ | ❌ |
| Manage Users | ✅ | ✅ | ❌ | ❌ |
| View Snapshots | ✅ | ✅ | ✅ | ✅ |
| View Dashboard | ✅ | ✅ | ✅ | ✅ |

**Implementation:**
```java
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
public ResponseEntity<Void> deleteServer(@PathVariable Long id) {
    // Only ADMIN or OWNER can delete servers
}
```

### Password Security

**Hashing:**
- Algorithm: bcrypt (Adaptive)
- Cost factor: 12
- Salt: Auto-generated per password

**Password Requirements:**
- Minimum length: 8 characters
- Recommended: 12+ characters with mixed case, numbers, symbols

**Implementation:**
```java
@Component
public class PasswordEncoder {
    
    private final BCryptPasswordEncoder encoder = 
        new BCryptPasswordEncoder(12);
    
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }
    
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
```

---

## Data Encryption

### AES-256-GCM Encryption

**Use Case:** Encrypting sensitive credentials (SSH keys, passwords)

**Key Management:**
- Master key stored in environment variable: `CHRONOVAULT_MASTER_KEY`
- Key length: 32 bytes (256 bits)
- Key derivation: Direct use (not derived)

**Implementation:**
```java
@Component
public class CredentialEncryptor {
    
    private final SecretKey secretKey;
    
    public CredentialEncryptor(@Value("${chronovault.master-key}") String masterKey) {
        byte[] keyBytes = Hex.decodeHex(masterKey);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }
    
    public String encrypt(String plaintext) {
        byte[] iv = new byte[12]; // 96-bit IV for GCM
        SecureRandom.getInstanceStrong().nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    public String decrypt(String encrypted) {
        byte[] combined = Base64.getDecoder().decode(encrypted);
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        
        return new String(cipher.doFinal(ciphertext));
    }
}
```

### What Gets Encrypted

| Data | Storage | Encryption |
|------|---------|------------|
| SSH Private Keys | servers.ssh_key_encrypted | AES-256-GCM |
| SSH Passwords | servers.ssh_password_encrypted | AES-256-GCM |
| Storage Credentials | storage_targets.credentials_encrypted | AES-256-GCM |
| API Keys | api_keys.key_hash | bcrypt (one-way) |
| User Passwords | users.password_hash | bcrypt (one-way) |
| JWT Tokens | In-memory only | HS256 signed |

### What Does NOT Get Encrypted

| Data | Reason |
|------|--------|
| Server IPs | Needed for display and SSH connection |
| User Emails | Needed for login and display |
| Snapshot Metadata | Non-sensitive operational data |
| Audit Logs | Needed for querying and analysis |

---

## Network Security

### TLS/HTTPS Configuration

**Backend (Spring Boot):**
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

**Nginx Configuration:**
```nginx
server {
    listen 443 ssl http2;
    server_name chronovault.example.com;

    ssl_certificate /etc/letsencrypt/live/chronovault.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/chronovault.example.com/privkey.pem;
    
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    # HSTS
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    
    # Other security headers
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "default-src 'self'" always;
}
```

### Rate Limiting

**Configuration:**
```yaml
chronovault:
  rate-limit:
    enabled: true
    requests-per-minute: 60
    burst-size: 10
```

**Implementation:**
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final int requestsPerMinute = 60;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        String clientId = getClientIdentifier(request);
        String key = "rate_limit:" + clientId;
        
        Long requests = redisTemplate.opsForValue().increment(key);
        if (requests == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        
        if (requests > requestsPerMinute) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### CORS Configuration

```yaml
chronovault:
  cors:
    allowed-origins: https://chronovault.example.com
    allowed-methods: GET,POST,PUT,DELETE
    allowed-headers: Authorization,Content-Type
    allow-credentials: true
    max-age: 3600
```

---

## SSH Security

### SSH Key Management

**Key Storage:**
- Private keys encrypted with AES-256-GCM
- Stored in `servers.ssh_key_encrypted`
- Never logged or exposed via API

**Key Rotation:**
- Manual rotation via UI
- Old keys retained until explicitly deleted
- Rotation logged in audit trail

### SSH Connection Security

**Host Key Verification:**
- Default: Trust On First Use (TOFU)
- Production: Configure known_hosts file

**Configuration:**
```yaml
chronovault:
  ssh:
    known-hosts-file: /etc/chronovault/known_hosts
    strict-host-key-checking: true
    timeout: 30000
```

**TOFU Implementation:**
```java
@Component
public class SshConnectionManager {
    
    public Connection getConnection(Server server) {
        // Check if we have a known host key
        String knownHost = getKnownHost(server.getIp());
        
        if (knownHost == null) {
            // First connection - trust and store
            addKnownHost(server.getIp(), fetchHostKey(server));
            log.warn("TOFU: First connection to {}, trusting host key", server.getIp());
        } else {
            // Verify against known host
            verifyHostKey(server.getIp(), knownHost);
        }
        
        // Establish connection
        return createConnection(server);
    }
}
```

### SSH Command Execution

**Safety Measures:**
- Commands executed via SSH are logged
- Dangerous commands blocked (rm -rf /, mkfs, dd)
- Timeout enforced (default: 30 seconds)
- Output captured and logged

**Blocked Commands:**
```java
private static final List<String> BLOCKED_COMMANDS = List.of(
    "rm -rf /",
    "mkfs",
    "dd if=",
    "shutdown",
    "reboot",
    "init 0",
    "init 6",
    ":(){:|:&};:"  # Fork bomb
);
```

---

## API Security

### API Key Authentication

**Use Case:** Agent → Backend communication

**Key Generation:**
- Random 256-bit key
- Stored as bcrypt hash in database
- Prefix stored for identification (first 8 characters)

**Usage:**
```http
POST /api/v1/agent/register
X-API-Key: cv_live_abc123def456...
Content-Type: application/json

{
  "server_name": "web-server-01",
  "ip": "192.168.1.100"
}
```

### Request Validation

**Input Validation:**
```java
public record CreateSnapshotRequest(
    @NotNull(message = "Server ID is required")
    Long serverId,
    
    @Size(max = 200, message = "Title must be less than 200 characters")
    String title,
    
    @Pattern(regexp = "^(FULL|INCREMENTAL|DIFF)$", message = "Invalid type")
    String type
) {}
```

**SQL Injection Prevention:**
- All queries use parameterized statements
- JPA/Hibernate parameter binding
- No string concatenation for queries

### Response Security

**Error Messages:**
```java
// Never expose internal details
catch (Exception e) {
    log.error("Internal error", e);  // Log full details
    return ResponseEntity.status(500)
        .body(ApiResponse.error(50001, "Internal server error"));  // Generic message
}
```

**Sensitive Data Filtering:**
```java
@Component
public class SensitiveDataMasker {
    
    public String mask(String data) {
        if (data == null || data.length() < 8) {
            return "***";
        }
        return data.substring(0, 4) + "***" + data.substring(data.length() - 4);
    }
}
```

---

## Container Security

### Docker Security

**Non-Root User:**
```dockerfile
FROM openjdk:17-jre-slim

RUN groupadd -r chronovault && useradd -r -g chronovault chronovault

WORKDIR /app

COPY --chown=chronovault:chronovault target/*.jar app.jar

USER chronovault

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Read-Only Filesystem:**
```yaml
services:
  backend:
    read_only: true
    tmpfs:
      - /tmp
```

**Resource Limits:**
```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 512M
        reservations:
          memory: 256M
```

### Database Security

**Connection Security:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/chronovault?ssl=true&sslmode=require
    username: chronovault
    password: ${POSTGRES_PASSWORD}
```

**Access Control:**
```sql
-- Create limited user for application
CREATE USER chronovault WITH PASSWORD 'secure-password';

-- Grant specific permissions
GRANT CONNECT ON DATABASE chronovault TO chronovault;
GRANT USAGE ON SCHEMA public TO chronovault;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO chronovault;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO chronovault;

-- Revoke dangerous permissions
REVOKE ALL ON DATABASE chronovault FROM PUBLIC;
```

---

## Threat Model

### Threat 1: Unauthorized Access to Server Data

**Attack Vector:**
- Attacker gains access to ChronoVault
- Accesses snapshot data or state information

**Mitigations:**
- JWT authentication required for all API calls
- Role-based access control
- SSH keys encrypted at rest
- Audit logging of all access

**Residual Risk:**
- Compromised JWT secret
- Insider threat with valid credentials

### Threat 2: SSH Key Compromise

**Attack Vector:**
- Attacker accesses encrypted SSH keys
- Decrypts keys using master key

**Mitigations:**
- Master key stored in environment variable (not in code)
- AES-256-GCM encryption for keys
- Key rotation capability
- Audit logging of key usage

**Residual Risk:**
- Compromised master key
- Access to server with master key

### Threat 3: Man-in-the-Middle Attack

**Attack Vector:**
- Attacker intercepts communication between components

**Mitigations:**
- TLS/HTTPS for all communications
- HMAC signatures for Agent-Backend communication
- SSH host key verification

**Residual Risk:**
- Compromised TLS certificate
- DNS hijacking

### Threat 4: Data Exfiltration

**Attack Vector:**
- Attacker gains access to database
- Exfiltrates sensitive data

**Mitigations:**
- Credentials encrypted in database
- Database access restricted to application only
- Regular backups
- Audit logging

**Residual Risk:**
- Compromised database credentials
- SQL injection (mitigated by parameterized queries)

### Threat 5: Denial of Service

**Attack Vector:**
- Attacker floods system with requests
- Exhausts resources

**Mitigations:**
- Rate limiting
- Resource limits on containers
- Connection pooling
- Timeout enforcement

**Residual Risk:**
- Sophisticated DDoS attack
- Zero-day vulnerability

---

## Security Best Practices

### Deployment Security

1. **Use HTTPS:**
   ```bash
   # Use Let's Encrypt for free certificates
   sudo certbot --nginx -d chronovault.example.com
   ```

2. **Restrict Network Access:**
   ```bash
   # Only allow necessary ports
   sudo ufw allow 22/tcp    # SSH
   sudo ufw allow 80/tcp    # HTTP (redirect to HTTPS)
   sudo ufw allow 443/tcp   # HTTPS
   sudo ufw enable
   ```

3. **Use Strong Secrets:**
   ```bash
   # Generate secure secrets
   JWT_SECRET=$(openssl rand -hex 32)
   MASTER_KEY=$(openssl rand -hex 32)
   RESTIC_PASSWORD=$(openssl rand -hex 32)
   ```

### Operational Security

1. **Rotate Secrets Regularly:**
   - SSH keys: Every 90 days
   - API keys: Every 90 days
   - JWT secret: When team member leaves

2. **Monitor Audit Logs:**
   ```bash
   # Check for suspicious activity
   docker compose exec postgres psql -U chronovault -c \
     "SELECT * FROM audit_logs WHERE action LIKE '%DELETE%' ORDER BY created_at DESC LIMIT 10;"
   ```

3. **Keep Software Updated:**
   ```bash
   # Update dependencies
   cd backend && ./mvnw versions:display-dependency-updates
   cd frontend && npm outdated
   ```

### Development Security

1. **Never Commit Secrets:**
   ```bash
   # Use .env file (gitignored)
   cp .env.example .env
   # Edit .env with real values
   
   # Verify .gitignore
   cat .gitignore | grep .env
   ```

2. **Use Environment Variables:**
   ```java
   // Good
   @Value("${chronovault.jwt.secret}")
   private String jwtSecret;
   
   // Bad
   private String jwtSecret = "my-secret-key";
   ```

3. **Validate All Input:**
   ```java
   @PostMapping
   public ResponseEntity<?> create(@Valid @RequestBody CreateRequest request) {
       // Input is validated by Jakarta Validation
   }
   ```

---

## Known Security Constraints

### Current Limitations

1. **SSH TOFU Mode:**
   - First connection trusts any host key
   - Production should configure known_hosts
   - Risk: Man-in-the-middle on first connection

2. **JWT Token Expiry:**
   - Tokens valid for 24 hours
   - No token revocation mechanism
   - Risk: Stolen token valid until expiry

3. **API Key Scope:**
   - API keys have full access to Agent endpoints
   - No fine-grained permissions
   - Risk: Compromised key grants full access

4. **Database Connection:**
   - SSL not enforced by default in development
   - Production should configure SSL
   - Risk: Unencrypted database traffic in dev

5. **Log Injection:**
   - User input logged without sanitization
   - Potential log injection attacks
   - Risk: Log analysis tools may execute commands

### Planned Improvements

1. **Token Revocation:**
   - Implement token blacklist in Redis
   - Add logout endpoint to invalidate tokens

2. **Fine-Grained Permissions:**
   - Per-server permissions
   - Read-only access for viewers

3. **SSH Certificate Authentication:**
   - Support SSH certificates instead of keys
   - Centralized certificate authority

4. **Webhook Security:**
   - HMAC signatures for webhooks
   - IP allowlisting

---

## Security Updates

### Version History

| Version | Date | Security Fix |
|---------|------|--------------|
| 0.1.0 | 2026-06-06 | Initial release with security controls |

### Update Process

1. Monitor security advisories:
   - GitHub Security Advisories
   - Dependency vulnerability scanners

2. Apply updates promptly:
   ```bash
   # Backend
   cd backend && ./mvnw versions:display-plugin-updates
   
   # Frontend
   cd frontend && npm audit
   ```

3. Test updates in staging before production

---

## Reporting Security Issues

### Responsible Disclosure

If you discover a security vulnerability, please report it responsibly:

1. **Email:** security@chronovault.com
2. **Do NOT** open a public GitHub issue
3. **Include:**
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

### Response Timeline

- **Acknowledgment:** Within 24 hours
- **Initial Assessment:** Within 72 hours
- **Fix Release:** Within 30 days for critical vulnerabilities

### Bug Bounty

Currently, ChronoVault does not offer a bug bounty program. We appreciate responsible disclosures and will acknowledge contributors in release notes.

---

## References

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
