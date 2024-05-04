package com.yupi.springbootinit.constant;

/**
 * RabbitMQ常量
 *
 * @author anyan
 * @datetime: 2024/5/3
 */
public interface RabbitMQConstant {

    /**
     * 邮件交换机
     */
    String EXCHANGE_EMAIL = "exchange_email";
    /**
     * 邮件队列
     */
    String QUEUE_EMAIL = "queue_email";
    /**
     * 邮件路由键
     */
    String ROUTING_KEY_EMAIL = "routing_key_email";
}
