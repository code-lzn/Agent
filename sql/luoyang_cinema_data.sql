-- =============================================
-- 洛阳地区影院测试数据
-- 数据库: szml
-- 包含: 5家影院 + 20个影厅 + 6部影片 + ~40场排片 + 座位自动生成
-- =============================================

USE szml;

-- =============================================
-- 1. 影院数据 (5家)
-- =============================================
INSERT INTO cinema (id, name, address, city, longitude, latitude, phone, businessHours, tags, basePrice, status, isDelete, createTime, updateTime) VALUES
(10001, '洛阳万达影城(泉舜店)', '洛龙区牡丹大道166号泉舜购物中心4楼', '洛阳', 112.4487, 34.6175, '0379-65108888', '09:00-23:00', 'IMAX,杜比,4DX,停车免费', 45.00, 'published', 0, NOW(), NOW()),
(10002, '洛阳奥斯卡国际影城', '涧西区南昌路188号丹尼斯百货5楼', '洛阳', 112.3952, 34.6571, '0379-64238888', '09:30-23:30', '杜比,巨幕,情侣座', 40.00, 'published', 0, NOW(), NOW()),
(10003, '洛阳耀莱成龙国际影城', '西工区中州中路268号王府井百货6楼', '洛阳', 112.4365, 34.6677, '0379-63269999', '10:00-22:30', 'IMAX,4DX,儿童厅', 42.00, 'published', 0, NOW(), NOW()),
(10004, '洛阳中影国际影城', '洛龙区开元大道288号正大广场3楼', '洛阳', 112.4602, 34.6238, '0379-65558888', '09:00-23:00', '杜比,IMAX,停车免费,会员折扣', 48.00, 'published', 0, NOW(), NOW()),
(10005, '洛阳CGV影城(宝龙广场店)', '洛龙区展览路99号宝龙城市广场4楼', '洛阳', 112.4723, 34.6112, '0379-63918888', '09:30-23:00', '杜比,ScreenX,情侣座,停车免费', 43.00, 'published', 0, NOW(), NOW());

-- =============================================
-- 2. 影厅数据 (每影院4个厅，共20个)
-- =============================================
-- 影院1: 万达影城(泉舜店)
INSERT INTO hall (id, cinemaId, name, hallType, rowCount, colCount, seatTemplate, isDelete, createTime, updateTime) VALUES
(200001, 10001, 'IMAX厅', 'IMAX', 12, 18, '{}', 0, NOW(), NOW()),
(200002, 10001, '杜比全景声厅', '杜比', 10, 15, '{}', 0, NOW(), NOW()),
(200003, 10001, '3号普通厅', '普通', 8, 12, '{}', 0, NOW(), NOW()),
(200004, 10001, 'VIP贵宾厅', 'VIP', 5, 8, '{}', 0, NOW(), NOW());

-- 影院2: 奥斯卡国际影城
INSERT INTO hall (id, cinemaId, name, hallType, rowCount, colCount, seatTemplate, isDelete, createTime, updateTime) VALUES
(200005, 10002, '1号杜比厅', '杜比', 10, 14, '{}', 0, NOW(), NOW()),
(200006, 10002, '2号巨幕厅', 'IMAX', 11, 16, '{}', 0, NOW(), NOW()),
(200007, 10002, '3号普通厅', '普通', 8, 12, '{}', 0, NOW(), NOW()),
(200008, 10002, 'VIP情侣厅', 'VIP', 5, 8, '{}', 0, NOW(), NOW());

-- 影院3: 耀莱成龙国际影城
INSERT INTO hall (id, cinemaId, name, hallType, rowCount, colCount, seatTemplate, isDelete, createTime, updateTime) VALUES
(200009, 10003, 'IMAX激光厅', 'IMAX', 12, 18, '{}', 0, NOW(), NOW()),
(200010, 10003, '2号杜比厅', '杜比', 9, 14, '{}', 0, NOW(), NOW()),
(200011, 10003, '3号普通厅', '普通', 8, 12, '{}', 0, NOW(), NOW()),
(200012, 10003, 'VIP贵宾厅', 'VIP', 5, 8, '{}', 0, NOW(), NOW());

-- 影院4: 中影国际影城
INSERT INTO hall (id, cinemaId, name, hallType, rowCount, colCount, seatTemplate, isDelete, createTime, updateTime) VALUES
(200013, 10004, 'IMAX厅', 'IMAX', 12, 18, '{}', 0, NOW(), NOW()),
(200014, 10004, '杜比全景声厅', '杜比', 10, 15, '{}', 0, NOW(), NOW()),
(200015, 10004, '3号普通厅', '普通', 8, 12, '{}', 0, NOW(), NOW()),
(200016, 10004, 'VIP贵宾厅', 'VIP', 5, 8, '{}', 0, NOW(), NOW());

-- 影院5: CGV影城(宝龙广场店)
INSERT INTO hall (id, cinemaId, name, hallType, rowCount, colCount, seatTemplate, isDelete, createTime, updateTime) VALUES
(200017, 10005, '杜比影院厅', '杜比', 10, 15, '{}', 0, NOW(), NOW()),
(200018, 10005, 'ScreenX厅', '4DX', 8, 10, '{}', 0, NOW(), NOW()),
(200019, 10005, '3号普通厅', '普通', 8, 12, '{}', 0, NOW(), NOW()),
(200020, 10005, 'VIP情侣厅', 'VIP', 5, 8, '{}', 0, NOW(), NOW());

-- =============================================
-- 3. 影片数据 (6部热映)
-- =============================================
INSERT INTO film (id, name, englishName, type, rating, duration, posterUrl, releaseDate, director, actors, description, status, isDelete, createTime, updateTime) VALUES
(300001, '流浪地球3', 'The Wandering Earth 3', '科幻,灾难', 9.2, 150, 'https://img.example.com/liulang3.jpg', '2026-07-20', '郭帆', '吴京,刘德华,李雪健,沙溢', '太阳急速老化，人类面临生存危机。在地球发动机的推动下，人类踏上了长达2500年的流浪之旅。第三部讲述地球穿越奥尔特星云时的惊险历程。', 'published', 0, NOW(), NOW()),
(300002, '封神第二部', 'Creation of the Gods II', '奇幻,动作,史诗', 8.8, 148, 'https://img.example.com/fengshen2.jpg', '2026-07-15', '乌尔善', '费翔,黄渤,于适,娜然', '殷商末年，纣王无道，姬发觉醒。封神榜重现人间，阐教与截教的大战一触即发。杨戬、哪吒等英雄踏上封神之路。', 'published', 0, NOW(), NOW()),
(300003, '哪吒之魔童闹海', 'Nezha: The Devil Child', '动画,奇幻,喜剧', 9.0, 110, 'https://img.example.com/nezha2.jpg', '2026-07-18', '饺子', '吕艳婷,瀚墨,陈浩', '天劫之后，哪吒与敖丙灵魂尚存，太乙真人欲以莲藕重塑其身。然而东海龙族不甘失败，一场更大的危机正在酝酿……', 'published', 0, NOW(), NOW()),
(300004, '热辣滚烫', 'YOLO', '喜剧,运动,励志', 8.5, 129, 'https://img.example.com/rela.jpg', '2026-07-10', '贾玲', '贾玲,雷佳音,张小斐,杨紫', '一个普通女孩决心改变自己，通过拳击重获新生。真实而热血的故事，笑中带泪，鼓舞每一个平凡的人勇敢追梦。', 'published', 0, NOW(), NOW()),
(300005, '飞驰人生3', 'Pegasus 3', '喜剧,运动,赛车', 8.3, 125, 'https://img.example.com/feichi3.jpg', '2026-07-22', '韩寒', '沈腾,范丞丞,尹正,张本煜', '张驰再次踏上赛道，这次他将挑战世界级的拉力赛。速度与激情的碰撞，笑料百出的旅程。', 'published', 0, NOW(), NOW()),
(300006, '熊出没·逆转时空', 'Boonie Bears: Time Twist', '动画,喜剧,科幻', 8.0, 98, 'https://img.example.com/xiongchumo.jpg', '2026-07-25', '林汇达', '谭笑,张伟,张秉君', '光头强发明了一台时空机器，熊大熊二意外穿越到恐龙时代、未来世界和古代中国。一场跨越时空的爆笑冒险！', 'published', 0, NOW(), NOW());

-- =============================================
-- 4. 排片数据 (~45场，覆盖7/31和8/1两天)
-- =============================================
-- 影院1 万达影城(泉舜店): IMAX=200001, 杜比=200002, 普通=200003, VIP=200004
INSERT INTO schedule (filmId, cinemaId, hallId, showDate, startTime, endTime, price, vipPrice, status, isDelete, createTime, updateTime) VALUES
-- 流浪地球3 IMAX
(300001, 10001, 200001, '2026-07-31', '10:00', '12:45', 89.00, 109.00, 'published', 0, NOW(), NOW()),
(300001, 10001, 200001, '2026-07-31', '14:00', '16:45', 89.00, 109.00, 'published', 0, NOW(), NOW()),
(300001, 10001, 200001, '2026-07-31', '19:00', '21:45', 99.00, 119.00, 'published', 0, NOW(), NOW()),
-- 封神第二部 杜比
(300002, 10001, 200002, '2026-07-31', '10:30', '13:13', 69.00, 89.00, 'published', 0, NOW(), NOW()),
(300002, 10001, 200002, '2026-07-31', '15:00', '17:43', 69.00, 89.00, 'published', 0, NOW(), NOW()),
(300002, 10001, 200002, '2026-07-31', '19:30', '22:13', 79.00, 99.00, 'published', 0, NOW(), NOW()),
-- 哪吒 普通
(300003, 10001, 200003, '2026-07-31', '12:00', '14:05', 49.00, 69.00, 'published', 0, NOW(), NOW()),
(300003, 10001, 200003, '2026-07-31', '16:30', '18:35', 49.00, 69.00, 'published', 0, NOW(), NOW()),
-- 热辣滚烫 VIP
(300004, 10001, 200004, '2026-07-31', '14:00', '16:24', 129.00, 149.00, 'published', 0, NOW(), NOW()),
(300004, 10001, 200004, '2026-07-31', '19:00', '21:24', 149.00, 169.00, 'published', 0, NOW(), NOW());

-- 8/1 排片（类似的）
INSERT INTO schedule (filmId, cinemaId, hallId, showDate, startTime, endTime, price, vipPrice, status, isDelete, createTime, updateTime) VALUES
(300001, 10001, 200001, '2026-08-01', '10:00', '12:45', 89.00, 109.00, 'published', 0, NOW(), NOW()),
(300001, 10001, 200001, '2026-08-01', '14:00', '16:45', 89.00, 109.00, 'published', 0, NOW(), NOW()),
(300001, 10001, 200001, '2026-08-01', '19:00', '21:45', 99.00, 119.00, 'published', 0, NOW(), NOW()),
(300002, 10001, 200002, '2026-08-01', '10:30', '13:13', 69.00, 89.00, 'published', 0, NOW(), NOW()),
(300002, 10001, 200002, '2026-08-01', '15:00', '17:43', 69.00, 89.00, 'published', 0, NOW(), NOW()),
(300005, 10001, 200003, '2026-08-01', '13:00', '15:20', 49.00, 69.00, 'published', 0, NOW(), NOW()),
(300005, 10001, 200003, '2026-08-01', '17:00', '19:20', 49.00, 69.00, 'published', 0, NOW(), NOW()),
(300004, 10001, 200004, '2026-08-01', '15:00', '17:24', 129.00, 149.00, 'published', 0, NOW(), NOW()),
(300004, 10001, 200004, '2026-08-01', '19:30', '21:54', 149.00, 169.00, 'published', 0, NOW(), NOW());

-- 影院2 奥斯卡国际影城: 巨幕=200006, 杜比=200005, 普通=200007, VIP=200008
INSERT INTO schedule (filmId, cinemaId, hallId, showDate, startTime, endTime, price, vipPrice, status, isDelete, createTime, updateTime) VALUES
(300001, 10002, 200006, '2026-07-31', '10:00', '12:45', 85.00, 105.00, 'published', 0, NOW(), NOW()),
(300001, 10002, 200006, '2026-07-31', '14:30', '17:15', 85.00, 105.00, 'published', 0, NOW(), NOW()),
(300002, 10002, 200005, '2026-07-31', '11:00', '13:43', 65.00, 85.00, 'published', 0, NOW(), NOW()),
(300002, 10002, 200005, '2026-07-31', '16:00', '18:43', 65.00, 85.00, 'published', 0, NOW(), NOW()),
(300003, 10002, 200007, '2026-07-31', '13:00', '15:05', 45.00, 65.00, 'published', 0, NOW(), NOW()),
(300004, 10002, 200007, '2026-07-31', '16:00', '18:24', 45.00, 65.00, 'published', 0, NOW(), NOW()),
(300005, 10002, 200008, '2026-07-31', '14:00', '16:20', 119.00, 139.00, 'published', 0, NOW(), NOW());

-- 影院3 耀莱成龙国际影城: IMAX=200009, 杜比=200010, 普通=200011, VIP=200012
INSERT INTO schedule (filmId, cinemaId, hallId, showDate, startTime, endTime, price, vipPrice, status, isDelete, createTime, updateTime) VALUES
(300001, 10003, 200009, '2026-07-31', '10:30', '13:15', 79.00, 99.00, 'published', 0, NOW(), NOW()),
(300001, 10003, 200009, '2026-07-31', '15:00', '17:45', 79.00, 99.00, 'published', 0, NOW(), NOW()),
(300002, 10003, 200010, '2026-07-31', '11:00', '13:43', 59.00, 79.00, 'published', 0, NOW(), NOW()),
(300003, 10003, 200011, '2026-07-31', '14:00', '16:05', 39.00, 59.00, 'published', 0, NOW(), NOW()),
(300005, 10003, 200011, '2026-07-31', '17:00', '19:20', 39.00, 59.00, 'published', 0, NOW(), NOW()),
(300004, 10003, 200012, '2026-07-31', '15:00', '17:24', 119.00, 139.00, 'published', 0, NOW(), NOW()),
(300001, 10003, 200009, '2026-08-01', '10:30', '13:15', 79.00, 99.00, 'published', 0, NOW(), NOW()),
(300003, 10003, 200011, '2026-08-01', '14:00', '16:05', 39.00, 59.00, 'published', 0, NOW(), NOW());

-- 影院4 中影国际影城: IMAX=200013, 杜比=200014, 普通=200015, VIP=200016
INSERT INTO schedule (filmId, cinemaId, hallId, showDate, startTime, endTime, price, vipPrice, status, isDelete, createTime, updateTime) VALUES
(300001, 10004, 200013, '2026-07-31', '10:00', '12:45', 99.00, 119.00, 'published', 0, NOW(), NOW()),
(300001, 10004, 200013, '2026-07-31', '19:00', '21:45', 109.00, 129.00, 'published', 0, NOW(), NOW()),
(300002, 10004, 200014, '2026-07-31', '14:00', '16:43', 75.00, 95.00, 'published', 0, NOW(), NOW()),
(300003, 10004, 200015, '2026-07-31', '13:00', '15:05', 49.00, 69.00, 'published', 0, NOW(), NOW()),
(300006, 10004, 200015, '2026-07-31', '16:00', '17:53', 39.00, 59.00, 'published', 0, NOW(), NOW()),
(300005, 10004, 200016, '2026-07-31', '15:00', '17:20', 139.00, 159.00, 'published', 0, NOW(), NOW());

-- 影院5 CGV影城(宝龙广场店): 杜比=200017, 4DX=200018, 普通=200019, VIP=200020
INSERT INTO schedule (filmId, cinemaId, hallId, showDate, startTime, endTime, price, vipPrice, status, isDelete, createTime, updateTime) VALUES
(300001, 10005, 200017, '2026-07-31', '10:30', '13:15', 79.00, 99.00, 'published', 0, NOW(), NOW()),
(300002, 10005, 200017, '2026-07-31', '14:30', '17:13', 79.00, 99.00, 'published', 0, NOW(), NOW()),
(300003, 10005, 200019, '2026-07-31', '11:00', '13:05', 45.00, 65.00, 'published', 0, NOW(), NOW()),
(300004, 10005, 200019, '2026-07-31', '14:00', '16:24', 45.00, 65.00, 'published', 0, NOW(), NOW()),
(300005, 10005, 200018, '2026-07-31', '13:00', '15:20', 69.00, 89.00, 'published', 0, NOW(), NOW()),
(300006, 10005, 200018, '2026-07-31', '16:00', '17:53', 59.00, 79.00, 'published', 0, NOW(), NOW()),
(300004, 10005, 200020, '2026-07-31', '19:00', '21:24', 139.00, 159.00, 'published', 0, NOW(), NOW());

-- =============================================
-- 5. 座位生成存储过程
-- =============================================
DROP PROCEDURE IF EXISTS generate_seats;

DELIMITER //
CREATE PROCEDURE generate_seats()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_schedule_id BIGINT;
    DECLARE v_hall_id BIGINT;
    DECLARE v_row_count INT;
    DECLARE v_col_count INT;
    DECLARE r INT;
    DECLARE c INT;

    DECLARE cur CURSOR FOR
        SELECT s.id, s.hallId, h.rowCount, h.colCount
        FROM schedule s
        JOIN hall h ON s.hallId = h.id
        WHERE s.isDelete = 0
          AND NOT EXISTS (
              SELECT 1 FROM seat WHERE seat.scheduleId = s.id LIMIT 1
          );

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_schedule_id, v_hall_id, v_row_count, v_col_count;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET r = 1;
        WHILE r <= v_row_count DO
            SET c = 1;
            WHILE c <= v_col_count DO
                INSERT INTO seat (scheduleId, hallId, rowNum, colNum, seatLabel, zone, status, isDelete, createTime, updateTime)
                VALUES (
                    v_schedule_id,
                    v_hall_id,
                    r,
                    c,
                    CONCAT(r, '排', c, '座'),
                    CASE WHEN r <= 2 THEN 'vip' ELSE 'regular' END,
                    'available',
                    0,
                    NOW(),
                    NOW()
                );
                SET c = c + 1;
            END WHILE;
            SET r = r + 1;
        END WHILE;
    END LOOP;

    CLOSE cur;
END//
DELIMITER ;

-- 执行座位生成
CALL generate_seats();

-- 清理存储过程（可选）
-- DROP PROCEDURE IF EXISTS generate_seats;

-- =============================================
-- 验证数据
-- =============================================
SELECT '影院' AS 数据, COUNT(*) AS 数量 FROM cinema WHERE isDelete = 0
UNION ALL
SELECT '影厅', COUNT(*) FROM hall WHERE isDelete = 0
UNION ALL
SELECT '影片', COUNT(*) FROM film WHERE isDelete = 0
UNION ALL
SELECT '排片', COUNT(*) FROM schedule WHERE isDelete = 0
UNION ALL
SELECT '座位', COUNT(*) FROM seat WHERE isDelete = 0;
