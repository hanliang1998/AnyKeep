import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TopologyToForwarding {
    
    // 1. 拓扑：源节点 -> 目的节点 -> 源节点的出接口（如node1→node4→Ethernet0/3）
    private static Map<String, Map<String, String>> sourceNodeToDestNode_OutPort = new HashMap<>();
    // 2. 拓扑扩展：源节点 -> 目的节点 -> 目的节点的对端接口（如node1→node4→Ethernet0/1）
    private static Map<String, Map<String, String>> sourceNodeToDestNode_PeerPort = new HashMap<>();
    // 3. 端口IP映射：节点+端口 -> IP（如node4,Ethernet0/1→10.4.1.1）
    private static Map<String, String> portIpMap = new HashMap<>();
    // 4. 所有节点集合
    private static Set<String> allNodes = new HashSet<>();
    // 5. IP转换开关（true=转整数，false=点分十进制）
    private static boolean convertIpToInt = false;
    
    /**
     * 设置IP转换开关
     */
    public static void setConvertIpToInt(boolean convert) {
        convertIpToInt = convert;
    }
    
    /**
     * 读取拓扑文件，同时记录“出接口”和“对端接口”
     */
    public static void readTopology(String topologyFile) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(topologyFile), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // 解析拓扑行：源节点 源出接口 目的节点 目的对端接口（如node1 Ethernet0/3 node4 Ethernet0/1）
                String[] parts = line.split("\\s+");
                if (parts.length != 4) {
                    System.out.println("跳过无效拓扑行: " + line);
                    continue;
                }
                String sourceNode = parts[0];  // 源节点（如node1）
                String sourceOutPort = parts[1];// 源节点出接口（如Ethernet0/3）
                String destNode = parts[2];    // 目的节点（如node4）
                String destPeerPort = parts[3];// 目的节点对端接口（如Ethernet0/1）
                
                // 添加节点到集合
                allNodes.add(sourceNode);
                allNodes.add(destNode);
                
                // 初始化映射：源节点→目的节点的出接口
                sourceNodeToDestNode_OutPort.putIfAbsent(sourceNode, new HashMap<>());
                sourceNodeToDestNode_OutPort.get(sourceNode).put(destNode, sourceOutPort);
                
                // 初始化映射：源节点→目的节点的对端接口
                sourceNodeToDestNode_PeerPort.putIfAbsent(sourceNode, new HashMap<>());
                sourceNodeToDestNode_PeerPort.get(sourceNode).put(destNode, destPeerPort);
                
            }
            System.out.println("拓扑解析完成，节点数：" + allNodes.size());
            
        } catch (IOException e) {
            System.err.println("读取拓扑文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 读取ports.txt，建立“节点+端口→IP”映射
     */
    public static void readPortIps(String portsFile) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(portsFile), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // 解析端口IP行：节点名,端口名,IP（如node4,Ethernet0/1,10.4.1.1）
                String[] parts = line.split(",");
                if (parts.length != 3) {
                    System.out.println("跳过无效端口IP行: " + line);
                    continue;
                }
                String node = parts[0];
                String port = parts[1];
                String ip = parts[2];
                
                // 存储映射（键格式：节点+端口，如"node4,Ethernet0/1"）
                portIpMap.put(node + "," + port, ip);
            }
            System.out.println("端口IP读取完成，记录数：" + portIpMap.size());
            
        } catch (IOException e) {
            System.err.println("读取端口IP文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * IP转换：点分十进制→整数（按需启用）
     */
    private static String ipToInt(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result |= Long.parseLong(parts[i]) << (24 - i * 8);
        }
        return String.valueOf(result);
    }
    
    /**
     * 根据开关返回IP格式（整数/点分十进制）
     */
    private static String getIpBySwitch(String ip) {
        return convertIpToInt ? ipToInt(ip) : ip;
    }
    
    /**
     * BFS计算最短路径：获取“源节点到每个目的节点的下一跳+出接口+对端接口”
     */
    private static void bfs(String sourceNode, 
                           Map<String, String> destNodeToNextHop,    // 目的节点→下一跳
                           Map<String, String> destNodeToOutPort,    // 目的节点→源出接口
                           Map<String, String> destNodeToPeerPort,   // 目的节点→对端接口
                           Map<String, Integer> destNodeToMetric) {  // 目的节点→跳数
        
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        // 初始化：源节点自身
        queue.add(sourceNode);
        visited.add(sourceNode);
        destNodeToMetric.put(sourceNode, 0);
        
        while (!queue.isEmpty()) {
            String currentNode = queue.poll();
            
            // 遍历当前节点的所有邻居（从拓扑映射中获取）
            Map<String, String> neighborToOutPort = sourceNodeToDestNode_OutPort.get(currentNode);
            if (neighborToOutPort == null) continue;  // 无邻居（理论上不会出现）
            
            for (Map.Entry<String, String> entry : neighborToOutPort.entrySet()) {
                String neighborNode = entry.getKey();    // 邻居节点（下一跳）
                String currentOutPort = entry.getValue();// 当前节点到邻居的出接口
                // 获取邻居节点的对端接口（从扩展拓扑映射中获取）
                String neighborPeerPort = sourceNodeToDestNode_PeerPort.get(currentNode).get(neighborNode);
                
                if (!visited.contains(neighborNode)) {
                    visited.add(neighborNode);
                    queue.add(neighborNode);
                    
                    // 1. 跳数：当前节点跳数+1
                    destNodeToMetric.put(neighborNode, destNodeToMetric.get(currentNode) + 1);
                    
                    // 2. 下一跳：
                    // - 直接邻居：下一跳=邻居节点自身
                    // - 间接邻居：下一跳=当前节点的下一跳
                    if (currentNode.equals(sourceNode)) {
                        destNodeToNextHop.put(neighborNode, neighborNode);
                    } else {
                        destNodeToNextHop.put(neighborNode, destNodeToNextHop.get(currentNode));
                    }
                    
                    // 3. 出接口：始终使用源节点到下一跳的出接口（确保路由正确转发）
                    destNodeToOutPort.put(neighborNode, sourceNodeToDestNode_OutPort.get(sourceNode).get(destNodeToNextHop.get(neighborNode)));
                    
                    // 4. 对端接口：使用邻居节点的对端接口（用于查询目的IP）
                    destNodeToPeerPort.put(neighborNode, neighborPeerPort);
                }
            }
        }
    }
    
    /**
     * 生成转发表：目的IP=对端节点端口IP，与拓扑完全匹配
     */
    public static void generateForwardingTable(String forwardingFile) {
        // 校验前置条件：拓扑和端口IP必须已读取
        if (sourceNodeToDestNode_OutPort.isEmpty() || portIpMap.isEmpty()) {
            System.out.println("错误：请先读取拓扑文件和端口IP文件！");
            return;
        }
        
        try (FileWriter writer = new FileWriter(forwardingFile, StandardCharsets.UTF_8)) {
            // 为每个节点生成转发表
            for (String sourceNode : allNodes) {
                // 存储BFS计算结果
                Map<String, String> destToNextHop = new HashMap<>();
                Map<String, String> destToOutPort = new HashMap<>();
                Map<String, String> destToPeerPort = new HashMap<>();
                Map<String, Integer> destToMetric = new HashMap<>();
                
                // 执行BFS，获取路径信息
                bfs(sourceNode, destToNextHop, destToOutPort, destToPeerPort, destToMetric);
                
                // 1. 写入本地路由（IP固定为0，避免重复）
                writer.write("+ fwd " + sourceNode + " 0 8 self 8\n");
                
                // 2. 写入到其他节点的路由（核心：目的IP=对端节点端口IP）
                for (String destNode : allNodes) {
                    if (sourceNode.equals(destNode)) continue;  // 跳过自身
                    
                    // 获取关键信息
                    String outPort = destToOutPort.get(destNode);       // 源节点出接口（如Ethernet0/3）
                    String peerNode = destToNextHop.get(destNode);      // 对端节点（如下一跳node4）
                    String peerPort = destToPeerPort.get(destNode);     // 对端节点接口（如Ethernet0/1）
                    int metric = destToMetric.get(destNode) * 8;        // 跳数*8（保持原有逻辑）
                    
                    // 计算对端节点+接口的键（用于查询目的IP）
                    String peerPortKey = peerNode + "," + peerPort;
                    String destIp = portIpMap.get(peerPortKey);          // 目的IP=对端端口IP（如10.4.1.1）
                    
                    // 校验：若未找到对端IP，跳过并警告
                    if (destIp == null) {
                        System.out.println("警告：未找到 " + peerPortKey + " 的IP，跳过路由 " + sourceNode + "→" + destNode);
                        continue;
                    }
                    
                    // 写入路由（格式：+ fwd 源节点 目的IP 32 出接口 metric）
                    writer.write(String.format("+ fwd %s %s 32 %s %d\n", 
                            sourceNode, getIpBySwitch(destIp), outPort, metric));
                }
            }
            System.out.println("转发表生成完成，路径：" + forwardingFile);
            
        } catch (IOException e) {
            System.err.println("写入转发表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        // 1. 配置文件路径（需与FullMeshTopologyGenerator生成的文件一致）
        String topologyFile = "./UB/2d/topo.txt";    // 拓扑文件（如node1 Ethernet0/3 node4 Ethernet0/1）
        String portsFile = "./UB/2d/ports.txt";      // 端口IP文件（如node4,Ethernet0/1,10.4.1.1）
        String forwardingFile = "./UB/2d/updates";   // 转发表输出文件
        
        // 2. 配置IP转换开关（false=保留点分十进制，便于验证）
        setConvertIpToInt(true);
        
        // 3. 读取拓扑和端口IP（顺序不可换）
        readTopology(topologyFile);
        readPortIps(portsFile);
        
        // 4. 生成转发表
        generateForwardingTable(forwardingFile);
    }
}