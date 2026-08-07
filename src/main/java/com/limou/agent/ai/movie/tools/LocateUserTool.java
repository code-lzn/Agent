package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.movie.ConversationContext;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户定位工具 — 回答"我在哪里"
 * <p>
 * 两种定位方式：
 * 1. 有 lat/lng 参数 → 逆地理编码（坐标→地址），精度最高
 * 2. 无 lat/lng → IP 定位 → 逆地理编码，精度约城市级
 *
 * @author henan
 */
@Slf4j
@Component
public class LocateUserTool extends BaseTool {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MovieStateManager stateManager;

    @Value("${amap.api-key}")
    private String amapApiKey;

    private static final String IP_LOCATE_URL = "https://restapi.amap.com/v3/ip";
    private static final String REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo";

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(description = """
            获取用户当前精确位置。用户问"我在哪里"、"我现在的位置"、"这是哪"、"定位"时调用。
            优先使用传入的 lat/lng 做逆地理编码获取精确地址；
            没有坐标时通过 IP 定位获取城市和粗略坐标。
            返回：格式化地址、城市、区县、街道、省份、坐标""")
    public String locateUser(
            @ToolParam(description = "用户当前纬度（WGS84），从对话状态获取（可选，有则精度更高）", required = false) Double lat,
            @ToolParam(description = "用户当前经度（WGS84），从对话状态获取（可选，有则精度更高）", required = false) Double lng
    ) {
        try {
            // 先尝试从 ConversationState 获取坐标（AI 不一定每次都传参）
            if (lat == null || lng == null || lat == 0 || lng == 0) {
                ConversationState state = getConversationState();
                if (state != null && state.getUserLat() != null && state.getUserLng() != null
                        && state.getUserLat() != 0 && state.getUserLng() != 0) {
                    lat = state.getUserLat();
                    lng = state.getUserLng();
                    log.info("LocateUser: 从 ConversationState 获取坐标 ({}, {})", lat, lng);
                }
            }

            if (lat != null && lng != null && lat != 0 && lng != 0) {
                // ===== 方式1: 逆地理编码（GPS 精确坐标） =====
                return reverseGeocode(lat, lng, "gps");
            } else {
                // ===== 方式2: IP 定位 → 逆地理编码（城市级精度） =====
                return ipThenReverseGeocode();
            }
        } catch (Exception e) {
            log.error("LocateUser 失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ---- IP 定位 → 逆地理编码 ----

    private String ipThenReverseGeocode() throws Exception {
        // Step 1: IP 定位获取城市和粗略坐标
        String ipUrl = IP_LOCATE_URL + "?key=" + amapApiKey + "&output=json";
        String ipBody = restTemplate.getForObject(ipUrl, String.class);
        JSONObject ipJson = JSONUtil.parseObj(ipBody);

        if (!"1".equals(ipJson.getStr("status"))) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "IP 定位失败，请检查网络连接");
            return objectMapper.writeValueAsString(err);
        }

        String province = ipJson.getStr("province");
        String city = ipJson.getStr("city");
        String adcode = ipJson.getStr("adcode");
        String rectangle = ipJson.getStr("rectangle");

        // 从矩形范围取中心坐标
        double lng = 0, lat = 0;
        if (rectangle != null && rectangle.contains(";")) {
            String[] parts = rectangle.split(";");
            String[] lb = parts[0].split(",");
            String[] rt = parts[1].split(",");
            lng = (Double.parseDouble(lb[0]) + Double.parseDouble(rt[0])) / 2;
            lat = (Double.parseDouble(lb[1]) + Double.parseDouble(rt[1])) / 2;
        }

        log.info("IP 定位: province={}, city={}, adcode={}, center=({}, {})",
                province, city, adcode, lng, lat);

        // 更新 ConversationState
        updateState(city, lat, lng);

        // Step 2: 逆地理编码获取详细地址（IP 级精度）
        if (lng != 0 && lat != 0) {
            return reverseGeocode(lat, lng, "ip");
        }

        // 降级：只返回 IP 定位结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("province", province);
        result.put("city", city);
        result.put("address", province + city);
        result.put("lat", lat);
        result.put("lng", lng);
        result.put("source", "ip");
        result.put("accuracy", "city"); // IP 定位只能到城市级
        return objectMapper.writeValueAsString(result);
    }

    // ---- 逆地理编码（坐标 → 地址） ----

    private String reverseGeocode(double lat, double lng, String source) throws Exception {
        String url = REGEO_URL + "?key=" + amapApiKey
                + "&location=" + lng + "," + lat
                + "&output=json&extensions=base&radius=1000";
        String body = restTemplate.getForObject(url, String.class);
        JSONObject json = JSONUtil.parseObj(body);

        if (!"1".equals(json.getStr("status")) || json.get("regeocode") == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "逆地理编码失败");
            return objectMapper.writeValueAsString(err);
        }

        JSONObject regeo = json.getJSONObject("regeocode");
        String formattedAddress = regeo.getStr("formatted_address");
        JSONObject comp = regeo.getJSONObject("addressComponent");

        String province = comp.getStr("province");
        String city = comp.getStr("city");
        String district = comp.getStr("district");
        String township = comp.getStr("township");
        String streetNumber = comp.getStr("streetNumber");
        String adcode = comp.getStr("adcode");

        // 构建完整地址描述
        StringBuilder addressBuilder = new StringBuilder();
        if (province != null && !province.isEmpty()) addressBuilder.append(province);
        if (city != null && !city.isEmpty() && !city.equals(province)) addressBuilder.append(city);
        if (district != null && !district.isEmpty()) addressBuilder.append(district);
        if (township != null && !township.isEmpty()) addressBuilder.append(township);
        if (streetNumber != null && !streetNumber.isEmpty()
                && !streetNumber.equals("[]") && !streetNumber.equals("null")) {
            String street = streetNumber.replaceAll("[\\[\\]\"]", "").trim();
            if (!street.isEmpty()) addressBuilder.append(street);
        }

        String simplifiedAddress = addressBuilder.length() > 0
                ? addressBuilder.toString()
                : formattedAddress;

        // ★ 补充附近 POI 信息让地址更生动
        String nearbyInfo = "";
        try {
            String aroundUrl = "https://restapi.amap.com/v3/place/around?key=" + amapApiKey
                    + "&location=" + lng + "," + lat
                    + "&radius=500&output=json&offset=3";
            String aroundBody = restTemplate.getForObject(aroundUrl, String.class);
            JSONObject aroundJson = JSONUtil.parseObj(aroundBody);
            if ("1".equals(aroundJson.getStr("status")) && aroundJson.getJSONArray("pois") != null
                    && !aroundJson.getJSONArray("pois").isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int count = 0;
                for (Object obj : aroundJson.getJSONArray("pois")) {
                    if (count >= 3) break;
                    JSONObject poi = (JSONObject) obj;
                    String name = poi.getStr("name");
                    String poiDist = poi.getStr("distance");
                    if (name != null) {
                        if (count > 0) sb.append("、");
                        sb.append(name);
                        if (poiDist != null) sb.append("(").append(poiDist).append("m").append(")");
                        count++;
                    }
                }
                if (sb.length() > 0) nearbyInfo = sb.toString();
            }
        } catch (Exception ignored) { /* 非关键 */ }

        // 更新 ConversationState
        String effectiveCity = (city != null && !city.isEmpty()) ? city : district;
        updateState(effectiveCity, lat, lng);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("formattedAddress", formattedAddress);
        result.put("simplifiedAddress", simplifiedAddress);
        result.put("province", province);
        result.put("city", effectiveCity);
        result.put("district", district);
        result.put("township", township);
        result.put("streetNumber", streetNumber);
        result.put("adcode", adcode);
        result.put("lat", lat);
        result.put("lng", lng);
        result.put("source", "regeo");
        result.put("accuracy", source); // "gps"=米级精确, "ip"=城市级近似
        if (!nearbyInfo.isEmpty()) {
            result.put("nearbyPOIs", nearbyInfo);
        }

        log.info("LocateUser 逆地理: address={}, coords=({}, {})", simplifiedAddress, lat, lng);
        return objectMapper.writeValueAsString(result);
    }

    // ---- 辅助方法 ----

    private ConversationState getConversationState() {
        String convId = ConversationContext.get();
        if (convId == null) return null;
        try {
            return stateManager.getState(convId);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateState(String city, double lat, double lng) {
        String convId = ConversationContext.get();
        if (convId == null) return;
        try {
            ConversationState state = stateManager.getState(convId);
            if (city != null && !city.isEmpty()) state.setCurrentCity(city);
            if (lat != 0) state.setUserLat(lat);
            if (lng != 0) state.setUserLng(lng);
            stateManager.saveState(convId, state);
        } catch (Exception e) {
            log.warn("LocateUser 写回 State 失败", e);
        }
    }

    @Override
    public String getToolName() {
        return "locateUser";
    }

    @Override
    public String getDisplayName() {
        return "获取当前位置";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return "[工具调用] 获取用户当前位置";
    }
}
