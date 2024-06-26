package com.yupi.springbootinit.service.impl;

import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.yupi.springbootinit.common.EmailMessage;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.LeakyBucket;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.UserMapper;
import com.yupi.springbootinit.model.dto.user.UserQueryRequest;
import com.yupi.springbootinit.model.dto.user.UserRegisterRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.UserRoleEnum;
import com.yupi.springbootinit.model.vo.LoginUserVO;
import com.yupi.springbootinit.model.vo.UserVO;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yupi.springbootinit.common.LeakyBucket.loginLeakyBucket;
import static com.yupi.springbootinit.common.LeakyBucket.registerLeakyBucket;
import static com.yupi.springbootinit.constant.RabbitMQConstant.EXCHANGE_EMAIL;
import static com.yupi.springbootinit.constant.RabbitMQConstant.ROUTING_KEY_EMAIL;
import static com.yupi.springbootinit.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "chenxi";

    /**
     * 验证码存储到redis前缀 存储两分钟
     */
    public static final String CAPTCHA_PREFIX = "captcha:prefix:";

    /**
     * 登录和注册的标识，方便切换不同的令牌桶来限制验证码发送
     */
    private static final String LOGIN_SIGN = "login";
    private static final String REGISTER_SIGN = "register";

    /**
     * redisKey存储验证码的前缀
     */
    private static final String CODE_REGISTER_PRE = "code:register:";
    private static final String CODE_LOGIN_PRE = "code:login:";

    /**
     * redis中存储发code的时间的key 用于令牌桶的限流
     */
    public static final String USER_EMAIL_CODE_LOGIN = "user:email:code:login:";
    public static final String USER_EMAIL_CODE_REGISTER = "user:email:code:register:";

    @Override
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response) {
        String signature = request.getHeader("signature");
        if (StringUtils.isBlank(signature)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        //纯数字四位验证码
        RandomGenerator randomGenerator = new RandomGenerator("0123456789", 4);
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(100, 30);
        lineCaptcha.setGenerator(randomGenerator);
        ShearCaptcha captcha = CaptchaUtil.createShearCaptcha(200, 45, 4, 4);

        try {
            //设置响应头
            response.setContentType("image/jpeg");
            response.setHeader("Pragma", "No-cache");

            //写回前端页面
            lineCaptcha.write(response.getOutputStream());

            log.info("captchaId:{}，code:{}", signature, lineCaptcha.getCode());
            // 将验证码设置到Redis中,2分钟过期
            stringRedisTemplate.opsForValue().set(CAPTCHA_PREFIX + signature, lineCaptcha.getCode(), 2, TimeUnit.MINUTES);
            response.getOutputStream().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendEmailCode(String emailNum, String captchaType) {
        if (StringUtils.isBlank(captchaType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码类型错误");
        }

        //令牌桶算法实现短信接口的限流，因为手机号码重复发送短信，要进行流量控制
        //解决同一个手机号的并发问题，锁的粒度非常小，不影响性能。只是为了防止用户第一次发送短信时的恶意调用
        synchronized (emailNum.intern()) {
            Boolean exist = stringRedisTemplate.hasKey(USER_EMAIL_CODE_REGISTER + emailNum);
            if (exist != null && exist) {
                //1.令牌桶算法对手机短信接口进行限流 具体限流规则为同一个手机号，60s只能发送一次
                long finalTime = 0L;
                LeakyBucket leakyBucket = null;
                if (REGISTER_SIGN.equals(captchaType)) {
                    String strFinalTime = stringRedisTemplate.opsForValue().get(USER_EMAIL_CODE_REGISTER + emailNum);
                    if (strFinalTime != null) {
                        finalTime = Long.parseLong(strFinalTime);
                    }
                    leakyBucket = registerLeakyBucket;
                }
                if (LOGIN_SIGN.equals(captchaType)) {
                    String strFinalTime = stringRedisTemplate.opsForValue().get(USER_EMAIL_CODE_LOGIN + emailNum);
                    if (strFinalTime != null) {
                        finalTime = Long.parseLong(strFinalTime);
                    }
                    leakyBucket = loginLeakyBucket;
                }

                if (leakyBucket != null && !leakyBucket.control(finalTime)) {
                    log.info("邮箱{}请求验证码太频繁了", emailNum);
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求邮箱太频繁了");
                }
            }

            //2.符合限流规则则生成验证码
            String code = RandomUtil.randomNumbers(4);
            //分login/register code存储到redis
            String CodeRedisKey = CODE_REGISTER_PRE;
            if (LOGIN_SIGN.equals(captchaType)) {
                CodeRedisKey = CODE_LOGIN_PRE;
            }
            stringRedisTemplate.opsForValue().set(CodeRedisKey + emailNum, code, 5, TimeUnit.MINUTES);

            String type = captchaType.equals(LOGIN_SIGN) ? "登录" : "注册";
            //3.通过消息队列发送，提供并发量
            EmailMessage emailMessage = new EmailMessage(emailNum, code, type);
            rabbitTemplate.convertAndSend(EXCHANGE_EMAIL, ROUTING_KEY_EMAIL, emailMessage);

            //4.更新发送短信时间
            if (REGISTER_SIGN.equals(captchaType)) {
                stringRedisTemplate.opsForValue().set(USER_EMAIL_CODE_REGISTER + emailNum,
                        "" + System.currentTimeMillis() / 1000, 5, TimeUnit.MINUTES);
            }
            if (LOGIN_SIGN.equals(captchaType)) {
                stringRedisTemplate.opsForValue().set(USER_EMAIL_CODE_LOGIN + emailNum,
                        "" + System.currentTimeMillis() / 1000, 5, TimeUnit.MINUTES);
            }
        }
    }

    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest, String signature) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

        String captcha = userRegisterRequest.getCaptcha();

        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 3 || checkPassword.length() < 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 验证码校验
        if (captcha.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码输入错误");
        }
        //获取redis中保存的验证码
        String code = stringRedisTemplate.opsForValue().get(CAPTCHA_PREFIX + signature);
        if (!captcha.equals(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码输入错误");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            // 默认用户名与账号相同
            user.setUserName(userAccount);
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public long userEmailRegister(String email, String captcha) {
        this.verifyCaptcha(captcha, email, CODE_REGISTER_PRE);
        synchronized (email.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userEmail", email);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱已存在重复");
            }

            // 3. 插入数据
            User user = new User();
            user.setUserEmail(email);
            //邮箱注册用户 默认用户名与账号相同
            user.setUserName(email);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            stringRedisTemplate.delete(CODE_REGISTER_PRE + email);
            return user.getId();
        }
    }

    private void verifyCaptcha(String captcha, String email, String captchaRedisKey) {
        // 1. 校验
        if (StringUtils.isAnyBlank(email, captcha)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        //邮箱格式
        if (!Pattern.matches("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$", email)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式错误");
        }

        // 验证码校验
        if (captcha.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码输入错误");
        }
        //获取redis中保存的验证码
        String code = stringRedisTemplate.opsForValue().get(captchaRedisKey + email);
        if (!captcha.equals(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码输入错误");
        }
    }

    @Override
    public LoginUserVO userEmailLogin(String email, String captcha, Boolean autoLogin,
                                      HttpServletRequest request, HttpServletResponse response) {
        this.verifyCaptcha(captcha, email, CODE_LOGIN_PRE);
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userEmail", email);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        return setLoginUserState(autoLogin, user);
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, Boolean autoLogin, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号密码为空");
        }
        if (userAccount.length() < 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        // 密码不正确
        if (!encryptPassword.equals(user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        return setLoginUserState(autoLogin, user);
    }

    /**
     * 设置登录用户状态
     *
     * @param autoLogin 是否自动登录（记住我）
     * @param user      用户
     * @return LoginUserVO（用户脱敏数据）
     */
    private LoginUserVO setLoginUserState(Boolean autoLogin, User user) {
        // 3. 记录用户的登录态
        // request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 使用 sa-token 记录登录态 配置JWT加密算法 userName作为附加信息 autoLogin->saToken的是否长期有效
        StpUtil.login(user.getId(), SaLoginConfig.setExtra("userName", user.getUserName()).setIsLastingCookie(autoLogin));
        // 主动序列化
        String userJson = new Gson().toJson(user);
        // 使用Redis 记录用户信息，提供访问速度
        stringRedisTemplate.opsForValue().set(USER_LOGIN_STATE + user.getId(), userJson, 3, TimeUnit.HOURS);
        // 4. 返回登录用户信息
        LoginUserVO loginUserVO = this.getLoginUserVO(user);
        if (autoLogin) {
            // 设置saToken 持久化到浏览器
            loginUserVO.setSaToken(StpUtil.getTokenValue());
        }
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo, HttpServletRequest request) {
        String unionId = wxOAuth2UserInfo.getUnionId();
        String mpOpenId = wxOAuth2UserInfo.getOpenid();
        // 单机锁
        synchronized (unionId.intern()) {
            // 查询用户是否已存在
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("unionId", unionId);
            User user = this.getOne(queryWrapper);
            // 被封号，禁止登录
            if (user != null && UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户已被封，禁止登录");
            }
            // 用户不存在则创建
            if (user == null) {
                user = new User();
                user.setUnionId(unionId);
                user.setMpOpenId(mpOpenId);
                user.setUserAvatar(wxOAuth2UserInfo.getHeadImgUrl());
                user.setUserName(wxOAuth2UserInfo.getNickname());
                boolean result = this.save(user);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败");
                }
            }
            // 记录用户的登录态
            request.getSession().setAttribute(USER_LOGIN_STATE, user);
            return getLoginUserVO(user);
        }
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        boolean login = StpUtil.isLogin();
        if (!login) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 获取当前登录用户id
        long loginUserId = StpUtil.getLoginIdAsLong();
        // 从缓存查询
        String userJson = stringRedisTemplate.opsForValue().get(USER_LOGIN_STATE + loginUserId);
        User user = new Gson().fromJson(userJson, User.class);
        // 缓存数据刚好过期 兜底方案
        if (user == null) {
            // 从数据库查询
            user = this.getById(loginUserId);
            if (user == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            }
            // 缓存数据
            userJson = new Gson().toJson(user);
            stringRedisTemplate.opsForValue().set(USER_LOGIN_STATE + loginUserId, userJson, 3, TimeUnit.HOURS);
        }
        return user;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        return this.getById(userId);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 获取当前会话是否已经登录，返回true=已登录，false=未登录
        boolean login = StpUtil.isLogin();

        if (!login) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        // 获取当前登录用户id
        long loginUserId = StpUtil.getLoginIdAsLong();
        // 移除登录态
        StpUtil.logout();
        // 移除缓存数据
        stringRedisTemplate.delete(USER_LOGIN_STATE + loginUserId);
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }
}
