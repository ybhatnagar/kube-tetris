#!/usr/bin/env bash

kubectl --kubeconfig /Users/hkatyal/workspace/k8s-forum/kube-tetris/kubeconfig delete pod --all

cd prob1/

kubectl --kubeconfig /Users/hkatyal/workspace/k8s-forum/kube-tetris/kubeconfig create -f apple.json -f blue.json -f carrot.json -f drums.json -f eggplant.json  -f falcon.json -f grass.json -f house.json -f ice.json