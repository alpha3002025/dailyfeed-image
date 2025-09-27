package click.dailyfeed.image.domain.image.service;

import click.dailyfeed.code.domain.image.type.ImageExtensionType;
import click.dailyfeed.code.domain.member.member.dto.MemberProfileDto;
import click.dailyfeed.image.domain.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageStorageService {
    @Value("${images.upload-root}")
    private String imageRoot;

    @Value("${images.max-width:500}")
    private int maxWidth;

    @Value("${images.max-height:500}")
    private int maxHeight;

    @Value("${images.thumbnail-size:150}")
    private int thumbnailSize;

    @Value("${images.quality:0.85}")
    private double quality;

    private final FileService fileService;

    // 최대 이미지 크기 (픽셀)
    private static final int MAX_PIXELS = 50_000_000; // 50MP

    public String store(MultipartFile file) throws IOException {
        fileService.validateFile(file);

        String imageId = UUID.randomUUID().toString();
        Path imageDir = Paths.get(imageRoot);

        // 디렉토리 생성
        fileService.createDirectories(imageRoot);

        File originalFile = null;
        File thumbnailFile = null;

        try {
            // 임시 파일 생성
            originalFile = fileService.resolveFileOrThrow(imageDir, imageId, ImageExtensionType.PNG);
            thumbnailFile = fileService.resolveFileOrThrow(imageDir, imageId + "-thumbnail", ImageExtensionType.PNG);

            fileService.createThumbnailOriginalOrThrow(
                    file, originalFile, ImageExtensionType.PNG,
                    maxWidth, maxHeight, quality
            );

            fileService.createThumbnailOrThrow(
                    originalFile, thumbnailFile, ImageExtensionType.PNG,
                    thumbnailSize, thumbnailSize, quality
            );

            return imageId;
        } catch (Exception e) {
            // 실패 시 생성된 파일들 정리
            fileService.cleanUpFileOrThrow(originalFile, thumbnailFile);
            log.error("Failed to store image: {}", e.getMessage(), e);
            throw new IOException("Failed to store image: " + e.getMessage(), e);
        }

    }

    public Resource get(String imageId, Boolean isThumbnail) {
        if (imageId == null || imageId.trim().isEmpty()) {
            log.warn("Invalid image ID provided");
            return null;
        }

        // 경로 순회 공격 방지
        if (imageId.contains("..") || imageId.contains("/") || imageId.contains("\\")) {
            log.warn("Invalid image ID format: {}", imageId);
            return null;
        }

        try {
            String suffix = Boolean.TRUE.equals(isThumbnail) ? "-thumbnail" : "";
            Path filePath = fileService.resolvePathOrThrow(imageRoot, imageId + suffix, ImageExtensionType.PNG);

            // 파일이 imageRoot 디렉토리 내에 있는지 확인
            Path normalizedPath = fileService.normalizePathOrThrow(filePath);
            Path rootPath = fileService.normalizePathOrThrow(Paths.get(imageRoot));

            if (!normalizedPath.startsWith(rootPath)) { // 경로 순회 (Path Traversal 공격) 방지 (보안)
                log.warn("Path traversal attempt detected: {}", imageId);
                return null;
            }

            Resource resource = new UrlResource(normalizedPath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                log.debug("Image not found or not readable: {}", normalizedPath);
                return null;
            }

        } catch (MalformedURLException e) {
            log.error("Malformed URL for image ID: {}", imageId, e);
            return null;
        } catch (IOException e) {
            log.error("IO Exception ", e);
            return null;
        } catch (Exception e) {
            log.error("Error retrieving image: {}", imageId, e);
            return null;
        }
    }

    public void deleteImages(MemberProfileDto.ImageDeleteBulkRequest imageDeleteBulkRequest) {
        if (imageDeleteBulkRequest == null || imageDeleteBulkRequest.getImageUrls() == null) {
            return;
        }

        for (String imageUrl : imageDeleteBulkRequest.getImageUrls()) {
            try {
                String viewId = extractViewIdFromUrl(imageUrl);
                if (viewId == null || viewId.trim().isEmpty()) {
                    log.warn("Invalid viewId extracted from URL: {}", imageUrl);
                    continue;
                }

                if (viewId.contains("..") || viewId.contains("/") || viewId.contains("\\")) {
                    log.warn("Invalid viewId format: {}", viewId);
                    continue;
                }

                Path originalPath = fileService.resolvePathOrThrow(imageRoot, viewId, ImageExtensionType.PNG);
                Path thumbnailPath = fileService.resolvePathOrThrow(imageRoot, viewId + "-thumbnail", ImageExtensionType.PNG);

                fileService.cleanUpFileOrThrow(originalPath.toFile(), thumbnailPath.toFile());

                log.info("Deleted images for viewId: {}", viewId);
            } catch (Exception e) {
                log.error("Failed to delete image from URL: {}", imageUrl, e);
            }
        }
    }

    private String extractViewIdFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return null;
        }

        String[] parts = imageUrl.split("/");
        if (parts.length == 0) {
            return null;
        }

        String lastPart = parts[parts.length - 1];

        int queryIndex = lastPart.indexOf('?');
        if (queryIndex > 0) {
            lastPart = lastPart.substring(0, queryIndex);
        }

        int extensionIndex = lastPart.lastIndexOf('.');
        if (extensionIndex > 0) {
            return lastPart.substring(0, extensionIndex);
        }

        return lastPart;
    }
}
