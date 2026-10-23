package io.github.foundationgames.automobility.client.lighting;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;

/** Exact local source admission, once per pack instance; never a per-frame directory scan. */
final class PinnedShaderTree {
    private static final int MAX_NODES = 4096;
    private static final int MAX_FILES = 2048;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    private PinnedShaderTree() { }

    static boolean matches(Path root, int expectedFiles, long expectedBytes, String expectedHash) {
        if (root == null || expectedFiles < 1 || expectedFiles > MAX_FILES
                || expectedBytes < 0 || expectedBytes > MAX_BYTES || expectedHash == null
                || !expectedHash.matches("[0-9a-f]{64}")) return false;
        try {
            Path normalized = root.toAbsolutePath().normalize();
            // Reject every path-component symlink, not just the tree's leaves.
            for (Path part = normalized; part != null; part = part.getParent()) {
                if (!ordinary(Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS))) return false;
            }
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) return false;
            var files = new ArrayList<Path>();
            int nodes = 0;
            long declaredBytes = 0;
            try (var walk = Files.walk(normalized, 16)) {
                var iterator = walk.iterator();
                while (iterator.hasNext()) {
                    Path node = iterator.next();
                    if (++nodes > MAX_NODES || !ordinary(Files.readAttributes(node, BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS))) return false;
                    if (Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
                        if (normalized.relativize(node).getNameCount() >= 16) return false;
                        continue;
                    }
                    if (!Files.isRegularFile(node, LinkOption.NOFOLLOW_LINKS)) return false;
                    long bytes = Files.size(node);
                    if (bytes > MAX_FILE_BYTES || (declaredBytes += bytes) > MAX_BYTES
                            || files.size() >= MAX_FILES) return false;
                    files.add(node);
                }
            }
            if (files.size() != expectedFiles || declaredBytes != expectedBytes) return false;
            files.sort(Comparator.comparing(file -> relative(normalized, file)));
            MessageDigest tree = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32768];
            long actualBytes = 0;
            for (Path file : files) {
                String relative = relative(normalized, file);
                if (relative.length() > 1024 || !ordinary(Files.readAttributes(file, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS))) return false;
                tree.update(relative.getBytes(StandardCharsets.UTF_8));
                tree.update((byte) 0);
                MessageDigest content = MessageDigest.getInstance("SHA-256");
                long fileBytes = 0;
                try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        fileBytes += count;
                        actualBytes += count;
                        if (fileBytes > MAX_FILE_BYTES || actualBytes > expectedBytes) return false;
                        content.update(buffer, 0, count);
                    }
                }
                tree.update(content.digest());
            }
            return actualBytes == expectedBytes && expectedHash.equals(HexFormat.of().formatHex(tree.digest()));
        } catch (Exception rejected) {
            return false;
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    // Windows junctions are isDirectory AND isOther, not necessarily isSymbolicLink.
    static boolean ordinary(BasicFileAttributes attributes) {
        return !attributes.isSymbolicLink() && !attributes.isOther()
                && (attributes.isDirectory() || attributes.isRegularFile());
    }
}
