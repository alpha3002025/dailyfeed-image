package click.dailyfeed.image.domain.image.api;

import click.dailyfeed.image.domain.image.service.ProfileImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.core.io.Resource;
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
    public ResponseEntity<String> uploadImage(@RequestParam("image") MultipartFile file) {

        try {
            String imageId = imageService.store(file);
            return ResponseEntity.ok(imageId);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to upload image: "+ e.getMessage());
        }

    }

    @GetMapping("/view/{imageId}")
    public ResponseEntity<Resource> getImage(@PathVariable("imageId") String imageId,
                                             @RequestParam(value = "thumbnail", defaultValue = "false") Boolean isThumbnail) {
        Resource image = imageService.get(imageId, isThumbnail);
        return (image != null) ? ResponseEntity.ok().contentType(MediaType.parseMediaType("image/jpeg")).body(image) : ResponseEntity.notFound().build();
    }
}
