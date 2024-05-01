# 数据库初始化
# @author <a href="https://github.com/liyupi">程序员鱼皮</a>
# @from <a href="https://yupi.icu">编程导航知识星球</a>

-- 创建库
create database if not exists dawn;

-- 切换库
use dawn;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           null comment '账号',
    userPassword varchar(512)                           null comment '密码',
    unionId      varchar(256)                           null comment '微信开放平台id',
    mpOpenId     varchar(256)                           null comment '公众号openId',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userEmail    varchar(256)                           null comment '用户邮箱',
    userPhone    varchar(256)                           null comment '用户手机号',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_unionId (unionId)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 帖子表
create table if not exists post
(
    id         bigint auto_increment comment 'id' primary key,
    title      varchar(512)                       null comment '标题',
    content    text                               null comment '内容',
    tags       varchar(1024)                      null comment '标签列表（json 数组）',
    thumbNum   int      default 0                 not null comment '点赞数',
    favourNum  int      default 0                 not null comment '收藏数',
    score      double   default 0                 not null comment '热度 / 分数（用于按照热度排行帖子）',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId)
) comment '帖子' collate = utf8mb4_unicode_ci;

-- 帖子点赞表（硬删除）
create table if not exists post_thumb
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_postId (postId),
    index idx_userId (userId)
) comment '帖子点赞';

-- 帖子收藏表（硬删除）
create table if not exists post_favour
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_postId (postId),
    index idx_userId (userId)
) comment '帖子收藏';

/*评论表*/
DROP TABLE IF EXISTS `comment`;
SET character_set_client = utf8mb4;
CREATE TABLE `comment`
(
    `id`          int(11)                            NOT NULL AUTO_INCREMENT comment '主键id',
    `user_id`     int(11)  DEFAULT NULL comment '用户id',
    `entity_type` int(11)  DEFAULT NULL comment '实体类型',
    `entity_id`   int(11)  DEFAULT NULL comment '实体的 id',
    `target_id`   int(11)  DEFAULT NULL comment '目标用户 id',
    `content`     text comment '评论/回复的内容',
    `status`      int(11)  DEFAULT 0                 not NULL comment '评论/回复状态',
    `create_time` datetime default CURRENT_TIMESTAMP null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete`   tinyint  default 0                 not null comment '是否删除（逻辑删除）',
    PRIMARY KEY (`id`),
    KEY `index_user_id` (`user_id`),
    KEY `index_entity_id` (`entity_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

/*私信表*/
DROP TABLE IF EXISTS `message`;
SET character_set_client = utf8mb4;
CREATE TABLE `message`
(
    `id`              int(11)                            NOT NULL AUTO_INCREMENT comment '主键id',
    `from_id`         int(11)  DEFAULT NULL comment '私信/系统通知的发送方 id',
    `to_id`           int(11)  DEFAULT NULL comment '私信/系统通知的接收方 id',
    `conversation_id` varchar(45)                        NOT NULL comment '标识两个用户之间的对话 发id_收id',
    `content`         text comment '私信/系统通知的内容',
    `status`          int(11)  DEFAULT 0                 not NULL COMMENT '0-未读;1-已读;2-删除;',
    `create_time`     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    `update_time`     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete`       tinyint  default 0                 not null comment '是否删除（逻辑删除）',
    PRIMARY KEY (`id`),
    KEY `index_from_id` (`from_id`),
    KEY `index_to_id` (`to_id`),
    KEY `index_conversation_id` (`conversation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
