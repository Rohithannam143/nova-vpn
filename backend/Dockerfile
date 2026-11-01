FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY cmd ./cmd
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/nova-api ./cmd/api

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/nova-api /nova-api
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/nova-api"]
