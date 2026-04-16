package com.kilor.demo.controller;

import com.kilor.demo.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/token")
public class TokenController {

    @Autowired
    private TokenService tokenService;

    /**
     * Save holder snapshot (called by frontend after fetching from Etherscan)
     */
    @PostMapping("/snapshot")
    public Map<String, Object> saveSnapshot(@RequestBody Map<String, Object> payload) {
        try {
            String contractAddress = (String) payload.get("contractAddress");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> holders = (List<Map<String, Object>>) payload.get("holders");
            tokenService.saveSnapshot(contractAddress, holders);
            return response(true, null, null);
        } catch (Exception e) {
            return response(false, null, e.getMessage());
        }
    }

    /**
     * Get 7-day holder changes by comparing snapshots
     */
    @GetMapping("/history")
    public Map<String, Object> getHistory(@RequestParam String address,
                                          @RequestParam(defaultValue = "7") int days) {
        try {
            List<Map<String, Object>> changes = tokenService.getHistoryChanges(address, days);
            return response(true, changes, null);
        } catch (Exception e) {
            return response(false, null, e.getMessage());
        }
    }

    private Map<String, Object> response(boolean ok, Object data, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("data", data);
        result.put("error", error);
        return result;
    }
}
