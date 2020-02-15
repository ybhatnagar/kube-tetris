#!/usr/bin/env bash

kubectl --kubeconfig /Users/hkatyal/workspace/k8s-forum/kube-tetris/kubeconfig delete pod --all

kubectl --kubeconfig /Users/hkatyal/workspace/k8s-forum/kube-tetris/kubeconfig create -f hemani.json -f hemani2.json -f yash.json -f yash2.json -f niana.json  -f niana2.json