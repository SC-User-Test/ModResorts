package com.acme.modres.service;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.logging.Logger;

/**
 * Azure Service Bus Scheduler Service for distributed task scheduling.
 * Replaces java.util.Timer with cloud-native scheduled message delivery.
 * Fixes blocker: cr-java-0111
 */
@Service
public class AzureServiceBusSchedulerService {

    private static final Logger logger = Logger.getLogger(AzureServiceBusSchedulerService.class.getName());

    @Value("${azure.servicebus.connection-string:}")
    private String connectionString;

    @Value("${azure.servicebus.queue-name:scheduled-tasks}")
    private String queueName;

    /**
     * Schedule a task to be executed at a specific time using Azure Service Bus scheduled messages.
     * 
     * @param taskId Unique identifier for the task
     * @param taskData Task data as JSON string
     * @param scheduledTime When the task should be executed
     */
    public void scheduleTask(String taskId, String taskData, OffsetDateTime scheduledTime) {
        if (connectionString == null || connectionString.isEmpty()) {
            logger.warning("Azure Service Bus connection string not configured, task scheduling disabled");
            return;
        }

        try (ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName)
                .buildClient()) {

            ServiceBusMessage message = new ServiceBusMessage(taskData)
                    .setMessageId(taskId)
                    .setScheduledEnqueueTime(scheduledTime);

            senderClient.sendMessage(message);
            logger.info("Scheduled task " + taskId + " for execution at " + scheduledTime);

        } catch (Exception e) {
            logger.severe("Failed to schedule task " + taskId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Schedule a task to be executed after a delay.
     * 
     * @param taskId Unique identifier for the task
     * @param taskData Task data as JSON string
     * @param delay Duration to wait before executing the task
     */
    public void scheduleTaskWithDelay(String taskId, String taskData, Duration delay) {
        OffsetDateTime scheduledTime = OffsetDateTime.now().plus(delay);
        scheduleTask(taskId, taskData, scheduledTime);
    }

    /**
     * Schedule a recurring task (note: Azure Service Bus doesn't support native recurring messages,
     * so the task handler should reschedule itself after execution).
     * 
     * @param taskId Unique identifier for the task
     * @param taskData Task data as JSON string
     * @param initialDelay Initial delay before first execution
     */
    public void scheduleRecurringTask(String taskId, String taskData, Duration initialDelay) {
        scheduleTaskWithDelay(taskId, taskData, initialDelay);
        logger.info("Scheduled recurring task " + taskId + " with initial delay " + initialDelay);
    }
}
