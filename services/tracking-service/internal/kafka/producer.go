// Package kafka wraps segmentio/kafka-go in a small async producer.
//
// We don't want HTTP handlers to block on Kafka latency or partition leader
// election. Instead, Publish enqueues to a bounded channel and a fixed worker
// pool drains it. If the channel is full, we drop the oldest message — for
// driver-location telemetry, fresh data beats complete data.
package kafka

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"time"

	"github.com/fdt/tracking-service/internal/config"
	"github.com/fdt/tracking-service/internal/domain"
	"github.com/segmentio/kafka-go"
	"github.com/segmentio/kafka-go/compress"
	"go.uber.org/zap"
)

type Producer struct {
	writer  *kafka.Writer
	ch      chan domain.Ping
	wg      sync.WaitGroup
	logger  *zap.Logger
	dropped uint64
	mu      sync.Mutex
}

func NewProducer(cfg *config.Config) (*Producer, error) {
	if cfg.KafkaBrokers == "" {
		return nil, errors.New("KAFKA_BROKERS is empty")
	}
	w := &kafka.Writer{
		Addr:                   kafka.TCP(cfg.KafkaBrokers),
		Topic:                  cfg.KafkaTopic,
		Balancer:               &kafka.Hash{},     // partition by driverId
		RequiredAcks:           kafka.RequireAll,  // durability over throughput
		Compression:            compress.Lz4,
		BatchTimeout:           20 * time.Millisecond,
		BatchSize:              500,
		AllowAutoTopicCreation: false,
	}
	p := &Producer{
		writer: w,
		ch:     make(chan domain.Ping, cfg.IngestBuffer),
		logger: zap.NewNop(),
	}
	for i := 0; i < cfg.IngestWorkers; i++ {
		p.wg.Add(1)
		go p.worker()
	}
	return p, nil
}

// Publish enqueues a ping. Returns false if the buffer is full and the
// oldest item had to be discarded.
func (p *Producer) Publish(ping domain.Ping) bool {
	select {
	case p.ch <- ping:
		return true
	default:
		// Buffer full: drop the head, push the new one. Newer pings matter more.
		p.mu.Lock()
		select {
		case <-p.ch:
			p.dropped++
		default:
		}
		p.ch <- ping
		p.mu.Unlock()
		return false
	}
}

func (p *Producer) worker() {
	defer p.wg.Done()
	for ping := range p.ch {
		payload, err := json.Marshal(ping)
		if err != nil {
			continue
		}
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		err = p.writer.WriteMessages(ctx, kafka.Message{
			Key:   []byte(ping.DriverID),
			Value: payload,
			Time:  ping.Timestamp,
			Headers: []kafka.Header{
				{Key: "event-type", Value: []byte("DriverLocation")},
			},
		})
		cancel()
		if err != nil {
			p.logger.Warn("kafka write failed", zap.Error(err))
		}
	}
}

func (p *Producer) Close() error {
	close(p.ch)
	p.wg.Wait()
	return p.writer.Close()
}

// DroppedCount returns how many pings have been dropped due to backpressure.
func (p *Producer) DroppedCount() uint64 {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.dropped
}
