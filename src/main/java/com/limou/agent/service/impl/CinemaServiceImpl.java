package com.limou.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Cinema;
import com.limou.agent.mapper.CinemaMapper;
import com.limou.agent.service.CinemaService;
import com.limou.agent.model.dto.cinema.CinemaFilterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Slf4j
@Service
public class CinemaServiceImpl extends ServiceImpl<CinemaMapper, Cinema> implements CinemaService {

    @Value("${amap.api-key}")
    private String amapApiKey;

    private static final String AROUND_SEARCH_URL = "https://restapi.amap.com/v3/place/around";

    private final RestTemplate restTemplate = new RestTemplate();

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

        // 通过高德 API 批量计算距离（与 AI 选影院工具使用相同的高德算法，确保距离一致）
        if (request.getUserLat() != null && request.getUserLng() != null && !list.isEmpty()) {
            fetchAmapDistances(request.getUserLat(), request.getUserLng(), list);
        }

        // 排序
        String sortType = request.getSortType();
        if ("price".equals(sortType)) {
            list.sort(Comparator.comparing(
                    c -> c.getBasePrice() != null ? c.getBasePrice() : BigDecimal.ZERO));
        } else if ("nearest".equals(sortType)
                && request.getUserLat() != null
                && request.getUserLng() != null) {
            list.sort(Comparator.comparingInt(c -> {
                if (c.getDistance() != null) return c.getDistance();
                // 无坐标或 API 未返回距离时排到最后
                if (c.getLatitude() == null || c.getLongitude() == null) return Integer.MAX_VALUE;
                return Integer.MAX_VALUE;
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
     * 通过高德 v3/place/around API 搜索附近影院 POI，按名称匹配到 DB 影院后
     * 直接使用 Amap POI 的 distance（用户位置 → 高德 POI 坐标的直线距离，米）。
     * 未匹配到的 DB 影院回退到与 AI 工具完全一致的 Haversine 公式。
     * 这是 AI 工具 SearchNearbyCinemasTool 的同一套逻辑，确保距离完全一致。
     */
    private void fetchAmapDistances(BigDecimal userLat, BigDecimal userLng, List<Cinema> cinemas) {
        double uLat = userLat.doubleValue();
        double uLng = userLng.doubleValue();

        // 1. 调用高德周边搜索（与 AI 工具参数一致）
        Map<String, Integer> poiDistances = new LinkedHashMap<>(); // normalized name → distance(m)
        try {
            String url = AROUND_SEARCH_URL
                    + "?key=" + amapApiKey
                    + "&location=" + uLng + "," + uLat
                    + "&keywords=电影院|影院|影城"
                    + "&types=060400"
                    + "&radius=10000"
                    + "&offset=25"
                    + "&page=1"
                    + "&extensions=all";
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = JSONUtil.parseObj(resp);
            if ("1".equals(json.getStr("status"))) {
                JSONArray pois = json.getJSONArray("pois");
                if (pois != null) {
                    for (int i = 0; i < pois.size(); i++) {
                        JSONObject poi = pois.getJSONObject(i);
                        String poiName = poi.getStr("name");
                        String distStr = poi.getStr("distance");
                        if (poiName != null && distStr != null) {
                            // 用标准化名称做 key：去除常见后缀和括号内容
                            String key = normalizeCinemaName(poiName);
                            try {
                                poiDistances.put(key, Integer.parseInt(distStr));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("高德周边搜索失败，全部回退 Haversine", e);
        }

        // 2. 匹配 DB 影院 → POI 距离
        for (Cinema c : cinemas) {
            if (c.getLatitude() == null || c.getLongitude() == null) continue;
            String dbKey = normalizeCinemaName(c.getName());
            // 尝试精确 key 匹配，再尝试关键词包含匹配
            Integer poiDist = poiDistances.get(dbKey);
            if (poiDist == null) {
                poiDist = findPoiDistanceByContain(dbKey, poiDistances);
            }
            if (poiDist != null) {
                c.setDistance(poiDist);
            } else {
                // 回退：与 SearchNearbyCinemasTool.haversineDistance() 完全一致
                c.setDistance(haversineDistance(uLat, uLng,
                        c.getLatitude().doubleValue(), c.getLongitude().doubleValue()));
            }
        }
    }

    /** 标准化影院名称用于匹配：去后缀、括号内容、空格 */
    private String normalizeCinemaName(String name) {
        if (name == null) return "";
        return name.trim()
                .replaceAll("[（(]影城|电影院|影院|影剧院|[）)]", "")
                .replaceAll("[（(][^）)]*[）)]", "")
                .replaceAll("\\s+", "")
                .replaceAll("[\\u4e00-\\u9fa5]{1,4}店$", "")
                .trim();
    }

    /** 通过关键词包含匹配 POI 距离 */
    private Integer findPoiDistanceByContain(String dbName, Map<String, Integer> poiDistances) {
        if (dbName == null || dbName.isEmpty()) return null;
        for (Map.Entry<String, Integer> e : poiDistances.entrySet()) {
            if (e.getKey().contains(dbName) || dbName.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    @Override
    public void computeAmapDistance(Cinema cinema, BigDecimal userLat, BigDecimal userLng) {
        if (cinema == null || userLat == null || userLng == null) return;
        if (cinema.getLatitude() == null || cinema.getLongitude() == null) return;
        fetchAmapDistances(userLat, userLng, List.of(cinema));
    }

    /**
     * Haversine 公式计算两点间直线距离（米），与 AI 工具
     * {@code SearchNearbyCinemasTool.haversineDistance()} 完全一致。
     */
    private static int haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(R * c);
    }
}
