import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FullMesh2DTopologyGenerator {
    
    // 记录每个端口的IP（用于写入ports.txt）
    private static List<String> portIpRecords = new ArrayList<>();
    // 存储节点的行列位置（修复：提升为类级变量，确保所有方法可访问）
    private static Map<String, int[]> nodePosMap = new HashMap<>();
    
    /**
     * 生成二维全互联拓扑（同一行和同一列全连接，斜对角不连接）
     * @param rows 行数
     * @param cols 列数
     * @param topoFilename 拓扑文件输出路径
     * @param portFilename 端口IP文件输出路径
     */
    public static void generate2DFullMesh(int rows, int cols, String topoFilename, String portFilename) {
        if (rows < 1 || cols < 1) {
            System.out.println("行数和列数必须至少为1");
            return;
        }
        if (rows == 1 && cols == 1) {
            System.out.println("单个节点无法形成拓扑");
            return;
        }
        
        // 清空静态集合（避免多次调用时数据残留）
        nodePosMap.clear();
        portIpRecords.clear();
        
        // 1. 生成二维节点列表（命名格式：node_i_j，i=行号，j=列号）
        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String node = "node_" + i + "_" + j;
                nodes.add(node);
                nodePosMap.put(node, new int[]{i, j}); // 记录（行，列）位置
            }
        }
        
        // 2. 记录每个节点的端口计数器（确保端口不重复）
        Map<String, Integer> nodePortCounter = new HashMap<>();
        for (String node : nodes) {
            nodePortCounter.put(node, 1); // 每个节点从端口1开始
        }
        
        try (FileWriter topoWriter = new FileWriter(topoFilename);
             FileWriter portWriter = new FileWriter(portFilename)) {

            // 3. 处理行内连接：同一行的节点两两连接（i相同，j不同）
            System.out.println("=== 行内连接 ===");
            for (int i = 0; i < rows; i++) { // 遍历每一行
                for (int j = 0; j < cols; j++) { // 遍历行内每个节点
                    String node1 = "node_" + i + "_" + j;
                    // 连接同一行中右侧的节点（j' > j，避免重复连接）
                    for (int j2 = j + 1; j2 < cols; j2++) {
                        String node2 = "node_" + i + "_" + j2;
                        connectNodes(node1, node2, nodePortCounter, topoWriter);
                    }
                }
            }
            
            // 4. 处理列内连接：同一列的节点两两连接（j相同，i不同）
            System.out.println("\n=== 列内连接 ===");
            for (int j = 0; j < cols; j++) { // 遍历每一列
                for (int i = 0; i < rows; i++) { // 遍历列内每个节点
                    String node1 = "node_" + i + "_" + j;
                    // 连接同一列中下方的节点（i' > i，避免重复连接）
                    for (int i2 = i + 1; i2 < rows; i2++) {
                        String node2 = "node_" + i2 + "_" + j;
                        connectNodes(node1, node2, nodePortCounter, topoWriter);
                    }
                }
            }
            
            // 5. 将端口IP记录写入ports.txt
            for (String record : portIpRecords) {
                portWriter.write(record + "\n");
            }
            
            System.out.println("\n二维全互联拓扑已写入 " + topoFilename);
            System.out.println("端口IP已写入 " + portFilename);
            
        } catch (IOException e) {
            System.err.println("写入文件错误: " + e.getMessage());
        }
    }
    
    /**
     * 生成两个节点之间的双向连接，并记录端口IP
     */
    private static void connectNodes(String node1, String node2, 
                                   Map<String, Integer> nodePortCounter, 
                                   FileWriter topoWriter) throws IOException {
        // 分配端口（每个节点独立递增）
        int port1 = nodePortCounter.get(node1);
        int port2 = nodePortCounter.get(node2);
        String port1Str = "Ethernet0/" + port1;
        String port2Str = "Ethernet0/" + port2;
        
        // 生成端口IP（规则：10.行号.列号.端口号，基于节点的行列位置）
        int[] pos1 = nodePosMap.get(node1); // 从类级变量获取位置（已修复空指针）
        int[] pos2 = nodePosMap.get(node2);
        String ip1 = "10." + pos1[0] + "." + pos1[1] + "." + port1; // 10.行.列.端口
        String ip2 = "10." + pos2[0] + "." + pos2[1] + "." + port2;
        
        // 生成正向和反向连接记录
        String forward = node1 + " " + port1Str + " " + node2 + " " + port2Str;
        String reverse = node2 + " " + port2Str + " " + node1 + " " + port1Str;
        
        // 写入拓扑文件并输出到控制台
        topoWriter.write(forward + "\n");
        topoWriter.write(reverse + "\n");
        System.out.println(forward);
        System.out.println(reverse);
        
        // 记录端口IP（格式：节点,端口,IP）
        portIpRecords.add(node1 + "," + port1Str + "," + ip1);
        portIpRecords.add(node2 + "," + port2Str + "," + ip2);
        
        // 更新端口计数器
        nodePortCounter.put(node1, port1 + 1);
        nodePortCounter.put(node2, port2 + 1);
    }
    
    public static void main(String[] args) {
        // 二维拓扑参数（可修改）
        int rows = 2; // 行数
        int cols = 2; // 列数
        String topoFile = "topo.txt";    // 拓扑文件
        String portFile = "ports.txt";   // 端口IP文件
        
        // 生成二维全互联拓扑
        generate2DFullMesh(rows, cols, topoFile, portFile);
    }
}
