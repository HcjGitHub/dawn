package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.dto.user.UserQueryRequest;
import com.yupi.springbootinit.model.dto.user.UserRegisterRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.LoginUserVO;
import com.yupi.springbootinit.model.vo.UserVO;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 用户服务
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public interface UserService extends IService<User> {

    /**
     * 获取验证码
     *
     * @param request
     * @param response
     */
    void getCaptcha(HttpServletRequest request, HttpServletResponse response);

    /**
     * 发送邮箱注册登录验证码
     *
     * @param emailNum    邮箱号码
     * @param captchaType 验证码类型 login:登录 register:注册
     */
    void sendEmailCode(String emailNum, String captchaType);

    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求体
     * @param signature           验证码唯一标识
     * @return 新用户 id
     */
    long userRegister(UserRegisterRequest userRegisterRequest, String signature);

    /**
     * 使用邮箱方式进行登录
     *
     * @param email     邮箱
     * @param captcha   验证码
     * @param autoLogin 是否自动登录
     * @param request
     * @param response
     * @return
     */
    LoginUserVO userEmailLogin(String email, String captcha, Boolean autoLogin, HttpServletRequest request, HttpServletResponse response);

    /**
     * 用户邮箱注册
     *
     * @param email   邮箱
     * @param captcha 验证码
     * @return
     */
    long userEmailRegister(String email, String captcha);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param autoLogin    是否自动登录
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, Boolean autoLogin, HttpServletRequest request);

    /**
     * 用户登录（微信开放平台）
     *
     * @param wxOAuth2UserInfo 从微信获取的用户信息
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    User getLoginUserPermitNull(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVO(List<User> userList);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

}
