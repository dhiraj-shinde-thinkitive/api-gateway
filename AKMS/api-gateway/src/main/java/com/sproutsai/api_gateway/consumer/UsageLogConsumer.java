package com.sproutsai.api_gateway.consumer;

import com.sproutsai.api_gateway.service.UsageTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer for processing usage log messages from RabbitMQ queue
 */
@Component
public class UsageLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageLogConsumer.class);

    private final UsageTrackingService usageTrackingService;

    public UsageLogConsumer(UsageTrackingService usageTrackingService) {
        this.usageTrackingService = usageTrackingService;
    }

    @RabbitListener(queues = "${app.messaging.usage-log-queue}")
    public void processUsageLog(UsageTrackingService.UsageLogMessage message) {
        log.debug("Received usage log message for processing: {}", message.correlationId());
        usageTrackingService.processUsageLogMessage(message);
    }
}