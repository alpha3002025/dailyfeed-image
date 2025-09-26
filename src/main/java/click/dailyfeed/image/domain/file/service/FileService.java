package click.dailyfeed.image.domain.file.service;

import click.dailyfeed.code.domain.image.exception.*;
import click.dailyfeed.code.domain.image.type.ImageExtensionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

        // 디버깅 로그 추가
        log.debug("Validating file - Name: {}, Size: {}, ContentType: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

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
        log.debug("File signature validation passed");
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
        return Paths.get(imageDir).resolve(imageExtensionType.withFileName(fileName));
    }

    /// 절대 경로 반환 (경로 순회 방지)
    public Path normalizePathOrThrow(Path path) throws IOException {
        return path.normalize();
    }

    public void createThumbnailOriginalOrThrow(
            MultipartFile file, File outputFile, ImageExtensionType extensionType,
            int maxWidth, int maxHeight, Double quality
    ) throws IOException {
        log.debug("Processing original image - Name: {}, Size: {}, ContentType: {}, Output: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType(), outputFile.getAbsolutePath());

        try {
            // 바이트 배열로 읽어서 처리 (InputStream 재사용 문제 방지)
            byte[] imageBytes = file.getBytes();
            log.debug("Read {} bytes from file", imageBytes.length);

            if (imageBytes.length == 0) {
                throw new EmptyImageFileException();
            }

            // ImageIO를 사용한 대체 방법 시도
            try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
                BufferedImage image = ImageIO.read(bais);
                if (image == null) {
                    log.error("ImageIO.read() returned null - file may not be a valid image");
                    throw new CorruptedImageException();
                }
                log.debug("ImageIO successfully read image: width={}, height={}", image.getWidth(), image.getHeight());

                // Thumbnailator로 처리
                Thumbnails.of(image)
                        .size(maxWidth, maxHeight)
                        .outputQuality(quality)
                        .outputFormat(extensionType.getExtension().toLowerCase())
                        .toFile(outputFile);
            }

            log.debug("Successfully created original image at: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to process original image: {}", e.getMessage(), e);
            throw new ImageProcessingFailException();
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
            throw new ImageProcessingFailException();
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
                throw new FileTooSmallException();
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

            throw new InvalidImageSignatureException();
        }
    }

    private void validateImageWithThumbnailator(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            log.debug("Starting Thumbnailator validation for file: {}", file.getOriginalFilename());

            // InputStream이 실제로 데이터를 가지고 있는지 확인
            if (inputStream.available() == 0) {
                log.error("InputStream is empty for file: {}", file.getOriginalFilename());
                throw new IllegalArgumentException("File stream is empty");
            }

            // Thumbnailator로 이미지 읽기 테스트
            // 실제로 처리하지는 않고 유효성만 검증
            Thumbnails.of(inputStream)
                    .scale(0.1) // 아주 작게 스케일링하여 메모리 사용량 최소화
                    .outputQuality(0.1)
                    .outputFormat("jpg")
                    .toOutputStream(new ByteArrayOutputStream()); // 실제 저장하지 않음
        } catch (Exception e) {
            log.error("Thumbnailator validation failed: {}", e.getMessage(), e);
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
