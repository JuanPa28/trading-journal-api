package com.tradingjournal.api.config;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// Componente de Object Storage que le toca a Juan Pablo construir para todo
// el grupo (los adjuntos generados durante el flujo del orquestador). Usa la
// misma llave API que ya usa el resto del proyecto para hablar con OCI — no
// un archivo de wallet aparte, todo viene de variables de entorno.
@Configuration
public class ObjectStorageConfig {

    @Bean
    public ObjectStorageClient objectStorageClient(
            @Value("${oci.tenancy-id}") String tenancyId,
            @Value("${oci.user-id}") String userId,
            @Value("${oci.fingerprint}") String fingerprint,
            @Value("${oci.private-key}") String privateKeyPem,
            @Value("${oci.region}") String region) {
        SimpleAuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(tenancyId)
                .userId(userId)
                .fingerprint(fingerprint)
                .privateKeySupplier(() -> (InputStream) new ByteArrayInputStream(privateKeyPem.getBytes(StandardCharsets.UTF_8)))
                .region(Region.fromRegionId(region))
                .build();
        return ObjectStorageClient.builder().build(provider);
    }
}
