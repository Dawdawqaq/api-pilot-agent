package com.dochelper.maintenance.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 在任何恢复写入前验证对象范围、长度与哈希。 */
class ObjectArchiveToolTest {
    @TempDir(factory = WorkspaceTemporaryDirectory.class) Path directory;
    /** 临时目录放入构建输出，避免 Windows 用户临时目录的权限差异。 */
    public static class WorkspaceTemporaryDirectory implements org.junit.jupiter.api.io.TempDirFactory {
        @Override public Path createTempDirectory(org.junit.jupiter.api.extension.AnnotatedElementContext context,
                org.junit.jupiter.api.extension.ExtensionContext extension) throws java.io.IOException {
            return Files.createTempDirectory(Path.of("target"), "object-archive-");
        }
    }
    private Path archive(String bucket, String key, String hash, boolean includeObject) throws Exception {
        byte[] content = "业务规则正文".getBytes(StandardCharsets.UTF_8);
        var entry = new ObjectArchiveTool.ObjectEntry(key,"objects/0",content.length,"text/plain",hash);
        Path file = directory.resolve("objects.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            if (includeObject) { zip.putNextEntry(new ZipEntry("objects/0")); zip.write(content); zip.closeEntry(); }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(new ObjectMapper().writeValueAsBytes(new ObjectArchiveTool.Manifest(1,bucket,List.of(entry)))); zip.closeEntry();
        }
        return file;
    }
    private String hash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("业务规则正文".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void shouldVerifyCompleteArchiveWithChineseObjectNames() throws Exception {
        assertThat(ObjectArchiveTool.verify(archive("dochelper-files","projects/1/documents/2/规则.txt",hash(),true)).objects()).hasSize(1);
    }
    @Test void shouldRejectCorruptedOrMissingObject() throws Exception {
        Path corrupted = archive("dochelper-files","projects/1/rules.txt","bad-hash",true);
        assertThatThrownBy(() -> ObjectArchiveTool.verify(corrupted)).hasMessageContaining("校验失败");
        Path missing = archive("dochelper-files","projects/1/rules.txt",hash(),false);
        assertThatThrownBy(() -> ObjectArchiveTool.verify(missing)).hasMessageContaining("缺失");
    }
    @Test void shouldRejectOtherBucketAndUnsafeObjectPaths() throws Exception {
        Path other = archive("other-bucket","projects/1/rules.txt",hash(),true);
        assertThatThrownBy(() -> ObjectArchiveTool.verify(other)).hasMessageContaining("范围不合法");
        Path unsafe = archive("dochelper-files","../rules.txt",hash(),true);
        assertThatThrownBy(() -> ObjectArchiveTool.verify(unsafe)).hasMessageContaining("清单不合法");
    }
}
