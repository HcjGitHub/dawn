package com.yupi.springbootinit.listener;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.EmailMessage;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.utils.MailClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.Resource;
import java.io.IOException;

import static com.yupi.springbootinit.constant.RabbitMQConstant.QUEUE_EMAIL;

/**
 * 邮件发送监听器
 *
 * @author anyan
 * @datetime: 2024/5/3
 */
@Component
@Slf4j
public class EmailSendListener {

    @Resource
    private MailClientUtils mailClientUtils;

    @Resource
    private TemplateEngine templateEngine;

    //queuesToDeclare注解可以让监听者自己定义自己监听的队列，并且生产者不会重复定义队列
    @RabbitListener(queuesToDeclare = {@Queue(QUEUE_EMAIL)})
    public void sendCode(EmailMessage emailMessage, Message message, Channel channel) throws IOException {
        String email = emailMessage.getEmail();
        String code = emailMessage.getCode();
        String type = emailMessage.getType();
        log.info("监听发送code队列的消息，email:{},code:{},type:{}", email, code, type);

        try {
            //给注册者发送验证码邮件
            Context context = new Context();
            context.setVariable("email", email);
            context.setVariable("code", code);
            context.setVariable("type", type);
            //加载邮件HTML页面
            String content = templateEngine.process("/email", context);
            mailClientUtils.sendMail(email, "晨曦 - 验证码", content);
        } catch (Exception e) {
            //拒绝消息 重新入队
            log.error("邮件发送失败，email:{},code:{}", email, code, e);
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "邮件发送失败");
        }

        //确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
