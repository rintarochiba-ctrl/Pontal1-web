package com.example.pontalweb.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

//AuditLogBatchのテスト。PontalのAPI(GET /api/audit-log-batches)のJSONを、正しく読み取れることを確認する
class AuditLogBatchTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesPontalApiJsonList_includingProcessingBatchWithNullCount() throws Exception {
        String json = "[{\"batchId\":12,\"periodStart\":\"2026-10-05\",\"periodEnd\":\"2026-10-11\","
                + "\"status\":\"COMPLETED\",\"recordCount\":100},"
                + "{\"batchId\":13,\"periodStart\":\"2026-10-19\",\"periodEnd\":\"2026-10-25\","
                + "\"status\":\"PROCESSING\",\"recordCount\":null}]";

        List<AuditLogBatch> batches = objectMapper.readValue(json, new TypeReference<>() { });

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0).getBatchId()).isEqualTo(12L);
        assertThat(batches.get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(batches.get(0).getRecordCount()).isEqualTo(100);
        //処理中は、件数がまだない(null)。エラーにならずに読めること
        assertThat(batches.get(1).getStatus()).isEqualTo("PROCESSING");
        assertThat(batches.get(1).getRecordCount()).isNull();
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        //APIに項目が増えても、読み取りが壊れない
        String json = "{\"batchId\":1,\"periodStart\":\"2026-10-05\",\"periodEnd\":\"2026-10-11\","
                + "\"status\":\"COMPLETED\",\"recordCount\":0,\"filePath\":\"audit-logs/x.csv\"}";

        AuditLogBatch batch = objectMapper.readValue(json, AuditLogBatch.class);

        assertThat(batch.getBatchId()).isEqualTo(1L);
        assertThat(batch.getRecordCount()).isZero(); //0件の週は、0(nullではない)
    }

    @Test
    void periodText_showsStartAndEndWithSlashes() {
        AuditLogBatch batch = new AuditLogBatch(1L, "2026-10-05", "2026-10-11", "COMPLETED", 100);

        assertThat(batch.getPeriodText()).isEqualTo("2026/10/05 〜 2026/10/11");
    }
}
