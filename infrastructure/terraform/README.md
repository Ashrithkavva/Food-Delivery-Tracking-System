# Terraform — AWS infrastructure

Provisions everything the platform needs in AWS:

| Resource              | Module                      | Purpose                                  |
|-----------------------|-----------------------------|------------------------------------------|
| VPC + subnets + NAT   | `modules/vpc`               | Network, public/private split per AZ     |
| EKS cluster + nodes   | `modules/eks`               | Runs the four application services       |
| RDS PostgreSQL        | `modules/rds`               | System of record for orders and drivers  |
| ElastiCache Redis     | inline                      | Geo-index, cache, pub/sub                 |
| MSK Kafka             | inline                      | Event spine                              |

## Quickstart

```bash
cd infrastructure/terraform
terraform init
terraform plan
terraform apply

# Once the cluster is up:
$(terraform output -raw kubeconfig_command)
kubectl get nodes
```

## Estimated cost (us-east-1, on-demand)

| Component        | Approx /mo |
|------------------|-----------:|
| EKS control plane | $73        |
| 5 × t3.large     | $300       |
| RDS db.t3.medium (Multi-AZ) | $140 |
| ElastiCache cache.t3.medium × 2 | $90 |
| MSK kafka.t3.small × 3 | $250 |
| NAT × 3, EIPs, traffic | $130 |
| **Total**       | **~$1000** |

Prices change; check current rates before sharing this number.

## Production hardening you should do

- Move state to S3 with DynamoDB locking (commented backend block at the top of `main.tf`).
- Enable IAM Roles for Service Accounts (IRSA) so pods get scoped AWS permissions instead of node-wide ones.
- Add a `prod` workspace with bigger instance sizes and three NAT gateways for high availability.
- Restrict EKS public endpoint to a CIDR allow-list, or turn it off entirely and use VPN access.
- Add WAF in front of the ALB ingress.
