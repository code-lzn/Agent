package com.limou.agent.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.exception.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/geo")
@Slf4j
public class GeoController {

    @Value("${amap.web-service-key:74bfb724d417db45d5a9ffe7215eb4b1}")
    private String amapWebServiceKey;

    /**
     * IP 定位 — 当 GPS 被浏览器拦截时兜底
     *
     * 调 ipapi.co 免费接口（无需 Key，1000次/天）
     * 返回 { city, lat, lng }
     */
    @GetMapping("/ip-locate")
    public BaseResponse<Map<String, Object>> ipLocate(HttpServletRequest request) {
        // 1. 获取真实 IP（优先取代理转发的）
        String ip = getClientIp(request);
        // 本地开发时 127.0.0.1 查不出城市，用公网兜底
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {

            ip = ""; // ipapi.co 不传 IP 则用当前公网出口 IP
        }

        // 2. 调 ipapi.co
        String url = StrUtil.isBlank(ip)
                ? "https://ipapi.co/json/"
                : "https://ipapi.co/" + ip + "/json/";
        Map<String, Object> result = new HashMap<>();
        try {
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);

            String city = json.getStr("city");
            Double lat = json.getDouble("latitude");
            Double lng = json.getDouble("longitude");

            if (city != null && lat != null && lng != null) {
                result.put("city", city);
                result.put("lat", lat);
                result.put("lng", lng);
                return ResultUtils.success(result);
            }
        } catch (Exception e) {
            log.warn("IP 定位失败: {}", e.getMessage());
        }

        // 3. 兜底（私网IP默认洛阳）
        result.put("city", "洛阳");
        result.put("lat", 34.62);
        result.put("lng", 112.45);
        return ResultUtils.success(result);
    }

    /**
     * 逆地理编码代理 — 前端不能直接调高德 Web服务 Key
     *
     * @param lat 纬度
     * @param lng 经度
     * @return { city, lat, lng }
     */
    @GetMapping("/reverse")
    public BaseResponse<Map<String, Object>> reverse(@RequestParam double lat,
                                                      @RequestParam double lng) {
        String key = "74bfb724d417db45d5a9ffe7215eb4b1";
        Map<String, Object> result = new HashMap<>();
        try {
            String url = String.format(
                    "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%f,%f&output=json",
                    key, lng, lat);
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);
            if ("1".equals(json.getStr("status")) && json.get("regeocode") != null) {
                JSONObject comp = json.getJSONObject("regeocode")
                        .getJSONObject("addressComponent");
                String city = comp.getStr("city");
                if (StrUtil.isBlank(city)) city = comp.getStr("district");
                if (StrUtil.isBlank(city)) city = comp.getStr("province");
                result.put("city", city != null ? city : "洛阳");
                result.put("lat", lat);
                result.put("lng", lng);
                return ResultUtils.success(result);
            }
            log.warn("逆地理返回异常: {}", body);
        } catch (Exception e) {
            log.warn("逆地理编码失败: {}", e.getMessage());
        }
        result.put("city", "洛阳");
        result.put("lat", lat);
        result.put("lng", lng);
        return ResultUtils.success(result);
    }

    /**
     * 高德 POI 关键词搜索（影院地址选择）。key 留在服务端，前端不接触。
     */
    @GetMapping("/place/search")
    public BaseResponse<List<Map<String, Object>>> placeSearch(@RequestParam String keyword,
                                                               @RequestParam(required = false) String city,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        ThrowUtils.throwIf(StrUtil.isBlank(keyword), ErrorCode.PARAMS_ERROR, "请输入搜索关键词");
        int offset = Math.min(Math.max(pageSize, 1), 20);
        try {
            String url = "https://restapi.amap.com/v3/place/text?keywords="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&offset=" + offset
                    + "&page=" + Math.max(page, 1)
                    + "&output=json"
                    + "&key=" + amapWebServiceKey;
            if (StrUtil.isNotBlank(city)) {
                url += "&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
            }
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);
            if (!"1".equals(json.getStr("status"))) {
                log.warn("高德 POI 搜索失败: {}", truncate(body));
                return ResultUtils.success(List.of());
            }
            List<Map<String, Object>> result = new ArrayList<>();
            if (json.getJSONArray("pois") != null) {
                for (Object obj : json.getJSONArray("pois")) {
                    JSONObject poi = (JSONObject) obj;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", poi.getStr("name"));
                    item.put("address", poi.getStr("address"));
                    item.put("city", poi.getStr("cityname"));
                    item.put("district", poi.getStr("adname"));
                    item.put("province", poi.getStr("pname"));
                    item.put("adcode", poi.getStr("adcode"));
                    String location = poi.getStr("location"); // 格式: 经度,纬度
                    if (StrUtil.isNotBlank(location) && location.contains(",")) {
                        String[] ll = location.split(",");
                        item.put("longitude", Double.parseDouble(ll[0]));
                        item.put("latitude", Double.parseDouble(ll[1]));
                    }
                    result.add(item);
                }
            }
            return ResultUtils.success(result);
        } catch (Exception e) {
            log.warn("高德 POI 搜索异常: {}", e.getMessage());
            return ResultUtils.success(List.of());
        }
    }

    /**
     * 地址反查坐标（B 端手动输入影院地址后获取经纬度）。
     */
    @GetMapping("/geocode")
    public BaseResponse<Map<String, Object>> geocode(@RequestParam String address) {
        ThrowUtils.throwIf(StrUtil.isBlank(address), ErrorCode.PARAMS_ERROR, "请输入地址");
        try {
            String url = "https://restapi.amap.com/v3/geocode/geo?address="
                    + URLEncoder.encode(address, StandardCharsets.UTF_8)
                    + "&output=json"
                    + "&key=" + amapWebServiceKey;
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);
            if ("1".equals(json.getStr("status")) && json.getJSONArray("geocodes") != null
                    && !json.getJSONArray("geocodes").isEmpty()) {
                JSONObject geo = json.getJSONArray("geocodes").getJSONObject(0);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("found", true);
                result.put("formattedAddress", geo.getStr("formatted_address"));
                result.put("city", geo.getStr("city"));
                result.put("district", geo.getStr("district"));
                result.put("province", geo.getStr("province"));
                result.put("adcode", geo.getStr("adcode"));
                String location = geo.getStr("location"); // 格式: 经度,纬度
                if (StrUtil.isNotBlank(location) && location.contains(",")) {
                    String[] ll = location.split(",");
                    result.put("longitude", Double.parseDouble(ll[0]));
                    result.put("latitude", Double.parseDouble(ll[1]));
                }
                return ResultUtils.success(result);
            }
            log.warn("高德地址反查返回异常: {}", truncate(body));
        } catch (Exception e) {
            log.warn("高德地址反查异常: {}", e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", false);
        result.put("message", "地址解析失败，请尝试输入更详细的地址");
        return ResultUtils.success(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

