// Package main wires the tracking service together.
//
// The tracking service has three responsibilities:
//
//  1. Accept driver location pings over HTTP and write them to a Redis geo
//     index for low-latency nearest-driver queries.
//  2. Republish each ping onto Kafka so downstream consumers (notification,
//     analytics, ETA models) can react.
//  3. Expose a nearest-driver query endpoint used by the driver assignment
//     pipeline.
//
// Design notes:
//
//   - The ingest path uses a bounded in-memory channel. If Kafka stalls we
//     drop the oldest pings rather than block the HTTP handler, because a
//     stale location is worse than a missing one.
//   - Redis writes use GEOADD; reads use GEOSEARCH BYRADIUS. The whole
//     dataset is keyed by city so we never scan globally.
//   - Graceful shutdown drains both the HTTP server and the worker pool
//     before exiting.
package main

import (
	"context"
	"errors"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	"github.com/fdt/tracking-service/internal/config"
	"github.com/fdt/tracking-service/internal/handler"
	"github.com/fdt/tracking-service/internal/kafka"
	"github.com/fdt/tracking-service/internal/redis"
	"github.com/fdt/tracking-service/internal/server"
	"go.uber.org/zap"
)

func main() {
	logger, _ := zap.NewProduction()
	defer logger.Sync() //nolint:errcheck

	cfg, err := config.Load()
	if err != nil {
		logger.Fatal("config load failed", zap.Error(err))
	}
	logger.Info("starting tracking service",
		zap.String("addr", cfg.HTTPAddr),
		zap.String("kafka", cfg.KafkaBrokers),
		zap.String("redis", cfg.RedisAddr))

	rdb, err := redis.New(cfg)
	if err != nil {
		logger.Fatal("redis connect failed", zap.Error(err))
	}
	defer rdb.Close()

	producer, err := kafka.NewProducer(cfg)
	if err != nil {
		logger.Fatal("kafka producer init failed", zap.Error(err))
	}
	defer producer.Close()

	geo := redis.NewGeoStore(rdb)

	h := handler.New(geo, producer, logger)
	srv := server.New(cfg.HTTPAddr, h)

	// Run server. Surface ListenAndServe errors that aren't a clean shutdown.
	errCh := make(chan error, 1)
	go func() {
		logger.Info("http server listening", zap.String("addr", cfg.HTTPAddr))
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
	}()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	select {
	case err := <-errCh:
		logger.Error("server failed", zap.Error(err))
	case <-ctx.Done():
		logger.Info("shutdown signal received")
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", zap.Error(err))
	}
	logger.Info("bye")
}
