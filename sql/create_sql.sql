-- 用户表
create database if not exists szml;
use szml;
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;


-- 对话历史表
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    sessionId       bigint                             not null comment '会话id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (sessionId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (sessionId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;


-- 会话表----后序会进行增加字段
-- 应用表
create table chat_session
(
    id           bigint auto_increment comment 'id' primary key,
    sessionName      varchar(256)                       null comment '会话名称',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appName (sessionName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;


CREATE TABLE `user_preference` (
                                   `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `userId`            BIGINT       NOT NULL COMMENT '用户ID',
                                   `preferredTypes`    VARCHAR(255) DEFAULT NULL COMMENT '偏好影片类型，逗号分隔',
                                   `preferredHallType` VARCHAR(50)  DEFAULT NULL COMMENT '偏好厅型: IMAX/杜比/普通/4DX/VIP',
                                   `budgetMax`         DECIMAL(10,2) DEFAULT NULL COMMENT '单张票价预算上限（元）',
                                   `frequentCinemaId`  BIGINT       DEFAULT NULL COMMENT '常去影院ID',
                                   `preferredSeatZone` VARCHAR(50)  DEFAULT NULL COMMENT '常用座位区域: 中间/靠前/靠后/靠边',
                                   `isDelete`          TINYINT(1)   NOT NULL DEFAULT 0,
                                   `createTime`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   `updateTime`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_userId` (`userId`),
                                   KEY `idx_frequentCinema` (`frequentCinemaId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好画像';


CREATE TABLE `film` (
                        `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                        `name`        VARCHAR(100) NOT NULL COMMENT '影片名称',
                        `englishName` VARCHAR(100) DEFAULT NULL COMMENT '英文名称',
                        `type`        VARCHAR(100) DEFAULT NULL COMMENT '影片类型，逗号分隔',
                        `rating`      DECIMAL(3,1) DEFAULT NULL COMMENT '评分 1.0-10.0',
                        `duration`    INT          NOT NULL COMMENT '片长（分钟）',
                        `posterUrl`   VARCHAR(500) DEFAULT NULL COMMENT '海报图片地址',
                        `releaseDate` DATE         DEFAULT NULL COMMENT '上映日期',
                        `director`    VARCHAR(100) DEFAULT NULL COMMENT '导演',
                        `actors`      VARCHAR(500) DEFAULT NULL COMMENT '主演，逗号分隔',
                        `description` TEXT         DEFAULT NULL COMMENT '影片简介（最多500字）',
                        `status`      VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT '状态: draft/published/offline',
                        `isDelete`    TINYINT(1)   NOT NULL DEFAULT 0,
                        `createTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updateTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`),
                        KEY `idx_status` (`status`),
                        KEY `idx_rating` (`rating`),
                        KEY `idx_releaseDate` (`releaseDate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影片';


CREATE TABLE `cinema` (
                          `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                          `name`          VARCHAR(100) NOT NULL COMMENT '影院名称',
                          `address`       VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
                            `city`          VARCHAR(50)    DEFAULT NULL COMMENT '城市',
                          `longitude`     DECIMAL(10,6) DEFAULT NULL COMMENT '经度（高德坐标系）',
                          `latitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '纬度（高德坐标系）',
                          `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
                          `businessHours` VARCHAR(50)  DEFAULT NULL COMMENT '营业时间: 09:00-23:00',
                          `tags`          VARCHAR(255) DEFAULT NULL COMMENT '特色标签，逗号分隔',
                          `basePrice`     DECIMAL(10,2) DEFAULT NULL COMMENT '基准票价（元）',
                          `status`        VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT '状态: draft/published/offline',
                          `isDelete`      TINYINT(1)   NOT NULL DEFAULT 0,
                          `createTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `updateTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`),
                          KEY `idx_status` (`status`),
                          KEY `idx_location` (`longitude`, `latitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影院';

CREATE TABLE `hall` (
                        `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                        `cinemaId`     BIGINT      NOT NULL COMMENT '所属影院ID',
                        `name`         VARCHAR(50) NOT NULL COMMENT '影厅名称',
                        `hallType`     VARCHAR(50) NOT NULL DEFAULT '普通' COMMENT '厅型: IMAX/杜比/普通/4DX/VIP',
                        `rowCount`     INT         NOT NULL COMMENT '座位行数',
                        `colCount`     INT         NOT NULL COMMENT '座位列数',
                        `seatTemplate` JSON        DEFAULT NULL COMMENT '座位模板JSON（特殊座位标记等）',
                        `isDelete`     TINYINT(1)  NOT NULL DEFAULT 0,
                        `createTime`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updateTime`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`),
                        KEY `idx_cinemaId` (`cinemaId`),
                        KEY `idx_hallType` (`hallType`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影厅';

CREATE TABLE `schedule` (
                            `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            `filmId`     BIGINT       NOT NULL COMMENT '影片ID',
                            `cinemaId`   BIGINT       NOT NULL COMMENT '影院ID',
                            `hallId`     BIGINT       NOT NULL COMMENT '影厅ID',
                            `showDate`   DATE         NOT NULL COMMENT '放映日期',
                            `startTime`  TIME         NOT NULL COMMENT '开场时间',
                            `endTime`    TIME         NOT NULL COMMENT '散场时间（自动计算: startTime + 片长 + 15min）',
                            `price`      DECIMAL(10,2) NOT NULL COMMENT '标准票价（元）',
                            `vipPrice`   DECIMAL(10,2) DEFAULT NULL COMMENT 'VIP区票价（元）',
                            `status`     VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT '状态: draft/published/offline/soldOut',
                            `isDelete`   TINYINT(1)   NOT NULL DEFAULT 0,
                            `createTime` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            `updateTime` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            KEY `idx_filmId` (`filmId`),
                            KEY `idx_cinemaId` (`cinemaId`),
                            KEY `idx_hallId` (`hallId`),
                            KEY `idx_showDate` (`showDate`),
                            KEY `idx_film_date` (`filmId`, `showDate`),
                            KEY `idx_cinema_date` (`cinemaId`, `showDate`),
                            KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='放映场次';


CREATE TABLE `seat` (
                        `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                        `scheduleId` BIGINT      NOT NULL COMMENT '场次ID',
                        `hallId`     BIGINT      NOT NULL COMMENT '影厅ID',
                        `rowNum`     INT         NOT NULL COMMENT '行号（从1开始）',
                        `colNum`     INT         NOT NULL COMMENT '列号（从1开始）',
                        `seatLabel`  VARCHAR(10) NOT NULL COMMENT '座位标签: 5排6座',
                        `zone`       VARCHAR(20) NOT NULL DEFAULT 'regular' COMMENT '区域: vip/regular',
                        `status`     VARCHAR(20) NOT NULL DEFAULT 'available' COMMENT '状态: available/locked/sold',
                        `isDelete`   TINYINT(1)  NOT NULL DEFAULT 0,
                        `createTime` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updateTime` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`),
                        KEY `idx_scheduleId` (`scheduleId`),
                        KEY `idx_hallId` (`hallId`),
                        KEY `idx_schedule_zone` (`scheduleId`, `zone`),
                        KEY `idx_schedule_status` (`scheduleId`, `status`),
                        UNIQUE KEY `uk_schedule_seat` (`scheduleId`, `rowNum`, `colNum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位（场次快照）';

CREATE TABLE `order` (
                         `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                         `orderNo`       VARCHAR(64)  NOT NULL COMMENT '订单编号（唯一）',
                         `userId`        BIGINT       NOT NULL COMMENT '用户ID',
                         `scheduleId`    BIGINT       NOT NULL COMMENT '场次ID',
                         `filmName`      VARCHAR(100) DEFAULT NULL COMMENT '影片名称（冗余）',
                         `cinemaName`    VARCHAR(100) DEFAULT NULL COMMENT '影院名称（冗余）',
                         `scheduleTime`  VARCHAR(50)  DEFAULT NULL COMMENT '放映时间（冗余）',
                         `hallName`      VARCHAR(50)  DEFAULT NULL COMMENT '影厅名称（冗余）',
                         `totalPrice`    DECIMAL(10,2) NOT NULL COMMENT '订单总价（元）',
                         `count`         INT          NOT NULL COMMENT '购票数量',
                         `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态: pending/paid/cancelled/completed',
                         `cancelReason`  VARCHAR(50)  DEFAULT NULL COMMENT '取消原因: timeout/user_cancelled',
                         `alipayTradeNo` VARCHAR(100) DEFAULT NULL COMMENT '支付宝交易号（沙箱生成）',
                         `alipayStatus`  VARCHAR(50)  DEFAULT NULL COMMENT '支付宝状态',
                         `paidAt`        DATETIME     DEFAULT NULL COMMENT '实际支付时间',
                         `expireAt`      DATETIME     DEFAULT NULL COMMENT '超时截止时间（创建时间+15分钟）',
                         `isDelete`      TINYINT(1)   NOT NULL DEFAULT 0,
                         `createTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         `updateTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_orderNo` (`orderNo`),
                         KEY `idx_userId` (`userId`),
                         KEY `idx_scheduleId` (`scheduleId`),
                         KEY `idx_status` (`status`),
                         KEY `idx_expireAt` (`expireAt`),
                         KEY `idx_user_status` (`userId`, `status`),
                         KEY `idx_alipayTradeNo` (`alipayTradeNo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';


CREATE TABLE `order_seat` (
                              `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                              `orderId`    BIGINT      NOT NULL COMMENT '订单ID',
                              `seatId`     BIGINT      NOT NULL COMMENT '座位ID',
                              `seatLabel`  VARCHAR(10) NOT NULL COMMENT '座位标签（冗余: 5排6座）',
                              `isUsed`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已使用: 0-未使用 1-已核销',
                              `isDelete`   TINYINT(1)  NOT NULL DEFAULT 0,
                              `createTime` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updateTime` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              KEY `idx_orderId` (`orderId`),
                              KEY `idx_seatId` (`seatId`),
                              KEY `idx_order_seat` (`orderId`, `seatId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单-座位关联（电子票）';

CREATE TABLE `system_config` (
                                 `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `configKey`   VARCHAR(100) NOT NULL COMMENT '配置键',
                                 `configValue` JSON         NOT NULL COMMENT '配置值（JSON格式）',
                                 `description` VARCHAR(255) DEFAULT NULL COMMENT '配置说明',
                                 `isDelete`    TINYINT(1)   NOT NULL DEFAULT 0,
                                 `createTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 `updateTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_configKey` (`configKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';


use szml;
-- 想看影片表
create table if not exists user_want_film
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                                  not null comment '用户ID',
    filmId     bigint                                  not null comment '影片ID',
    createTime datetime default CURRENT_TIMESTAMP      not null comment '创建时间',
    isDelete   tinyint  default 0                      not null comment '是否删除',
    UNIQUE KEY uk_user_film (userId, filmId),
    INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户想看影片';

-- 看过影片表
create table if not exists user_watched_film
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                                  not null comment '用户ID',
    filmId     bigint                                  not null comment '影片ID',
    createTime datetime default CURRENT_TIMESTAMP      not null comment '创建时间',
    isDelete   tinyint  default 0                      not null comment '是否删除',
    UNIQUE KEY uk_user_film (userId, filmId),
    INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户看过影片';


use szml;
CREATE TABLE film_review (
                             id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                             userId        BIGINT    NOT NULL,
                             filmId        BIGINT    NOT NULL,
                             orderId       BIGINT    NULL COMMENT '关联订单（购票用户可关联）',
                             rating        TINYINT   NOT NULL COMMENT '评分 1-5',
                             content       TEXT      NOT NULL COMMENT '影评内容',
                             tags          VARCHAR(255) NULL COMMENT '标签，逗号分隔',
                             helpfulCount  INT DEFAULT 0 COMMENT '有用数',
                             commentCount  INT DEFAULT 0 COMMENT '回复数',
                             isDelete      TINYINT DEFAULT 0,
                             createTime    DATETIME DEFAULT CURRENT_TIMESTAMP,
                             KEY idx_filmId (filmId),
                             KEY idx_userId (userId),
                             UNIQUE KEY uk_user_film (userId, filmId)
);

-- 影评有用记录表
CREATE TABLE `review_helpful` (
                                  `id`       bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `userId`   bigint NOT NULL COMMENT '用户ID',
                                  `reviewId` bigint NOT NULL COMMENT '影评ID',
                                  `isDelete` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
                                  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  PRIMARY KEY (`id`),
                                  INDEX `idx_user_review` (`userId`, `reviewId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影评有用记录';

-- 影评评论表
CREATE TABLE `review_comment` (
                                  `id`       bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `reviewId` bigint NOT NULL COMMENT '影评ID',
                                  `userId`   bigint NOT NULL COMMENT '评论用户ID',
                                  `parentId` bigint DEFAULT NULL COMMENT '父评论ID，NULL表示直接评论影评',
                                  `content`  varchar(500) NOT NULL COMMENT '评论内容',
                                  `isDelete` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
                                  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  PRIMARY KEY (`id`),
                                  INDEX `idx_review` (`reviewId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影评评论';



CREATE TABLE `review_comment_helpful` (
                                          `id`        bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                          `userId`    bigint NOT NULL COMMENT '用户ID',
                                          `commentId` bigint NOT NULL COMMENT '评论ID',
                                          `isDelete`  tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                                          `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          PRIMARY KEY (`id`),
                                          INDEX `idx_user_comment` (`userId`, `commentId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论有用记录';


ALTER TABLE `review_comment` ADD COLUMN `helpfulCount` int NOT NULL DEFAULT 0 COMMENT '有用数' AFTER `content`;


ALTER TABLE `review_comment` ADD COLUMN `replyToUserId` bigint DEFAULT NULL COMMENT '实际回复的用户ID' AFTER `parentId`;