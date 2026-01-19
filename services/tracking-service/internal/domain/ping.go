package domain

import "time"

// Ping is one driver GPS sample.
type Ping struct {
	DriverID  string    `json:"driverId"`
	Lat       float64   `json:"lat"`
	Lon       float64   `json:"lon"`
	Heading   float64   `json:"heading,omitempty"`
	SpeedMps  float64   `json:"speedMps,omitempty"`
	AccuracyM float64   `json:"accuracyM,omitempty"`
	Timestamp time.Time `json:"timestamp"`
}

// NearestQuery asks for available drivers within radiusMeters of (lat, lon).
type NearestQuery struct {
	Lat           float64 `json:"lat"`
	Lon           float64 `json:"lon"`
	RadiusMeters  int     `json:"radiusMeters"`
	Limit         int     `json:"limit"`
}

// NearbyDriver is one result of a nearest query.
type NearbyDriver struct {
	DriverID    string  `json:"driverId"`
	DistanceM   float64 `json:"distanceMeters"`
	Lat         float64 `json:"lat"`
	Lon         float64 `json:"lon"`
}
