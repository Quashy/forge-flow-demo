package cn.quashy.forgeflow.config;

import cn.quashy.forgeflow.service.DemoDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final DemoDataService demoDataService;
    private final boolean enabled;

    public DemoDataInitializer(DemoDataService demoDataService,
                               @Value("${demo.seed.enabled:true}") boolean enabled) {
        this.demoDataService = demoDataService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        demoDataService.initializeIfEmpty();
    }
}
