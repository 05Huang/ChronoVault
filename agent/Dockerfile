# Stage 1: Build
FROM golang:1.22-alpine AS build
WORKDIR /app
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o chronovault-agent .

# Stage 2: Runtime
FROM alpine:3.19
RUN apk add --no-cache ca-certificates restic

RUN addgroup -S cv && adduser -S cv -G cv

COPY --from=build /app/chronovault-agent /usr/local/bin/chronovault-agent

USER cv

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8081/health || exit 1

ENTRYPOINT ["chronovault-agent", "run"]
