Allure-Server Kubernetes Helm Chart
---

Helm chart to deploy Allure Server to Kubernetes.

### Download and install Helm

### Download chart directory

[allure-server chart](../allure-server)

### Execute commands

- `helm delete allure-server -n allure-production`  
  Delete previous chart if exist
- `cd <path_to_project>/.helm/allure-server`  
  Go to chart directory
- `helm upgrade --install allure-server . -n allure-production`  
  Install chart

### Execute commands

- Access via `ingress.host`