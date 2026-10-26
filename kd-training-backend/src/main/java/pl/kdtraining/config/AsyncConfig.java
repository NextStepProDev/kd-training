package pl.kdtraining.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setTaskDecorator(loggingDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated single-thread executor for bulk campaigns (admin broadcast/newsletter). A campaign runs as ONE
     * task looping over recipients sequentially, so memory stays flat (one MimeMessage at a time) and the shared
     * {@code mailExecutor} — which carries transactional mail (account mail, notifications) — is never
     * flooded or starved by a large send. Campaigns serialize behind each other; CallerRunsPolicy means a send
     * submitted while the small queue is full runs on the caller thread instead of being rejected mid-campaign.
     */
    @Bean("mailCampaignExecutor")
    public Executor mailCampaignExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("mail-campaign-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(loggingDecorator());
        executor.initialize();
        return executor;
    }

    private TaskDecorator loggingDecorator() {
        return runnable -> () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("Mail task failed unexpectedly — email was NOT sent", e);
            }
        };
    }
}
