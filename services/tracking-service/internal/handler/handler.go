// Package handler exposes the HTTP API.
package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/fdt/tracking-service/internal/domain"
	"github.com/fdt/tracking-service/internal/kafka"
	"github.com/fdt/tracking-service/internal/redis"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.uber.org/zap"
)

type Handler struct {
	geo      *redis.GeoStore
	producer *kafka.Producer
	logger   *zap.Logger
}

func New(geo *redis.GeoStore, p *kafka.Producer, logger *zap.Logger) http.Handler {
	h := &Handler{geo: geo, producer: p, logger: logger}

	r := chi.NewRouter()
	r.Use(middleware.RealIP)
	r.Use(middleware.RequestID)
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(10 * time.Second))

	r.Get("/healthz", h.healthz)
	r.Get("/readyz", h.readyz)
	r.Handle("/metrics", promhttp.Handler())

	r.Route("/api/v1", func(r chi.Router) {
		r.Post("/locations", h.postLocation)
		r.Get("/drivers/nearest", h.nearest)
	})

	return r
}

func (h *Handler) healthz(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("ok"))
}

func (h *Handler) readyz(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("ready"))
}

func (h *Handler) postLocation(w http.ResponseWriter, r *http.Request) {
	var p domain.Ping
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		http.Error(w, "invalid body: "+err.Error(), http.StatusBadRequest)
		return
	}
	if p.DriverID == "" {
		http.Error(w, "driverId required", http.StatusBadRequest)
		return
	}
	if p.Lat < -90 || p.Lat > 90 || p.Lon < -180 || p.Lon > 180 {
		http.Error(w, "lat/lon out of range", http.StatusBadRequest)
		return
	}
	if p.Timestamp.IsZero() {
		p.Timestamp = time.Now().UTC()
	}

	if err := h.geo.UpsertPosition(r.Context(), p); err != nil {
		http.Error(w, "geo write failed", http.StatusServiceUnavailable)
		return
	}
	accepted := h.producer.Publish(p)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusAccepted)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"accepted":     accepted,
		"recordedAt":   time.Now().UTC(),
	})
}

func (h *Handler) nearest(w http.ResponseWriter, r *http.Request) {
	lat, err := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	if err != nil {
		http.Error(w, "lat required", http.StatusBadRequest)
		return
	}
	lon, err := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if err != nil {
		http.Error(w, "lon required", http.StatusBadRequest)
		return
	}
	radius, _ := strconv.Atoi(r.URL.Query().Get("radius"))
	if radius <= 0 {
		radius = 2000
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit <= 0 {
		limit = 10
	}

	drivers, err := h.geo.Nearest(r.Context(), domain.NearestQuery{
		Lat:          lat,
		Lon:          lon,
		RadiusMeters: radius,
		Limit:        limit,
	})
	if err != nil {
		http.Error(w, "query failed", http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"drivers": drivers,
		"count":   len(drivers),
	})
}
