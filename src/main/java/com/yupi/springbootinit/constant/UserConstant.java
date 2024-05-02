package com.yupi.springbootinit.constant;

/**
 * 用户常量
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public interface UserConstant {

    /**
     * 用户登录状态缓存key
     * 格式：user:login:state:{userId}
     * 过期时间：3小时
     * 作用：记录用户的登录态，提供访问速度
     */
    String USER_LOGIN_STATE = "user:login:state:";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * 被封号
     */
    String BAN_ROLE = "ban";

    // endregion
}
