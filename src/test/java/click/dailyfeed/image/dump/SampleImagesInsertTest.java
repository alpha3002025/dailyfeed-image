package click.dailyfeed.image.dump;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@SpringBootTest
@AutoConfigureMockMvc
public class SampleImagesInsertTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadAllSampleImages() throws Exception {
        String sampleImagesDir = "src/test/resources/sample_images";

        for (int i = 1; i <= 47; i++) {
            String fileName = i + ".png";
            Path imagePath = Paths.get(sampleImagesDir, fileName);

            try (FileInputStream fis = new FileInputStream(imagePath.toFile())) {
                MockMultipartFile file = new MockMultipartFile(
                        "image",
                        fileName,
                        MediaType.IMAGE_PNG_VALUE,
                        fis
                );

                mockMvc.perform(multipart("/api/images/upload/profile")
                                .file(file))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.result").value("SUCCESS"))
                        .andExpect(jsonPath("$.status").value(200))
                        .andExpect(jsonPath("$.data").exists());

                System.out.println("Uploaded: " + fileName);
            }
        }
    }

    @Test
    void printNonThumbnailImageFiles() throws Exception {
        String imageStorePath = "./tmp/dailyfeed/store/images";
        Path imagesDir = Paths.get(imageStorePath);

        if (!imagesDir.toFile().exists()) {
            System.out.println("Image store directory does not exist: " + imageStorePath);
            return;
        }

        System.out.println("=== Non-Thumbnail Image Files ===");

        try (java.util.stream.Stream<Path> paths = java.nio.file.Files.walk(imagesDir)) {
            paths.filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".PNG"))
                    .filter(path -> !path.getFileName().toString().contains("-thumbnail"))
                    .forEach(path -> System.out.println(path.getFileName().toString().replace(".PNG", "")));
        }
    }



}