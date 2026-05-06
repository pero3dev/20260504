package com.example.inventory.hub.adapter.out.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.hub.application.port.out.OutboundDestination;

/**
 * ローカルファイルに 1 行追記する {@link OutboundDestination} 実装(MVP / テスト用)。
 *
 * <p>パスは {@code <baseDir>/<tenantId>/<fileName>}。ディレクトリは初回呼出時に作成。
 *
 * <p>本実装は MVP の参照だが、将来 S3 / SFTP / AS2 が同 port で並ぶときの「Local fallback」 として残る価値がある(オフライン開発、CI
 * テスト用、本番前検証など)。
 */
public class LocalFileDestination implements OutboundDestination {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileDestination.class);

    private final Path baseDir;
    private final String fileName;

    public LocalFileDestination(Path baseDir, String fileName) {
        this.baseDir = baseDir;
        this.fileName = fileName;
    }

    @Override
    public void write(TenantId tenantId, String formattedLine) {
        Path tenantDir = baseDir.resolve(tenantId.value());
        Path file = tenantDir.resolve(fileName);
        try {
            Files.createDirectories(tenantDir);
            Files.writeString(
                    file,
                    formattedLine + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            LOG.debug("OutboundFile に追記 path={} line={}", file, formattedLine);
        } catch (IOException e) {
            // 書込失敗は呼出側(Listener)に伝搬し、ack しないことで Spring Kafka の retry に任せる。
            throw new UncheckedIOException("OutboundFile への追記に失敗 path=" + file, e);
        }
    }
}
