package com.vmware.borathon;

import java.util.List;

public interface MigrationController {
    void addNode(Node node);
    List<Node> getNodes();
}
