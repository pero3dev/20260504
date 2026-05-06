package com.example.inventory.hub.adapter.out.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.inventory.commons.tenant.TenantId;

class LocalFileDestinationTest {

    @Test
    void テナントごとにディレクトリを切って_ファイルに追記される(@TempDir Path baseDir) throws Exception {
        LocalFileDestination dest = new LocalFileDestination(baseDir, "out.csv");

        dest.write(new TenantId("tenant-a"), "row-1");
        dest.write(new TenantId("tenant-a"), "row-2");
        dest.write(new TenantId("tenant-b"), "row-3");

        Path tenantA = baseDir.resolve("tenant-a").resolve("out.csv");
        Path tenantB = baseDir.resolve("tenant-b").resolve("out.csv");

        assertThat(Files.readAllLines(tenantA)).containsExactly("row-1", "row-2");
        assertThat(Files.readAllLines(tenantB)).containsExactly("row-3");
    }

    @Test
    void ディレクトリが未作成でも初回呼出で作られる(@TempDir Path baseDir) throws Exception {
        Path nested = baseDir.resolve("nested").resolve("layer");
        LocalFileDestination dest = new LocalFileDestination(nested, "out.csv");

        dest.write(new TenantId("tenant-a"), "hello");

        Path file = nested.resolve("tenant-a").resolve("out.csv");
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readAllLines(file)).isEqualTo(List.of("hello"));
    }
}
