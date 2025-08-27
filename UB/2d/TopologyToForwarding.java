import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TopologyToForwarding {
    
    // 拓扑：源节点 -> 邻居节点 -> 源节点的出接口
    private static Map<String, Map<String, String>> sourceToNeighbor_OutPort = new HashMap<>();
    // 拓扑扩展：源节点 -> 邻居节点 -> 邻居节点的对端接口
    private static Map<String, Map<String, String>> sourceToNeighbor_PeerPort = new HashMap<>();
    // 端口IP映射：节点+端口 -> IP
    private static Map<String, String> portIpMap = new HashMap<>();
    // 所有节点集合
    private static Set<String> allNodes = new HashSet<>();
    // IP转换开关（默认关闭）
    private static boolean convertIpToInt = false;
    
    /**
     * 设置IP转换开关
     */
    public static void setConvertIpToInt(boolean convert) {
        convertIpToInt = convert;
    }
    
    /**
     * 读取拓扑文件，记录出接口和对端接口
     */
    public static void readTopology(String topologyFile) {
        // 清空之前的数据，避免重复读取
        sourceToNeighbor_OutPort.clear();
        sourceToNeighbor_PeerPort.clear();
        allNodes.clear();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(topologyFile), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split("\\s+");
                if (parts.length != 4) {
                    System.out.println("跳过无效拓扑行: " + line);
                    continue;
                }
                String sourceNode = parts[0];
                String sourceOutPort = parts[1];
                String neighborNode = parts[2];
                String neighborPeerPort = parts[3];
                
                allNodes.add(sourceNode);
                allNodes.add(neighborNode);
                
                sourceToNeighbor_OutPort.putIfAbsent(sourceNode, new HashMap<>());
                sourceToNeighbor_OutPort.get(sourceNode).put(neighborNode, sourceOutPort);
                
                sourceToNeighbor_PeerPort.putIfAbsent(sourceNode, new HashMap<>());
                sourceToNeighbor_PeerPort.get(sourceNode).put(neighborNode, neighborPeerPort);
            }
            System.out.println("拓扑解析完成，节点数：" + allNodes.size());
            
        } catch (IOException e) {
            System.err.println("读取拓扑文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 读取ports.txt，建立节点+端口→IP映射
     */
    public static void readPortIps(String portsFile) {
        // 清空之前的数据
        portIpMap.clear();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(portsFile), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length != 3) {
                    System.out.println("跳过无效端口IP行: " + line);
                    continue;
                }
                String node = parts[0];
                String port = parts[1];
                String ip = parts[2];
                
                portIpMap.put(node + "," + port, ip);
            }
            System.out.println("端口IP读取完成，记录数：" + portIpMap.size());
            
        } catch (IOException e) {
            System.err.println("读取端口IP文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * IP转换：点分十进制→整数
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
     * 根据开关返回IP格式
     */
    private static String getIpBySwitch(String ip) {
        return convertIpToInt ? ipToInt(ip) : ip;
    }
    
    /**
     * BFS计算最短路径
     */
    private static void bfs(String sourceNode, 
                           Map<String, String> destToNextHop, 
                           Map<String, String> destToOutPort, 
                           Map<String, Integer> destToHopCount) {
        
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.add(sourceNode);
        visited.add(sourceNode);
        destToHopCount.put(sourceNode, 0);
        
        while (!queue.isEmpty()) {
            String currentNode = queue.poll();
            int currentHop = destToHopCount.get(currentNode);
            
            Map<String, String> neighbors = sourceToNeighbor_OutPort.get(currentNode);
            if (neighbors == null) continue;
            
            for (String neighbor : neighbors.keySet()) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    
                    destToHopCount.put(neighbor, currentHop + 1);
                    
                    if (currentNode.equals(sourceNode)) {
                        destToNextHop.put(neighbor, neighbor);
                        destToOutPort.put(neighbor, sourceToNeighbor_OutPort.get(sourceNode).get(neighbor));
                    } else {
                        destToNextHop.put(neighbor, destToNextHop.get(currentNode));
                        destToOutPort.put(neighbor, destToOutPort.get(currentNode));
                    }
                }
            }
        }
    }
    
    /**
     * 生成转发表：为目的节点的所有端口生成路由
     */
    public static void generateForwardingTable(String forwardingFile) {
        if (sourceToNeighbor_OutPort.isEmpty() || portIpMap.isEmpty()) {
            System.out.println("错误：请先读取拓扑和端口IP文件！");
            return;
        }
        
        try (FileWriter writer = new FileWriter(forwardingFile, StandardCharsets.UTF_8)) {
            for (String sourceNode : allNodes) {
                Map<String, String> destToNextHop = new HashMap<>();
                Map<String, String> destToOutPort = new HashMap<>();
                Map<String, Integer> destToHopCount = new HashMap<>();
                
                bfs(sourceNode, destToNextHop, destToOutPort, destToHopCount);
                
                // 本地路由
                writer.write("+ fwd " + sourceNode + " 0 8 self 8\n");
                
                // 为每个目的节点的所有端口生成路由
                for (String destNode : allNodes) {
                    if (sourceNode.equals(destNode)) continue;
                    
                    String nextHop = destToNextHop.get(destNode);
                    String outPort = destToOutPort.get(destNode);
                    Integer hopCount = destToHopCount.get(destNode);
                    
                    if (nextHop == null || outPort == null || hopCount == null) {
                        System.out.println("警告：" + sourceNode + "到" + destNode + "无可达路径，跳过");
                        continue;
                    }
                    
                    // 获取目的节点的所有端口IP
                    List<String> allDestPortIps = getAllPortIpsOfDestNode(destNode);
                    for (String destIp : allDestPortIps) {
                        int metric = hopCount;
                        writer.write(String.format("+ fwd %s %s 32 %s %d\n", 
                                sourceNode, getIpBySwitch(destIp), outPort, metric));
                    }
                }
            }
            System.out.println("转发表已写入 " + forwardingFile);
            
        } catch (IOException e) {
            System.err.println("写入转发表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取目的节点的所有端口IP
     */
    private static List<String> getAllPortIpsOfDestNode(String destNode) {
        List<String> portIps = new ArrayList<>();
        for (Map.Entry<String, String> entry : portIpMap.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(destNode + ",")) {
                portIps.add(entry.getValue());
            }
        }
        return portIps;
    }
    
    public static void main(String[] args) {
        String topologyFile = "topo.txt";
        String portsFile = "ports.txt";
        
        // 先读取拓扑和端口文件（核心修复：调整调用顺序）
        readTopology(topologyFile);
        readPortIps(portsFile);
        
        // 生成整数IP格式的转发表
        setConvertIpToInt(true);
        generateForwardingTable("updates.txt");
        
        // 生成点分十进制IP格式的转发表（无需重新读取文件，数据已在内存中）
        setConvertIpToInt(false);
        generateForwardingTable("updatesIP.txt");
    }
}
