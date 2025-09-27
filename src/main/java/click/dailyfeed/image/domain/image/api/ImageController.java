package click.dailyfeed.image.domain.image.api;

import click.dailyfeed.code.domain.image.exception.ImageProcessingFailException;
import click.dailyfeed.code.domain.image.exception.ImageReadingFailException;
import click.dailyfeed.code.domain.member.member.dto.MemberProfileDto;
import click.dailyfeed.code.global.web.code.ResponseSuccessCode;
import click.dailyfeed.code.global.web.response.DailyfeedServerResponse;
import click.dailyfeed.image.domain.image.service.ProfileImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/images")
public class ImageController {
    private final ProfileImageStorageService imageService;

    @PostMapping("/upload")
    public DailyfeedServerResponse<String> uploadImage(@RequestParam("image") MultipartFile file) {
        try {
            String imageId = imageService.store(file);
            return DailyfeedServerResponse.<String>builder()
                    .result(ResponseSuccessCode.SUCCESS)
                    .status(HttpStatus.OK.value())
                    .data(imageId)
                    .build();
        } catch (IOException e) {
            throw new ImageProcessingFailException();
        }
    }

    @PostMapping("/upload/profile")
    public DailyfeedServerResponse<String> uploadProfileImage(
            @RequestParam("image") MultipartFile file
    ) {
        try {
            String imageId = imageService.store(file);
            return DailyfeedServerResponse.<String>builder()
                    .result(ResponseSuccessCode.SUCCESS)
                    .status(HttpStatus.OK.value())
                    .data(imageId)
                    .build();
        } catch (IOException e) {
            throw new ImageProcessingFailException();
        }
    }

    @GetMapping("/view/{imageId}")
    public ResponseEntity<Resource> getImage(@PathVariable("imageId") String imageId,
                                             @RequestParam(value = "thumbnail", defaultValue = "false") Boolean isThumbnail) {
        Resource image = imageService.get(imageId, isThumbnail);
        if (image != null) {
            String contentType = "image/jpeg";
            if (imageId.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (imageId.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (imageId.toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + imageId + "\"")
                    .body(image);
        }
        throw new ImageReadingFailException();
    }

    @PostMapping("/view/command/delete/in")
    public DailyfeedServerResponse<Boolean> deleteImage(
            @RequestBody MemberProfileDto.ImageDeleteBulkRequest imageDeleteBulkRequest
    ) {
        imageService.deleteImages(imageDeleteBulkRequest);
        return DailyfeedServerResponse.<Boolean>builder()
                .result(ResponseSuccessCode.SUCCESS)
                .status(HttpStatus.OK.value())
                .data(Boolean.TRUE)
                .build();
    }
}
