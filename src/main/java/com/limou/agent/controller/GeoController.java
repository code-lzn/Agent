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
import java.util.Map;

@RestController
@RequestMapping("/geo")
@Slf4j
public class GeoController {

    @Value("${amap.web-service-key:74bfb724d417db45d5a9ffe7215eb4b1}")
    private String amapWebServiceKey;

    /**
     * IP 粗略定位。仅使用真实客户端公网 IP，本地开发不会误用服务器出口位置。
     */
    @GetMapping("/ip-locate")
    public BaseResponse<Map<String, Object>> ipLocate(HttpServletRequest request) {
        String ip = getClientIp(request);
        if (!isValidIp(ip)) {
            return ResultUtils.success(unavailable("未获取到有效的客户端 IP"));
        }

        if (isPrivateIp(ip)) {
            String devPublicIp = System.getenv("DEV_PUBLIC_IP");
            if (!isValidIp(devPublicIp) || isPrivateIp(devPublicIp)) {
                log.info("本地或内网访问无法进行客户端 IP 定位: ip={}", ip);
                return ResultUtils.success(unavailable(
                        "本地开发无法通过服务器 IP 获取你的位置，请使用浏览器定位或手动选择城市"));
            }
            ip = devPublicIp;
        }

        Map<String, Object> geo = tryIpApiCom(ip);
        if (geo != null) {
            return ResultUtils.success(geo);
        }

        geo = tryIpapiCo(ip);
        if (geo != null) {
            return ResultUtils.success(geo);
        }

        log.warn("所有 IP 定位服务均失败: ip={}", ip);
        return ResultUtils.success(unavailable("IP 定位服务暂不可用，请手动选择城市"));
    }

    private Map<String, Object> tryIpapiCo(String ip) {
        try {
            String url = StrUtil.isBlank(ip)
                    ? "https://ipapi.co/json/"
                    : "https://ipapi.co/" + ip + "/json/";
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);
            String city = json.getStr("city");
            Double lat = json.getDouble("latitude");
            Double lng = json.getDouble("longitude");
            if (city != null && lat != null && lng != null) {
                Map<String, Object> result = located(normalizeCity(city), lat, lng, "ip", false);
                log.info("ipapi.co 定位成功: ip={}, city={}", ip, city);
                return result;
            }
            log.warn("ipapi.co 返回字段缺失: {}", truncate(body));
        } catch (Exception e) {
            log.warn("ipapi.co 失败: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, Object> tryIpApiCom(String ip) {
        try {
            String url = "http://ip-api.com/json/" + (StrUtil.isBlank(ip) ? "" : ip) + "?lang=zh-CN";
            String body = HttpUtil.get(url, 3000);
            JSONObject json = JSONUtil.parseObj(body);
            if ("success".equals(json.getStr("status"))) {
                String city = json.getStr("city");
                Double lat = json.getDouble("lat");
                Double lng = json.getDouble("lon");
                if (city != null && lat != null && lng != null) {
                    Map<String, Object> result = located(normalizeCity(city), lat, lng, "ip", false);
                    log.info("ip-api.com 定位成功: ip={}, city={}", ip, city);
                    return result;
                }
            }
            log.warn("ip-api.com 返回异常: {}", truncate(body));
        } catch (Exception e) {
            log.warn("ip-api.com 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 将浏览器坐标反解为城市，前端无需持有高德 Web 服务 Key。
     */
    @GetMapping("/reverse")
    public BaseResponse<Map<String, Object>> reverse(@RequestParam double lat,
                                                      @RequestParam double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return ResultUtils.success(unavailable("坐标超出有效范围"));
        }

        try {
            String url = String.format(
                    "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%f,%f&output=json",
                    amapWebServiceKey, lng, lat);
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);
            if ("1".equals(json.getStr("status")) && json.get("regeocode") != null) {
                JSONObject component = json.getJSONObject("regeocode")
                        .getJSONObject("addressComponent");
                String city = component.getStr("city");
                if (StrUtil.isBlank(city)) {
                    city = component.getStr("district");
                }
                if (StrUtil.isBlank(city)) {
                    city = component.getStr("province");
                }
                if (StrUtil.isNotBlank(city)) {
                    return ResultUtils.success(located(normalizeCity(city), lat, lng, "gps", true));
                }
            }
            log.warn("逆地理返回异常: {}", truncate(body));
        } catch (Exception e) {
            log.warn("逆地理编码失败: {}", e.getMessage());
        }

        Map<String, Object> result = unavailable("坐标解析失败，请稍后重试或手动选择城市");
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
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private Map<String, Object> located(String city, double lat, double lng,
                                         String source, boolean precise) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("located", true);
        result.put("city", city);
        result.put("lat", lat);
        result.put("lng", lng);
        result.put("source", source);
        result.put("precise", precise);
        return result;
    }

    private Map<String, Object> unavailable(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("located", false);
        result.put("source", "none");
        result.put("message", message);
        return result;
    }

    private String normalizeCity(String city) {
        String normalized = city == null ? "" : city.trim();
        return normalized.endsWith("市")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String truncate(String body) {
        return body != null && body.length() > 200 ? body.substring(0, 200) : body;
    }

    private boolean isValidIp(String ip) {
        return StrUtil.isNotBlank(ip) && ip.matches("[0-9a-fA-F:.]+") && ip.length() <= 45;
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null) {
            return true;
        }
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) {
            return true;
        }
        if (ip.startsWith("fc") || ip.startsWith("fd") || ip.startsWith("fe80:")) {
            return true;
        }
        if (!ip.startsWith("172.")) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
