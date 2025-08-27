import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FullMeshTopologyGenerator {
    
    // 新增：记录每个端口的IP（用于后续写入ports.txt）
    private static List<String> portIpRecords = new ArrayList<>();
    
    // 生成包含双向连接的full mesh拓扑 + 记录端口IP
    public static void generateBidirectionalFullMesh(int nodeCount, String topoFilename, String portFilename) {
        if (nodeCount < 2) {
            System.out.println("节点数量必须至少为2才能形成全互联拓扑");
            return;
        }
        
        // 创建节点列表（node1, node2, ..., nodeN）
        List<String> nodes = new ArrayList<>();
        for (int i = 1; i <= nodeCount; i++) {
            nodes.add("node" + i);
        }
        
        // 记录每个节点已使用的端口数量（确保端口不重复）
        Map<String, Integer> nodePortCounter = new HashMap<>();
        for (String node : nodes) {
            nodePortCounter.put(node, 1); // 每个节点从端口1开始
        }
        
        try (FileWriter topoWriter = new FileWriter(topoFilename);
             FileWriter portWriter = new FileWriter(portFilename)) { // 新增：ports.txt的写入流

            // 为每个节点对生成双向连接 + 分配端口IP
            for (int i = 0; i < nodeCount; i++) {
                String node1 = nodes.get(i);
                int node1Num = i + 1; // 节点编号（node1→1，node2→2...）
                
                for (int j = i + 1; j < nodeCount; j++) {
                    String node2 = nodes.get(j);
                    int node2Num = j + 1; // 节点2的编号
                    
                    // 分配端口（每个节点的端口独立递增）
                    int port1 = nodePortCounter.get(node1);
                    int port2 = nodePortCounter.get(node2);
                    String port1Str = "Ethernet0/" + port1;
                    String port2Str = "Ethernet0/" + port2;
                    
                    // 新增：为两个端口分配IP（规则：10.节点编号.端口编号.1）
                    String ip1 = "10." + node1Num + "." + port1 + ".1";
                    String ip2 = "10." + node2Num + "." + port2 + ".1";
                    
                    // 生成正向/反向拓扑记录（与原逻辑一致）
                    String forwardConnection = node1 + " " + port1Str + " " + node2 + " " + port2Str;
                    String reverseConnection = node2 + " " + port2Str + " " + node1 + " " + port1Str;
                    
                    // 写入拓扑文件 + 控制台输出（与原逻辑一致）
                    topoWriter.write(forwardConnection + "\n");
                    topoWriter.write(reverseConnection + "\n");
                    System.out.println(forwardConnection);
                    System.out.println(reverseConnection);
                    
                    // 新增：记录端口IP（格式：节点名，端口名，ip）
                    String port1Record = node1 + "," + port1Str + "," + ip1;
                    String port2Record = node2 + "," + port2Str + "," + ip2;
                    portIpRecords.add(port1Record);
                    portIpRecords.add(port2Record);
                    
                    // 更新端口计数器（与原逻辑一致）
                    nodePortCounter.put(node1, port1 + 1);
                    nodePortCounter.put(node2, port2 + 1);
                }
            }
            
            // 新增：将所有端口IP记录写入ports.txt
            for (String record : portIpRecords) {
                portWriter.write(record + "\n");
            }
            
            System.out.println("\n双向全互联拓扑已成功写入到 " + topoFilename);
            System.out.println("所有节点端口IP已成功写入到 " + portFilename);
            
        } catch (IOException e) {
            System.err.println("写入文件时发生错误: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        // 可修改节点数量和输出文件名（与TopologyToForwarding的输入路径一致）
        int nodeCount = 4; // 生成4个节点的全互联拓扑
        String topoOutputFile = "./UB/topo.txt";    // 原拓扑输出文件
        String portOutputFile = "./UB/ports.txt";   // 新增端口IP输出文件
        
        // 调用修改后的方法：同时生成拓扑和端口IP
        generateBidirectionalFullMesh(nodeCount, topoOutputFile, portOutputFile);
    }
}