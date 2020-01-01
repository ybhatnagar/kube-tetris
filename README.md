# Kube-tetris

[![](https://github.com/ybhatnagar/kube-tetris/workflows/Java%20CI/badge.svg)](https://github.com/ybhatnagar/kube-tetris/actions)


Tetris is an extension to kubernetes that attempts to handle the fragmentation of available resources on the nodes. It provides a plan for pod migrations that will consolidate the chunks of available resouces across nodes together. This will help in more efficient utilization of cluster resources. 

Tetris also has a feature to avoid such a resource fragmentation by periodically performing a swap or migration of pods across nodes to keep a healthy balance of different type of resouce requesting pods together on a node.

## Overview
- Tetris uses the specified pod requests and limits to understand the resource requirements.
- It takes a snapshot of the kubernetes cluster in memory before any migration.

## Features



## Prerequisites

- kubeconfig of the cluster (--runAs=external), or else it must be run inside the kubernetes cluster (--runAs=internal)

## Setup and Installation


