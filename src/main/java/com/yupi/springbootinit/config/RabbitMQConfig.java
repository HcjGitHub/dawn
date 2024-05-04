package com.yupi.springbootinit.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.yupi.springbootinit.constant.RabbitMQConstant.*;

/**
 * RabbitMQ配置
 *
 * @author anyan
 * @datetime: 2024/5/3
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 邮件交换机
     */
    @Bean(EXCHANGE_EMAIL)
    public Exchange emailExchange() {
        return new DirectExchange(EXCHANGE_EMAIL, true, false);
    }

    /**
     * 邮件队列
     */
    @Bean(QUEUE_EMAIL)
    public Queue emailQueue() {
        return new Queue(QUEUE_EMAIL, true, false, false);
    }

    /**
     * 邮件队列绑定
     */
    @Bean
    public Binding emailBinding() {
        return new Binding(QUEUE_EMAIL, Binding.DestinationType.QUEUE, EXCHANGE_EMAIL, ROUTING_KEY_EMAIL, null);
    }

    /**
     * 消息转换器 json序列化
     */
    @Bean
    public MessageConverter message() {
        return new Jackson2JsonMessageConverter();
    }
}
