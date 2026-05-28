package com.example.opcua.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.*;

@RestController
@RequestMapping("/api/mock")
@CrossOrigin(origins = "*")
public class MockDataController {

    private final Random random = new Random();

    @GetMapping("/sensors")
    public Map<String, Object> getSensors() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("temperature", Math.round((20 + random.nextDouble() * 30) * 100.0) / 100.0);
        data.put("pressure", Math.round((100 + random.nextDouble() * 50) * 100.0) / 100.0);
        data.put("humidity", Math.round((40 + random.nextDouble() * 40) * 100.0) / 100.0);
        data.put("flowRate", Math.round((10 + random.nextDouble() * 90) * 100.0) / 100.0);

        Map<String, Object> device1 = new LinkedHashMap<>();
        device1.put("deviceId", "DEVICE_001");
        device1.put("status", "online");
        device1.put("voltage", Math.round((220 + random.nextDouble() * 10 - 5) * 100.0) / 100.0);
        device1.put("current", Math.round((5 + random.nextDouble() * 10) * 100.0) / 100.0);

        Map<String, Object> device2 = new LinkedHashMap<>();
        device2.put("deviceId", "DEVICE_002");
        device2.put("status", "online");
        device2.put("voltage", Math.round((380 + random.nextDouble() * 20 - 10) * 100.0) / 100.0);
        device2.put("current", Math.round((10 + random.nextDouble() * 20) * 100.0) / 100.0);

        data.put("devices", Arrays.asList(device1, device2));
        result.put("data", data);
        return result;
    }

    @PostMapping("/sensors")
    public Map<String, Object> postSensors() {
        return getSensors();
    }

    @GetMapping("/array")
    public Map<String, Object> getArrayData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);

        List<Map<String, Object>> items = new ArrayList<>();
        String[] names = {"温度", "压力", "湿度", "流量", "电压"};
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", names[i]);
            item.put("nodeId", "NODE_00" + (i + 1));
            item.put("value", Math.round((10 + random.nextDouble() * 90) * 100.0) / 100.0);
            item.put("unit", i < 2 ? "MPa" : i < 4 ? "%" : "V");
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", names.length);
        result.put("data", data);
        return result;
    }
}
