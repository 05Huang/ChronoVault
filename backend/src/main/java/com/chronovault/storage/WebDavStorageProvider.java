package com.chronovault.storage;

import com.chronovault.entity.StorageTarget;
import com.chronovault.security.CredentialEncryptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebDavStorageProvider implements StorageProvider {

    private final CredentialEncryptor encryptor;
    private final ObjectMapper objectMapper;

    @Override
    public StorageTarget.StorageType getType() {
        return StorageTarget.StorageType.WEBDAV;
    }

    @Override
    public boolean supports(StorageTarget.StorageType type) {
        return type == StorageTarget.StorageType.WEBDAV;
    }

    @Override
    public void initialize(StorageTarget target) throws Exception {
        Sardine sardine = buildClient(target);
        String baseUrl = target.getEndpoint();
        if (!sardine.exists(baseUrl)) {
            sardine.createDirectory(baseUrl);
        }
    }

    @Override
    public void upload(StorageTarget target, String remotePath, InputStream data) throws Exception {
        Sardine sardine = buildClient(target);
        String url = target.getEndpoint() + "/" + remotePath;
        sardine.put(url, data);
    }

    @Override
    public InputStream download(StorageTarget target, String remotePath) throws Exception {
        Sardine sardine = buildClient(target);
        String url = target.getEndpoint() + "/" + remotePath;
        return sardine.get(url);
    }

    @Override
    public void delete(StorageTarget target, String remotePath) throws Exception {
        Sardine sardine = buildClient(target);
        String url = target.getEndpoint() + "/" + remotePath;
        sardine.delete(url);
    }

    @Override
    public StorageHealthInfo getHealth(StorageTarget target) throws Exception {
        Sardine sardine = buildClient(target);
        boolean exists = sardine.exists(target.getEndpoint());
        return exists ?
                new StorageHealthInfo("健康", "N/A", "~20ms", "50 MB/s", 0) :
                new StorageHealthInfo("ERROR", "0", "0ms", "0 MB/s", 1);
    }

    @Override
    public long getUsedBytes(StorageTarget target) throws Exception {
        return target.getUsedBytes() != null ? target.getUsedBytes() : 0;
    }

    @Override
    public long getTotalBytes(StorageTarget target) throws Exception {
        return target.getTotalBytes() != null ? target.getTotalBytes() : 0;
    }

    private Sardine buildClient(StorageTarget target) throws Exception {
        Map<String, String> creds = objectMapper.readValue(encryptor.decrypt(target.getCredentialsEncrypted()),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        return SardineFactory.begin(creds.get("username"), creds.get("password"));
    }
}
