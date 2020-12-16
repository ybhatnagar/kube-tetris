# Kube-tetris

[![](https://github.com/ybhatnagar/kube-tetris/workflows/Java%20CI/badge.svg)]
(https://github.com/ybhatnagar/kube-tetris/actions)


Tetris is an extension to kubernetes that attempts to handle the fragmentation of available resources on the nodes. It provides a plan for pod migrations that will consolidate the chunks of available resouces across nodes together. This will help in more efficient utilization of cluster resources. 

Tetris also has a feature to avoid such a resource fragmentation by periodically performing a swap or migration of pods across nodes to keep a healthy balance of different type of resouce requesting pods together on a node.

## Overview
- Tetris uses the specified pod requests and limits to understand the resource requirements.
- It takes a snapshot of the kubernetes cluster in memory before any migration.

## Features

- Tetris can "try" to fit a given pod which is in pending state due to lack of available resources (cpu/memory).
If it is possible, it tries to perform strategic migrations of one or more pods to consolidate available resources together on a given node, where the pod can be placed.

- Tetris can "try" to avoid fragmentation by keeping a healthy balance of different type of resource consuming workloads on a node, (Currently supporting cpu/memory)


## Prerequisites

- Java 8 or higher
- Kubeconfig of the kubernetes cluster
- Kube proxy to access the resources and perform delete/create permissions


## Setup and Installation

./gradlew shadowJar
cd build/libs

- For placing a pod named "zombie" which is currently pending:

java -jar kube-tetris-1.0-SNAPSHOT-all.jar -Dplace -Dpod=zombie -DproxyUrl=http://localhost:9900

Note that only one pod should be pending in the cluster due to resources.

- For attempting balancing of resources in the nodes:

java -jar kube-tetris-1.0-SNAPSHOT-all.jar kube-tetris-1.0-SNAPSHOT-all.jar -Dbalance -Diterations=50


