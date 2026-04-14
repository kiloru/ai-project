package com.kilor.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // Weibo 热搜 - 无需认证
    private static final String WEIBO_API = "https://weibo.com/ajax/side/hotSearch";
    // 哔哩哔哩热搜
    private static final String BILIBILI_API = "https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all";
    // 抖音热搜
    private static final String DOUYIN_API = "https://www.douyin.com/aweme/v1/web/hot/search/list/?device_platform=webapp&aid=6383";
    // 知乎热榜 (需cookie，作为fallback)
    private static final String ZHIHU_API = "https://www.zhihu.com/hot";

    // 备用: 第三方免费聚合API
    private static final String AGGREGATE_API = "https://api.gumengya.com/Api/ReDian?encode=json";
    private static final String AGGREGATE_API2 = "http://api.03c3.cn/api/hot?encode=json";

    private List<Map<String, Object>> cachedWeibo = new ArrayList<>();
    private List<Map<String, Object>> cachedBilibili = new ArrayList<>();
    private List<Map<String, Object>> cachedDouyin = new ArrayList<>();
    private String lastUpdateTime = "未更新";

    @PostConstruct
    public void init() {
        fetchAllNews();
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void scheduledFetch() {
        log.info("Scheduled news fetch started");
        fetchAllNews();
        log.info("Scheduled news fetch completed. Weibo:{}, Bilibili:{}, Douyin:{}",
                cachedWeibo.size(), cachedBilibili.size(), cachedDouyin.size());
    }

    private void fetchAllNews() {
        // Weibo
        List<Map<String, Object>> weibo = fetchWeibo();
        if (!weibo.isEmpty()) cachedWeibo = weibo;

        // Bilibili
        List<Map<String, Object>> bili = fetchBilibili();
        if (!bili.isEmpty()) cachedBilibili = bili;

        // Douyin (likely to fail, skip silently)
        List<Map<String, Object>> douyin = fetchDouyin();
        if (!douyin.isEmpty()) cachedDouyin = douyin;

        lastUpdateTime = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private List<Map<String, Object>> fetchWeibo() {
        try {
            String body = fetchUrl(WEIBO_API, "https://www.weibo.com");
            if (body == null) return Collections.emptyList();

            JsonNode root = mapper.readTree(body);
            JsonNode items = root.path("data").path("realtime");
            if (!items.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < Math.min(items.size(), 30); i++) {
                JsonNode node = items.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", node.path("word").asText(""));
                item.put("url", "https://s.weibo.com/weibo?q=" + node.path("word").asText(""));
                item.put("hot", node.path("num").asText("0"));
                result.add(item);
            }
            log.info("Fetched {} weibo hot search items", result.size());
            return result;
        } catch (Exception e) {
            log.warn("Weibo fetch failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> fetchBilibili() {
        try {
            String body = fetchUrl(BILIBILI_API, "https://www.bilibili.com/");
            if (body == null) return Collections.emptyList();

            JsonNode root = mapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.debug("Bilibili API returned code: {}", code);
                return Collections.emptyList();
            }

            JsonNode items = root.path("data").path("list");
            if (!items.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < Math.min(items.size(), 30); i++) {
                JsonNode node = items.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", node.path("title").asText(""));
                item.put("url", "https://www.bilibili.com/video/" + node.path("bvid").asText(""));
                item.put("hot", node.path("stat").path("view").asText("0"));
                item.put("desc", node.path("owner").path("name").asText(""));
                result.add(item);
            }
            log.info("Fetched {} bilibili ranking items", result.size());
            return result;
        } catch (Exception e) {
            log.warn("Bilibili fetch failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> fetchDouyin() {
        // Douyin requires signed requests, skip for now
        return Collections.emptyList();
    }

    private String fetchUrl(String urlStr) {
        return fetchUrl(urlStr, null);
    }

    private String fetchUrl(String urlStr, String referer) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (referer != null) {
                conn.setRequestProperty("Referer", referer);
                conn.setRequestProperty("Origin", referer);
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                log.debug("HTTP {} from {}", code, urlStr);
                return null;
            }

            return new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Fetch failed: {} - {}", urlStr, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getAllHotNews() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        data.put("微博热搜", cachedWeibo);
        data.put("B站排行", cachedBilibili);
        data.put("抖音热搜", cachedDouyin);
        result.put("data", data);
        result.put("updateTime", lastUpdateTime);
        return result;
    }
}
