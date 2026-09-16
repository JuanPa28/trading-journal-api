package com.tradingjournal.api.service;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentStorageServiceTest {

    @Mock
    private ObjectStorageClient client;

    private AttachmentStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new AttachmentStorageService(client, "my-namespace", "my-bucket");
    }

    @Test
    void store_uploadsContentAndReturnsAGeneratedObjectName() {
        when(client.putObject(any(PutObjectRequest.class))).thenReturn(PutObjectResponse.builder().build());

        String objectName = storageService.store("{\"trade\":1}");

        assertThat(objectName).endsWith(".json");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture());
        assertThat(captor.getValue().getNamespaceName()).isEqualTo("my-namespace");
        assertThat(captor.getValue().getBucketName()).isEqualTo("my-bucket");
        assertThat(captor.getValue().getObjectName()).isEqualTo(objectName);
    }

    @Test
    void retrieve_returnsStoredContent_whenObjectExists() {
        String content = "{\"trade\":1}";
        GetObjectResponse response = GetObjectResponse.builder()
                .inputStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .build();
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        String result = storageService.retrieve("abc.json");

        assertThat(result).isEqualTo(content);
    }

    @Test
    void retrieve_returnsNull_whenObjectDoesNotExist() {
        when(client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new BmcException(404, "NotFound", "Object not found", "opc-request-id"));

        String result = storageService.retrieve("missing.json");

        assertThat(result).isNull();
    }
}
