package com.yupi.springbootinit.common;

import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

/**
 * @author anyan
 * DateTime: 2024/5/1
 */

public class UserPassWordTest {

    @Test
    public void test() {
        String encryptPassword = DigestUtils.md5DigestAsHex(("chenxi" + "123").getBytes());
        System.out.println(encryptPassword);
    }
}
