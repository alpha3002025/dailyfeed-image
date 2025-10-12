package click.dailyfeed.image.dump;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@ActiveProfiles("local-k8s-test")
@SpringBootTest
public class LocalK8sImageUploadTest {

    private static final String BASE_URL = "http://localhost:8889";
    private static final String UPLOAD_ENDPOINT = "/api/images/upload/profile";
    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void uploadAllSampleImages() throws Exception {
        String sampleImagesDir = "src/test/resources/sample_images";

        for (int i = 1; i <= 47; i++) {
            String fileName = i + ".png";
            Path imagePath = Paths.get(sampleImagesDir, fileName);
            File imageFile = imagePath.toFile();

            if (!imageFile.exists()) {
                System.out.println("Skipping missing file: " + fileName);
                continue;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new FileSystemResource(imageFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        BASE_URL + UPLOAD_ENDPOINT,
                        requestEntity,
                        String.class
                );

                System.out.println("Uploaded: " + fileName + " - Status: " + response.getStatusCode());
                System.out.println("Response: " + response.getBody());
            } catch (Exception e) {
                System.err.println("Failed to upload: " + fileName);
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Test
    void uploadSingleSampleImage() throws Exception {
        String sampleImagesDir = "src/test/resources/sample_images";
        String fileName = "1.png";
        Path imagePath = Paths.get(sampleImagesDir, fileName);
        File imageFile = imagePath.toFile();

        if (!imageFile.exists()) {
            System.out.println("Image file does not exist: " + fileName);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new FileSystemResource(imageFile));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    BASE_URL + UPLOAD_ENDPOINT,
                    requestEntity,
                    String.class
            );

            System.out.println("Upload successful!");
            System.out.println("Status: " + response.getStatusCode());
            System.out.println("Response: " + response.getBody());
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}