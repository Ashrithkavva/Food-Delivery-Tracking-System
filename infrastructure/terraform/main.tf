terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # Configure remote state in S3+DynamoDB for production use.
  # backend "s3" {
  #   bucket         = "fdt-terraform-state"
  #   key            = "global/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "fdt-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "food-delivery-tracker"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# -----------------------------------------------------------------------------
# Network
# -----------------------------------------------------------------------------
module "vpc" {
  source = "./modules/vpc"

  name               = "${var.project}-${var.environment}"
  cidr               = var.vpc_cidr
  availability_zones = var.availability_zones
}

# -----------------------------------------------------------------------------
# EKS — runs the four application services
# -----------------------------------------------------------------------------
module "eks" {
  source = "./modules/eks"

  cluster_name    = "${var.project}-${var.environment}"
  cluster_version = "1.30"
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnet_ids

  node_groups = {
    general = {
      instance_types = ["t3.large"]
      min_size       = 3
      desired_size   = 5
      max_size       = 20
    }
  }
}

# -----------------------------------------------------------------------------
# Managed data plane
# -----------------------------------------------------------------------------
module "rds_postgres" {
  source = "./modules/rds"

  identifier        = "${var.project}-${var.environment}"
  engine_version    = "16.4"
  instance_class    = "db.t3.medium"
  allocated_storage = 100
  database_name     = "fdt"
  username          = var.db_username
  vpc_id            = module.vpc.vpc_id
  subnet_ids        = module.vpc.private_subnet_ids
  allowed_cidrs     = [module.vpc.vpc_cidr]
}

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.project}-${var.environment}-redis"
  subnet_ids = module.vpc.private_subnet_ids
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${var.project}-${var.environment}"
  description                = "FDT Redis (geo, cache, pubsub)"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = "cache.t3.medium"
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [module.vpc.default_security_group_id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}

resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${var.project}-${var.environment}"
  kafka_version          = "3.7.x"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = module.vpc.private_subnet_ids
    security_groups = [module.vpc.default_security_group_id]
    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
}
