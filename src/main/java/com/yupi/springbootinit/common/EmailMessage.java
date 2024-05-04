package com.yupi.springbootinit.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送邮件消息对象
 *
 * @author anyan
 * DateTime: 2024/5/3
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailMessage {

    /**
     * 邮件地址
     */
    private String email;

    /**
     * 邮件验证码
     */
    private String code;

    /**
     * 邮件类型 登录/注册
     */
    private String type;
}
