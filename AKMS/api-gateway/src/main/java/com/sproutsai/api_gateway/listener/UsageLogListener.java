//package com.sproutsai.api_gateway.listener;
//
//import com.sproutsai.api_gateway.service.UsageTrackingService;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
///**
// * Message listener for processing usage log messages
// */
////@Component
//public class UsageLogListener {
//
//    private final UsageTrackingService usageTrackingService;
//
//    public UsageLogListener(UsageTrackingService usageTrackingService) {
//        this.usageTrackingService = usageTrackingService;
//    }
//
////    @RabbitListener(queues = "${app.messaging.usage-log-queue}")
//    public void handleUsageLogMessage(UsageTrackingService.UsageLogMessage message) {
//        usageTrackingService.processUsageLogMessage(message);
//    }
//}