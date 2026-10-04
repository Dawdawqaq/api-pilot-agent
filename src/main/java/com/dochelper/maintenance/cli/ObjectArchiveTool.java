package com.dochelper.maintenance.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.*;

/** 离线备份工具，不启动 Spring 或 Agent；访问范围固定为 DocHelper 的 Bucket。 */
public final class ObjectArchiveTool {
    private static final String BUCKET = "dochelper-files";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ObjectArchiveTool() { }

    public static void main(String[] args) {
        try {
            if (args.length != 2 || !List.of("backup", "verify", "restore").contains(args[0]))
                throw new IllegalArgumentException("请指定 backup/verify/restore 与归档路径");
            Path archive = Path.of(args[1]).toAbsolutePath();
            if ("verify".equals(args[0])) { verify(archive); System.out.println("对象归档完整性校验通过"); return; }
            var http = new okhttp3.OkHttpClient.Builder().connectTimeout(java.time.Duration.ofSeconds(5))
                    .readTimeout(java.time.Duration.ofSeconds(60)).writeTimeout(java.time.Duration.ofSeconds(60)).build();
            try {
                var client = MinioClient.builder().endpoint(environment("MINIO_ENDPOINT", "http://localhost:9000"))
                        .credentials(environment("MINIO_ACCESS_KEY", "dochelper"), environment("MINIO_SECRET_KEY", "dochelper-local-secret"))
                        .httpClient(http).build();
                if ("backup".equals(args[0])) backup(client, archive);
                else restore(client, archive);
            } finally {
                // 独立命令及时关闭网络资源，避免备份已完成却仍等待连接池线程退出。
                http.dispatcher().executorService().shutdown(); http.connectionPool().evictAll();
            }
        } catch (Exception exception) {
            System.err.println("对象归档操作失败，请检查文件、凭据和 MinIO（" + exception.getClass().getSimpleName() + "）");
            System.exit(1);
        }
    }

    static void backup(MinioClient client, Path archive) throws Exception {
        List<ObjectEntry> entries = new ArrayList<>();
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var result : client.listObjects(ListObjectsArgs.builder().bucket(BUCKET).recursive(true).build())) {
                var item = result.get();
                if (item.isDir()) continue;
                var stat = client.statObject(StatObjectArgs.builder().bucket(BUCKET).object(item.objectName()).build());
                String entryName = "objects/" + entries.size();
                zip.putNextEntry(new ZipEntry(entryName));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size;
                try (var input = client.getObject(GetObjectArgs.builder().bucket(BUCKET).object(item.objectName()).build())) {
                    size = copyAndHash(input, zip, digest);
                }
                zip.closeEntry();
                if (size != stat.size()) throw new IllegalStateException("备份期间对象发生变化，请停止写入后重试");
                entries.add(new ObjectEntry(item.objectName(), entryName, size, stat.contentType(), HexFormat.of().formatHex(digest.digest())));
            }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(MAPPER.writeValueAsBytes(new Manifest(1, BUCKET, entries))); zip.closeEntry();
        }
        verify(archive);
        System.out.println("已备份 DocHelper 原始对象 " + entries.size() + " 个");
    }

    static Manifest verify(Path archive) throws Exception {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null || manifestEntry.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("归档清单缺失或过大");
            Manifest manifest;
            try (var input = zip.getInputStream(manifestEntry)) { manifest = MAPPER.readValue(input, Manifest.class); }
            if (manifest.version() != 1 || !BUCKET.equals(manifest.bucket()) || manifest.objects() == null) throw new IllegalArgumentException("归档范围不合法");
            var keys = new java.util.HashSet<String>();
            for (ObjectEntry entry : manifest.objects()) {
                if (entry.key() == null || entry.key().isBlank() || entry.key().startsWith("/")
                        || entry.key().contains("\\") || java.util.Arrays.asList(entry.key().split("/")).contains("..")
                        || entry.key().chars().anyMatch(Character::isISOControl) || !keys.add(entry.key())
                        || !entry.entry().matches("objects/\\d+") || entry.size() < 0) throw new IllegalArgumentException("对象清单不合法");
                ZipEntry stored = zip.getEntry(entry.entry());
                if (stored == null || stored.getSize() != entry.size()) throw new IllegalArgumentException("归档对象缺失或长度变化");
                var digest = MessageDigest.getInstance("SHA-256");
                try (var input = zip.getInputStream(stored)) { copyAndHash(input, java.io.OutputStream.nullOutputStream(), digest); }
                if (!HexFormat.of().formatHex(digest.digest()).equals(entry.sha256())) throw new IllegalArgumentException("归档对象校验失败");
            }
            return manifest;
        }
    }

    static void restore(MinioClient client, Path archive) throws Exception {
        Manifest manifest = verify(archive);
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) throw new IllegalStateException("DocHelper Bucket 尚未初始化");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ObjectEntry entry : manifest.objects()) {
                try (var input = zip.getInputStream(zip.getEntry(entry.entry()))) {
                    client.putObject(PutObjectArgs.builder().bucket(BUCKET).object(entry.key())
                            .stream(input, entry.size(), -1).contentType(entry.contentType()).build());
                }
            }
        }
        System.out.println("已恢复 DocHelper 原始对象 " + manifest.objects().size() + " 个；未删除备份以外的对象");
    }

    private static long copyAndHash(InputStream input, java.io.OutputStream output, MessageDigest digest) throws Exception {
        byte[] buffer = new byte[65536]; int read; long total = 0;
        while ((read = input.read(buffer)) != -1) { output.write(buffer, 0, read); digest.update(buffer, 0, read); total += read; }
        return total;
    }
    private static String environment(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
    public record Manifest(int version, String bucket, List<ObjectEntry> objects) { }
    public record ObjectEntry(String key, String entry, long size, String contentType, String sha256) { }
}
