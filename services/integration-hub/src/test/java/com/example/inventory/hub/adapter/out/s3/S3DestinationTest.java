package com.example.inventory.hub.adapter.out.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.hub.config.HubProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3DestinationTest {

    private static final TenantId TENANT = new TenantId("acme");

    private S3Client s3;
    private S3Destination destination;

    @BeforeEach
    void setUp() {
        s3 = Mockito.mock(S3Client.class);
        HubProperties.S3Config config =
                new HubProperties.S3Config(
                        "outbound-bucket", "ap-northeast-1", "retail-order-csv", null, "text/csv");
        destination = new S3Destination(s3, config);
    }

    @Test
    void writeBatch_は_行を改行で結合した_1_オブジェクトを_PUT() throws Exception {
        destination.writeBatch(TENANT, List.of("ord-1,sku-a,3", "ord-1,sku-b,1"));

        ArgumentCaptor<PutObjectRequest> reqCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(reqCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest req = reqCaptor.getValue();
        assertThat(req.bucket()).isEqualTo("outbound-bucket");
        assertThat(req.contentType()).isEqualTo("text/csv");
        assertThat(req.key()).startsWith("retail-order-csv/acme/").endsWith(".csv");

        try (var in = bodyCaptor.getValue().contentStreamProvider().newStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("ord-1,sku-a,3\nord-1,sku-b,1\n");
        }
    }

    @Test
    void writeBatch_は_空リストでは_PUT_を呼ばない() {
        destination.writeBatch(TENANT, List.of());

        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void write_単発も_1_オブジェクトとして_PUT() {
        destination.write(TENANT, "single-line");

        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(PutObjectRequest.class), bodyCaptor.capture());
    }

    @Test
    void key_は_keyPrefix_無しでも_tenantId_配下の_yyyy_MM_dd_partition_に置かれる() {
        HubProperties.S3Config noPrefix =
                new HubProperties.S3Config("b", "ap-northeast-1", "", null, "text/csv");
        S3Destination noPrefixDest = new S3Destination(s3, noPrefix);

        noPrefixDest.write(TENANT, "x");

        ArgumentCaptor<PutObjectRequest> reqCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(reqCaptor.capture(), any(RequestBody.class));
        assertThat(reqCaptor.getValue().key()).startsWith("acme/").endsWith(".csv");
    }
}
