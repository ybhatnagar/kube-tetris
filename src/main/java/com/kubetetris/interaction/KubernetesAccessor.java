package com.kubetetris.interaction;

import com.kubetetris.Node;
import com.kubetetris.Pod;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.util.List;

public interface KubernetesAccessor {
    List<Node> populateSystem();
    void migratePod(String toRemove, String toNode) throws Exception;
    List<Node> getSystemSnapshot() throws Exception;
    void swapPods(Pod podA, Node nodeA, Pod podB, Node nodeB) throws Exception;
    void createPod(String onNode,JSONObject podConfig) throws InterruptedException,ParseException;
    boolean deletePod(String podName) throws InterruptedException;
    JSONObject readPodToBePlaced(String nodeName, long cpu, long memoryMB);
    void cleanSystem();
}
