package cn.quashy.forgeflow.config;

import cn.quashy.forgeflow.service.DemoDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "demo.reset.enabled", havingValue = "true")
public class DemoDataResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoDataResetScheduler.class);

    private final DemoDataService demoDataService;

    public DemoDataResetScheduler(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @Scheduled(cron = "${demo.reset.cron:0 0 4 * * *}", zone = "${demo.reset.zone:Asia/Shanghai}")
    public void restoreBaseline() {
        log.info("开始恢复演示数据基线");
        demoDataService.resetToBaseline();
    }
}
