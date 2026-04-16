package com.kilor.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kilor.demo.entity.TokenHolderSnapshot;
import com.kilor.demo.repository.TokenHolderSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ETHERSCAN_API = "https://api.etherscan.io/api";

    @Value("${etherscan.api.key:}")
    private String apiKey;

    private final TokenHolderSnapshotRepository snapshotRepository;

    public TokenService(TokenHolderSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Get token basic info from Etherscan
     */
    public Map<String, Object> getTokenInfo(String contractAddress) throws Exception {
        String url = ETHERSCAN_API + "?module=token&action=tokeninfo&contractAddress=" + contractAddress
                + "&apikey=" + apiKey;
        String body = fetchUrl(url);
        if (body == null) throw new RuntimeException("Failed to fetch token info");

        JsonNode root = mapper.readTree(body);
        String status = root.path("status").asText("0");
        if (!"1".equals(status)) {
            String message = root.path("result").asText("Unknown error");
            throw new RuntimeException(message);
        }

        JsonNode result = root.path("result");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", result.path("tokenName").asText(""));
        info.put("symbol", result.path("tokenSymbol").asText(""));
        info.put("totalSupply", formatSupply(result.path("totalSupply").asText("0"), result.path("divisor").asText("18")));
        info.put("decimals", result.path("divisor").asText("18"));
        info.put("contractAddress", contractAddress);
        return info;
    }

    /**
     * Get top 10 token holders from Etherscan
     */
    public List<Map<String, Object>> getTokenHolders(String contractAddress) throws Exception {
        String url = ETHERSCAN_API + "?module=stats&action=tokenholderlist&contractAddress=" + contractAddress
                + "&page=1&offset=10&apikey=" + apiKey;
        String body = fetchUrl(url);
        if (body == null) throw new RuntimeException("Failed to fetch holders");

        JsonNode root = mapper.readTree(body);
        String status = root.path("status").asText("0");
        if (!"1".equals(status)) {
            String message = root.path("result").asText("Unknown error");
            throw new RuntimeException(message);
        }

        JsonNode items = root.path("result");
        if (!items.isArray()) throw new RuntimeException("Unexpected response format");

        List<Map<String, Object>> holders = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), 10); i++) {
            JsonNode node = items.get(i);
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("address", node.path("TokenAddress").asText(""));
            holder.put("quantity", node.path("Quantity").asText("0"));
            holders.add(holder);
        }

        // Calculate percentages
        double total = holders.stream()
                .mapToDouble(h -> parseQuantity((String) h.get("quantity")))
                .sum();
        for (Map<String, Object> holder : holders) {
            double qty = parseQuantity((String) holder.get("quantity"));
            holder.put("percentage", total > 0 ? Math.round(qty / total * 10000.0) / 100.0 : 0.0);
        }

        return holders;
    }

    /**
     * Save holder snapshot to database
     */
    public void saveSnapshot(String contractAddress, List<Map<String, Object>> holders) {
        LocalDate today = LocalDate.now();
        for (Map<String, Object> holder : holders) {
            TokenHolderSnapshot snapshot = new TokenHolderSnapshot();
            snapshot.setContractAddress(contractAddress.toLowerCase());
            snapshot.setHolderAddress((String) holder.get("address"));
            snapshot.setBalance((String) holder.get("quantity"));
            snapshot.setPercentage((Double) holder.get("percentage"));
            snapshot.setSnapshotDate(today);
            snapshotRepository.save(snapshot);
        }
        log.info("Saved {} holder snapshots for {} on {}", holders.size(), contractAddress, today);
    }

    /**
     * Get 7-day holder changes by comparing latest and 7-day-ago snapshots
     */
    public List<Map<String, Object>> getHistoryChanges(String contractAddress, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        List<TokenHolderSnapshot> snapshots = snapshotRepository
                .findByContractAddressAndSnapshotDateBetween(
                        contractAddress.toLowerCase(), start, end);

        if (snapshots.isEmpty()) return Collections.emptyList();

        // Group by date
        Map<LocalDate, Map<String, TokenHolderSnapshot>> byDate = new TreeMap<>();
        for (TokenHolderSnapshot s : snapshots) {
            byDate.computeIfAbsent(s.getSnapshotDate(), k -> new HashMap<>())
                    .put(s.getHolderAddress().toLowerCase(), s);
        }

        if (byDate.size() < 2) return Collections.emptyList();

        // Compare oldest vs newest in range
        LocalDate firstDate = byDate.keySet().iterator().next();
        LocalDate lastDate = byDate.keySet().stream().reduce((a, b) -> b).get();
        Map<String, TokenHolderSnapshot> first = byDate.get(firstDate);
        Map<String, TokenHolderSnapshot> latest = byDate.get(lastDate);

        // Build change list from latest snapshot
        List<Map<String, Object>> changes = new ArrayList<>();
        Set<String> allAddresses = new HashSet<>();
        allAddresses.addAll(latest.keySet());
        allAddresses.addAll(first.keySet());

        for (String addr : allAddresses) {
            TokenHolderSnapshot current = latest.get(addr);
            TokenHolderSnapshot previous = first.get(addr);

            Map<String, Object> change = new LinkedHashMap<>();
            change.put("address", addr);
            change.put("currentQuantity", current != null ? current.getBalance() : "0");
            change.put("currentPercentage", current != null ? current.getPercentage() : 0.0);
            change.put("previousQuantity", previous != null ? previous.getBalance() : "0");
            change.put("previousPercentage", previous != null ? previous.getPercentage() : 0.0);

            double changePct = 0;
            if (current != null && previous != null) {
                changePct = Math.round((current.getPercentage() - previous.getPercentage()) * 100.0) / 100.0;
            } else if (current != null) {
                changePct = current.getPercentage();
            } else if (previous != null) {
                changePct = -previous.getPercentage();
            }
            change.put("changePercentage", changePct);
            changes.add(change);
        }

        // Sort by current percentage descending
        changes.sort((a, b) -> Double.compare(
                (double) (b.get("currentPercentage")),
                (double) (a.get("currentPercentage"))
        ));

        return changes;
    }

    private String fetchUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("Etherscan HTTP {} from {}", code, urlStr);
                return null;
            }

            return new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Etherscan fetch failed: {} - {}", urlStr, e.getMessage());
            return null;
        }
    }

    private String formatSupply(String rawSupply, String decimals) {
        try {
            BigInteger supply = new BigInteger(rawSupply);
            int dec = Integer.parseInt(decimals);
            if (dec == 0) return rawSupply;
            BigInteger divisor = BigInteger.TEN.pow(dec);
            BigInteger integerPart = supply.divide(divisor);
            BigInteger fractionalPart = supply.remainder(divisor);
            String fracStr = String.format("%0" + dec + "d", fractionalPart);
            // Trim trailing zeros
            fracStr = fracStr.replaceAll("0+$", "");
            if (fracStr.isEmpty()) return integerPart.toString();
            return integerPart + "." + fracStr;
        } catch (Exception e) {
            return rawSupply;
        }
    }

    private double parseQuantity(String qty) {
        try {
            return new java.math.BigDecimal(qty).doubleValue();
        } catch (Exception e) {
            return 0;
        }
    }
}
