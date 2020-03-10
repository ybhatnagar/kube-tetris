package io.kubernetes.client.openapi.apis;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Status;
import okhttp3.Call;

import java.lang.reflect.Type;

public class CoreV1ApiOverride extends CoreV1Api{

    @Override
    public ApiResponse<V1Status> deleteNamespacedPodWithHttpInfo(String name, String namespace, String pretty, String dryRun, Integer gracePeriodSeconds, Boolean orphanDependents, String propagationPolicy, V1DeleteOptions body) throws ApiException {
        Call localVarCall = this.deleteNamespacedPodValidateBeforeCall(name, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body, (ApiCallback)null);
        Type localVarReturnType = (new TypeToken<V1Status>() {
        }).getType();
        ApiResponse response = super.getApiClient().execute(localVarCall);

        if (response.getStatusCode() ==200){
            return new ApiResponse<V1Status>(response.getStatusCode(),response.getHeaders());
        }
        return null;
    }

    private Call deleteNamespacedPodValidateBeforeCall(String name, String namespace, String pretty, String dryRun, Integer gracePeriodSeconds, Boolean orphanDependents, String propagationPolicy, V1DeleteOptions body, ApiCallback _callback) throws ApiException {
        if (name == null) {
            throw new ApiException("Missing the required parameter 'name' when calling deleteNamespacedPod(Async)");
        } else if (namespace == null) {
            throw new ApiException("Missing the required parameter 'namespace' when calling deleteNamespacedPod(Async)");
        } else {
            Call localVarCall = this.deleteNamespacedPodCall(name, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body, _callback);
            return localVarCall;
        }
    }

}
