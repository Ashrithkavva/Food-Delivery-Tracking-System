# Deployment

## Local Kubernetes (kind, minikube, k3d)

```bash
# 1. Bring up the data plane
kubectl apply -k infrastructure/kubernetes/data-plane

# 2. Wait for Kafka to come up and create topics
kubectl -n fdt wait --for=condition=complete job/kafka-topic-init --timeout=180s

# 3. Apply the services
kubectl apply -k infrastructure/kubernetes/services

# 4. Port-forward locally
kubectl -n fdt port-forward svc/order-service 8081:8080
kubectl -n fdt port-forward svc/tracking-service 8082:8080
kubectl -n fdt port-forward svc/notification-service 8083:8080

# 5. Smoke test
curl -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' -d @docs/sample-order.json
```

## AWS production

### 1. Provision infrastructure

```bash
cd infrastructure/terraform
terraform init
terraform apply  # ~15 minutes
```

This creates the VPC, EKS cluster, RDS Postgres, ElastiCache Redis, and MSK Kafka.

### 2. Configure kubectl

```bash
$(terraform output -raw kubeconfig_command)
kubectl get nodes  # should see your worker nodes
```

### 3. Push images

GitHub Actions builds and pushes images to GHCR on every push to `main`. Update the `image:` references in `infrastructure/kubernetes/services/*.yaml` to your registry.

### 4. Deploy

```bash
# Wire k8s secrets to AWS Secrets Manager values
kubectl -n fdt create secret generic fdt-postgres \
  --from-literal=POSTGRES_USER=fdt \
  --from-literal=POSTGRES_PASSWORD="$(aws secretsmanager get-secret-value --secret-id fdt-dev-db-... --query SecretString --output text | jq -r .password)"

# Point the shared ConfigMap at the managed brokers
kubectl -n fdt edit configmap fdt-shared
# set KAFKA_BROKERS=<msk bootstrap brokers>
# set REDIS_HOST=<elasticache primary endpoint>

kubectl apply -k infrastructure/kubernetes/services
```

## Rollback

Each Deployment has `kubectl rollout history`. To revert:

```bash
kubectl -n fdt rollout undo deployment/order-service
```

## Observability checklist

- Cluster autoscaler enabled? Check `kubectl -n kube-system get pods -l app=cluster-autoscaler`.
- Prometheus scraping pod annotations? Check `up{job="kubernetes-pods"}`.
- Kafka consumer lag exported? Run `kafka-consumer-groups.sh --bootstrap-server <msk> --describe --group notification-service`.

## Known runbook items

**Symptom: Order is stuck in PENDING.**
Causes: outbox relay failing, driver-service unable to consume, no drivers in range.

```bash
# Is the outbox draining?
kubectl -n fdt logs deploy/order-service | grep "outbox publish failed"
# Is anyone consuming orders.events?
kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group driver-service
```

**Symptom: Customers see "Driver unavailable" even though drivers are online.**
Causes: tracking-service unhealthy, Redis geo-index empty, driver-service can't reach tracking-service.

```bash
kubectl -n fdt logs deploy/driver-service | grep "tracking-service unreachable"
kubectl -n fdt exec deploy/redis -- redis-cli ZRANGE drivers:geo:default 0 -1 WITHSCORES
```
