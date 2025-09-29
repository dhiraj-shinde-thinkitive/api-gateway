package com.sproutsai.api_gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for message queuing
 */
@Configuration
public class RabbitMQConfig {

    @Value("${app.messaging.usage-log-exchange}")
    private String usageLogExchange;

    @Value("${app.messaging.usage-log-queue}")
    private String usageLogQueue;

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }

    // Exchange
    @Bean
    public TopicExchange usageLogExchange() {
        return new TopicExchange(usageLogExchange, true, false);
    }

    // Queue
    @Bean
    public Queue usageLogQueue() {
        return QueueBuilder.durable(usageLogQueue)
            .withArgument("x-dead-letter-exchange", usageLogExchange + ".dlx")
            .withArgument("x-dead-letter-routing-key", "dead-letter")
            .build();
    }

    // Dead Letter Queue
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(usageLogQueue + ".dlq").build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(usageLogExchange + ".dlx", true, false);
    }

    // Bindings
    @Bean
    public Binding usageLogBinding() {
        return BindingBuilder
            .bind(usageLogQueue())
            .to(usageLogExchange())
            .with(usageLogQueue);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
            .bind(deadLetterQueue())
            .to(deadLetterExchange())
            .with("dead-letter");
    }
}
