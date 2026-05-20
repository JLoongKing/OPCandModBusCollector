package com.example.opcua.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ModbusTcpUtilTest {

    @Test
    public void testReadModbusData() {
        try {
            // 测试读取模拟数据
            String host = "localhost"; // 替换为你的模拟软件IP
            int port = 502;
            int timeout = 5000;
            
            // 测试输入寄存器(RO) - 输出功率 (地址100, 32位浮点)
            String value1 = ModbusTcpUtil.readPoint(host, port, timeout, "30100", "dev1", "node1", "float", 32, 1.0);
            System.out.println("输出功率: " + value1);
            
            // 测试输入寄存器(RO) - 运行状态 (地址102, 16位整数)
            String value2 = ModbusTcpUtil.readPoint(host, port, timeout, "1;30102", "dev1", "node2", "uint", 16, 1.0);
            System.out.println("运行状态: " + value2);
            
            // 测试输入寄存器(RO) - AC电压 (地址110, 16位整数)
            String value3 = ModbusTcpUtil.readPoint(host, port, timeout, "1;30110", "dev1", "node3", "int", 16, 1.0);
            System.out.println("AC电压: " + value3);
            
            // 测试保持寄存器(RW) - 目标输出功率 (地址100, 32位浮点)
            String value4 = ModbusTcpUtil.readPoint(host, port, timeout, "1;40100", "dev1", "node4", "float", 32, 1.0);
            System.out.println("目标输出功率: " + value4);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}