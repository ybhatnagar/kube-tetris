package com.kubetetris.collector;

/**
 * Where to find the target cluster.
 *
 * <ul>
 *   <li>{@code kubeConfigPath} — path to a kubeconfig file. Required for out-of-cluster
 *       collection; may be {@code null} if the caller supplies its own client.</li>
 *   <li>{@code contextName} — optional named context inside that kubeconfig. When
 *       {@code null} the current-context is used.</li>
 * </ul>
 */
public record KubeContext(String kubeConfigPath, String contextName) {

    public static KubeContext ofFile(String path) {
        return new KubeContext(path, null);
    }
}
