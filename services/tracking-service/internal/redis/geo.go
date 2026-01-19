// Package redis wraps go-redis with the operations the tracking service needs.
package redis

import (
	"context"
	"fmt"
	"time"

	"github.com/fdt/tracking-service/internal/config"
	"github.com/fdt/tracking-service/internal/domain"
	"github.com/redis/go-redis/v9"
)

func New(cfg *config.Config) (*redis.Client, error) {
	rdb := redis.NewClient(&redis.Options{
		Addr:         cfg.RedisAddr,
		Password:     cfg.RedisPassword,
		DialTimeout:  2 * time.Second,
		ReadTimeout:  500 * time.Millisecond,
		WriteTimeout: 500 * time.Millisecond,
		PoolSize:     32,
	})
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if err := rdb.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("redis ping: %w", err)
	}
	return rdb, nil
}

// GeoStore writes driver positions to a Redis geo index and queries them.
//
// The "geo" structure in Redis is a sorted set keyed by a geohash, so writes
// and radius searches are O(log N). One key per region keeps the working set
// small; in a real deployment we'd shard on a city or h3 cell prefix.
type GeoStore struct {
	rdb *redis.Client
	key string
}

func NewGeoStore(rdb *redis.Client) *GeoStore {
	return &GeoStore{rdb: rdb, key: "drivers:geo:default"}
}

// UpsertPosition writes the driver's current location. A side index maps
// driverId -> last update time so stale drivers can be reaped.
func (g *GeoStore) UpsertPosition(ctx context.Context, p domain.Ping) error {
	pipe := g.rdb.Pipeline()
	pipe.GeoAdd(ctx, g.key, &redis.GeoLocation{
		Name:      p.DriverID,
		Latitude:  p.Lat,
		Longitude: p.Lon,
	})
	pipe.HSet(ctx, "drivers:last_seen", p.DriverID, p.Timestamp.UnixMilli())
	pipe.Expire(ctx, "drivers:last_seen", 30*time.Minute)
	_, err := pipe.Exec(ctx)
	return err
}

// Nearest returns drivers within the radius, ordered by distance.
func (g *GeoStore) Nearest(ctx context.Context, q domain.NearestQuery) ([]domain.NearbyDriver, error) {
	if q.Limit <= 0 {
		q.Limit = 20
	}
	res, err := g.rdb.GeoSearchLocation(ctx, g.key, &redis.GeoSearchLocationQuery{
		GeoSearchQuery: redis.GeoSearchQuery{
			Longitude:  q.Lon,
			Latitude:   q.Lat,
			Radius:     float64(q.RadiusMeters),
			RadiusUnit: "m",
			Sort:       "ASC",
			Count:      q.Limit,
		},
		WithCoord: true,
		WithDist:  true,
	}).Result()
	if err != nil {
		return nil, err
	}
	out := make([]domain.NearbyDriver, 0, len(res))
	for _, r := range res {
		out = append(out, domain.NearbyDriver{
			DriverID:  r.Name,
			DistanceM: r.Dist,
			Lat:       r.Latitude,
			Lon:       r.Longitude,
		})
	}
	return out, nil
}
