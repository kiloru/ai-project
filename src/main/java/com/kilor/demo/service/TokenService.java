package com.kilor.demo.service;

import com.kilor.demo.entity.TokenHolderSnapshot;
import com.kilor.demo.repository.TokenHolderSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final TokenHolderSnapshotRepository snapshotRepository;

    public TokenService(TokenHolderSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Save holder snapshot to database (called after frontend fetches from Etherscan)
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
     * Get holder changes by comparing earliest and latest snapshots in the date range
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

        changes.sort((a, b) -> Double.compare(
                (double) (b.get("currentPercentage")),
                (double) (a.get("currentPercentage"))
        ));

        return changes;
    }
}
