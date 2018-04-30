package com.vmware.borathon.interaction;

import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.util.Collection;
import java.util.List;

public interface KubernetesAccessor {
    void migratePod(String toRemove, String toNode) throws IllegalStateException, ParseException,InterruptedException;
    List<Node> getSystemSnapshot() throws ParseException;
    void swapPods(String podA, String nodeA, String podB, String nodeB);
    void createPod(String onNode,JSONObject podConfig) throws InterruptedException,ParseException;
    boolean deletePod(String podName) throws InterruptedException;
}
