package config

import (
	"github.com/kelseyhightower/envconfig"
)

type Config struct {
	HTTPAddr         string `envconfig:"HTTP_ADDR" default:":8080"`
	KafkaBrokers     string `envconfig:"KAFKA_BROKERS" default:"kafka:9092"`
	KafkaTopic       string `envconfig:"KAFKA_TOPIC" default:"driver.locations"`
	RedisAddr        string `envconfig:"REDIS_ADDR" default:"redis:6379"`
	RedisPassword    string `envconfig:"REDIS_PASSWORD" default:""`
	GeoKey           string `envconfig:"GEO_KEY" default:"drivers:geo:default"`
	IngestBuffer     int    `envconfig:"INGEST_BUFFER" default:"4096"`
	IngestWorkers    int    `envconfig:"INGEST_WORKERS" default:"8"`
	NearestMaxRadius int    `envconfig:"NEAREST_MAX_RADIUS_M" default:"5000"`
}

func Load() (*Config, error) {
	var c Config
	if err := envconfig.Process("", &c); err != nil {
		return nil, err
	}
	return &c, nil
}
