package com.kubetetris.interaction;

import com.kubetetris.Node;
import com.kubetetris.Pod;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Component;

import java.util.List;

public interface KubernetesAccessor {
    void migratePod(String toRemove, String toNode) throws Exception;
    List<Node> getSystemSnapshot() throws Exception;
    void swapPods(Pod podA, Node nodeA, Pod podB, Node nodeB) throws Exception;
    void createPod(String onNode, V1Pod toBeCreated) throws Exception;
    boolean deletePod(V1Pod pod) throws Exception;
    V1Pod getPod(String name) throws ApiException;
    Long getPodRequestForResource(String resource, V1Pod v1Pod);
}
