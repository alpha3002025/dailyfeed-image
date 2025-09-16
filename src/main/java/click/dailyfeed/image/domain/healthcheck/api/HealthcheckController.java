package click.dailyfeed.image.domain.healthcheck.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/healthcheck")
public class HealthcheckController {
    @GetMapping("/ready")
    public String readinessProbe() {
        return "ready";
    }

    @GetMapping("/live")
    public String livenessProbe() {
        return "ok";
    }
}
