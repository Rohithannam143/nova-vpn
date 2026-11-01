package main

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

type server struct {
	ID       string   `json:"id"`
	Name     string   `json:"name"`
	Region   string   `json:"region"`
	Country  string   `json:"country"`
	Endpoint string   `json:"endpoint"`
	Protocol string   `json:"protocol"`
}

type response struct {
	Data  any    `json:"data"`
	Error string `json:"error,omitempty"`
}

func main() {
	addr := strings.TrimSpace(os.Getenv("NOVA_API_ADDR"))
	if addr == "" {
		addr = "127.0.0.1:8080"
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, response{Data: map[string]string{
			"status": "ok",
			"service": "nova-control-plane",
			"note": "API liveness only; VPN gateway readiness is not implied.",
		}})
	})
	mux.HandleFunc("GET /api/v1/servers", listServers)

	srv := &http.Server{
		Addr:              addr,
		Handler:           securityHeaders(mux),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("NOVA control-plane API listening on %s", addr)
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

func listServers(w http.ResponseWriter, r *http.Request) {
	// Until real gateways are provisioned and health-checked, return no
	// selectable servers. Never manufacture endpoints or fake availability.
	_ = r
	writeJSON(w, http.StatusOK, response{Data: []server{}})
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, payload response) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		log.Printf("write JSON response: %v", err)
	}
}
