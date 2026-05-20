package com.example.opcua.service;

import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.ip.IpParameters;
import com.serotonin.modbus4j.locator.BaseLocator;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modbus TCP 通信工具类
 * <p>
 * 基于 modbus4j 库实现 Modbus TCP 协议通信，提供高性能、高可靠性的点位数据读取功能。
 * 支持连接池复用、自动重试、智能字节序检测等特性。
 * <p>
 * 地址格式支持：
 * <ul>
 *   <li>{@code 40001} - 保持寄存器（默认从站ID=1）</li>
 *   <li>{@code 30001} - 输入寄存器（默认从站ID=1）</li>
 *   <li>{@code 1;40001} 或 {@code 1:40001} - 指定从站ID的保持寄存器</li>
 *   <li>{@code 100} - 直接地址（默认保持寄存器，从站ID=1）</li>
 * </ul>
 * <p>
 * 地址范围约定：
 * <ul>
 *   <li>30001-39999 - 输入寄存器（Input Registers），使用功能码 FC4</li>
 *   <li>40001-49999 - 保持寄存器（Holding Registers），使用功能码 FC3</li>
 *   <li>1-9999 - 直接地址，默认使用保持寄存器（功能码 FC3）</li>
 * </ul>
 * <p>
 * 数据类型支持：
 * <ul>
 *   <li>int - 有符号整数（16位或32位）</li>
 *   <li>uint - 无符号整数（16位或32位）</li>
 *   <li>float - 浮点数（32位）</li>
 * </ul>
 *
 * @author Trae AI
 * @version 2.0
 * @since 2026-05-19
 */
@Slf4j
public final class ModbusTcpUtil {

    /**
     * Modbus TCP 默认端口
     */
    private static final int MODBUS_DEFAULT_PORT = 502;

    /**
     * Modbus 工厂实例（单例）
     */
    private static final ModbusFactory MODBUS_FACTORY = new ModbusFactory();

    /**
     * 连接缓存池，用于复用 Modbus Master 连接
     * <p>
     * 键格式：host:port
     * 值：ModbusMaster 实例
     */
    private static final Map<String, ModbusMaster> CONNECTION_CACHE = new ConcurrentHashMap<>();

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRIES = 3;

    /**
     * 地址分隔符数组
     */
    private static final String[] SEPARATORS = {";", ":"};

    /**
     * 私有构造函数，防止实例化
     */
    private ModbusTcpUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 读取单个点位数据
     * <p>
     * 支持自动重试、连接复用、字节序自动检测等特性
     *
     * @param host         Modbus TCP 服务器地址
     * @param port         Modbus TCP 服务器端口
     * @param timeoutMs    超时时间（毫秒）
     * @param address      点位地址（支持从站前缀格式）
     * @param devId        设备ID（预留参数，暂未使用）
     * @param nodeId       节点ID（预留参数，暂未使用）
     * @param dataType     数据类型：int, uint, float
     * @param bitLength    数据位数：16, 32
     * @param scaleFactor  比例系数（可选，默认1.0）
     * @return 解析后的字符串值
     * @throws Exception 通信异常或数据解析异常
     */
    public static String readPoint(String host, int port, int timeoutMs, String address,
                                   String devId, String nodeId, String dataType, Integer bitLength, Double scaleFactor) throws Exception {
        // 参数校验
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("服务器地址不能为空");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("点位地址不能为空");
        }

        // 解析从站ID和寄存器地址
        int[] parsed = parseSlaveAndRegister(address.trim());
        int slaveId = parsed[0];
        int refNumber = parsed[1];

        // 根据地址范围确定寄存器类型和起始地址
        ModbusAddressInfo addressInfo = resolveAddress(refNumber);

        // 设置用户指定的数据类型和位数
        addressInfo.dataType = convertToModbus4jDataType(dataType, bitLength);

        log.debug("[Modbus读取请求] 地址: {}, 从站: {}, 寄存器类型: {}, 起始地址: {}, 数据类型: {}",
                address, slaveId, addressInfo.registerType, addressInfo.startAddress, addressInfo.dataType);

        Exception lastException = null;

        // 自动重试机制
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            ModbusMaster master = null;
            try {
                // 获取或创建连接
                String connectionKey = host + ":" + port;
                master = getOrCreateMaster(connectionKey, host, port, timeoutMs);

                // 读取数据（先尝试正常字节序）
                Number value = readWithByteOrder(master, slaveId, addressInfo, false);

                // 正常字节序读取失败，尝试调换字节序
                if (value == null) {
                    log.debug("正常字节序读取失败，尝试调换字节序");
                    value = readWithByteOrder(master, slaveId, addressInfo, true);
                }

                // 处理结果
                if (value == null) {
                    log.warn("所有字节序读取均失败，返回默认值0");
                    return "0";
                }

                // 记录读取完成的时间（作为数据获取时间）
                LocalDateTime readTime = LocalDateTime.now();
                
                // 返回结果和时间戳
                return processResult(value, scaleFactor) + "|" + readTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            } catch (Exception e) {
                lastException = e;
                log.warn("读取失败 (尝试 {}/{}): {}", retry + 1, MAX_RETRIES, e.getMessage());

                // 清除无效连接
                if (master != null) {
                    invalidateConnection(host, port);
                }

                // 最后一次重试失败，抛出异常
                if (retry == MAX_RETRIES - 1) {
                    throw e;
                }

                // 短暂延迟后重试
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw lastException != null ? lastException : new Exception("读取失败，未知错误");
    }

    /**
     * 使用指定字节序读取数据
     *
     * @param master       Modbus Master 实例
     * @param slaveId      从站ID
     * @param addressInfo  地址信息
     * @param swapWord     是否调换字节序
     * @return 读取到的数值
     * @throws Exception 读取异常
     */
    private static Number readWithByteOrder(ModbusMaster master, int slaveId, ModbusAddressInfo addressInfo, boolean swapWord) throws Exception {
        try {
            BaseLocator<Number> locator = createLocator(slaveId, addressInfo, swapWord);
            Number value = master.getValue(locator);
            log.debug("读取成功 [字节序{}] 值: {}, 类型: {}",
                    swapWord ? "调换" : "正常", value, value != null ? value.getClass().getSimpleName() : "null");
            return value;
        } catch (Exception e) {
            log.debug("读取失败 [字节序{}]: {}", swapWord ? "调换" : "正常", e.getMessage());
            return null;
        }
    }

    /**
     * 处理读取结果，应用比例系数并格式化
     *
     * @param value        原始数值
     * @param scaleFactor  比例系数
     * @return 格式化后的字符串
     */
    private static String processResult(Number value, Double scaleFactor) {
        double result = value.doubleValue();

        // 应用比例系数
        if (scaleFactor != null && scaleFactor != 1.0) {
            result *= scaleFactor;
            log.debug("应用比例系数 {}，结果从 {} 变为 {}", scaleFactor, value.doubleValue(), result);
        }

        // 格式化输出
        return formatResult(result);
    }

    /**
     * 获取或创建 Modbus Master 连接
     * <p>
     * 优先从缓存获取连接，缓存不存在或无效时创建新连接
     *
     * @param key          缓存键（host:port）
     * @param host         服务器地址
     * @param port         服务器端口
     * @param timeoutMs    超时时间
     * @return Modbus Master 实例
     * @throws Exception 连接创建失败
     */
    private static ModbusMaster getOrCreateMaster(String key, String host, int port, int timeoutMs) throws Exception {
        // 从缓存获取连接
        ModbusMaster master = CONNECTION_CACHE.get(key);
        if (master != null) {
            try {
                // 尝试使用连接，验证有效性
                return master;
            } catch (Exception e) {
                log.debug("缓存连接无效，重新创建: {}", e.getMessage());
                invalidateConnection(key);
            }
        }

        // 创建新连接
        return createNewConnection(key, host, port, timeoutMs);
    }

    /**
     * 创建新的 Modbus Master 连接
     *
     * @param key          缓存键
     * @param host         服务器地址
     * @param port         服务器端口
     * @param timeoutMs    超时时间
     * @return Modbus Master 实例
     * @throws Exception 连接创建失败
     */
    private static ModbusMaster createNewConnection(String key, String host, int port, int timeoutMs) throws Exception {
        IpParameters params = new IpParameters();
        params.setHost(host);
        params.setPort(port);
        params.setEncapsulated(false);

        ModbusMaster newMaster = MODBUS_FACTORY.createTcpMaster(params, false);
        newMaster.setTimeout(timeoutMs);
        newMaster.setRetries(0); // 由外层控制重试

        try {
            newMaster.init();
            CONNECTION_CACHE.put(key, newMaster);
            log.debug("创建新连接成功: {}", key);
            return newMaster;
        } catch (Exception e) {
            log.error("创建连接失败: {}", e.getMessage(), e);
            try {
                newMaster.destroy();
            } catch (Exception ex) {
                log.debug("销毁失败连接时出错: {}", ex.getMessage());
            }
            throw new Exception("无法创建Modbus连接: " + e.getMessage(), e);
        }
    }

    /**
     * 失效指定主机和端口的连接
     *
     * @param host 服务器地址
     * @param port 服务器端口
     */
    private static void invalidateConnection(String host, int port) {
        invalidateConnection(host + ":" + port);
    }

    /**
     * 失效指定键的连接
     *
     * @param key 缓存键
     */
    private static void invalidateConnection(String key) {
        ModbusMaster master = CONNECTION_CACHE.remove(key);
        if (master != null) {
            try {
                master.destroy();
                log.debug("连接已失效并销毁: {}", key);
            } catch (Exception e) {
                log.warn("销毁连接失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 转换数据类型为 modbus4j 兼容格式
     *
     * @param dataType     用户指定的数据类型
     * @param bitLength    用户指定的数据位数
     * @return modbus4j 兼容的数据类型字符串
     */
    private static String convertToModbus4jDataType(String dataType, Integer bitLength) {
        // 默认值处理
        if (dataType == null) {
            dataType = "float";
        }
        if (bitLength == null) {
            bitLength = 32;
        }

        dataType = dataType.toLowerCase();

        // 转换为 modbus4j 兼容格式
        switch (dataType) {
            case "float":
                return "float32";
            case "int":
                return bitLength == 32 ? "int32" : "int16";
            case "uint":
                return bitLength == 32 ? "uint32" : "uint16";
            default:
                log.warn("不支持的数据类型: {}，使用默认类型 float32", dataType);
                return "float32";
        }
    }

    /**
     * 读取单个点位数据（使用默认端口和超时）
     *
     * @param host         Modbus TCP 服务器地址
     * @param address      点位地址
     * @param devId        设备ID（预留参数）
     * @param nodeId       节点ID（预留参数）
     * @return 解析后的字符串值
     * @throws Exception 通信异常
     */
    public static String readPoint(String host, String address, String devId, String nodeId) throws Exception {
        return readPoint(host, MODBUS_DEFAULT_PORT, 5000, address, devId, nodeId, null, null, null);
    }

    /**
     * 解析从站ID和寄存器地址
     *
     * @param address 地址字符串
     * @return int[2] {从站ID, 寄存器号}
     */
    private static int[] parseSlaveAndRegister(String address) {
        int unitId = 1;
        String numPart = address;

        // 支持的分隔符：分号或冒号
        for (String sep : SEPARATORS) {
            if (address.contains(sep)) {
                String[] parts = address.split(sep, 2);
                try {
                    unitId = Integer.parseInt(parts[0].trim());
                    numPart = parts[1].trim();
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("无效的从站ID格式: " + parts[0]);
                }
                break;
            }
        }

        try {
            int registerNumber = Integer.parseInt(numPart.trim());
            return new int[]{unitId, registerNumber};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的寄存器地址: " + numPart);
        }
    }

    /**
     * 根据寄存器号解析数据类型和起始地址
     *
     * @param refNumber 寄存器号
     * @return Modbus地址信息
     */
    private static ModbusAddressInfo resolveAddress(int refNumber) {
        if (refNumber >= 40001 && refNumber <= 49999) {
            // 保持寄存器 (Holding Registers)
            return new ModbusAddressInfo("holding_register", refNumber - 40000, "float32");
        } else if (refNumber >= 30001 && refNumber <= 39999) {
            // 输入寄存器 (Input Registers)
            return new ModbusAddressInfo("input_register", refNumber - 30000, "float32");
        } else if (refNumber >= 10001 && refNumber <= 19999) {
            // 离散输入 (Discrete Inputs)
            return new ModbusAddressInfo("input_register", refNumber - 10000, "int16");
        } else if (refNumber >= 1 && refNumber <= 9999) {
            // 直接地址 - 默认使用保持寄存器
            return new ModbusAddressInfo("holding_register", refNumber, "float32");
        } else {
            throw new IllegalArgumentException("不支持的寄存器地址范围: " + refNumber);
        }
    }

    /**
     * 创建数据定位器
     *
     * @param slaveId     从站ID
     * @param addressInfo 地址信息
     * @param swapWord    是否调换字节序
     * @return 数据定位器
     */
    private static BaseLocator<Number> createLocator(int slaveId, ModbusAddressInfo addressInfo, boolean swapWord) {
        int dataTypeCode;

        // 根据数据类型和字节序选择对应的类型
        switch (addressInfo.dataType) {
            case "float32":
                dataTypeCode = swapWord ?
                        com.serotonin.modbus4j.code.DataType.FOUR_BYTE_FLOAT_SWAPPED :
                        com.serotonin.modbus4j.code.DataType.FOUR_BYTE_FLOAT;
                break;
            case "int32":
                dataTypeCode = swapWord ?
                        com.serotonin.modbus4j.code.DataType.FOUR_BYTE_INT_SIGNED_SWAPPED :
                        com.serotonin.modbus4j.code.DataType.FOUR_BYTE_INT_SIGNED;
                break;
            case "uint32":
                dataTypeCode = swapWord ?
                        com.serotonin.modbus4j.code.DataType.FOUR_BYTE_INT_UNSIGNED_SWAPPED :
                        com.serotonin.modbus4j.code.DataType.FOUR_BYTE_INT_UNSIGNED;
                break;
            case "int16":
                dataTypeCode = com.serotonin.modbus4j.code.DataType.TWO_BYTE_INT_SIGNED;
                break;
            case "uint16":
                dataTypeCode = com.serotonin.modbus4j.code.DataType.TWO_BYTE_INT_UNSIGNED;
                break;
            default:
                log.warn("不支持的数据类型: {}，使用默认类型 uint16", addressInfo.dataType);
                dataTypeCode = com.serotonin.modbus4j.code.DataType.TWO_BYTE_INT_UNSIGNED;
        }

        // 根据寄存器类型创建定位器
        if ("holding_register".equals(addressInfo.registerType)) {
            return BaseLocator.holdingRegister(slaveId, addressInfo.startAddress, dataTypeCode);
        } else {
            return BaseLocator.inputRegister(slaveId, addressInfo.startAddress, dataTypeCode);
        }
    }

    /**
     * 格式化结果数值
     * <p>
     * 整数格式化为整数字符串，浮点数保留最多6位小数
     *
     * @param value 原始数值
     * @return 格式化后的字符串
     */
    private static String formatResult(double value) {
        // 整数处理
        if (value == (long) value) {
            return String.valueOf((long) value);
        }

        // 浮点数处理，保留最多6位小数，去除末尾的0
        return String.format("%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    /**
     * 关闭所有缓存连接，用于应用程序关闭时的资源清理
     */
    public static void shutdown() {
        log.info("开始关闭所有Modbus连接...");
        for (String key : CONNECTION_CACHE.keySet()) {
            invalidateConnection(key);
        }
        CONNECTION_CACHE.clear();
        log.info("所有Modbus连接已关闭");
    }

    /**
     * Modbus 地址信息内部类
     * <p>
     * 用于存储寄存器类型、起始地址和数据类型等信息
     */
    private static class ModbusAddressInfo {
        final String registerType;  // "holding_register" 或 "input_register"
        final int startAddress;     // 寄存器起始地址
        String dataType;            // 数据类型

        /**
         * 构造函数
         *
         * @param registerType 寄存器类型
         * @param startAddress 起始地址
         * @param dataType     数据类型
         */
        ModbusAddressInfo(String registerType, int startAddress, String dataType) {
            this.registerType = registerType;
            this.startAddress = startAddress;
            this.dataType = dataType;
        }
    }
}
