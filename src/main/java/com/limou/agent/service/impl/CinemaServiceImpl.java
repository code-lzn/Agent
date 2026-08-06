package com.limou.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Cinema;
import com.limou.agent.mapper.CinemaMapper;
import com.limou.agent.service.CinemaService;
import com.limou.agent.model.dto.cinema.CinemaFilterRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class CinemaServiceImpl extends ServiceImpl<CinemaMapper, Cinema> implements CinemaService {

    @Override
    public List<Cinema> filterCinemas(CinemaFilterRequest request) {
        QueryWrapper qw = QueryWrapper.create();

        // 关键词搜索：影院名称模糊匹配（SQL LIKE）
        if (StrUtil.isNotBlank(request.getKeyword())) {
            qw.like("name", request.getKeyword());
        }

        // 区域筛选：地址包含区域名称（SQL LIKE）
        if (StrUtil.isNotBlank(request.getDistrict())) {
            qw.like("address", request.getDistrict());
        }

        List<Cinema> list = mapper.selectListByQuery(qw);

        // 品牌筛选：Java 流过滤，支持多品牌 OR 关系
        if (StrUtil.isNotBlank(request.getBrand())) {
            List<String> brandKeywords = Arrays.stream(request.getBrand().split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .map(this::getBrandKeyword)
                    .collect(Collectors.toList());
            if (!brandKeywords.isEmpty()) {
                list = list.stream()
                        .filter(c -> StrUtil.isNotBlank(c.getName())
                                && brandKeywords.stream().anyMatch(kw -> c.getName().contains(kw)))
                        .collect(Collectors.toList());
            }
        }

        // 服务筛选：Java 流过滤，支持多服务 OR 关系
        if (StrUtil.isNotBlank(request.getServices())) {
            List<String> services = Arrays.stream(request.getServices().split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
            if (!services.isEmpty()) {
                list = list.stream()
                        .filter(c -> StrUtil.isNotBlank(c.getTags())
                                && services.stream().anyMatch(svc -> c.getTags().contains(svc)))
                        .collect(Collectors.toList());
            }
        }

        // 排序
        String sortType = request.getSortType();
        if ("price".equals(sortType)) {
            list.sort(Comparator.comparing(
                    c -> c.getBasePrice() != null ? c.getBasePrice() : BigDecimal.ZERO));
        } else if ("nearest".equals(sortType)
                && request.getUserLat() != null
                && request.getUserLng() != null) {
            final BigDecimal userLat = request.getUserLat();
            final BigDecimal userLng = request.getUserLng();
            list.sort(Comparator.comparingDouble(c -> {
                if (c.getLatitude() == null || c.getLongitude() == null) {
                    return Double.MAX_VALUE;
                }
                return haversine(userLat, userLng, c.getLatitude(), c.getLongitude());
            }));
        }
        // "composite" 或其他保持数据库默认顺序

        return list;
    }

    /**
     * 品牌名称 → 影院名搜索关键词（部分品牌需要去除"影城"后缀以扩大匹配范围）。
     */
    private String getBrandKeyword(String brand) {
        return switch (brand.trim()) {
            case "奥斯卡影城" -> "奥斯卡";
            default -> brand.trim();
        };
    }

    /**
     * Haversine 公式计算两点间距离（千米）。
     */
    private double haversine(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double dLat = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double dLng = Math.toRadians(lng2.subtract(lng1).doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c;
    }
}
