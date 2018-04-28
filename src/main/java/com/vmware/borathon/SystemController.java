package com.vmware.borathon;

import java.util.List;

public interface SystemController {
    void addNode(Node node);
    void removeNode(Node node);
    List<Node> getNodes();
    List<Node> getNodesSortedByRatio();
    double getPivotRatio();
    double getSystemEntropy();
}
