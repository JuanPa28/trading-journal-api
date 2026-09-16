package com.tradingjournal.api.service;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Guarda y recupera adjuntos (el JSON acumulado del flujo entre las 3 nubes,
// u otro archivo que el orquestador quiera anexar) en un bucket real de OCI
// Object Storage — este es el componente transversal de Object Storage que
// le corresponde a Juan Pablo construir para todo el grupo.
@Service
public class AttachmentStorageService {

    private final ObjectStorageClient client;
    private final String namespace;
    private final String bucketName;

    public AttachmentStorageService(
            ObjectStorageClient client,
            @Value("${oci.object-storage.namespace}") String namespace,
            @Value("${oci.object-storage.bucket}") String bucketName) {
        this.client = client;
        this.namespace = namespace;
        this.bucketName = bucketName;
    }

    public String store(String content) {
        String objectName = UUID.randomUUID().toString() + ".json";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectName)
                .contentLength((long) bytes.length)
                .putObjectBody(new ByteArrayInputStream(bytes))
                .build();
        client.putObject(request);
        return objectName;
    }

    public String retrieve(String objectName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectName)
                .build();
        try {
            GetObjectResponse response = client.getObject(request);
            try (InputStream in = response.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
