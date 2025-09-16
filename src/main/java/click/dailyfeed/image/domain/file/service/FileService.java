package click.dailyfeed.image.domain.file.service;

import click.dailyfeed.code.domain.image.type.ImageExtensionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${images.max-file-size:10485760}") // 1MB default
    private long maxFileSize;

    // 지원되는 이미지 포맷
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/bmp", "image/gif"
    );

    // 이미지 파일 시그니처 검증용
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF_SIGNATURE = {0x47, 0x49, 0x46};
    private static final byte[] WEBP_SIGNATURE = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] BMP_SIGNATURE = {0x42, 0x4D}; // "BM"


    public void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        // 파일 크기 검증
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size: %d bytes", maxFileSize)
            );
        }

        // Content-Type 검증
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_FORMATS.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file format: " + contentType);
        }

        // 파일 시그니처 검증 (실제 이미지 파일인지 확인)
        validateImageSignature(file);

        // Thumbnailator를 이용한 이미지 유효성 검증
        validateImageWithThumbnailator(file);
    }

    public Path createDirectories(String imageRoot) throws IOException {
        Path imageDir = Paths.get(imageRoot);
        Files.createDirectories(imageDir);
        return imageDir;
    }

    public File resolveFileOrThrow(Path imageDir, String fileName, ImageExtensionType imageExtensionType) throws IOException {
        return imageDir.resolve(imageExtensionType.withFileName(fileName)).toFile();
    }

    public Path resolvePathOrThrow(String imageDir, String fileName, ImageExtensionType imageExtensionType) throws IOException {
        return Paths.get(imageDir).resolve(fileName + ".jpg");
    }

    /// 절대 경로 반환 (경로 순회 방지)
    public Path normalizePathOrThrow(Path path) throws IOException {
        return path.normalize();
    }

    public void createThumbnailOriginalOrThrow(
            MultipartFile file, File outputFile, ImageExtensionType extensionType,
            int maxWidth, int maxHeight, Double quality
    ) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            Thumbnails.of(inputStream)
                    .size(maxWidth, maxHeight)
                    .outputQuality(quality)
                    .outputFormat(extensionType.getExtension().toLowerCase())
                    .toFile(outputFile);
        } catch (Exception e) {
            throw new IOException("FAILED TO PROCESS IMAGE (ORIGINAL)" ,e);
        }
    }

    public void createThumbnailOrThrow(
            File file, File outputFile, ImageExtensionType extensionType,
            int maxWidth, int maxHeight, Double quality
    ) throws IOException {
        try {
            Thumbnails.of(file)
                    .crop(Positions.CENTER)
                    .size(maxWidth, maxHeight)
                    .outputQuality(quality)
                    .outputFormat(extensionType.getExtension().toLowerCase())
                    .toFile(outputFile);
        } catch (Exception e) {
            throw new IOException("FAILED TO PROCESS IMAGE (THUMBNAIL)" ,e);
        }
    }

    public void cleanUpFileOrThrow(File... files){
        for (File file : files) {
            if (file != null && file.exists()) {
                try {
                    Files.delete(file.toPath());
                    log.debug("Cleaned up file: {}", file.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("Failed to cleanup file: {}", file.getAbsolutePath(), e);
                }
            }
        }
    }

    /// helpers ///

    private void validateImageSignature(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[12]; // 가장 긴 시그니처에 맞춰 설정
            int bytesRead = inputStream.read(header);

            if (bytesRead < 3) {
                throw new IllegalArgumentException("File too small to be a valid image");
            }

            // JPEG 검증
            if (startsWith(header, JPEG_SIGNATURE)) {
                return;
            }

            // PNG 검증
            if (startsWith(header, PNG_SIGNATURE)) {
                return;
            }

            // GIF 검증
            if (startsWith(header, GIF_SIGNATURE)) {
                return;
            }

            // BMP 검증
            if (startsWith(header, BMP_SIGNATURE)) {
                return;
            }

            // WEBP 검증 (RIFF...WEBP 구조)
            if (startsWith(header, WEBP_SIGNATURE) && bytesRead >= 12) {
                byte[] webpSignature = {0x57, 0x45, 0x42, 0x50}; // "WEBP"
                if (startsWith(header, 8, webpSignature)) {
                    return;
                }
            }

            throw new IllegalArgumentException("Invalid image file signature");
        }
    }

    private void validateImageWithThumbnailator(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            // Thumbnailator로 이미지 읽기 테스트
            // 실제로 처리하지는 않고 유효성만 검증
            Thumbnails.of(inputStream)
                    .scale(0.1) // 아주 작게 스케일링하여 메모리 사용량 최소화
                    .outputQuality(0.1)
                    .outputFormat("jpg")
                    .toOutputStream(new ByteArrayOutputStream()); // 실제 저장하지 않음
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or corrupted image file", e);
        }
    }

    private boolean startsWith(byte[] array, byte[] prefix) {
        return startsWith(array, 0, prefix);
    }

    private boolean startsWith(byte[] array, int offset, byte[] prefix) {
        if (array.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (array[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
