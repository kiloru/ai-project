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

    @GetMapping("/info")
    public Map<String, Object> getTokenInfo(@RequestParam String address) {
        try {
            Map<String, Object> data = tokenService.getTokenInfo(address);
            return response(true, data, null);
        } catch (Exception e) {
            return response(false, null, e.getMessage());
        }
    }

    @GetMapping("/holders")
    public Map<String, Object> getTokenHolders(@RequestParam String address) {
        try {
            List<Map<String, Object>> holders = tokenService.getTokenHolders(address);
            // Save snapshot after successful fetch
            tokenService.saveSnapshot(address, holders);
            return response(true, holders, null);
        } catch (Exception e) {
            return response(false, null, e.getMessage());
        }
    }

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
